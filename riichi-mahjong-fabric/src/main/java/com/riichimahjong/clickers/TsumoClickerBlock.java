// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.clickers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class TsumoClickerBlock extends ClickerBlock {


    public TsumoClickerBlock(Properties properties) {
        super(properties);
    }



    @Override
    protected void performClick(ServerLevel level, BlockPos pos, YakuGeneratorBlockEntity target) {
        target.tsumoAutomated();
    }
}
