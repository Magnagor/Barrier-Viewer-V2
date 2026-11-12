package com.barrierviewer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All the client-side logic of the mod:
 *  - a rebindable hotkey (Options > Controls > Key Binds > "Barrier Viewer")
 *  - scanning for barrier blocks around the player
 *  - drawing each barrier as a thin red outline + transparent red fill
 */
public final class BarrierViewerClient {

    // ----------------------------------------------------------------------------------
    // Settings you can tweak
    // ----------------------------------------------------------------------------------

    /** How far (in blocks) around the player barriers are shown. */
    private static final int RANGE = 32;

    /** How often (in ticks, 20 ticks = 1 second) the area is re-scanned for barriers. */
    private static final int SCAN_INTERVAL_TICKS = 10;

    /** Safety limit so a gigantic barrier wall can't kill your FPS. */
    private static final int MAX_BARRIERS = 6000;

    /** Outline colour (ARGB): solid red. */
    private static final int EDGE_COLOR = 0xFFFF2020;

    /** Outline thickness. Vanilla default is 2.0, so 1.5 is a bit thinner. */
    private static final float EDGE_WIDTH = 1.5F;

    /**
     * Fill colour (ARGB): red with low alpha (0x28 = about 16% visible) so you can look through it.
     * Make the first two digits bigger (e.g. 0x40) for a stronger fill, smaller (e.g. 0x18) for a weaker one.
     */
    private static final int FILL_COLOR = 0x28FF0000;

    /** Default hotkey - can be changed in the Controls menu. */
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_B;

    // ----------------------------------------------------------------------------------

    private static final GizmoStyle STYLE = GizmoStyle.strokeAndFill(EDGE_COLOR, EDGE_WIDTH, FILL_COLOR);

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BarrierViewer.MODID, "main"));

    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.barrierviewer.toggle",
            InputConstants.Type.KEYSYM,
            DEFAULT_KEY,
            CATEGORY
    );

    private static boolean enabled = false;
    private static int ticksSinceScan = 0;
    private static ClientLevel lastLevel = null;
    private static List<BlockPos> barriers = new ArrayList<>();
    private static final Map<Long, Long> lastSubmitted = new HashMap<>();

    private BarrierViewerClient() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        RegisterKeyMappingsEvent.getBus(context.getModBusGroup()).addListener(BarrierViewerClient::onRegisterKeys);
        TickEvent.ClientTickEvent.Post.BUS.addListener(BarrierViewerClient::onClientTick);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        // Hotkey handling
        while (TOGGLE_KEY.consumeClick()) {
            enabled = !enabled;
            if (!enabled) {
                clearCache();
            }
            if (player != null) {
                Component state = enabled
                        ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                        : Component.literal("OFF").withStyle(ChatFormatting.RED);
                player.displayClientMessage(
                        Component.literal("Barrier View: ").withStyle(ChatFormatting.GRAY).append(state),
                        true
                );
            }
        }

        if (!enabled || player == null || level == null) {
            return;
        }

        // New world / dimension -> start fresh
        if (level != lastLevel) {
            lastLevel = level;
            clearCache();
            ticksSinceScan = SCAN_INTERVAL_TICKS;
        }

        // Re-scan the area around the player every few ticks
        if (++ticksSinceScan >= SCAN_INTERVAL_TICKS) {
            ticksSinceScan = 0;
            rescan(level, player.blockPosition());
        }

        // Draw. Gizmos stay on screen for a short time, so each barrier is re-submitted
        // shortly before its previous one expires. Each block gets its own slightly
        // different lifetime so they don't all refresh at the exact same moment.
        long now = System.currentTimeMillis();
        for (BlockPos pos : barriers) {
            long key = pos.asLong();
            int lifetime = lifetimeFor(key);
            Long last = lastSubmitted.get(key);
            if (last == null || now - last >= lifetime - 40) {
                // The tiny 0.002 expansion stops the outline z-fighting with neighbouring blocks.
                Gizmos.cuboid(pos, 0.002F, STYLE).persistForMillis(lifetime);
                lastSubmitted.put(key, now);
            }
        }
    }

    private static int lifetimeFor(long key) {
        // Between 500 and 999 ms, different for each block position.
        return 500 + (int) (((key * 0x9E3779B97F4A7C15L) >>> 40) % 500L);
    }

    private static void rescan(ClientLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        scan:
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dz = -RANGE; dz <= RANGE; dz++) {
                for (int dy = -RANGE; dy <= RANGE; dy++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(cursor).is(Blocks.BARRIER)) {
                        found.add(cursor.immutable());
                        if (found.size() >= MAX_BARRIERS) {
                            break scan;
                        }
                    }
                }
            }
        }

        barriers = found;

        // Forget barriers that are gone
        Set<Long> stillThere = new HashSet<>();
        for (BlockPos pos : found) {
            stillThere.add(pos.asLong());
        }
        lastSubmitted.keySet().retainAll(stillThere);
    }

    private static void clearCache() {
        barriers = new ArrayList<>();
        lastSubmitted.clear();
    }
}
