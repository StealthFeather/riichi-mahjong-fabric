package com.riichimahjong.mahjongcore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

public class MahjongRoundResolvedEvent extends Event {

    private final ServerLevel level;
    private final BlockPos sourcePos;
    private final int han;

    public MahjongRoundResolvedEvent(ServerLevel level, BlockPos sourcePos, int han) {
        this.level = level;
        this.sourcePos = sourcePos;
        this.han = han;
    }

    public static void post(ServerLevel level, BlockPos sourcePos, int han) {
        NeoForge.EVENT_BUS.post(new MahjongRoundResolvedEvent(level, sourcePos, han));
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos sourcePos() {
        return sourcePos;
    }

    public int han() {
        return han;
    }
}
