// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.clickers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class DiscardClickerBlock extends ClickerBlock {

    public enum Tier {
        TIER_1(1, 80),
        TIER_2(2, 90),
        TIER_3(3, 100);

        private final int index;
        private final int accuracyPct;

        Tier(int index, int accuracyPct) {
            this.index = index;
            this.accuracyPct = accuracyPct;
        }

        public int index() { return index; }
        public int accuracyPct() { return accuracyPct; }
    }


    private final Tier tier;

    public DiscardClickerBlock(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }



    public Tier tier() { return tier; }

    @Override
    protected void performClick(ServerLevel level, BlockPos pos, YakuGeneratorBlockEntity target) {
        target.discardForClicker(tier.accuracyPct());
    }
}
