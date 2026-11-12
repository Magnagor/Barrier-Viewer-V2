package com.barrierviewer;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Barrier Viewer - shows barrier blocks as a thin red outline with a see-through red fill.
 * Client-side only: do not install it on a server.
 */
@Mod(BarrierViewer.MODID)
public class BarrierViewer {
    public static final String MODID = "barrierviewer";

    public BarrierViewer(FMLJavaModLoadingContext context) {
        // Safety check: only touch client classes when they actually exist
        if (!isClient()) {
            return;
        }
        BarrierViewerClient.init(context);
    }

    private static boolean isClient() {
        try {
            Class.forName("net.minecraft.client.Minecraft", false, BarrierViewer.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
