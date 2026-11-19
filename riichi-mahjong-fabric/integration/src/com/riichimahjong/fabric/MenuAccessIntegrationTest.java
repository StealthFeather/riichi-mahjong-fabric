package com.riichimahjong.fabric;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.riichimahjong.analysis.AnalysisMode;
import com.riichimahjong.analysis.PlayerAnalysisStore;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.fabric.wineffect.WinEffects;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer.TableButton;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.cuterenderer.InteractKey;
import com.themahjong.TheMahjongDiscard;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.variants.ExchangeThreeState;
import com.themahjong.variants.Jokers;
import com.themahjong.yaku.Furiten;
import com.riichimahjong.registry.ModBlocks;
import com.themahjong.TheMahjongTile;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import io.netty.channel.ChannelFutureListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Explicit, synchronous handler tests on a temporary table; never logs in a real player. */
public final class MenuAccessIntegrationTest implements ModInitializer {
    private static final BlockPos POS = new BlockPos(0, 300, 0);
    private static final Path RESULT = Path.of("menu-access-integration-result.json");
    private static final List<String> checks = new ArrayList<>();
    private static boolean running;

    @Override public void onInitialize() {
        if (!Files.exists(Path.of("riichi-menu-test-enabled")))
            throw new IllegalStateException("Menu integration addon requires its explicit test-server marker");
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("riichi_menu_test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("run").executes(context -> run(context.getSource().getServer())))));
    }

    private record SavedBlock(BlockState state, CompoundTag blockEntity) {}

    private static int run(MinecraftServer server) {
        if (running) return 0;
        running = true; checks.clear();
        var result = new LinkedHashMap<String,Object>();
        var level = server.overworld();
        var saved = new LinkedHashMap<BlockPos,SavedBlock>();
        var players = new ArrayList<MenuPlayer>();
        MahjongTableBlockEntity table = null;
        try {
            level.getChunkAt(POS);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                var pos = POS.offset(dx, 0, dz);
                var be = level.getBlockEntity(pos);
                saved.put(pos, new SavedBlock(level.getBlockState(pos),
                        be == null ? null : be.saveWithFullMetadata(server.registryAccess())));
            }
            var state = ModBlocks.MAHJONG_TABLE.get().defaultBlockState();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                level.setBlock(POS.offset(dx, 0, dz), state.setValue(BaseMultipartBlock.PART_X, dx + 1)
                        .setValue(BaseMultipartBlock.PART_Z, dz + 1), 3);
            table = (MahjongTableBlockEntity) level.getBlockEntity(POS);
            require(table != null, "temporary table did not create a master block entity");
            var host = register(server, "MenuOwner", players);
            var visitor = register(server, "MenuVisitor", players);
            var other = register(server, "MenuOther", players);

            PortMenus.settings(host, table);
            require(table.owner().filter(host.getUUID()::equals).isPresent(), "first menu opener did not claim ownerless table");
            require(host.opened instanceof PortMenus.Actions, "owner menu did not open");
            categoryMenus(table, host, visitor);
            analysisMenus(server, table, host, visitor);
            windPlate(table, visitor);
            ruleFamilies(table, host, visitor);
            competitionStarts(table, host);
            bloodBattleOption(table, host, visitor);
            readOnly(table, visitor);
            menuSeats(table, host, visitor);
            viewingOptions(table, host, visitor);
            transferConfirmation(server, table, host, visitor, other);
            activeMenu(table, host, visitor);
            passedRonRiichiHandlers(table, host, visitor, other);
            advanceWithWindPlate(table, visitor, other);
            result.put("passed", true);
        } catch (Throwable failure) {
            failure.printStackTrace(); result.put("passed", false); result.put("failure", failure.toString());
        } finally {
            if (table != null) {
                if (table.hasActiveMatch()) table.endGame();
                table.clearSpectators();
            }
            boolean profilesCleaned = true;
            for (var player : players) {
                try { Files.deleteIfExists(analysisDirectory(server).resolve(player.getUUID() + ".json")); }
                catch (java.io.IOException failure) {
                    profilesCleaned = false;
                    result.put("passed", false);
                    result.put("cleanupFailure", failure.toString());
                }
                server.getPlayerList().getPlayers().remove(player);
                server.getPlayerList().getPlayersByUUID().remove(player.getUUID(), player);
                player.getTextFilter().leave();
            }
            result.put("temporaryAnalysisProfilesRemoved", profilesCleaned);
            // Remove the temporary multipart table before reconstructing the exact original region.
            for (var pos : saved.keySet()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            for (var entry : saved.entrySet()) level.setBlock(entry.getKey(), entry.getValue().state(), 2);
            for (var entry : saved.entrySet()) if (entry.getValue().blockEntity() != null) {
                var restored = BlockEntity.loadStatic(entry.getKey(), entry.getValue().state(),
                        entry.getValue().blockEntity(), server.registryAccess());
                if (restored != null) level.setBlockEntity(restored);
            }
            result.put("fakePlayersRemoved", players.stream().allMatch(p -> server.getPlayerList().getPlayer(p.getUUID()) == null));
            result.put("originalRegionRestored", saved.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue().state())));
            result.put("checks", List.copyOf(checks));
            result.put("clientVisualTest", false);
            result.put("temporaryTable", List.of(POS.getX(), POS.getY(), POS.getZ()));
            try { Files.writeString(RESULT, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n"); }
            catch (Exception failure) { failure.printStackTrace(); }
            running = false;
        }
        System.out.println("RIICHI_MENU_INTEGRATION " + new GsonBuilder().create().toJson(result));
        return Boolean.TRUE.equals(result.get("passed")) ? 1 : 0;
    }

    private static MenuPlayer register(MinecraftServer server, String name, List<MenuPlayer> registered) {
        var uuid = UUID.randomUUID();
        require(server.getPlayerList().getPlayer(uuid) == null && Files.notExists(analysisDirectory(server).resolve(uuid + ".json")),
                "temporary test UUID already used");
        var player = new MenuPlayer(server, uuid, name);
        server.getPlayerList().getPlayers().add(player);
        server.getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        registered.add(player);
        return player;
    }

    private static Path analysisDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/riichi-analysis");
    }

    private static void categoryMenus(MahjongTableBlockEntity table, MenuPlayer host, MenuPlayer visitor) {
        var titles = List.of("牌桌座次", "玩法设置", "个人设置", "历史战绩", "牌桌操作");
        for (int i = 0; i < titles.size(); i++) {
            PortMenus.settings(visitor, table);
            int entry = 20 + i;
            require(analysisLabel(visitor, entry).contains(titles.get(i)), "missing category " + titles.get(i));
            click(visitor, entry);
            require(visitor.title.contains(titles.get(i)), "category opened a different page: " + visitor.title);
            click(visitor, 49);
            require(analysisLabel(visitor,entry).contains(titles.get(i)), "category did not return home");
        }
        var hostSkin = PlayerCosmetics.skin(host);
        String hostEffect = PlayerCosmetics.winEffect(host, table.winEffectId());
        page(visitor, table, 22);
        click(visitor, 12);
        var effects = WinEffects.all();
        for (int i = 0; i < effects.size(); i++) {
            var choice = effects.get(i);
            require(analysisLabel(visitor, 27 + i).contains(choice.displayName()), "effect not visible: " + choice.id());
            click(visitor, 27 + i);
            require(PlayerCosmetics.winEffect(visitor, table.winEffectId()).equals(choice.id())
                            && analysisLabel(visitor, 27 + i).contains("已选"),
                    "effect was not selected directly: " + choice.id());
        }
        require(visitor.opened.getSlot(32).getItem().isEmpty(), "old effect cycle button remains visible");
        require(PlayerCosmetics.skin(visitor).name().equals("GOLD")
                        && !PlayerCosmetics.winEffect(visitor,table.winEffectId()).equals(hostEffect)
                        && PlayerCosmetics.skin(host) == hostSkin
                        && PlayerCosmetics.winEffect(host,table.winEffectId()).equals(hostEffect),
                "visitor cosmetics were ineffective or modified the owner's appearance");
        var reconnected = new MenuPlayer(visitor.level().getServer(), visitor.getUUID(), "MenuReconnected");
        require(PlayerCosmetics.skin(reconnected).name().equals("GOLD")
                        && PlayerCosmetics.winEffect(reconnected,table.winEffectId()).equals(
                                PlayerCosmetics.winEffect(visitor,table.winEffectId())),
                "new player instance did not recover its UUID's personal cosmetics");
        click(visitor,10); click(visitor,27);
        checks.add("five-categories-return-to-home-and-non-owner-personal-cosmetics-persist-without-changing-owner");
    }

    private static void analysisMenus(MinecraftServer server, MahjongTableBlockEntity table, MenuPlayer winner, MenuPlayer payer) throws Exception {
        var store = PlayerAnalysisStore.get();
        require(store != null, "world startup did not initialize the statistics store");
        var mode = AnalysisMode.of(RuleSetPreset.MAHJONG_SOUL_4P, MatchLength.SOUTH, false, false, false);
        var hand = List.of(
                new PlayerAnalysisStore.HandResult(winner.getUUID(), true, false, false, List.of("RIICHI", "RIICHI")),
                new PlayerAnalysisStore.HandResult(payer.getUUID(), false, false, true, List.of()));
        var event = UUID.randomUUID().toString();
        require(store.recordHand(event, mode, hand), "new hand record rejected");
        for (int i = 0; i < 10; i++) store.recordMatch(event + "/" + i, mode, winner.getUUID(), 12, 3L, 4L);
        store.recordMatch(event, mode, payer.getUUID(), -100, 0L, 0L);

        var fromDisk = new PlayerAnalysisStore(analysisDirectory(server));
        require(!fromDisk.recordHand(event, mode, hand)
                        && !fromDisk.recordMatch(event + "/0", mode, winner.getUUID(), 12, 3L, 4L).recorded(),
                "disk reload reapplied an existing hand or match");
        require(fromDisk.profile(winner.getUUID(), mode).equals(store.profile(winner.getUUID(), mode))
                        && fromDisk.profile(winner.getUUID(), mode).yaku().equals(Map.of("RIICHI", 1L))
                        && fromDisk.profile(payer.getUUID(), mode).scoreTenths() == 0,
                "world data lost a profile, duplicated a yaku or charged below zero");
        checks.add("world-store-uuid-persistence-reopen-idempotence-yaku-and-zero-score-floor");

        // The outer test command is already running. A nested performPrefixedCommand queues work
        // until after it returns, so dispatch the registered player command synchronously here.
        winner.closeContainer();
        var dispatcher = server.getCommands().getDispatcher();
        require(dispatcher.execute("mahjong stats", winner.createCommandSourceStack()) == 1,
                "registered stats command did not succeed");
        require(winner.opened instanceof PortMenus.Actions, "registered player stats command did not open a menu");
        require(analysisLabel(winner, 19).equals("胡牌：1") && analysisLabel(winner, 21).equals("荣和：1")
                        && analysisLabel(winner, 24).equals("累计积分：12.0")
                        && analysisLabel(winner, 27).equals("近 10 局平均一致率：75.00")
                        && winner.opened.getSlot(28).getItem().isEmpty() && winner.opened.getSlot(29).getItem().isEmpty(),
                "unexpected stats menu values " + List.of(analysisLabel(winner, 19), analysisLabel(winner, 21),
                        analysisLabel(winner, 24), analysisLabel(winner, 27), analysisLabel(winner, 28), analysisLabel(winner, 29)));
        click(winner, 11);
        require(analysisLabel(winner, 19).equals("胡牌：0") && winner.opened.getSlot(24).getItem().isEmpty(),
                "entertainment history exposes a ranked game or score");
        click(winner, 11);
        require(analysisLabel(winner, 19).equals("胡牌：1"), "returning to ranked history lost the winner's profile");

        page(payer, table, 23);
        click(payer, 24);
        require(analysisLabel(payer, 19).equals("胡牌：0") && analysisLabel(payer, 22).equals("点炮：1")
                        && analysisLabel(payer, 24).equals("累计积分：0.0") && payer.opened.getSlot(27).getItem().isEmpty(),
                "table stats showed the wrong UUID, mode, points or history");
        click(payer, 8);
        click(payer, 49);
        require(analysisLabel(payer,20).contains("我的牌谱"), "refreshing stats lost the history return path");
        click(payer, 20);
        require(payer.title.contains("我的牌谱"), "history did not open the personal replay calendar");
        int back = payer.opened.getSlot(45).getItem().is(Items.ARROW) ? 45 : 49;
        require(analysisLabel(payer,back).contains("历史战绩"), "archive calendar, waiting or error page lost its return");
        click(payer, back);
        require(analysisLabel(payer,24).contains("我的统计"), "archive return did not open history");
        click(payer,40);
        require(payer.containerMenu == payer.inventoryMenu && payer.getInventory().countItem(
                com.riichimahjong.registry.ModItems.MAHJONG_TABLE_RECORD.get()) == 1, "blank record was not delivered to viewer");
        checks.add("table-history-personal-replay-calendar-statistics-and-blank-record");
        checks.add("registered-stats-command-and-table-button-personal-mode-filtered-stats-and-consistency-windows");
    }

    private static String analysisLabel(MenuPlayer player, int slot) {
        return player.opened.getSlot(slot).getItem().getHoverName().getString();
    }

    private static void ruleFamilies(MahjongTableBlockEntity table, MenuPlayer host, MenuPlayer visitor) {
        for(var family:RuleSetPreset.Family.values()) {
            // Choosing a four-player-only family must restore the fourth seat after sanma.
            table.selectPreset(RuleSetPreset.TENHOU_SANMA_3P);
            page(host, table, 21);
            var option=host.opened.getSlot(family.ordinal()).getItem();
            require(option.getHoverName().getString().contains(family.label()),"rule family missing from first menu row: "+family);
            require(option.get(DataComponents.LORE)!=null&&!option.get(DataComponents.LORE).lines().isEmpty(),
                    "rule family has no readable description: "+family);
            click(host,family.ordinal());
            require(table.preset()==family.preset(3),"owner could not select menu family: "+family);
            require(table.seats().stream().filter(s->s.enabled()).count()==(family.supportsThreePlayers()?3:4),
                    "selecting competition rules retained unsupported seat count: "+family);
            require(host.opened.getSlot(family.ordinal()).getItem().is(Items.EMERALD),"selected family has no selected indicator");
            var selected=table.preset();
            page(visitor, table, 21);click(visitor,(family.ordinal()+1)%RuleSetPreset.Family.values().length);
            require(table.preset()==selected,"non-owner changed rule family through its new menu slot");
            var saved=table.saveCustomOnly(host.level().getServer().registryAccess());
            require(saved.getStringOr("Preset","").equals(selected.name()),"menu selection did not persist preset identity");
            page(host, table, 21);click(host,10);
            require(table.preset()==(family.supportsThreePlayers()?family.preset(4):selected),
                    "player-count toggle changed or disabled a four-player competition rule");
            for(int slot:new int[]{11,12,13})require(!host.opened.getSlot(slot).getItem().isEmpty(),
                    "rule family selection overwrote shared variant controls");
        }
        table.selectPreset(RuleSetPreset.MAHJONG_SOUL_4P);
        checks.add("six-real-rule-family-buttons-owner-selection-four-player-only-restoration-read-only-access-and-preset-nbt");
    }

    private static void competitionStarts(MahjongTableBlockEntity table,MenuPlayer host) {
        table.setExchangeThreeTiles(false);table.setJokersEnabled(false);table.setBloodBattle(false);
        require(table.claimSeat(0,host.getUUID()),"competition menu fixture could not claim the owner's seat");
        for(var preset:List.of(RuleSetPreset.JPML_4P,RuleSetPreset.M_LEAGUE_4P)) {
            page(host, table, 21);click(host,preset.family().ordinal());page(host, table, 24);click(host,18);
            require(table.hasActiveMatch()&&table.preset()==preset,"real start button did not start selected competition rule");
            var driver=table.driver();var match=driver.match();var round=driver.currentRound();
            String profile=preset==RuleSetPreset.JPML_4P?"JPML_A":"M_LEAGUE";
            int starting=preset==RuleSetPreset.JPML_4P?30000:25000;
            int redCount=preset==RuleSetPreset.JPML_4P?0:3;
            require(match.playerCount()==4&&table.seats().stream().filter(seat->seat.enabled()).count()==4,
                    "competition match did not start with exactly four seats");
            require(driver.rules().competitiveProfile().name().equals(profile)&&!driver.rules().exchangeThree()
                            &&!driver.rules().jokersEnabled()&&!driver.rules().bloodBattle(),
                    "menu start lost its competition profile or silently enabled a variant");
            require(round.players().stream().allMatch(player->player.points()==starting),
                    "competition menu match used the wrong initial scores");
            var dealt=new ArrayList<TheMahjongTile>();
            round.players().forEach(player->dealt.addAll(player.currentHand()));
            dealt.addAll(round.liveWall());dealt.addAll(round.rinshanTiles());
            dealt.addAll(round.doraIndicators());dealt.addAll(round.uraDoraIndicators());
            require(dealt.size()==136&&dealt.stream().filter(TheMahjongTile::redDora).count()==redCount,
                    "actual competition deal has the wrong tile count or red fives");
            page(host, table, 21);click(host,RuleSetPreset.Family.TENHOU.ordinal());
            require(table.preset()==preset&&table.driver()==driver&&driver.match()==match,
                    "active competition menu allowed changing its rule family");
            var saved=table.saveCustomOnly(host.level().getServer().registryAccess());
            var registries = host.level().getServer().registryAccess();
            var analysis = saved.getCompoundOrEmpty("PlayerAnalysis").getStringOr("Session", "");
            require(!analysis.isEmpty() && !table.getUpdateTag(registries).contains("PlayerAnalysis"),
                    "statistics session is missing from disk data or visible in public update data");
            // The active driver profile wins over a stale legacy menu selection.
            saved.putString("Preset",RuleSetPreset.MAHJONG_SOUL_4P.name());
            table.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING,
                    host.level().getServer().registryAccess(),saved));
            require(table.preset()==preset&&table.driver().rules().competitiveProfile().name().equals(profile)
                            &&table.driver().match().equals(match),
                    "active competition block-entity reload lost profile, preset identity, dealt tiles or points");
            require(table.saveCustomOnly(registries).getCompoundOrEmpty("PlayerAnalysis").getStringOr("Session", "").equals(analysis),
                    "active competition table reload changed the statistics session");
            page(host, table, 21);click(host,RuleSetPreset.Family.TENHOU.ordinal());
            require(table.preset()==preset&&table.driver().rules().competitiveProfile().name().equals(profile),
                    "reloaded competition menu failed to retain active rule lock");
            table.endGame();
        }
        table.releaseSeat(0);table.selectPreset(RuleSetPreset.MAHJONG_SOUL_4P);
        checks.add("jpml-mleague-real-menu-start-four-seats-initial-points-red-tile-deal-active-rule-lock-and-profile-authoritative-be-nbt");
        checks.add("jpml-mleague-match-analysis-session-survives-real-table-nbt-and-stays-private");
    }

    private static void bloodBattleOption(MahjongTableBlockEntity table, MenuPlayer host, MenuPlayer visitor) {
        require(!table.bloodBattle(), "new table enables blood battle by default");
        for (var preset : RuleSetPreset.values()) {
            table.selectPreset(preset);
            page(host, table, 21);
            require(host.opened.getSlot(13).getItem().is(Items.GUNPOWDER), "disabled blood battle icon is not gunpowder");
            page(visitor, table, 21);click(visitor, 13);
            require(!table.bloodBattle(), "visitor enabled blood battle");
            page(host, table, 21);click(host, 13);
            require(table.bloodBattle() && host.opened.getSlot(13).getItem().is(Items.REDSTONE),
                    "owner could not enable the redstone blood battle switch for " + preset);
            var saved = table.saveCustomOnly(host.level().getServer().registryAccess());
            require(saved.getBooleanOr("BloodBattle", false), "table does not persist blood battle option");
            click(host, 13);
            require(!table.bloodBattle(), "owner could not disable blood battle");
        }
        table.selectPreset(RuleSetPreset.MAHJONG_SOUL_4P);
        checks.add("all-preset-owner-only-blood-battle-toggle-redstone-gunpowder-icons-and-table-nbt");
    }

    private static void readOnly(MahjongTableBlockEntity table, MenuPlayer visitor) {
        var originalOwner = table.owner();
        var originalPreset = table.preset();
        var originalLength = table.matchLength();
        boolean exchange = table.exchangeThreeTiles(), jokers = table.jokersEnabled(), mint = table.mintTilesFromNothing(), bloodBattle = table.bloodBattle();
        var defaultAi = table.defaultBotDifficulty(); var effect = table.winEffectId();
        long revision = table.matchRevision();
        for (int[] controls : List.of(new int[]{21,0,1,2,3,4,5,9,10,11,12,13,19,20},
                new int[]{20,4,20,24,40,45}, new int[]{24,18,27,35})) {
          for (int i = 1; i < controls.length; i++) {
            int slot = controls[i];
            page(visitor, table, controls[0]);
            require(visitor.opened instanceof PortMenus.Actions, "visitor could not open the shared configuration menu");
            click(visitor, slot);
            require(table.owner().equals(originalOwner) && table.preset() == originalPreset && table.matchLength() == originalLength
                            && table.exchangeThreeTiles() == exchange && table.jokersEnabled() == jokers
                            && table.mintTilesFromNothing() == mint && table.defaultBotDifficulty() == defaultAi
                            && table.winEffectId().equals(effect) && !table.hasActiveMatch()
                            && !table.spectatorHandsVisible() && table.bloodBattle() == bloodBattle && table.matchRevision() == revision,
                    "visitor mutated protected configuration through slot " + slot);
        }
        }
        checks.add("non-owner-real-menu-opens-but-rule-start-reset-inventory-ai-and-visibility-buttons-remain-protected");
    }

    private static void aimAtWindPlate(MenuPlayer player) {
        var target = new Vec3(POS.getX() + .5, POS.getY() + 9.5 / 16, POS.getZ() + .5);
        var delta = target.subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
    }

    private static void windPlate(MahjongTableBlockEntity table, MenuPlayer visitor) {
        aimAtWindPlate(visitor);
        var originalOwner = table.owner();
        for (boolean sneaking : List.of(false,true)) {
            visitor.setShiftKeyDown(sneaking);
            require(MahjongTableBlock.routeWindPlateUse(visitor, table)
                            && visitor.opened instanceof PortMenus.Actions && table.owner().equals(originalOwner)
                            && analysisLabel(visitor,23).contains("历史战绩"),
                    "a visitor could not open the real wind plate's home menu without taking ownership");
        }
        visitor.setShiftKeyDown(false);
        checks.add("non-owner-real-shift-wind-plate-route-opens-view-menu-without-owner-takeover");
    }

    private static void advanceWithWindPlate(MahjongTableBlockEntity table, MenuPlayer player, MenuPlayer watcher) throws Exception {
        require(table.claimSeat(1, player.getUUID()) && table.seatOfPlayer(watcher.getUUID()).isEmpty(),
                "wind plate fixture requires a seated player and an unseated observer");
        var initial = RuleSetPreset.MAHJONG_SOUL_4P.newMatch().startRound(new Random(17));
        var ended = initial.currentRound().orElseThrow().abortiveDraw(initial.ruleSet());
        var match = new TheMahjongMatch(4, initial.startingPoints(), initial.targetPoints(), initial.roundCount(),
                initial.state(), initial.tileSet(), initial.ruleSet(), List.of(), ended);
        var humans = new ArrayList<MahjongPlayerInterface>();
        for (int seat = 0; seat < match.playerCount(); seat++) humans.add(new MahjongTableHumanPlayer());
        var driver = new TheMahjongDriver(match, humans, new Random(17),
                new TheMahjongDriver.Snapshot(new MatchPhase.RoundEnded(List.of()), false, OptionalInt.empty()));
        var driverField = MahjongTableBlockEntity.class.getDeclaredField("driver");
        driverField.setAccessible(true);
        driverField.set(table, driver);
        var stage = MahjongTableBlockEntity.class.getDeclaredField("resultAnimStage");
        stage.setAccessible(true);
        try {
            aimAtWindPlate(player);
            aimAtWindPlate(watcher);
            player.setShiftKeyDown(true);
            stage.set(table, MahjongTableBlockEntity.ResultAnimStage.SHOW_FINAL);
            player.closeContainer();
            require(MahjongTableBlock.routeWindPlateUse(player, table) && player.opened instanceof PortMenus.Actions
                            && driver.currentRound() == ended, "wind plate skipped the result presentation");
            stage.set(table, MahjongTableBlockEntity.ResultAnimStage.AWAITING_ADVANCE);
            watcher.setShiftKeyDown(true);
            watcher.closeContainer();
            require(MahjongTableBlock.routeWindPlateUse(watcher, table) && watcher.opened instanceof PortMenus.Actions
                            && driver.currentRound() == ended, "unseated observer could advance the result");
            player.setShiftKeyDown(false);
            player.closeContainer();
            require(MahjongTableBlock.routeWindPlateUse(player, table) && player.opened instanceof PortMenus.Actions
                            && driver.currentRound() == ended, "ordinary wind plate click skipped the result");
            player.setShiftKeyDown(true);
            player.closeContainer();
            require(MahjongTableBlock.routeWindPlateUse(player, table) && player.opened == player.inventoryMenu
                            && driver.currentRound() != ended && driver.match().completedRounds().size() == 1,
                    "seated shift-click did not advance directly to the next hand");
            var next = driver.currentRound();
            require(MahjongTableBlock.routeWindPlateUse(player, table) && player.opened instanceof PortMenus.Actions
                            && driver.currentRound() == next, "a repeated wind plate click skipped the new hand");
            checks.add("wind-plate-ready-seated-sneak-advances-once-other-clicks-open-menu");
        } finally {
            watcher.setShiftKeyDown(false);
            player.setShiftKeyDown(false);
            table.endGame();
        }
    }

    private static void menuSeats(MahjongTableBlockEntity table, MenuPlayer host, MenuPlayer visitor) {
        page(visitor, table, 20); click(visitor, 31);
        require(table.seatOfPlayer(visitor.getUUID()).orElse(-1) == 1, "visitor could not join an empty menu seat");
        var head = visitor.opened.getSlot(31).getItem();
        require(head.is(Items.PLAYER_HEAD) && head.get(DataComponents.PROFILE) != null
                        && head.get(DataComponents.PROFILE).partialProfile().id().equals(visitor.getUUID()),
                "occupied seat did not contain the seated player's profile head");
        click(visitor, 31);
        require(table.seatOfPlayer(visitor.getUUID()).isEmpty(), "clicking own head did not leave the seat");
        require(analysisLabel(visitor,31).contains("空位"), "leaving did not restore an empty seat icon");
        page(host, table, 20); click(host, 23);
        require(table.seatOfPlayer(host.getUUID()).orElse(-1) == 0, "owner could not join their empty seat");
        var defaultAi = table.defaultBotDifficulty();
        int[] repeaters = {24, 40, 20, 4};
        for (int seat = 0; seat < repeaters.length; seat++) {
            int slot = repeaters[seat];
            require(host.opened.getSlot(slot).getItem().is(Items.REPEATER), "missing repeater for seat " + seat);
            click(host, slot);
            require(table.botDifficultyOverride(seat).isPresent(), "repeater did not change seat " + seat);
            for (int other = 0; other < repeaters.length; other++)
                require(other == seat || table.botDifficultyOverride(other).isEmpty(), "repeater changed another seat");
            table.setSeatBotDifficulty(seat, null);
        }
        require(host.opened.getSlot(45).getItem().isEmpty() && table.defaultBotDifficulty() == defaultAi,
                "removed AI control is present or per-seat selection changed the default");
        checks.add("real-seat-menu-join-profile-head-and-own-head-leave");
        checks.add("seat-repeaters-change-only-their-own-ai-and-no-default-control");
    }

    private static void viewingOptions(MahjongTableBlockEntity table, MenuPlayer host, MenuPlayer visitor) {
        page(visitor, table, 21);
        require(visitor.opened.getSlot(20).getItem().is(Items.ENDER_PEARL), "hidden spectator hands used the wrong icon");
        page(visitor, table, 24); click(visitor, 44);
        require(table.isWatching(visitor.getUUID()) && table.seatOfPlayer(visitor.getUUID()).isEmpty(),
                "watch button claimed a playing seat or did not register explicit audience");
        click(visitor, 44); require(!table.isWatching(visitor.getUUID()), "watch button did not let viewer leave");
        page(host, table, 21); click(host, 20);
        require(table.spectatorHandsVisible() && host.opened.getSlot(20).getItem().is(Items.ENDER_EYE),
                "owner could not enable spectator hands with an eye icon");
        page(visitor, table, 21); click(visitor, 20);
        require(table.spectatorHandsVisible(), "viewer toggled protected spectator-hand visibility");
        page(host, table, 21); click(host, 20);
        require(!table.spectatorHandsVisible() && host.opened.getSlot(20).getItem().is(Items.ENDER_PEARL),
                "owner could not hide spectator hands with a pearl icon");
        checks.add("slot-44-explicit-watch-toggle-and-owner-only-slot-20-eye-pearl-visibility-control");
    }

    private static void transferConfirmation(MinecraftServer server, MahjongTableBlockEntity table,
            MenuPlayer host, MenuPlayer target, MenuPlayer other) {
        page(target, table, 20); click(target, 31);
        page(host, table, 20); click(host, 31);
        require(table.owner().filter(host.getUUID()::equals).isPresent() && host.opened.getSlot(29).getItem().is(Items.REDSTONE_BLOCK),
                "clicking another player's head transferred authority without a confirmation");
        click(host, 33); require(table.owner().filter(host.getUUID()::equals).isPresent(), "cancel transferred authority");

        page(host, table, 20); click(host, 31); var oldConfirmation = host.opened;
        page(target, table, 20); click(target, 31);
        oldConfirmation.clicked(29, 0, ContainerInput.PICKUP, host);
        require(table.owner().filter(host.getUUID()::equals).isPresent(), "target leaving did not invalidate a transfer confirmation");
        page(target, table, 20); click(target, 31);

        page(host, table, 20); click(host, 31); var offlineConfirmation = host.opened;
        server.getPlayerList().getPlayersByUUID().remove(target.getUUID());
        offlineConfirmation.clicked(29, 0, ContainerInput.PICKUP, host);
        require(table.owner().filter(host.getUUID()::equals).isPresent(), "offline target received ownership");
        server.getPlayerList().getPlayersByUUID().put(target.getUUID(), target);

        page(host, table, 20); click(host, 31); var confirmed = host.opened;
        click(host, 29);
        require(table.owner().filter(target.getUUID()::equals).isPresent(), "confirmed owner transfer failed");
        confirmed.clicked(29, 0, ContainerInput.PICKUP, host);
        require(table.owner().filter(target.getUUID()::equals).isPresent(), "former owner's repeated confirmation changed authority");
        long revision = table.matchRevision(); var preset = table.preset();
        page(host, table, 21); click(host, 2);
        require(table.matchRevision() == revision && table.preset() == preset, "former owner retained configuration authority");

        page(target, table, 20); click(target, 23); click(target, 29);
        require(table.owner().filter(host.getUUID()::equals).isPresent(), "new owner could not deliberately transfer authority back");
        checks.add("real-owner-transfer-confirm-cancel-target-leave-offline-old-menu-and-revoked-authority");
    }

    private static void activeMenu(MahjongTableBlockEntity table, MenuPlayer host, MenuPlayer visitor) throws Exception {
        require(table.tryStartMatch(782463L) == MahjongTableBlockEntity.StartMatchResult.STARTED, "active menu fixture failed to start");
        var driver = table.driver(); var match = driver.match();
        discardPreference(table, host, visitor);
        boolean bloodBattle=table.bloodBattle();
        var preset=table.preset();
        for(var family:RuleSetPreset.Family.values()) {
            page(host, table, 21);click(host,family.ordinal());
            require(table.preset()==preset&&table.driver()==driver&&driver.match()==match,
                    "active match allowed rule-family change through "+family);
        }
        page(host, table, 21); click(host, 13);
        require(table.bloodBattle()==bloodBattle&&driver.match().ruleSet().bloodBattle()==bloodBattle,
                "active match allowed its blood battle switch to change");
        require(!table.watch(visitor), "already seated player could also register as a spectator");
        page(visitor, table, 24); click(visitor, 44);
        require(table.seatOfPlayer(visitor.getUUID()).isEmpty(), "active watch button did not release the playing seat");
        require(table.driver() == driver && driver.match() == match, "menu departure changed active hand or scores");
        require(table.isWatching(visitor.getUUID()), "departing player could not enter watch mode");
        page(host, table, 21); click(host, 20);
        require(table.spectatorHandsVisible() && !table.spectatorCanSeeHands(visitor.getUUID())
                        && !SpectatorHands.eligible(table, visitor),
                "former participant obtained concealed hands after switching to spectator");
        page(host, table, 21); click(host, 20);
        page(visitor, table, 20);
        click(visitor, 31);
        require(table.seatOfPlayer(visitor.getUUID()).orElse(-1) == 1 && !table.isWatching(visitor.getUUID()),
                "reclaiming a reserved playing seat did not stop watch mode");
        require(table.driver() == driver && driver.match() == match, "menu takeover rewrote the running match");
        table.endGame();
        checks.add("active-seat-menu-leave-watch-rejoin-preserves-rules-hand-and-scores");
    }

    private static void discardPreference(MahjongTableBlockEntity table, MenuPlayer host, MenuPlayer visitor) {
        require(!DiscardPreference.requiresConfirmation(host) && !DiscardPreference.requiresConfirmation(visitor),
                "new players must start in direct discard mode");
        windPlate(table, visitor);
        click(visitor, 22);
        click(visitor, 19);
        require(DiscardPreference.requiresConfirmation(visitor) && !DiscardPreference.requiresConfirmation(host)
                        && analysisLabel(visitor, 19).contains("二次确认"),
                "a seated non-owner could not change only their own mode in an active match");
        var registries = visitor.level().getServer().registryAccess();
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        visitor.saveWithoutId(saved);
        click(visitor, 19);
        require(!DiscardPreference.requiresConfirmation(visitor) && analysisLabel(visitor, 19).contains("右键直接出牌"),
                "menu did not restore direct discard mode");
        visitor.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved.buildResult()));
        require(DiscardPreference.requiresConfirmation(visitor), "confirm mode was lost on player NBT reload");
        page(visitor, table, 22); click(visitor, 19);
        checks.add("active-non-owner-wind-plate-discard-mode-personal-default-toggle-and-player-nbt-reload");
    }

    private static void spectatorRendering(MahjongTableBlockEntity table, MenuPlayer host, MenuPlayer seated,
            MenuPlayer watcher) throws Exception {
        var hud = new TableHud();
        SpectatorHands privateHands = null;
        try {
            var board = PublicTable.capture(table);
            require(board.seats().stream().allMatch(s -> s.revealed().isEmpty()), "live fixture prematurely revealed public hands");
            hud.update(table, board);
            require(hud.bars.stream().allMatch(bar -> bar.getPlayers().contains(host) && bar.getPlayers().contains(seated)
                            && !bar.getPlayers().contains(watcher)), "unregistered nearby viewer received combat HUD");
            require(!SpectatorHands.eligible(table, watcher), "unregistered nearby viewer could request concealed hands");
            require(table.watch(watcher), "explicit watcher registration failed");
            hud.update(table, board);
            require(hud.bars.stream().allMatch(bar -> bar.getPlayers().contains(host) && bar.getPlayers().contains(seated)
                            && bar.getPlayers().contains(watcher)), "explicit spectator did not receive the same HUD audience as players");
            require(!SpectatorHands.eligible(table, watcher), "hidden-hand option leaked private hands to a registered watcher");
            page(host, table, 21); click(host, 20);
            require(SpectatorHands.eligible(table, watcher) && !SpectatorHands.eligible(table, seated),
                    "spectator reveal permission included an opponent or excluded its watcher");
            board = PublicTable.capture(table);
            require(board.seats().stream().allMatch(s -> s.revealed().isEmpty()),
                    "enabling spectator hands leaked concealed identities into the shared public snapshot");
            var snapshot = SpectatorHands.capture(table, board);
            require(snapshot.hands().size() == table.driver().match().playerCount()
                            && snapshot.hands().stream().allMatch(hand -> !hand.tiles().isEmpty() && hand.tiles().stream().allMatch(code -> code >= 0)),
                    "watcher snapshot did not contain each private hand's real tile identities");
            privateHands = new SpectatorHands(table, watcher, snapshot);
            var holderField = SpectatorHands.class.getDeclaredField("holder"); holderField.setAccessible(true);
            var holder = (ElementHolder)holderField.get(privateHands);
            require(holder.getWatchingPlayers().size() == 1 && holder.getWatchingPlayers().contains(watcher.connection),
                    "private spectator hands were tracked by another player connection");
            require(holder.getElements().stream().allMatch(element -> element instanceof ItemDisplayElement display
                            && display.getVisibilityPredicate().test(watcher)
                            && !display.getVisibilityPredicate().test(host) && !display.getVisibilityPredicate().test(seated)),
                    "private spectator view had a control entity or failed its owner-only visibility predicate");
            var faces = holder.getElements().stream().map(ItemDisplayElement.class::cast)
                    .map(ItemDisplayElement::getItem).toList();
            require(faces.size() == snapshot.hands().stream().mapToInt(hand -> hand.tiles().size()).sum()
                            && faces.stream().noneMatch(stack -> stack.hasFoil()
                                    || stack.get(DataComponents.ITEM_MODEL).getPath().startsWith("bridge/dora/")),
                    "spectator received private dora appearance in a concealed hand");
            require(faces.stream().anyMatch(stack -> stack.get(DataComponents.ITEM_MODEL).getPath().equals("bridge/tile_dragon_white")),
                    "eligible watcher did not receive the ordinary face of the fixture's concealed dora");
            page(host, table, 21); click(host, 20);
            require(!SpectatorHands.eligible(table, watcher), "disabling spectator hands left reveal permission active");
            privateHands.destroy(); privateHands = null;
            require(holder.getWatchingPlayers().isEmpty(), "discarded spectator hand holder retained client tracking");
            table.stopWatching(watcher.getUUID());
            hud.update(table, PublicTable.capture(table));
            require(hud.bars.stream().noneMatch(bar -> bar.getPlayers().contains(watcher)), "leaving watch mode retained combat HUD");
            checks.add("real-spectator-hud-opt-in-shared-player-bars-and-private-hand-visibility-toggle-without-public-leak");
        } finally {
            if (privateHands != null) privateHands.destroy();
            table.stopWatching(watcher.getUUID());
            hud.destroy();
        }
    }

    /** Real server-side player handlers; this deliberately does not manufacture client mouse packets. */
    private static void passedRonRiichiHandlers(MahjongTableBlockEntity table,MenuPlayer source,MenuPlayer skipped,MenuPlayer watcher) throws Exception {
        var white=riichiTile(4,1);var green=riichiTile(4,2);
        for(boolean variants:new boolean[]{false,true}) {
            table.selectPreset(RuleSetPreset.MAHJONG_SOUL_4P);
            table.setExchangeThreeTiles(variants);table.setJokersEnabled(variants);table.setBloodBattle(variants);
            require(table.claimSeat(0,source.getUUID())&&table.claimSeat(1,skipped.getUUID()),
                    "real passed-ron handler fixture cannot assign its source/recipient seats");
            var base=RuleSetPreset.MAHJONG_SOUL_4P.newMatch(MatchLength.SOUTH,variants,variants,variants);
            var players=new ArrayList<TheMahjongPlayer>();var humans=new ArrayList<MahjongTableHumanPlayer>();
            for(int seat=0;seat<4;seat++) {
                var hand=new ArrayList<TheMahjongTile>();
                for(int rank=1;rank<=9;rank++)hand.add(riichiTile(seat%3,rank));
                hand.addAll(Collections.nCopies(3,riichiTile(3,1)));hand.add(white);
                if(variants)hand.set(0,Jokers.TILE);
                if(seat==0)hand.add(white);
                players.add(new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(),
                        List.of(new TheMahjongDiscard(riichiTile(2,8),false)),List.of()));
                var human=new MahjongTableHumanPlayer();human.setOccupant(seat==0?source.getUUID():seat==1?skipped.getUUID():UUID.randomUUID());humans.add(human);
            }
            var indicators=Collections.nCopies(5,riichiTile(4,3));
            var round=new TheMahjongRound(TheMahjongTile.Wind.SOUTH,2,0,0,0,TheMahjongRound.State.TURN,0,-1,
                    TheMahjongRound.ActiveTile.drawn(white),Collections.nCopies(32,green),Collections.nCopies(4,green),
                    indicators,indicators,1,players,List.of());
            var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                    base.tileSet(),base.ruleSet(),List.of(),round);
            var driver=new TheMahjongDriver(match,new ArrayList<MahjongPlayerInterface>(humans),new Random(93),
                    new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
            if(variants)driver.restoreExchangeState(new ExchangeThreeState.Snapshot(4,1,Collections.nCopies(4,List.of()),Set.of(),true));
            var driverField=MahjongTableBlockEntity.class.getDeclaredField("driver");driverField.setAccessible(true);driverField.set(table,driver);
            PrivateHand privateHand=null;
            try {
                if (variants) submit(driver,0,new PlayerAction.Discard(white));
                else {
                    spectatorRendering(table, source, skipped, watcher);
                    confirmDuplicateDiscard(table, source, skipped, humans.get(0), white);
                }
                require(driver.legalActions(1).stream().anyMatch(action->action instanceof PlayerAction.DeclareRon),
                        "live handler fixture has no legal ron");
                var pass=new TableButton.SubmitAction(PlayerAction.Pass.INSTANCE);
                clickLiveTableButton(table,skipped,1,pass);
                var confirm=table.tableButtonsForSeat(1).stream().filter(button->button instanceof TableButton.ConfirmSkip).findFirst().orElseThrow();
                require(!humans.get(1).hasQueued()&&!driver.currentRound().players().get(1).temporaryFuriten(),
                        "opening real skip prompt committed an unconfirmed pass");
                clickLiveTableButton(table,skipped,1,confirm);
                require(humans.get(1).hasQueued(),"real confirmation did not queue a pass");
                driver.advance(.05);passLiveClaims(driver);
                require(driver.currentRound().players().get(1).temporaryFuriten()
                                &&!Furiten.canRon(driver.currentRound().players().get(1),white),
                        "confirmed real passed ron did not preserve temporary ron exclusion");
                submit(driver,1,PlayerAction.Draw.INSTANCE);
                var snapshot=PrivateHand.snapshot(table,1);
                var toggle=snapshot.buttons().stream().filter(button->button instanceof TableButton.RiichiToggle).findFirst().orElseThrow();
                privateHand=new PrivateHand(table,skipped,1,snapshot);
                require(privateHand.valid(skipped)&&privateHand.holder.getWatchingPlayers().contains(skipped.connection),
                        "passed-ron riichi controls were not registered to the real seated ServerPlayer");
                require(humans.get(1).selectTableButton(toggle,driver,1,skipped),
                        "actual ServerPlayer sender could not select riichi after skipped ron");
                privateHand.destroy();privateHand=null;
                int displayed=table.handDisplayOrder(1).indexOf(green);
                require(displayed>=0,"actual displayed drawn tile has no click index");
                if (variants) {
                    page(skipped, table, 22); click(skipped, 19); skipped.closeContainer();
                    table.onCuteClick(skipped,new InteractKey.SeatSlot((byte)1,InteractKey.SeatSlot.AREA_HAND,(short)displayed));
                    require(!humans.get(1).hasQueued() && table.selectedDiscardIndex(1)==displayed
                                    && driver.currentRound().players().get(1).points()==25000,
                            "first riichi discard click queued a declaration or spent a stick");
                }
                table.onCuteClick(skipped,new InteractKey.SeatSlot((byte)1,InteractKey.SeatSlot.AREA_HAND,(short)displayed));
                require(humans.get(1).hasQueued(),"actual table hand-click handler failed to queue riichi discard after passed ron");
                driver.advance(.05);passLiveClaims(driver);
                var declared=driver.currentRound().players().get(1);
                require(declared.riichi()&&declared.points()==24000&&driver.currentRound().riichiSticks()==1,
                        "real passed-ron riichi was not accepted with exactly one 1000-point deposit");
                require(!declared.temporaryFuriten()&&!declared.riichiPermanentFuriten(),
                        "pre-riichi temporary furiten incorrectly persisted after completed own discard");
            } finally {
                if(privateHand!=null)privateHand.destroy();
                table.endGame();
            }
        }
        checks.add("real-server-player-confirmed-passed-ron-then-riichi-private-controls-hand-click-and-single-deposit-normal-and-south-exchange-joker-blood");
    }

    private static void confirmDuplicateDiscard(MahjongTableBlockEntity table, MenuPlayer owner, MenuPlayer opponent,
            MahjongTableHumanPlayer human, TheMahjongTile white) {
        var order = table.handDisplayOrder(0);
        int first = order.indexOf(white), second = order.lastIndexOf(white);
        require(first >= 0 && second > first, "fixture requires two identical white dragons");
        var round = table.driver().currentRound();
        page(owner, table, 22); click(owner, 19); owner.closeContainer();
        var hand = new PrivateHand(table, owner, 0, PrivateHand.snapshot(table, 0));
        try {
            var goldFaces = hand.holder.getElements().stream().filter(ItemDisplayElement.class::isInstance)
                    .map(ItemDisplayElement.class::cast)
                    .filter(display -> display.getItem().get(DataComponents.ITEM_MODEL).getPath().equals("bridge/dora/tile_dragon_white"))
                    .toList();
            require(goldFaces.size() == 2 && hand.holder.getWatchingPlayers().size() == 1
                            && hand.holder.getWatchingPlayers().contains(owner.connection)
                            && goldFaces.stream().allMatch(display -> !display.getItem().hasFoil()
                                    && display.getVisibilityPredicate().test(owner) && !display.getVisibilityPredicate().test(opponent)),
                    "concealed white-dragon dora did not use two owner-only gold models");
            checks.add("concealed-dora-gold-for-its-owner-and-ordinary-faces-for-authorized-spectator");
            for (int index : List.of(first, second)) {
                table.onCuteClick(owner, new InteractKey.SeatSlot((byte)0, InteractKey.SeatSlot.AREA_HAND, (short)index));
                hand.snapshot = PrivateHand.snapshot(table, 0);
                hand.tick();
                require(!human.hasQueued() && table.driver().currentRound() == round && hand.snapshot.selectedDiscardIndex() == index,
                        "clicking a different copy of the same tile submitted a discard or lost the selection");
                var glowing = hand.holder.getElements().stream().filter(ItemDisplayElement.class::isInstance)
                        .map(ItemDisplayElement.class::cast).filter(ItemDisplayElement::isGlowing).toList();
                var direction = PortViews.outward(0, table.getBlockState());
                var position = TableLayout.handAnchor(POS, direction,
                        TableLayout.handAlong(hand.snapshot.handSlots().get(index), hand.snapshot.handGapStarts()), false);
                require(glowing.size() == 1 && glowing.getFirst().getOverridePos().distanceTo(position) < 1e-7
                                && glowing.getFirst().getVisibilityPredicate().test(owner)
                                && !glowing.getFirst().getVisibilityPredicate().test(opponent),
                        "the selected physical copy did not have the sole owner-only outline");
            }
            page(owner, table, 22); click(owner, 19);
            require(table.selectedDiscardIndex(0) < 0 && !human.hasQueued(), "changing mode retained or submitted the preview");
            click(owner, 19); owner.closeContainer();
            var target = new InteractKey.SeatSlot((byte)0, InteractKey.SeatSlot.AREA_HAND, (short)second);
            table.onCuteClick(owner, target);
            require(!human.hasQueued(), "a cleared selection could be confirmed by one click");
            table.onCuteClick(owner, target);
            require(human.hasQueued() && table.selectedDiscardIndex(0) < 0, "second click did not confirm and clear the preview");
            table.driver().advance(.05);
            checks.add("actual-discard-selection-identical-copies-private-outline-reselection-mode-change-and-confirmation");
        } finally {
            hand.destroy();
        }
    }
    private static TheMahjongTile riichiTile(int suit,int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.values()[suit],rank,false);
    }
    private static void submit(TheMahjongDriver driver,int seat,PlayerAction action) {
        driver.submitAction(seat,action);driver.advance(.05);
    }
    private static void passLiveClaims(TheMahjongDriver driver) {
        for(int tries=0;driver.currentPhase() instanceof MatchPhase.AwaitingClaims claims;tries++) {
            require(tries<5,"real passed-ron probe claim window failed to resolve");
            for(int seat:claims.pendingSeats())if(driver.legalActions(seat).contains(PlayerAction.Pass.INSTANCE))driver.submitAction(seat,PlayerAction.Pass.INSTANCE);
            driver.advance(.05);
        }
    }
    private static void clickLiveTableButton(MahjongTableBlockEntity table,ServerPlayer player,int seat,TableButton button) {
        int index=table.tableButtonsForSeat(seat).indexOf(button);require(index>=0,"actual table button disappeared before explicit click");
        table.onCuteClick(player,new InteractKey.SeatSlot((byte)seat,InteractKey.SeatSlot.AREA_BUTTON,(short)index));
    }

    private static void page(MenuPlayer player, MahjongTableBlockEntity table, int entry) {
        PortMenus.settings(player, table);
        click(player, entry);
    }
    private static void click(MenuPlayer player, int slot) {
        require(player.opened != null && player.opened == player.containerMenu && player.opened != player.inventoryMenu,
                "test has no current action menu");
        player.opened.clicked(slot, 0, ContainerInput.PICKUP, player);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class MenuPlayer extends ServerPlayer {
        AbstractContainerMenu opened;
        String title;
        MenuPlayer(MinecraftServer server, UUID uuid, String name) {
            super(server, server.overworld(), new GameProfile(uuid, name), ClientInformation.createDefault());
            setPos(POS.getX() + .5, POS.getY() + 1, POS.getZ() + 2.5);
            new ServerGamePacketListenerImpl(server, new CapturingConnection(), this,
                    CommonListenerCookie.createInitial(getGameProfile(), false));
        }
        @Override public OptionalInt openMenu(MenuProvider provider) {
            title = provider.getDisplayName().getString();
            opened = provider.createMenu(1, getInventory(), this);
            if (opened != null) containerMenu = opened;
            return opened == null ? OptionalInt.empty() : OptionalInt.of(1);
        }
        @Override public void closeContainer() { containerMenu = inventoryMenu; opened = inventoryMenu; }
        @Override public void sendSystemMessage(Component text) {}
        @Override public void sendSystemMessage(Component text, boolean overlay) {}
    }

    private static final class CapturingConnection extends Connection {
        CapturingConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(Packet<?> packet) {}
        @Override public void send(Packet<?> packet, ChannelFutureListener listener) {}
        @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {}
        @Override public boolean isMemoryConnection() { return true; }
    }
}
