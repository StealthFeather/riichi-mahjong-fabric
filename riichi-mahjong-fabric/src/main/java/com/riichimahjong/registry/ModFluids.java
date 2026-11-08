package com.riichimahjong.registry;
import com.riichimahjong.fabric.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.item.Item;

/** Water physics with the original experience fluid's server identity. */
public final class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(Registries.FLUID,"riichi_mahjong");
    public static final Supplier<FlowingFluid> LIQUID_EXPERIENCE=FLUIDS.register("liquid_experience",Source::new);
    public static final Supplier<FlowingFluid> LIQUID_EXPERIENCE_FLOWING=FLUIDS.register("liquid_experience_flowing",Flowing::new);
    static BlockState block(FluidState s){return ModBlocks.LIQUID_EXPERIENCE_BLOCK.get().defaultBlockState().setValue(LiquidBlock.LEVEL,legacy(s));}
    static int legacy(FluidState s){return s.isSource()?0:8-Math.min(s.getAmount(),8)+(s.getValue(FlowingFluid.FALLING)?8:0);}
    public static class Source extends WaterFluid.Source {
        @Override public Fluid getFlowing(){return LIQUID_EXPERIENCE_FLOWING.get();}
        @Override public Fluid getSource(){return LIQUID_EXPERIENCE.get();}
        @Override public Item getBucket(){return ModItems.LIQUID_EXPERIENCE_BUCKET.get();}
        @Override public boolean isSame(Fluid f){return f==getFlowing()||f==getSource();}
        @Override public BlockState createLegacyBlock(FluidState s){return block(s);}
    }
    public static class Flowing extends WaterFluid.Flowing {
        @Override public Fluid getFlowing(){return LIQUID_EXPERIENCE_FLOWING.get();}
        @Override public Fluid getSource(){return LIQUID_EXPERIENCE.get();}
        @Override public Item getBucket(){return ModItems.LIQUID_EXPERIENCE_BUCKET.get();}
        @Override public boolean isSame(Fluid f){return f==getFlowing()||f==getSource();}
        @Override public BlockState createLegacyBlock(FluidState s){return block(s);}
    }
}
