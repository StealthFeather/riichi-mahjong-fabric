package com.riichimahjong.fabric;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.riichimahjong.ai.AiRuntime;
import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.KobalabBot;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.registry.ModBlocks;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongRound;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.bots.StupidActiveBot;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/** Real table/controller tests, installed only on the disposable integration server. */
public final class AiIntegrationTest implements ModInitializer {
    private static final BlockPos POS = new BlockPos(16, -60, 0);
    private static final Path RESULTS = Path.of("riichi-ai-integration-results.json");
    private static final UUID OWNER = UUID.fromString("77fdc6db-a898-498f-bd32-04ea566876a5");
    private static final List<String> checks = new ArrayList<>();
    private static final Map<String, Object> observations = new LinkedHashMap<>();
    private static MahjongTableBlockEntity table;
    private static MenuPlayer menuPlayer;
    private static int stage = -1;
    private static long startNanos, dueTick;
    private static TheMahjongRound humanRound;
    private static KobalabBot staleBot;
    private static DecisionRequest staleRequest;
    private static Map<String, Long> metricsBefore;
    private static int acceptedDiscards;
    private static TheMahjongRound countedRound;
    private static int countedDiscards;

    @Override
    public void onInitialize() {
        if (!Files.exists(Path.of("riichi-integration-enabled"))) {
            throw new IllegalStateException("AI integration addon requires the isolated-server marker");
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("riichi_ai_test")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("run").executes(context -> {
                            begin(context.getSource().getServer());
                            return 1;
                        }))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stage < 0) return;
            try { tick(server); }
            catch (Throwable failure) { finish(server, false, failure.toString()); }
        });
    }

    private static void begin(MinecraftServer server) {
        require(stage < 0, "AI integration test is already running");
        startNanos = System.nanoTime();
        checks.clear();
        observations.clear();
        acceptedDiscards = 0;
        countedRound = null;
        staleBot = null;
        staleRequest = null;
        metricsBefore = AiRuntime.metrics();
        try {
            var level = server.overworld();
            level.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, true);
            level.getChunkAt(POS);
            var state = ModBlocks.MAHJONG_TABLE.get().defaultBlockState();
            if (!(level.getBlockEntity(POS) instanceof MahjongTableBlockEntity)) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(POS.offset(dx, 0, dz), state
                            .setValue(BaseMultipartBlock.PART_X, dx + 1)
                            .setValue(BaseMultipartBlock.PART_Z, dz + 1), 3);
                }
            }
            table = (MahjongTableBlockEntity) level.getBlockEntity(POS);
            require(table != null, "real table entity was not created");
            menuPlayer = new MenuPlayer(server);
            if (table.hasActiveMatch()) table.endGame();
            table.selectPreset(RuleSetPreset.MAHJONG_SOUL_4P);
            table.setDefaultBotDifficulty(BotDifficulty.NORMAL);
            for (int seat = 0; seat < 4; seat++) table.setSeatBotDifficulty(seat, null);
            checkIdleConfiguration();
            checkMenuConfiguration();
            checkSavedConfiguration(server);
            checkResetMenus(server);
            checkSanma();
            table.selectPreset(RuleSetPreset.MAHJONG_SOUL_4P);
            require(table.tryStartMatch(123456L) == MahjongTableBlockEntity.StartMatchResult.STARTED,
                    "four-player table did not start");
            assertControllers();
            checkLockedConfiguration();
            checkActiveSaveAndRestore(server);

            var before = table.driver().currentRound();
            require(table.claimSeat(0, OWNER), "human could not take an unreserved bot seat");
            require(table.driver().playerAt(0) instanceof MahjongTableHumanPlayer,
                    "taking bot seat did not create a human controller");
            require(table.driver().currentRound() == before, "taking a seat changed the current round");
            stage = 0;
            writeReport(false, "running");
            System.out.println("RIICHI_AI_INTEGRATION_RUNNING " + AiRuntime.status());
        } catch (Throwable failure) {
            finish(server, false, failure.toString());
        }
    }

    private static void checkIdleConfiguration() {
        require(table.defaultBotDifficulty() == BotDifficulty.NORMAL, "default difficulty is not normal");
        table.cycleSeatBotDifficulty(3);
        require(table.botDifficultyOverride(3).orElseThrow() == BotDifficulty.EASY, "inherit did not cycle to easy");
        table.cycleSeatBotDifficulty(3);
        require(table.botDifficultyOverride(3).orElseThrow() == BotDifficulty.NORMAL, "easy did not cycle to normal");
        table.cycleSeatBotDifficulty(3);
        require(table.botDifficultyOverride(3).orElseThrow() == BotDifficulty.HARD, "normal did not cycle to hard");
        table.cycleSeatBotDifficulty(3);
        require(table.botDifficultyOverride(3).isEmpty(), "hard did not cycle to inherit");
        require(table.setDefaultBotDifficulty(BotDifficulty.HARD), "idle default setting rejected");
        require(table.setSeatBotDifficulty(0, BotDifficulty.EASY), "idle easy override rejected");
        require(table.setSeatBotDifficulty(1, BotDifficulty.NORMAL), "idle normal override rejected");
        require(table.setSeatBotDifficulty(2, BotDifficulty.HARD), "idle hard override rejected");
        require(table.botDifficulty(3) == BotDifficulty.HARD, "inherited difficulty did not follow table default");
        require(!table.setSeatBotDifficulty(-1, BotDifficulty.EASY)
                && !table.setSeatBotDifficulty(4, BotDifficulty.EASY), "invalid seat accepted a difficulty override");
        pass("default-and-per-seat-difficulty-cycle");
    }

    private static void checkSavedConfiguration(MinecraftServer server) throws Exception {
        CompoundTag saved = table.exportRecordSnapshot(server.registryAccess());
        var legacy = saved.copy();
        legacy.remove("BotDifficulty");
        var seats = legacy.getListOrEmpty("Seats");
        for (int seat = 0; seat < seats.size(); seat++) seats.getCompoundOrEmpty(seat).remove("BotDifficulty");
        load(server, legacy);
        require(table.defaultBotDifficulty() == BotDifficulty.NORMAL, "legacy save did not default to normal");
        for (int seat = 0; seat < 4; seat++) require(table.botDifficultyOverride(seat).isEmpty(),
                "legacy seat retained an override");
        pass("old-save-defaults-to-normal-with-inherited-seats");

        var malformed = saved.copy();
        malformed.putString("BotDifficulty", "invalid-default");
        malformed.getListOrEmpty("Seats").getCompoundOrEmpty(0).putString("BotDifficulty", "invalid-seat");
        load(server, malformed);
        require(table.defaultBotDifficulty() == BotDifficulty.NORMAL, "invalid default did not recover to normal");
        require(table.botDifficultyOverride(0).isEmpty(), "invalid seat did not recover to inherit");
        require(table.botDifficulty(1) == BotDifficulty.NORMAL, "valid seat override was lost with an invalid default");
        pass("invalid-difficulty-nbt-recovers-without-losing-valid-overrides");

        load(server, saved);
        require(table.defaultBotDifficulty() == BotDifficulty.HARD && table.botDifficulty(0) == BotDifficulty.EASY
                && table.botDifficulty(1) == BotDifficulty.NORMAL && table.botDifficulty(2) == BotDifficulty.HARD
                && table.botDifficultyOverride(3).isEmpty(), "difficulty settings did not round-trip through table NBT");
        pass("default-and-seat-difficulties-persist-in-table-nbt");
    }

    private static void checkSanma() {
        table.selectPreset(RuleSetPreset.MAHJONG_SOUL_SANMA_3P);
        PortMenus.settings(menuPlayer, table);
        require(menuText(22).contains("三麻使用原版 BOT") && menuText(21).contains("仅四麻生效"),
                "sanma menu does not identify its original bot and inactive difficulty settings");
        menuClick(21);
        require(table.defaultBotDifficulty() == BotDifficulty.HARD, "sanma menu changed four-player difficulty");
        pass("actual-sanma-menu-explains-four-player-only-ai");
        require(table.tryStartMatch(123L) == MahjongTableBlockEntity.StartMatchResult.STARTED, "sanma did not start");
        require(table.driver().match().playerCount() == 3, "sanma started with four players");
        for (int seat = 0; seat < 3; seat++) require(table.driver().playerAt(seat) instanceof StupidActiveBot,
                "four-player AI was incorrectly installed in sanma");
        table.endGame();
        pass("sanma-retains-original-bot");
    }

    private static void assertControllers() {
        for (int seat = 0; seat < 4; seat++) {
            require(table.driver().playerAt(seat) instanceof KobalabBot, "four-player seat is not a Kobalab bot: " + seat);
            require(((KobalabBot) table.driver().playerAt(seat)).difficulty() == table.botDifficulty(seat),
                    "controller difficulty differs from saved seat configuration: " + seat);
        }
    }

    private static void checkLockedConfiguration() {
        require(!table.setDefaultBotDifficulty(BotDifficulty.EASY), "active table allowed a default difficulty change");
        require(!table.setSeatBotDifficulty(0, BotDifficulty.HARD), "active table allowed a seat difficulty change");
        table.cycleSeatBotDifficulty(0);
        require(table.defaultBotDifficulty() == BotDifficulty.HARD && table.botDifficulty(0) == BotDifficulty.EASY,
                "active configuration lock changed a difficulty");
        PortMenus.settings(menuPlayer, table);
        require(menuText(21).contains("对局中锁定") && menuText(48).contains("对局中锁定"),
                "active menu does not show the difficulty lock");
        menuClick(21);
        menuClick(48);
        require(table.defaultBotDifficulty() == BotDifficulty.HARD && table.botDifficultyOverride(3).isEmpty(),
                "actual menu actions changed locked difficulties");
        pass("actual-active-menu-actions-respect-difficulty-lock");
        pass("active-match-locks-default-and-seat-settings");
    }

    private static void checkMenuConfiguration() {
        PortMenus.settings(menuPlayer, table);
        require(menuText(21).contains("默认四麻 AI：高级"), "default AI menu text is incorrect");
        require(menuText(45).contains("东位四麻 AI：初级") && menuText(46).contains("南位四麻 AI：中级")
                && menuText(47).contains("西位四麻 AI：高级") && menuText(48).contains("跟随默认（高级）"),
                "seat menu text does not match configured difficulties");
        Map<String, String> labels = new LinkedHashMap<>();
        for (int slot : List.of(21, 45, 46, 47, 48)) labels.put(Integer.toString(slot), menuText(slot));
        observations.put("headlessMenuLabels", labels);
        menuClick(21);
        require(table.defaultBotDifficulty() == BotDifficulty.EASY && menuText(48).contains("跟随默认（初级）"),
                "default menu click did not update inherited difficulty");
        menuClick(21);
        require(table.defaultBotDifficulty() == BotDifficulty.NORMAL, "default menu did not cycle to normal");
        menuClick(21);
        require(table.defaultBotDifficulty() == BotDifficulty.HARD, "default menu did not cycle back to hard");
        menuClick(48);
        require(table.botDifficultyOverride(3).orElseThrow() == BotDifficulty.EASY, "seat menu did not select easy");
        menuClick(48);
        require(table.botDifficultyOverride(3).orElseThrow() == BotDifficulty.NORMAL, "seat menu did not select normal");
        menuClick(48);
        require(table.botDifficultyOverride(3).orElseThrow() == BotDifficulty.HARD, "seat menu did not select hard");
        menuClick(48);
        require(table.botDifficultyOverride(3).isEmpty() && menuText(48).contains("跟随默认"),
                "seat menu did not return to inherited difficulty");
        observations.put("headlessMenuActionsVerified", true);
        pass("actual-settings-menu-labels-default-cycle-and-seat-cycle");
    }

    private static String menuText(int slot) {
        require(menuPlayer.opened instanceof PortMenus.Actions, "settings did not create its actual actions menu");
        return ((PortMenus.Actions) menuPlayer.opened).getContainer().getItem(slot).getHoverName().getString();
    }

    private static void checkResetMenus(MinecraftServer server) throws Exception {
        var saved = table.exportRecordSnapshot(server.registryAccess());
        UUID host = menuPlayer.getUUID();
        require(table.claimSeat(0, host), "reset fixture could not claim host seat");
        table.setItem(135, new ItemStack(Items.DIAMOND, 7));
        table.setMintTilesFromNothing(false);
        table.toggleSeatEnabled(3);
        var idleSeats = table.seats();
        PortMenus.settings(menuPlayer, table);
        require(menuText(35).contains("重置牌桌"), "reset entry missing from actual settings menu");
        menuClick(35);
        var cancelled = menuPlayer.opened;
        require(menuText(12).contains("比分与供托") && menuText(13).contains("库存")
                && menuText(14).contains("释放所有座位"), "reset confirmation does not describe its effects");
        require(table.seats().equals(idleSeats), "opening confirmation reset the table");
        menuClick(33);
        cancelled.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.seats().equals(idleSeats), "cancelled confirmation reset the table");
        menuClick(35);
        var closed = menuPlayer.opened;
        menuPlayer.containerMenu = menuPlayer.inventoryMenu;
        closed.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.seats().equals(idleSeats), "closed confirmation reset the table");

        PortMenus.settings(menuPlayer, table);
        menuClick(35);
        menuClick(29);
        require(!table.hasActiveMatch() && table.humanSeatCount() == 0 && !table.seats().get(3).enabled(),
                "idle reset did not release occupants while preserving seat configuration");
        require(table.getItem(135).is(Items.DIAMOND) && table.getItem(135).getCount() == 7
                && !table.mintTilesFromNothing() && table.defaultBotDifficulty() == BotDifficulty.HARD
                && table.botDifficulty(0) == BotDifficulty.EASY, "idle reset changed inventory or configuration");
        pass("reset-confirmation-cancel-and-close-are-inert-idle-reset-preserves-inventory-and-settings");

        table.toggleSeatEnabled(3);
        require(table.claimSeat(0, host), "reset host could not rejoin");
        require(table.tryStartMatch(456L) == MahjongTableBlockEntity.StartMatchResult.STARTED,
                "reset active fixture did not start");
        UUID returning = UUID.randomUUID();
        require(table.claimSeat(1, returning), "reset fixture could not claim returning seat");
        table.releaseSeat(1);
        var active = table.driver();
        var bots = new ArrayList<KobalabBot>();
        for (int seat = 0; seat < 4; seat++) if (active.playerAt(seat) instanceof KobalabBot bot) bots.add(bot);
        table.handDisplayOrder(0);
        table.recordPrivateClick(menuPlayer);

        var outsider = new MenuPlayer(server, UUID.randomUUID());
        PortMenus.settings(outsider, table);
        var outsiderSettings = outsider.opened;
        outsiderSettings.clicked(35, 0, ContainerInput.PICKUP, outsider);
        require(outsider.opened == outsiderSettings && table.driver() == active,
                "non-host opened or executed a reset");

        PortMenus.settings(menuPlayer, table);
        menuClick(35);
        var denied = menuPlayer.opened;
        menuPlayer.setPos(POS.getX() + 100, POS.getY(), POS.getZ());
        denied.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.driver() == active, "distant host reset the table");
        menuPlayer.setPos(POS.getX() + 0.5, POS.getY() + 0.5, POS.getZ() + 0.5);
        require(table.claimSeat(2, outsider.getUUID()), "authorization fixture could not claim another seat");
        table.releaseSeat(0);
        denied.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.driver() == active, "confirmation bypassed changed host authorization");
        table.releaseSeat(2);
        require(table.claimSeat(0, host), "original host could not resume fixture");
        pass("reset-revalidates-current-host-and-distance");

        PortMenus.settings(menuPlayer, table);
        menuClick(35);
        var removed = menuPlayer.opened;
        table.setRemoved();
        removed.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.driver() == active, "removed table accepted a reset");
        table.clearRemoved();
        removed.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.driver() == active, "old confirmation survived table removal/reactivation");

        PortMenus.settings(menuPlayer, table);
        menuClick(35);
        var stale = menuPlayer.opened;
        table.endGame();
        require(table.tryStartMatch(789L) == MahjongTableBlockEntity.StartMatchResult.STARTED,
                "replacement match did not start");
        var replacement = table.driver();
        stale.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.driver() == replacement, "old confirmation reset a replacement match");
        require(bots.stream().allMatch(KobalabBot::isClosed), "ending original fixture left an AI open");

        require(table.claimSeat(1, returning), "replacement fixture could not reserve returning seat");
        table.releaseSeat(1);

        var resetSnapshot = table.exportRecordSnapshot(server.registryAccess());
        resetSnapshot.putString("ResultAnimStage", "AWAITING_ADVANCE");
        resetSnapshot.putInt("ResultAnimYakuIdx", 9);
        load(server, resetSnapshot);
        var resetDriver = table.driver();
        var resetBots = new ArrayList<KobalabBot>();
        for (int seat = 0; seat < 4; seat++) if (resetDriver.playerAt(seat) instanceof KobalabBot bot) resetBots.add(bot);
        table.handDisplayOrder(0);
        table.recordPrivateClick(menuPlayer);
        PortMenus.settings(menuPlayer, table);
        menuClick(35);
        var confirmed = menuPlayer.opened;
        menuClick(29);
        require(table.driver() == null && table.humanSeatCount() == 0 && table.enabledSeatCount() == 4,
                "confirmed active reset did not return to an empty lobby");
        require(resetBots.stream().allMatch(KobalabBot::isClosed), "confirmed reset left AI controllers active");
        require(table.returningSeat(host).isEmpty() && table.returningSeat(returning).isEmpty()
                && !table.recentPrivateClick(menuPlayer), "reset retained reservations or private click state");
        for (int seat = 0; seat < table.seats().size(); seat++) {
            require(table.handDisplayOrder(seat).isEmpty() && table.handDisplaySlots(seat).isEmpty()
                    && table.handDisplayGapStarts(seat).isEmpty() && !table.hasSeparatedDrawnTile(seat),
                    "reset retained hand layout or exposed unavailable hand state at seat " + seat);
        }
        require(table.resultAnimStage() == MahjongTableBlockEntity.ResultAnimStage.NONE
                && table.resultAnimYakuIdx() == 0, "reset retained hand layout or result animation");
        var board = PublicTable.capture(table);
        require(board.riichiSticks() == 0 && board.seats().stream().allMatch(seat -> seat.points() == 0
                && seat.handCount() == 0 && seat.river().isEmpty() && seat.melds().isEmpty()),
                "reset retained public score, supply or tile displays");
        var persisted = table.exportRecordSnapshot(server.registryAccess());
        require(!persisted.contains("Driver") && !persisted.contains("HumanPlayers")
                && persisted.getListOrEmpty("ReturningSeats").isEmpty(), "reset saved obsolete match data");
        require(table.getItem(135).is(Items.DIAMOND) && table.getItem(135).getCount() == 7
                && !table.mintTilesFromNothing() && table.preset() == RuleSetPreset.MAHJONG_SOUL_4P
                && table.defaultBotDifficulty() == BotDifficulty.HARD && table.botDifficulty(0) == BotDifficulty.EASY,
                "active reset changed preserved inventory or settings");
        long revision = table.matchRevision();
        confirmed.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.matchRevision() == revision, "repeated confirmation executed reset twice");
        require(table.claimSeat(0, outsider.getUUID()) && table.tryStartMatch(999L)
                == MahjongTableBlockEntity.StartMatchResult.STARTED, "reset table could not be reused by another host");
        confirmed.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.hasActiveMatch(), "old confirmed menu reset the new host's game");
        table.endGame();
        for (int seat = 0; seat < 4; seat++) table.releaseSeat(seat);

        PortMenus.settings(menuPlayer, table);
        menuClick(35);
        var idleStale = menuPlayer.opened;
        table.tryStartMatch(111L);
        table.endGame();
        long currentRevision = table.matchRevision();
        idleStale.clicked(29, 0, ContainerInput.PICKUP, menuPlayer);
        require(table.matchRevision() == currentRevision, "idle confirmation revived after an intervening match");
        pass("confirmed-reset-clears-runtime-closes-ai-releases-seats-and-rejects-removed-stale-or-repeated-menus");
        load(server, saved);
        PortMenus.settings(menuPlayer, table);
    }

    private static void menuClick(int slot) {
        require(menuPlayer.opened instanceof PortMenus.Actions, "settings menu was not captured");
        menuPlayer.opened.clicked(slot, 0, ContainerInput.PICKUP, menuPlayer);
    }

    /** Captures the real vanilla menu without a network connection or a registered world player. */
    private static final class MenuPlayer extends ServerPlayer {
        AbstractContainerMenu opened;
        MenuPlayer(MinecraftServer server) {
            this(server, UUID.fromString("bb4db14b-9c6e-4e90-aa6d-36d82f425c21"));
        }
        MenuPlayer(MinecraftServer server, UUID uuid) {
            super(server, server.overworld(), new GameProfile(
                    uuid, "AiMenuTest"),
                    ClientInformation.createDefault());
            setPos(POS.getX() + 0.5, POS.getY() + 0.5, POS.getZ() + 0.5);
        }
        @Override public OptionalInt openMenu(MenuProvider provider) {
            opened = provider.createMenu(1, getInventory(), this);
            if (opened != null) containerMenu = opened;
            return opened == null ? OptionalInt.empty() : OptionalInt.of(1);
        }
        @Override public void sendSystemMessage(Component message, boolean overlay) {}
    }

    private static void checkActiveSaveAndRestore(MinecraftServer server) throws Exception {
        var old = table.driver();
        TheMahjongMatch match = old.match();
        MatchPhase phase = old.currentPhase();
        var oldBot = (KobalabBot) old.playerAt(1);
        load(server, table.exportRecordSnapshot(server.registryAccess()));
        require(oldBot.isClosed(), "loading a saved table left an old controller active");
        require(table.driver() != old && table.driver().match().equals(match)
                && table.driver().currentPhase().equals(phase), "restoring an active save changed hand, scores or phase");
        assertControllers();
        pass("active-save-restores-controllers-hand-scores-and-phase");
    }

    private static void tick(MinecraftServer server) throws Exception {
        require((System.nanoTime() - startNanos) / 1_000_000 < 180_000, "AI integration exceeded three minutes");
        var driver = table.driver();
        require(driver != null, "table lost its driver while testing");
        if (stage == 0) {
            if (!(driver.currentPhase() instanceof MatchPhase.AwaitingDiscard discard) || discard.seat() != 0) return;
            humanRound = driver.currentRound();
            var humanPhase = driver.currentPhase();
            var hand = humanRound.players().get(0).currentHand();
            var scores = humanRound.players().stream().map(player -> player.points()).toList();
            table.releaseSeat(0);
            require(table.driver().playerAt(0) instanceof KobalabBot, "leaving did not install four-player AI");
            staleBot = (KobalabBot) table.driver().playerAt(0);
            require(staleBot.difficulty() == BotDifficulty.EASY, "leaving did not use the seat difficulty");
            require(table.returningSeat(OWNER).orElse(-1) == 0, "leaving did not reserve the original seat");
            require(!table.claimSeat(0, UUID.randomUUID()), "another player could steal a returning seat");
            staleRequest = new DecisionRequest(0, driver.currentPhase(), driver.legalActions(0),
                    driver.currentRound(), driver.rules());
            staleBot.chooseAction(staleRequest, 0.05);
            require(table.claimSeat(0, OWNER), "original owner could not retake the AI seat");
            require(staleBot.isClosed(), "returning human did not close the old bot");
            require(driver.currentRound() == humanRound && driver.currentPhase().equals(humanPhase)
                    && driver.currentRound().players().get(0).currentHand().equals(hand)
                    && driver.currentRound().players().stream().map(player -> player.points()).toList().equals(scores),
                    "leave-and-rejoin changed hand, scores or phase");
            require(driver.playerAt(0) instanceof MahjongTableHumanPlayer, "returning human did not regain control");
            pass("leave-and-rejoin-preserves-seat-hand-scores-and-turn");

            var beforeSuspend = driver.playerAt(1);
            table.setRemoved();
            require(((KobalabBot) beforeSuspend).isClosed(), "table removal did not close AI");
            table.clearRemoved();
            require(driver.playerAt(1) != beforeSuspend && driver.playerAt(1) instanceof KobalabBot
                    && !((KobalabBot) driver.playerAt(1)).isClosed(), "reactivated table did not restore its AI controller");
            require(table.driver() == driver && driver.currentRound() == humanRound, "table reactivation reset the game");
            pass("removed-and-reactivated-table-restores-ai-without-reset");
            dueTick = server.overworld().getGameTime() + 30;
            stage = 1;
            return;
        }
        if (stage == 1) {
            require(driver.currentRound() == humanRound, "an old bot result changed the round after human return");
            if (server.overworld().getGameTime() < dueTick) return;
            require(staleBot.chooseAction(staleRequest, 10).isEmpty(), "closed bot returned a delayed action");
            pass("pending-ai-cannot-act-after-human-rejoins");
            table.releaseSeat(0);
            assertControllers();
            countedRound = driver.currentRound();
            countedDiscards = riverSize(countedRound);
            stage = 2;
            return;
        }
        TheMahjongRound current = driver.currentRound();
        if (current.roundWind() != countedRound.roundWind() || current.handNumber() != countedRound.handNumber()
                || current.honba() != countedRound.honba()) {
            countedDiscards = 0;
        }
        int now = riverSize(current);
        acceptedDiscards += Math.max(0, now - countedDiscards);
        countedDiscards = now;
        countedRound = current;
        if (acceptedDiscards >= 12) {
            Map<String, Long> metrics = AiRuntime.metrics();
            long successful = metrics.getOrDefault("engineSuccess", 0L) - metricsBefore.getOrDefault("engineSuccess", 0L);
            require(successful > 0, "the real Node AI engine never completed a decision");
            for (String level : List.of("engineEasy", "engineNormal", "engineHard")) {
                require(metrics.getOrDefault(level, 0L) > metricsBefore.getOrDefault(level, 0L),
                        "a difficulty never completed a real Node engine decision: " + level);
            }
            observations.put("acceptedDiscards", acceptedDiscards);
            observations.put("engineDecisionsDuringTest", successful);
            observations.put("runtime", AiRuntime.status());
            observations.put("metricsBefore", metricsBefore);
            observations.put("metricsAfter", metrics);
            pass("all-three-levels-use-real-node-engine-during-twelve-accepted-discards");
            finish(server, true, "passed");
            return;
        }
        if (table.resultAnimStage() == MahjongTableBlockEntity.ResultAnimStage.AWAITING_ADVANCE) {
            table.advanceRoundAfterResult();
        }
        require(!(driver.currentPhase() instanceof MatchPhase.MatchEnded), "match ended before twelve discards");
    }

    private static int riverSize(TheMahjongRound round) {
        return round.players().stream().mapToInt(player -> player.discards().size()).sum();
    }

    private static void load(MinecraftServer server, CompoundTag snapshot) throws Exception {
        Method load = MahjongTableBlockEntity.class.getDeclaredMethod("loadAdditional", ValueInput.class);
        load.setAccessible(true);
        load.invoke(table, TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), snapshot));
    }

    private static void pass(String check) {
        checks.add(check);
        System.out.println("RIICHI_AI_CHECK_PASS " + check);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void finish(MinecraftServer server, boolean passed, String detail) {
        stage = -1;
        observations.put("metricsAfter", AiRuntime.metrics());
        observations.put("acceptedDiscards", acceptedDiscards);
        writeReport(passed, detail);
        if (table != null && table.hasActiveMatch()) table.endGame();
        server.overworld().setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, false);
        System.out.println((passed ? "RIICHI_AI_INTEGRATION_PASS " : "RIICHI_AI_INTEGRATION_FAIL ") + detail);
    }

    private static void writeReport(boolean passed, String detail) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("passed", passed);
        report.put("detail", detail);
        report.put("version", FabricLoader.getInstance().getModContainer("riichi_mahjong").orElseThrow()
                .getMetadata().getVersion().getFriendlyString());
        report.put("elapsedMillis", (System.nanoTime() - startNanos) / 1_000_000);
        report.put("checks", List.copyOf(checks));
        report.put("observations", new LinkedHashMap<>(observations));
        report.put("clientMenuVisualVerified", false);
        try { Files.writeString(RESULTS, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (Exception failure) { throw new RuntimeException("Could not write AI integration report", failure); }
    }
}
