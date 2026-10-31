package imgui.glfw;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class AnubisGlfw extends ImGuiImplGlfw {
   public void windowFocusCallback(long window, boolean focused) {
      if (!focused) {
         AnubisKeys.clear(window);
      }

      super.windowFocusCallback(window, focused);
   }

   public void restoreCallbacks(long window) {
      AnubisKeys.clear(window);
      super.restoreCallbacks(window);
   }
}
