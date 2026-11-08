// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.cuterenderer;

import net.minecraft.server.level.ServerPlayer;

public interface CuteClickHandler {
    void onCuteClick(ServerPlayer player, InteractKey key);
}
