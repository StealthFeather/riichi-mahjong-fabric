// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongcore;

import com.themahjong.TheMahjongTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import com.riichimahjong.fabric.DeferredRegister;

public final class MahjongTileItems {
    public static final int CODE_MAN_5_AKA = 34;
    public static final int CODE_PIN_5_AKA = 35;
    public static final int CODE_SOU_5_AKA = 36;
    private static final int TILE_COUNT = 37;

    public static final int CODE_MAN_4 =
            codeForSuitRank(new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false));

    @SuppressWarnings("unchecked")
    private static final Supplier<Block>[] TILE_BLOCKS_BY_CODE = new Supplier[TILE_COUNT];

    @SuppressWarnings("unchecked")
    private static final Supplier<Item>[] TILE_ITEMS_BY_CODE = new Supplier[TILE_COUNT];
    private static final Map<Item, Integer> CODE_BY_ITEM = new HashMap<>();

    private MahjongTileItems() {}

    public static void register(DeferredRegister.Blocks blocks, DeferredRegister.Items items) {
        for (TheMahjongTile.Suit suit : TheMahjongTile.Suit.values()) {
            for (int rank = 1; rank <= suit.maxRank(); rank++) {
                TheMahjongTile tile = new TheMahjongTile(suit, rank, false);
                registerTile(blocks, items, codeForSuitRank(tile), idForSuitRank(suit, rank));
            }
        }
        registerTile(blocks, items, CODE_MAN_5_AKA, "tile_man_5_aka");
        registerTile(blocks, items, CODE_PIN_5_AKA, "tile_pin_5_aka");
        registerTile(blocks, items, CODE_SOU_5_AKA, "tile_sou_5_aka");
    }

    private static String idForSuitRank(TheMahjongTile.Suit suit, int rank) {
        return switch (suit) {
            case MANZU -> "tile_man_" + rank;
            case PINZU -> "tile_pin_" + rank;
            case SOUZU -> "tile_sou_" + rank;
            case WIND -> switch (TheMahjongTile.Wind.fromTileRank(rank)) {
                case EAST -> "tile_wind_east";
                case SOUTH -> "tile_wind_south";
                case WEST -> "tile_wind_west";
                case NORTH -> "tile_wind_north";
            };
            case DRAGON -> switch (TheMahjongTile.Dragon.fromTileRank(rank)) {
                case HAKU -> "tile_dragon_white";
                case HATSU -> "tile_dragon_green";
                case CHUN -> "tile_dragon_red";
            };
        };
    }

    private static void registerTile(DeferredRegister.Blocks blocks, DeferredRegister.Items items,
                                     int code, String id) {
        Supplier<Block> blockRo = blocks.registerBlock(id,
                props -> new MahjongTileBlock(props),
                props -> MahjongTileBlock.applyDefaults(props));
        TILE_BLOCKS_BY_CODE[code] = blockRo;
        TILE_ITEMS_BY_CODE[code] = items.registerItem(id,
                props -> new com.riichimahjong.fabric.PortBlockItem(blockRo.get(), props),
                props -> MahjongTileFlavor.applyPropertiesForCode(props, code));
    }

    public static Item itemForCode(int code) {
        if (code < 0 || code >= TILE_ITEMS_BY_CODE.length) {
            return null;
        }
        Supplier<Item> ro = TILE_ITEMS_BY_CODE[code];
        return ro == null ? null : ro.get();
    }

    public static Optional<Supplier<Item>> itemSupplierForCode(int code) {
        if (code < 0 || code >= TILE_ITEMS_BY_CODE.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(TILE_ITEMS_BY_CODE[code]);
    }

    public static Block blockForTile(TheMahjongTile tile) {
        return blockForCode(codeForTile(tile));
    }

    public static int codeForTile(TheMahjongTile tile) {
        if (tile.redDora() && tile.rank() == 5) {
            return switch (tile.suit()) {
                case MANZU -> CODE_MAN_5_AKA;
                case PINZU -> CODE_PIN_5_AKA;
                case SOUZU -> CODE_SOU_5_AKA;
                default -> codeForSuitRank(tile);
            };
        }
        return codeForSuitRank(tile);
    }

    private static int codeForSuitRank(TheMahjongTile tile) {
        return switch (tile.suit()) {
            case MANZU  -> tile.rank() - 1;
            case PINZU  -> 9 + tile.rank() - 1;
            case SOUZU  -> 18 + tile.rank() - 1;
            case WIND   -> 27 + tile.rank() - 1;
            case DRAGON -> 31 + tile.rank() - 1;
        };
    }

    public static Block blockForCode(int code) {
        if (code < 0 || code >= TILE_BLOCKS_BY_CODE.length) {
            return null;
        }
        Supplier<Block> ro = TILE_BLOCKS_BY_CODE[code];
        return ro == null ? null : ro.get();
    }

    public static Integer codeForItem(Item item) {
        if (CODE_BY_ITEM.isEmpty()) {
            for (int code = 0; code < TILE_ITEMS_BY_CODE.length; code++) {
                Supplier<Item> ro = TILE_ITEMS_BY_CODE[code];
                if (ro != null) {
                    CODE_BY_ITEM.put(ro.get(), code);
                }
            }
        }
        return CODE_BY_ITEM.get(item);
    }

    public static List<Item> allTileItems() {
        ArrayList<Item> out = new ArrayList<>(TILE_COUNT);
        for (Supplier<Item> ro : TILE_ITEMS_BY_CODE) {
            if (ro != null) {
                out.add(ro.get());
            }
        }
        return out;
    }

    public static List<Block> allTileBlocks() {
        ArrayList<Block> out = new ArrayList<>(TILE_COUNT);
        for (Supplier<Block> ro : TILE_BLOCKS_BY_CODE) {
            if (ro != null) {
                out.add(ro.get());
            }
        }
        return out;
    }
}
