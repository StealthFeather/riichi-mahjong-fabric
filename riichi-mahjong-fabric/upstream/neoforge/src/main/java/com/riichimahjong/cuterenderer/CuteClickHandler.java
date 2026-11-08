package com.riichimahjong.cuterenderer;

import net.minecraft.server.level.ServerPlayer;

public interface CuteClickHandler {
    void onCuteClick(ServerPlayer player, InteractKey key);
}
