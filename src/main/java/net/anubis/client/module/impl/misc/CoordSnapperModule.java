package net.anubis.client.module.impl.misc;

import java.util.Locale;
import java.util.Optional;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.KeyInputEvent;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.input.ModuleKeybinds;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public final class CoordSnapperModule extends ClientModule {
   private static final String TARGET_LOOKED_AT = "Looked At";
   private static final String TARGET_OWN_POSITION = "Own Position";
   private static final String FORMAT_SPACES = "X Y Z";
   private static final String FORMAT_COMMAS = "X, Y, Z";
   private static final String FORMAT_TP = "/tp Command";
   private static final double STEP_PAST_BLOCK = 0.001;
   private final KeySetting key = this.addSetting("key", new KeySetting("Key", -1));
   private final ModeSetting target = this.addSetting("target", new ModeSetting("Target", "Looked At", "Looked At", "Own Position"));
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 256.0F, 16.0F, 512.0F).step(1.0F));
   private final BooleanSetting stopAtFluids = this.addSetting("stop-at-fluids", new BooleanSetting("Stop at Fluids", false));
   private final ModeSetting format = this.addSetting("format", new ModeSetting("Format", "X Y Z", "X Y Z", "X, Y, Z", "/tp Command"));
   private final BooleanSetting includeDimension = this.addSetting("include-dimension", new BooleanSetting("Include Dimension", true));
   private final BooleanSetting confirm = this.addSetting("confirm", new BooleanSetting("Chat Confirmation", true));

   public CoordSnapperModule() {
      super("coord-snapper", "Coord Snapper", "Copies the coordinates you look at or stand on with one key.", ModuleCategory.MISC);
      this.range.visibleWhen(() -> this.target.is("Looked At"));
      this.stopAtFluids.visibleWhen(() -> this.target.is("Looked At"));
      BooleanSetting notifications = this.usesNotifications();
      this.confirm.visibleWhen(notifications::get);
   }

   @Override
   protected void onEnable() {
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
   }

   private void onKey(KeyInputEvent event) {
      if (event.action == 1) {
         this.onPress(event.key);
      }
   }

   private void onMouse(MouseInputEvent event) {
      if (event.action == 1) {
         this.onPress(1000 + event.button);
      }
   }

   private void onPress(int pressed) {
      int bound = this.key.value();
      if (ModuleKeybinds.isBindable(bound) && pressed == bound) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.currentScreen == null && mc.player != null && mc.world != null) {
            this.snap(mc, mc.player, mc.world);
         }
      }
   }

   private void snap(MinecraftClient mc, ClientPlayerEntity player, ClientWorld level) {
      BlockPos pos;
      if (this.target.is("Own Position")) {
         pos = player.getBlockPos();
      } else {
         pos = this.raycast(mc, player, level);
         if (pos == null) {
            ModuleChat.send(
               this, ModuleChat.body().danger("Nothing copied").text(": no loaded block within ").value(this.range.getInt()).text(" blocks of your aim")
            );
            return;
         }

         if (this.format.is("/tp Command")) {
            pos = pos.up();
         }
      }

      String text = this.formatCoords(pos, level.getRegistryKey());
      mc.keyboard.setClipboard(text);
      if (this.confirm.get()) {
         ModuleChat.send(this, ModuleChat.body().good("Copied").text(" ").value(text));
      }
   }

   private BlockPos raycast(MinecraftClient mc, ClientPlayerEntity player, ClientWorld level) {
      FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
      Vec3d origin;
      Vec3d direction;
      if (freecam != null && freecam.isEnabled()) {
         Camera camera = mc.gameRenderer.getCamera();
         origin = camera.getCameraPos();
         direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
      } else {
         origin = player.getCameraPosVec(1.0F);
         direction = player.getRotationVec(1.0F);
      }

      Vec3d end = origin.add(direction.multiply(this.range.get()));
      FluidHandling fluids = this.stopAtFluids.get() ? FluidHandling.ANY : FluidHandling.NONE;
      RaycastContext context = new RaycastContext(origin, end, ShapeType.OUTLINE, fluids, player);
      BlockPos viewBlock = BlockPos.ofFloored(origin);
      if (startsInside(context, level, viewBlock, origin)) {
         Optional<Vec3d> exit = new Box(viewBlock).raycast(end, origin);
         if (exit.isEmpty()) {
            return null;
         }

         Vec3d start = exit.get().add(direction.multiply(0.001));
         context = new RaycastContext(start, end, ShapeType.OUTLINE, fluids, player);
      }

      BlockHitResult hit = level.raycast(context);
      return hit.getType() == Type.BLOCK ? hit.getBlockPos() : null;
   }

   private static boolean startsInside(RaycastContext context, ClientWorld level, BlockPos pos, Vec3d point) {
      return contains(context.getBlockShape(level.getBlockState(pos), level, pos), pos, point)
         || contains(context.getFluidShape(level.getFluidState(pos), level, pos), pos, point);
   }

   private static boolean contains(VoxelShape shape, BlockPos pos, Vec3d point) {
      for (Box box : shape.getBoundingBoxes()) {
         if (box.offset(pos).contains(point)) {
            return true;
         }
      }

      return false;
   }

   private String formatCoords(BlockPos pos, RegistryKey<World> dimension) {
      int x = pos.getX();
      int y = pos.getY();
      int z = pos.getZ();
      boolean withDimension = this.includeDimension.get();

      return switch (this.format.get()) {
         case "/tp Command" -> withDimension
            ? String.format(Locale.ROOT, "/execute in %s run tp @s %d %d %d", dimension.getValue(), x, y, z)
            : String.format(Locale.ROOT, "/tp %d %d %d", x, y, z);
         case "X, Y, Z" -> String.format(Locale.ROOT, "%d, %d, %d", x, y, z) + dimensionSuffix(dimension, withDimension);
         default -> String.format(Locale.ROOT, "%d %d %d", x, y, z) + dimensionSuffix(dimension, withDimension);
      };
   }

   private static String dimensionSuffix(RegistryKey<World> dimension, boolean enabled) {
      return enabled ? " (" + dimensionName(dimension) + ")" : "";
   }

   private static String dimensionName(RegistryKey<World> dimension) {
      if (World.OVERWORLD.equals(dimension)) {
         return "Overworld";
      } else if (World.NETHER.equals(dimension)) {
         return "Nether";
      } else {
         return World.END.equals(dimension) ? "End" : dimension.getValue().toString();
      }
   }

}
