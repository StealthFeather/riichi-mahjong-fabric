// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.fovnormalizer.FovNormalizerBlockEntity;
import com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.riichimahjong.fabric.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RiichiMahjong.MOD_ID);

    public static final Supplier<BlockEntityType<MahjongTableBlockEntity>> MAHJONG_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_table_new",
                    () -> new BlockEntityType<>(
                            MahjongTableBlockEntity::new,
                            Set.of(ModBlocks.MAHJONG_TABLE.get())));

    public static final Supplier<BlockEntityType<MahjongSolitaireBlockEntity>> MAHJONG_SOLITAIRE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_solitaire",
                    () -> new BlockEntityType<>(
                            MahjongSolitaireBlockEntity::new,
                            Set.of(ModBlocks.MAHJONG_SOLITAIRE.get())));

    public static final Supplier<BlockEntityType<MahjongAltarBlockEntity>> MAHJONG_ALTAR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_altar",
                    () -> new BlockEntityType<>(
                            MahjongAltarBlockEntity::new,
                            Set.of(ModBlocks.MAHJONG_ALTAR.get())));

    public static final Supplier<BlockEntityType<YakuGeneratorBlockEntity>> YAKU_GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "yaku_generator",
                    () -> new BlockEntityType<>(
                            YakuGeneratorBlockEntity::new,
                            Set.of(
                                    ModBlocks.YAKU_GENERATOR_TIER_1.get(),
                                    ModBlocks.YAKU_GENERATOR_TIER_2.get(),
                                    ModBlocks.YAKU_GENERATOR_TIER_3.get())));

    public static final Supplier<BlockEntityType<FovNormalizerBlockEntity>> FOV_NORMALIZER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "fov_normalizer",
                    () -> new BlockEntityType<>(
                            FovNormalizerBlockEntity::new,
                            Set.of(ModBlocks.FOV_NORMALIZER.get())));

    private ModBlockEntities() {}
}
