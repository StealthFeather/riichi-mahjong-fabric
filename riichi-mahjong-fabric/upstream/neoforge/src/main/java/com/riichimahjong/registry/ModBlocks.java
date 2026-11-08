package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarBlock;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlock;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import java.util.function.Supplier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RiichiMahjong.MOD_ID);

    public static final Supplier<Block> MAHJONG_TABLE = BLOCKS.registerBlock(
            "mahjong_table_new",
            props -> new MahjongTableBlock(props),
            props -> props
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD));

    public static final Supplier<? extends Item> MAHJONG_TABLE_ITEM = ModItems.ITEMS.registerSimpleBlockItem("mahjong_table_new", MAHJONG_TABLE);

    public static final Supplier<Block> MAHJONG_SOLITAIRE = BLOCKS.registerBlock(
            "mahjong_solitaire",
            props -> new com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlock(props),
            props -> props
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD));

    public static final Supplier<? extends Item> MAHJONG_SOLITAIRE_ITEM = ModItems.ITEMS.registerSimpleBlockItem("mahjong_solitaire", MAHJONG_SOLITAIRE);

    public static final Supplier<Block> MAHJONG_ALTAR = BLOCKS.registerBlock(
            "mahjong_altar",
            props -> new MahjongAltarBlock(props),
            props -> props
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 5.0f)
                    .sound(SoundType.METAL));

    public static final Supplier<? extends Item> MAHJONG_ALTAR_ITEM = ModItems.ITEMS.registerSimpleBlockItem("mahjong_altar", MAHJONG_ALTAR);

    public static final Supplier<Block> YAKU_GENERATOR_TIER_1 = BLOCKS.registerBlock(
            "yaku_generator_tier_1",
            props -> new com.riichimahjong.yakugenerator.YakuGeneratorBlock(props, com.riichimahjong.yakugenerator.YakuGeneratorBlock.Tier.TIER_1),
            props -> props
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL));

    public static final Supplier<Block> YAKU_GENERATOR_TIER_2 = BLOCKS.registerBlock(
            "yaku_generator_tier_2",
            props -> new com.riichimahjong.yakugenerator.YakuGeneratorBlock(props, com.riichimahjong.yakugenerator.YakuGeneratorBlock.Tier.TIER_2),
            props -> props
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL));

    public static final Supplier<Block> YAKU_GENERATOR_TIER_3 = BLOCKS.registerBlock(
            "yaku_generator_tier_3",
            props -> new com.riichimahjong.yakugenerator.YakuGeneratorBlock(props, com.riichimahjong.yakugenerator.YakuGeneratorBlock.Tier.TIER_3),
            props -> props
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL));

    public static final Supplier<? extends Item> YAKU_GENERATOR_TIER_1_ITEM = ModItems.ITEMS.registerSimpleBlockItem("yaku_generator_tier_1", YAKU_GENERATOR_TIER_1);
    public static final Supplier<? extends Item> YAKU_GENERATOR_TIER_2_ITEM = ModItems.ITEMS.registerSimpleBlockItem("yaku_generator_tier_2", YAKU_GENERATOR_TIER_2);
    public static final Supplier<? extends Item> YAKU_GENERATOR_TIER_3_ITEM = ModItems.ITEMS.registerSimpleBlockItem("yaku_generator_tier_3", YAKU_GENERATOR_TIER_3);

    public static final Supplier<Block> DISCARD_CLICKER_TIER_1 = BLOCKS.registerBlock(
            "discard_clicker_tier_1",
            props -> new com.riichimahjong.clickers.DiscardClickerBlock(props, com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_1),
            props -> props
                    .mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.METAL));
    public static final Supplier<Block> DISCARD_CLICKER_TIER_2 = BLOCKS.registerBlock(
            "discard_clicker_tier_2",
            props -> new com.riichimahjong.clickers.DiscardClickerBlock(props, com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_2),
            props -> props
                    .mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.METAL));
    public static final Supplier<Block> DISCARD_CLICKER_TIER_3 = BLOCKS.registerBlock(
            "discard_clicker_tier_3",
            props -> new com.riichimahjong.clickers.DiscardClickerBlock(props, com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_3),
            props -> props
                    .mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.METAL));

    public static final Supplier<? extends Item> DISCARD_CLICKER_TIER_1_ITEM = ModItems.ITEMS.registerItem("discard_clicker_tier_1",
            props -> new com.riichimahjong.clickers.DiscardClickerItem(DISCARD_CLICKER_TIER_1.get(), com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_1, props));
    public static final Supplier<? extends Item> DISCARD_CLICKER_TIER_2_ITEM = ModItems.ITEMS.registerItem("discard_clicker_tier_2",
            props -> new com.riichimahjong.clickers.DiscardClickerItem(DISCARD_CLICKER_TIER_2.get(), com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_2, props));
    public static final Supplier<? extends Item> DISCARD_CLICKER_TIER_3_ITEM = ModItems.ITEMS.registerItem("discard_clicker_tier_3",
            props -> new com.riichimahjong.clickers.DiscardClickerItem(DISCARD_CLICKER_TIER_3.get(), com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_3, props));

    public static final Supplier<Block> TSUMO_CLICKER = BLOCKS.registerBlock(
            "tsumo_clicker",
            props -> new com.riichimahjong.clickers.TsumoClickerBlock(props),
            props -> props
                    .mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.METAL));
    public static final Supplier<? extends Item> TSUMO_CLICKER_ITEM = ModItems.ITEMS.registerItem("tsumo_clicker", props -> new com.riichimahjong.clickers.TsumoClickerItem(TSUMO_CLICKER.get(), props));

    public static final Supplier<Block> FOV_NORMALIZER = BLOCKS.registerBlock(
            "fov_normalizer",
            props -> new com.riichimahjong.fovnormalizer.FovNormalizerBlock(props),
            props -> props
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL));

    public static final Supplier<? extends Item> FOV_NORMALIZER_ITEM = ModItems.ITEMS.registerSimpleBlockItem("fov_normalizer", FOV_NORMALIZER);

    public static final Supplier<net.minecraft.world.level.block.LiquidBlock> LIQUID_EXPERIENCE_BLOCK =
            BLOCKS.registerBlock("liquid_experience_block",
                    props -> new net.minecraft.world.level.block.LiquidBlock(
                            (net.minecraft.world.level.material.FlowingFluid)
                                    com.riichimahjong.registry.ModFluids.LIQUID_EXPERIENCE.get(),
                            props),
                    props -> props
                            .mapColor(MapColor.GLOW_LICHEN)
                            .noCollision()
                            .replaceable()
                            .strength(100f)
                            .noLootTable()
                            .liquid()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    private ModBlocks() {}
}
