package imgui.glfw;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class AnubisKeys {
   private AnubisKeys() {
   }

   public static boolean isAndroid() {
      return false;
   }

   public static int getKey(long window, int key) {
      return GLFW.glfwGetKey(window, key);
   }

   public static void record(long window, int key, int action) {
      // Desktop GLFW delivers key state directly.
   }

   public static void clear(long window) {
      // No cached Android key state on desktop.
   }
}
