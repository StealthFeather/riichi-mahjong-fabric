package com.riichimahjong.fabric;

import com.google.gson.JsonParser;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlock;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Real multipart states and data-driven vanilla item tags, without a fake player or world. */
public final class TableMiningRegression {
    private record Fixture(MahjongTableBlock table, MahjongSolitaireBlock solitaire) {}

    private static Fixture bootstrap() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        // A standalone JVM has no Fabric registration hook before vanilla freezes BLOCK.
        // Temporarily permit registry creation, register real blocks, then run normal bootstrap.
        var flag = Bootstrap.class.getDeclaredField("isBootstrapped");
        flag.setAccessible(true);
        check(!flag.getBoolean(null),"mining regression requires its own fresh JVM");
        flag.setBoolean(null,true);
        MahjongTableBlock table;
        MahjongSolitaireBlock solitaire;
        try {
            var tableId = Identifier.fromNamespaceAndPath("riichi_mining_test","table");
            table = Registry.register(BuiltInRegistries.BLOCK,tableId,new MahjongTableBlock(
                    BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,tableId))));
            var solitaireId = Identifier.fromNamespaceAndPath("riichi_mining_test","solitaire");
            solitaire = Registry.register(BuiltInRegistries.BLOCK,solitaireId,new MahjongSolitaireBlock(
                    BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,solitaireId))));
        } finally {
            flag.setBoolean(null,false);
        }
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());
        return new Fixture(table,solitaire);
    }

    private static List<Holder<Item>> vanillaPickaxes() throws Exception {
        var items = new ArrayList<Holder<Item>>();
        try (var stream = TableMiningRegression.class.getResourceAsStream("/data/minecraft/tags/item/pickaxes.json")) {
            check(stream!=null,"server JAR lacks the vanilla pickaxes tag");
            var json = JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : json.getAsJsonArray("values")) {
                var id = Identifier.parse(entry.getAsString());
                items.add(BuiltInRegistries.ITEM.get(id).orElseThrow());
            }
        }
        check(!items.isEmpty(),"vanilla pickaxe fixture is empty");
        return List.copyOf(items);
    }

    private static void loadPickaxeTag(List<Holder<Item>> pickaxes) {
        BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<Item>(
                Registries.ITEM,Map.of(ItemTags.PICKAXES,pickaxes))).apply();
    }

    private static void allTableParts(Fixture fixture, List<Holder<Item>> pickaxes) {
        var wrongTools = List.of(ItemStack.EMPTY,new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.IRON_AXE),new ItemStack(Items.STICK));
        var center = new BlockPos(30,64,40);
        for (var facing : List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            for (int x=0; x<BaseMultipartBlock.GRID_SIZE; x++) {
                for (int z=0; z<BaseMultipartBlock.GRID_SIZE; z++) {
                    var state = fixture.table.defaultBlockState().setValue(BaseMultipartBlock.PART_X,x)
                            .setValue(BaseMultipartBlock.PART_Z,z).setValue(BaseMultipartBlock.FACING,facing);
                    var part = center.offset(x-BaseMultipartBlock.CENTER,0,z-BaseMultipartBlock.CENTER);
                    check(BaseMultipartBlock.masterPos(part,state).equals(center),"multipart fixture does not refer to its table centre");
                    for (var stack : wrongTools) {
                        check(!TableMining.matches(state,stack),"empty hand or non-pickaxe removed table part "+x+","+z);
                    }
                    for (var holder : pickaxes) {
                        var stack = new ItemStack(holder);
                        check(stack.is(ItemTags.PICKAXES),"official tag reload did not bind a vanilla pickaxe");
                        check(TableMining.matches(state,stack),"a tagged pickaxe tier was rejected at table part "+x+","+z);
                    }
                    var solitaire = fixture.solitaire.defaultBlockState()
                            .setValue(BaseMultipartBlock.PART_X,x).setValue(BaseMultipartBlock.PART_Z,z);
                    check(TableMining.matches(solitaire,ItemStack.EMPTY),"single-player matching board inherited table tool restrictions");
                }
            }
        }
        for (var block : List.<Block>of(Blocks.STONE,Blocks.CHEST,Blocks.AIR)) {
            check(TableMining.matches(block.defaultBlockState(),ItemStack.EMPTY),"non-table vanilla block was restricted");
        }
    }

    private static void tagReloadChangesPolicy(Fixture fixture, List<Holder<Item>> original) {
        var changed = new ArrayList<>(original);
        var wooden = Items.WOODEN_PICKAXE.builtInRegistryHolder();
        var stick = Items.STICK.builtInRegistryHolder();
        check(changed.remove(wooden),"vanilla tag fixture omitted the wooden pickaxe");
        changed.add(stick);
        loadPickaxeTag(changed);
        var state = fixture.table.defaultBlockState();
        check(TableMining.matches(state,new ItemStack(Items.STICK)),"policy ignored an item added to the pickaxes tag");
        check(!TableMining.matches(state,new ItemStack(Items.WOODEN_PICKAXE)),"policy hard-coded pickaxe classes instead of current tags");
        loadPickaxeTag(original);
        check(TableMining.matches(state,new ItemStack(Items.WOODEN_PICKAXE))
                        && !TableMining.matches(state,new ItemStack(Items.STICK)),
                "restored vanilla tag did not restore table removal policy");
    }

    public static void main(String[] args) throws Exception {
        var fixture = bootstrap();
        var pickaxes = vanillaPickaxes();
        loadPickaxeTag(pickaxes);
        allTableParts(fixture,pickaxes);
        tagReloadChangesPolicy(fixture,pickaxes);
        System.out.println("PASS: all nine table parts in four rotations require a main-hand tagged pickaxe of any vanilla tier");
        System.out.println("PASS: other blocks and solitaire remain unrestricted; live tag reload updates the mining policy");
    }
}
