package com.riichimahjong.fabric;

import java.util.*;
import com.riichimahjong.registry.*;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Original solitaire automation exposed through transactional Fabric item and fluid APIs. */
public final class FabricAutomation {
    public static void register(){
        ItemStorage.SIDED.registerForBlockEntity((be,side)->be.fabricItemStorage(),ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get());
        FluidStorage.SIDED.registerForBlockEntity((be,side)->be.fabricFluidStorage(),ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get());
    }
    static void restore(MahjongSolitaireBlockEntity be,CompoundTag tag){be.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,be.getLevel().registryAccess(),tag));}
    public static final class Hints extends SnapshotParticipant<Plan> implements Storage<ItemVariant> {
        final MahjongSolitaireBlockEntity be;MahjongSolitaireBlockEntity preview;final List<ItemStack> pending=new ArrayList<>();
        public Hints(MahjongSolitaireBlockEntity be){this.be=be;}
        void prepare(){if(preview==null){preview=new MahjongSolitaireBlockEntity(be.getBlockPos(),be.getBlockState());preview.setLevel(be.getLevel());preview.fabricPreview=true;restore(preview,be.saveCustomOnly(be.getLevel().registryAccess()));}}
        @Override public long insert(ItemVariant variant,long max,TransactionContext transaction){
            if(max<=0||variant.isBlank()||!MahjongSolitaireBlockEntity.isHintItem(variant.toStack()))return 0;prepare();
            updateSnapshots(transaction);var stack=variant.toStack((int)Math.min(64,max));int consumed=preview.tryInsertHintItem(stack,false);
            if(consumed>0)pending.add(variant.toStack(consumed));return consumed;
        }
        @Override public long extract(ItemVariant variant,long max,TransactionContext transaction){return 0;}
        @Override public boolean supportsExtraction(){return false;}
        @Override public Iterator<StorageView<ItemVariant>> iterator(){return Collections.emptyIterator();}
        @Override protected Plan createSnapshot(){prepare();return new Plan(preview.saveCustomOnly(be.getLevel().registryAccess()),pending.stream().map(ItemStack::copy).toList());}
        @Override protected void readSnapshot(Plan plan){restore(preview,plan.tag);pending.clear();plan.items.forEach(s->pending.add(s.copy()));}
        @Override protected void onFinalCommit(){for(var stack:pending)be.tryInsertHintItem(stack,false);pending.clear();preview=null;}
        @Override public void afterOuterClose(TransactionContext.Result result){super.afterOuterClose(result);preview=null;pending.clear();}
    }
    record Plan(CompoundTag tag,List<ItemStack> items){}
    public static final class Experience extends SnapshotParticipant<Integer> implements Storage<FluidVariant>,StorageView<FluidVariant> {
        final MahjongSolitaireBlockEntity be;public Experience(MahjongSolitaireBlockEntity be){this.be=be;}
        @Override public long insert(FluidVariant v,long max,TransactionContext t){return 0;}
        @Override public boolean supportsInsertion(){return false;}
        @Override public long extract(FluidVariant v,long max,TransactionContext transaction){if(max<=0||v.getFluid()!=ModFluids.LIQUID_EXPERIENCE.get())return 0;int mb=(int)Math.min(Integer.MAX_VALUE,max/81);if(mb<=0)return 0;updateSnapshots(transaction);return be.extractLiquidXp(mb,false)*81L;}
        @Override protected Integer createSnapshot(){return be.getLiquidXpStored();}
        @Override protected void readSnapshot(Integer amount){be.fabricSetLiquidXp(amount);}
        @Override public boolean isResourceBlank(){return be.getLiquidXpStored()==0;}
        @Override public FluidVariant getResource(){return isResourceBlank()?FluidVariant.blank():FluidVariant.of(ModFluids.LIQUID_EXPERIENCE.get());}
        @Override public long getAmount(){return be.getLiquidXpStored()*81L;}
        @Override public long getCapacity(){return MahjongSolitaireBlockEntity.LIQUID_XP_CAPACITY_MB*81L;}
        @Override public Iterator<StorageView<FluidVariant>> iterator(){return List.<StorageView<FluidVariant>>of(this).iterator();}
    }
}
