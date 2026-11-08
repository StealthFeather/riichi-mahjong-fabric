package com.riichimahjong.common.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ClientCompat {
    private ClientCompat() {}

    @Nullable
    public static Screen currentScreen(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else
        /*return mc.screen;*/
    }

    public static void setScreen(Minecraft mc, @Nullable Screen screen) {
        //? if >=26.2 {
        mc.gui.setScreen(screen);
        //?} else
        /*mc.setScreen(screen);*/
    }

    public static Camera mainCamera(Minecraft mc) {
        //? if >=26.2 {
        return mc.gameRenderer.mainCamera();
        //?} else
        /*return mc.gameRenderer.getMainCamera();*/
    }

    public static void setOverlayMessage(Minecraft mc, Component message, boolean animateColor) {
        //? if >=26.2 {
        mc.gui.hud.setOverlayMessage(message, animateColor);
        //?} else
        /*mc.gui.setOverlayMessage(message, animateColor);*/
    }
}
