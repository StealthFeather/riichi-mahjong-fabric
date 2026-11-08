package com.riichimahjong.cuterenderer.editor;

import com.mojang.blaze3d.platform.InputConstants;
import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.common.client.ClientCompat;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class CuteEditorKeyHandler {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "cute_editor"));

    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.riichi_mahjong.cute_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY);

    private CuteEditorKeyHandler() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(CATEGORY);
            event.register(TOGGLE);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            while (TOGGLE.consumeClick()) {
                Screen current = ClientCompat.currentScreen(Minecraft.getInstance());
                if (current != null && !(current instanceof CuteEditorScreen)) {
                    continue;
                }
                CuteEditor.toggle();
            }
        });
    }
}
