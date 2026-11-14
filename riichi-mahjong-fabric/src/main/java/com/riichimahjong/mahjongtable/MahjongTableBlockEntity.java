// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable;

import com.riichimahjong.mahjongcore.MahjongRoundResolvedEvent;
import com.riichimahjong.mahjongcore.MahjongWinEffects;
import com.riichimahjong.cuterenderer.CuteClickHandler;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.riichimahjong.fabric.HandDisplayState;
import com.riichimahjong.fabric.ResultHandReveals;
import com.riichimahjong.fabric.TableOwnership;
import com.riichimahjong.fabric.WinningHandDisplay;
import com.riichimahjong.replay.ReplayRecord;
import com.riichimahjong.replay.ReplayArchiveQueue;
import com.riichimahjong.replay.ReplayRecorder;
import com.riichimahjong.replay.ReplaySession;
import com.riichimahjong.replay.ReplayStore;
import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.KobalabBot;
import com.riichimahjong.ai.RandomThinkingBot;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongMeld;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class MahjongTableBlockEntity extends BlockEntity implements Container, MenuProvider, CuteClickHandler {
    public enum State {
        IDLE,
        GAME
    }

    public record SeatInfo(boolean enabled, Optional<UUID> occupant) {
        public static SeatInfo open() {
            return new SeatInfo(true, Optional.empty());
        }

        public static SeatInfo claimed(UUID uuid) {
            return new SeatInfo(true, Optional.of(uuid));
        }

        public static SeatInfo closed() {
            return new SeatInfo(false, Optional.empty());
        }
    }

    public enum StartMatchResult {
        STARTED,
        NOT_IDLE,
        SEATS_MISMATCH_PRESET
    }

    public static final int INVENTORY_SIZE = 136;

    public static final int DEFAULT_SEAT_COUNT = 4;

    public static final double AUTO_SEAT_RADIUS = 8.0;

    private static final double SECONDS_PER_TICK = 1.0 / 20.0;
    private static final String NBT_INVENTORY = "MtItemsInt";
    private static final String NBT_INVENTORY_SLOT = "Slot";
    private static final String NBT_INVENTORY_ITEM = "Item";

    private static final String NBT_LAST_POWERED = "LastPowered";
    private static final String NBT_DRIVER = "Driver";
    private static final String NBT_SEATS = "Seats";
    private static final String NBT_SEAT_ENABLED = "enabled";
    private static final String NBT_SEAT_OCCUPANT = "uuid";

    private static final String NBT_SEAT_LEGACY_TYPE = "type";
    private static final String NBT_PRESET = "Preset";
    private static final String NBT_RANDOM_SEED = "RandomSeed";
    private static final String NBT_BOT_DIFFICULTY = "BotDifficulty";
    private static final String NBT_MINT_TILES = "MintTilesFromNothing";
    private static final String NBT_DELIVER_TO_MAIN_HAND = "DeliverToMainHand";
    private static final String NBT_HUMAN_PLAYERS = "HumanPlayers";
    private static final String NBT_RESULT_ANIM_STAGE = "ResultAnimStage";
    private static final String NBT_RESULT_ANIM_YAKU_IDX = "ResultAnimYakuIdx";

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

    private boolean lastPowered;
    private List<SeatInfo> seats = defaultSeats();
    private final ReturningSeats returningSeats = new ReturningSeats();
    private final TableOwnership ownership = new TableOwnership();
    private final Map<UUID,Long> privateClicks = new HashMap<>();
    private final HandDisplayState handDisplay = new HandDisplayState();
    private final ResultHandReveals resultHandReveals = new ResultHandReveals();
    @Nullable private WinningHandDisplay winningHandDisplay;
    @Nullable private TheMahjongDriver handDisplayDriver;
    private RuleSetPreset preset = RuleSetPreset.MAHJONG_SOUL_4P;
    private BotDifficulty defaultBotDifficulty = BotDifficulty.NORMAL;
    private final Map<Integer, BotDifficulty> seatBotDifficulties = new HashMap<>();
    private boolean botsSuspended;
    private long randomSeed;

    private boolean mintTilesFromNothing = true;

    private boolean deliverToMainHand = true;

    @Nullable
    private TheMahjongDriver driver;
    @Nullable private ReplayRecorder replayRecorder;
    @Nullable private ReplaySession replay;
    private long appliedReplayRevision=-1;
    private final ReplayArchiveQueue replayArchives=new ReplayArchiveQueue();
    private long nextArchiveAttempt;

    // A live confirmation must not survive a reset, reload, seat/host change or new hand.
    private transient long matchRevision;

    public enum ResultAnimStage { NONE, SHOW_HEADER, SHOW_YAKU_LINES, SHOW_FINAL, AWAITING_ADVANCE }

    private ResultAnimStage resultAnimStage = ResultAnimStage.NONE;

    private int resultAnimYakuIdx = 0;

    private transient long resultAnimNextTick;

    private transient long lastCuteClickGameTime = -1L;

    @Nullable public transient Object clientRenderState;

    public MahjongTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(com.riichimahjong.registry.ModBlockEntities.MAHJONG_TABLE_BLOCK_ENTITY.get(), pos, blockState);
    }

    private static List<SeatInfo> defaultSeats() {
        List<SeatInfo> out = new ArrayList<>(DEFAULT_SEAT_COUNT);
        for (int i = 0; i < DEFAULT_SEAT_COUNT; i++) {
            out.add(SeatInfo.open());
        }
        return out;
    }

    public State state() { return driver != null ? State.GAME : State.IDLE; }

    @Nullable
    public TheMahjongDriver driver() { return driver; }

    public long matchRevision() { return matchRevision; }

    @Nullable public ReplaySession replay() { return replay; }

    /** Loading history never overwrites the destination table's inventory, preset or seats. */
    public boolean startReplay(ReplayRecord record,ServerPlayer requester,boolean analysis) {
        if(!validMenuActor(requester)||!isHost(requester.getUUID())||driver!=null||!record.completed())return false;
        if(seats.stream().anyMatch(s->s.occupant().filter(id->!id.equals(requester.getUUID())).isPresent()))return false;
        try {
            var session=new ReplaySession(record,requester.getUUID(),analysis);
            // Validate the first frame before changing table state.
            var restored=session.frame().restore(level.registryAccess());
            replay=session;driver=restored;replayRecorder=null;appliedReplayRevision=-1;
            applyReplayFrame();matchRevision++;markChangedAndSynced();return true;
        } catch(RuntimeException error) {
            if(replay!=null)clearMatchState();
            System.getLogger("riichi_mahjong/replay").log(System.Logger.Level.WARNING,"Cannot load replay "+record.id(),error);
            requester.sendSystemMessage(Component.literal("牌谱数据无法读取。"),true);return false;
        }
    }

    public void stopReplay() {
        if(replay==null)return;
        clearMatchState();markChangedAndSynced();
    }

    public boolean controlReplay(ServerPlayer player,ReplaySession.Control action) {
        if(replay==null||!validMenuActor(player)||!isHost(player.getUUID()))return false;
        if(action==ReplaySession.Control.EXIT){stopReplay();return true;}
        replay.control(action);applyReplayFrame();markChangedAndSynced();return true;
    }

    private void applyReplayFrame() {
        if(replay==null||appliedReplayRevision==replay.revision())return;
        var frame=replay.frame();
        driver=frame.restore(level.registryAccess());
        var in=TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),frame.driver());
        winningHandDisplay=in.child("ReplayWinningHand").map(WinningHandDisplay::read).orElse(null);
        handDisplay.clear();handDisplayDriver=null;resultHandReveals.clear();
        resultAnimStage=driver.currentPhase() instanceof MatchPhase.RoundEnded?ResultAnimStage.SHOW_FINAL:ResultAnimStage.NONE;
        resultAnimYakuIdx=0;appliedReplayRevision=replay.revision();
    }

    private List<ReplayRecord.Participant> recordingParticipants(int count) {
        var people=new ArrayList<ReplayRecord.Participant>();
        for(int seat=0;seat<count;seat++) {
            UUID uuid=seats.get(seat).occupant().orElse(null);
            var player=uuid==null?null:level.getServer().getPlayerList().getPlayer(uuid);
            people.add(new ReplayRecord.Participant(seat,uuid,player==null?(uuid==null?"BOT":"离线玩家"):player.getName().getString(),0));
        }
        return people;
    }

    private void beginRecording(RuleSetPreset rules) {
        replayRecorder=new ReplayRecorder(rules.name(),recordingParticipants(driver.match().playerCount()));
        attachRecordingObserver();
    }

    private void attachRecordingObserver() {
        if(driver==null||replayRecorder==null||replay!=null)return;
        driver.setStateObserver(observed->{
            if(observed!=driver||replayRecorder==null||replay!=null)return;
            try {
                replayRecorder.capture(observed);
                if(observed.currentPhase() instanceof MatchPhase.MatchEnded&&!replayRecorder.finished()) {
                    replayArchives.offer(replayRecorder.finish(observed));
                    flushReplayArchives();
                }
                setChanged();
            } catch(RuntimeException error) {
                System.getLogger("riichi_mahjong/replay").log(System.Logger.Level.ERROR,"Replay recording failed; match remains playable",error);
                replayRecorder=null;
            }
        });
    }

    private void flushReplayArchives() {
        if(!(level instanceof ServerLevel sl)||replayArchives.isEmpty())return;
        replayArchives.pump(ReplayStore::archive,sl.getServer()::execute,this::setChanged);
        nextArchiveAttempt=sl.getGameTime()+100;
    }

    private boolean updateHandDisplay() {
        if (handDisplayDriver != driver) { handDisplay.clear(); handDisplayDriver = driver; }
        if (driver == null || level == null) return false;
        var round = driver.match().currentRound().orElse(null);
        if (round == null) { handDisplay.clear(); return false; }
        handDisplay.update(round, level.getGameTime());
        return true;
    }

    public List<TheMahjongTile> handDisplayOrder(int seat) {
        if (!updateHandDisplay()) return List.of();
        return winningHandLayout(seat).map(WinningHandDisplay.Layout::order).orElseGet(() -> handDisplay.hand(seat));
    }

    public List<Integer> handDisplaySlots(int seat) {
        if (!updateHandDisplay()) return List.of();
        return winningHandLayout(seat).map(WinningHandDisplay.Layout::slots).orElseGet(() -> handDisplay.slots(seat));
    }

    public List<Integer> handDisplayGapStarts(int seat) {
        if (!updateHandDisplay()) return List.of();
        return winningHandLayout(seat).map(WinningHandDisplay.Layout::gapStarts).orElseGet(() -> handDisplay.gapStarts(seat));
    }

    public boolean hasSeparatedDrawnTile(int seat) {
        if (!updateHandDisplay()) return false;
        return winningHandLayout(seat).map(layout -> !layout.gapStarts().isEmpty()).orElseGet(() -> handDisplay.separated(seat));
    }

    private Optional<WinningHandDisplay.Layout> winningHandLayout(int seat) {
        if(winningHandDisplay==null||driver==null)return Optional.empty();
        return driver.match().currentRound().flatMap(round -> winningHandDisplay.layout(round,driver.currentPhase(),seat));
    }

    public List<SeatInfo> seats() { return List.copyOf(seats); }

    public boolean hasActiveMatch() { return driver != null; }

    public OptionalInt seatOfPlayer(UUID uuid) {
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).occupant().filter(uuid::equals).isPresent()) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public int humanSeatCount() {
        int count = 0;
        for (SeatInfo info : seats) {
            if (info.occupant().isPresent()) count++;
        }
        return count;
    }

    public int enabledSeatCount() {
        int count = 0;
        for (SeatInfo info : seats) {
            if (info.enabled()) count++;
        }
        return count;
    }

    public RuleSetPreset preset() { return preset; }

    public BotDifficulty defaultBotDifficulty() { return defaultBotDifficulty; }

    public Optional<BotDifficulty> botDifficultyOverride(int seat) {
        return Optional.ofNullable(seatBotDifficulties.get(seat));
    }

    public BotDifficulty botDifficulty(int seat) {
        return seatBotDifficulties.getOrDefault(seat, defaultBotDifficulty);
    }

    public boolean setDefaultBotDifficulty(BotDifficulty difficulty) {
        if (driver != null || difficulty == null) return false;
        defaultBotDifficulty = difficulty;
        markChangedAndSynced();
        return true;
    }

    public boolean setSeatBotDifficulty(int seat, @Nullable BotDifficulty difficulty) {
        if (driver != null || seat < 0 || seat >= DEFAULT_SEAT_COUNT) return false;
        if (difficulty == null) seatBotDifficulties.remove(seat);
        else seatBotDifficulties.put(seat, difficulty);
        markChangedAndSynced();
        return true;
    }

    public void cycleSeatBotDifficulty(int seat) {
        BotDifficulty current = seatBotDifficulties.get(seat);
        setSeatBotDifficulty(seat, current == null ? BotDifficulty.EASY
                : current == BotDifficulty.HARD ? null : current.next());
    }

    public RuleSetPreset effectivePreset() {
        if (preset == RuleSetPreset.MAHJONG_SOUL_4P
                && seats.size() >= 4 && !seats.get(3).enabled()) {
            return RuleSetPreset.MAHJONG_SOUL_SANMA_3P;
        }
        return preset;
    }

    public Optional<UUID> owner() { return ownership.owner(); }

    public boolean isHost(UUID uuid) { return ownership.isHost(uuid); }

    private boolean playerOnline(UUID uuid) {
        return level instanceof ServerLevel sl && sl.getServer().getPlayerList().getPlayer(uuid) != null;
    }

    public boolean hasOfflineHost() {
        return level instanceof ServerLevel && ownership.hasOfflineOwner(this::playerOnline);
    }

    private boolean validMenuActor(ServerPlayer player) {
        return level instanceof ServerLevel sl && player.level() == level && !player.isSpectator()
                && !isRemoved() && sl.hasChunkAt(getBlockPos()) && sl.getBlockEntity(getBlockPos()) == this
                && sl.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.distanceToSqr(getBlockPos().getX() + .5, getBlockPos().getY() + .5,
                        getBlockPos().getZ() + .5) <= 64;
    }

    /** The first menu opener claims an unowned table; existing ownership never changes here. */
    public boolean claimHostIfUnowned(ServerPlayer player) {
        if (!validMenuActor(player)) return false;
        if (ownership.claimIfAbsent(player.getUUID())) {
            matchRevision++;
            markChangedAndSynced();
        }
        return ownership.isHost(player.getUUID());
    }

    public boolean takeOverHost(ServerPlayer player, UUID expectedOwner, long expectedRevision) {
        if (!validMenuActor(player) || matchRevision != expectedRevision
                || !ownership.takeOver(player.getUUID(), expectedOwner, this::playerOnline)) return false;
        matchRevision++;
        markChangedAndSynced();
        return true;
    }

    public record OfflineSeatCandidate(int seat, UUID occupant, boolean reserved) {}

    public List<OfflineSeatCandidate> offlineSeatCandidates() {
        if (!(level instanceof ServerLevel)) return List.of();
        return OfflineSeats.candidates(seats, returningSeats,
                driver == null ? -1 : driver.match().playerCount(), this::playerOnline);
    }

    /** Permanently turn this offline assignment into an ordinary, unreserved AI seat. */
    public boolean replaceOfflineWithAI(ServerPlayer player, int seat, UUID expectedOccupant,
            @Nullable TheMahjongDriver expectedDriver, long expectedRevision) {
        if (replay!=null || !validMenuActor(player) || !isHost(player.getUUID())
                || !OfflineSeats.matches(driver, matchRevision, expectedDriver, expectedRevision,
                        seat, expectedOccupant, offlineSeatCandidates())) return false;
        // Leaving has already installed a BOT. Keep that controller and any accepted claim decision.
        if (driver != null && driver.playerAt(seat) instanceof MahjongTableHumanPlayer) {
            replaceController(seat, buildBot(seat, driver.match().playerCount(), activeRedFives()));
        }
        returningSeats.forget(expectedOccupant, seat);
        seats.set(seat, new SeatInfo(seats.get(seat).enabled(), Optional.empty()));
        privateClicks.remove(expectedOccupant);
        matchRevision++;
        markChangedAndSynced();
        return true;
    }

    public OptionalInt returningSeat(UUID uuid) { return returningSeats.seat(uuid); }

    public boolean canClaimSeat(int seat, UUID uuid) {
        if (replay!=null) return false;
        if (seat < 0 || seat >= seats.size()) return false;
        if (!seats.get(seat).enabled()) return false;
        if (seats.get(seat).occupant().filter(id->!id.equals(uuid)).isPresent()) return false;
        if (driver != null) {
            int current=seatOfPlayer(uuid).orElse(-1);
            if (seat>=driver.match().playerCount() || current>=0 && current!=seat
                    || !returningSeats.allows(uuid,seat)) return false;
        }
        return true;
    }

    public boolean claimSeat(int seat, UUID uuid) {
        if (!canClaimSeat(seat,uuid)) return false;
        if (seats.get(seat).occupant().filter(uuid::equals).isPresent()) return true;
        if (driver != null) {
            returningSeats.remember(uuid,seat);
            replaceController(seat, buildPlayer(SeatInfo.claimed(uuid), seat, driver.match().playerCount(), activeRedFives()));
        }
        seatOfPlayer(uuid).ifPresent(prev -> {
            if (prev != seat) seats.set(prev, SeatInfo.open());
        });
        seats.set(seat, SeatInfo.claimed(uuid));
        if(replayRecorder!=null&&driver!=null&&level!=null) {
            var person=level.getServer().getPlayerList().getPlayer(uuid);
            replayRecorder.remember(seat,uuid,person==null?"离线玩家":person.getName().getString());
        }
        ownership.claimIfAbsent(uuid);
        matchRevision++;
        markChangedAndSynced();
        return true;
    }

    public void releaseSeat(int seat) {
        if (replay!=null) return;
        if (seat < 0 || seat >= seats.size()) return;
        SeatInfo info = seats.get(seat);
        if (info.occupant().isPresent()) {
            if (driver != null && seat < driver.match().playerCount()) {
                returningSeats.remember(info.occupant().orElseThrow(),seat);
                replaceController(seat, buildBot(seat, driver.match().playerCount(), activeRedFives()));
            }
            seats.set(seat, SeatInfo.open());
            matchRevision++;
            markChangedAndSynced();
        }
    }

    public void selectPreset(RuleSetPreset newPreset) {
        if (driver != null) return;
        this.preset = newPreset;
        matchRevision++;
        padSeatsAtLeast(DEFAULT_SEAT_COUNT);
        int wantOpen = newPreset.playerCount();
        for (int i = 0; i < seats.size(); i++) {
            boolean shouldBeOpen = i < wantOpen;
            if (seats.get(i).enabled() != shouldBeOpen) {
                seats.set(i, shouldBeOpen ? SeatInfo.open() : SeatInfo.closed());
            }
        }
        markChangedAndSynced();
    }

    public void toggleSeatEnabled(int seat) {
        if (driver != null) return;
        if (seat < 0 || seat >= seats.size()) return;
        SeatInfo current = seats.get(seat);
        seats.set(seat, current.enabled() ? SeatInfo.closed() : SeatInfo.open());
        matchRevision++;
        markChangedAndSynced();
    }

    public void onRedstone(boolean nowPowered) {
        if (nowPowered != lastPowered) {
            lastPowered = nowPowered;
            setChanged();
            if (nowPowered) {
                onRisingEdge();
            }
        }
    }

    private void onRisingEdge() {
        if (replay!=null) return;
        if (driver == null) {
            tryStartMatch(new Random().nextLong());
        } else {
            endGame();
        }
    }

    public boolean isDriverTerminal() {
        return driver == null || driver.currentPhase() instanceof MatchPhase.MatchEnded;
    }

    public StartMatchResult tryStartMatch(long seed) {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("tryStartMatch must be called on the server");
        }
        if (driver != null) return StartMatchResult.NOT_IDLE;

        RuleSetPreset effective = effectivePreset();
        if (!seatsMatchCanonicalLayout(effective.playerCount())) {
            return StartMatchResult.SEATS_MISMATCH_PRESET;
        }

        TheMahjongMatch match = effective.newMatch().validate();
        List<SeatInfo> activeSeats = new ArrayList<>(match.playerCount());
        for (int i = 0; i < match.playerCount(); i++) activeSeats.add(seats.get(i));

        resetReturningSeats();
        this.randomSeed = seed;
        this.driver = new TheMahjongDriver(match, buildPlayersFromSeats(activeSeats, match.tileSet().tiles().stream().anyMatch(TheMahjongTile::redDora)), new java.security.SecureRandom());
        this.driver.setAnimationsEnabled(true);
        beginRecording(effective);
        this.driver.startMatch();
        matchRevision++;
        markChangedAndSynced();
        return StartMatchResult.STARTED;
    }

    private boolean seatsMatchCanonicalLayout(int wantOpen) {
        if (seats.size() < wantOpen) return false;
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).enabled() != (i < wantOpen)) return false;
        }
        return true;
    }

    public StartMatchResult tryStartMatchWithAutoSeating(long seed) {
        if (driver != null) return StartMatchResult.NOT_IDLE;
        if (humanSeatCount() == 0) {
            autoSeatNearbyPlayers();
        }
        return tryStartMatch(seed);
    }

    public StartMatchResult tryApplyPredefined(
            long seed, RuleSetPreset preset, TheMahjongMatch fixedMatch, java.util.UUID hostUuid) {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("tryApplyPredefined must be called on the server");
        }
        if (driver != null) return StartMatchResult.NOT_IDLE;
        if (fixedMatch.playerCount() != preset.playerCount()) {
            throw new IllegalArgumentException(
                    "fixedMatch playerCount " + fixedMatch.playerCount()
                            + " != preset.playerCount " + preset.playerCount());
        }

        this.preset = preset;

        padSeatsAtLeast(DEFAULT_SEAT_COUNT);
        for (int i = 0; i < seats.size(); i++) {
            if (i == 0) {
                seats.set(i, SeatInfo.claimed(hostUuid));
            } else if (i < preset.playerCount()) {
                seats.set(i, SeatInfo.open());
            } else {
                seats.set(i, SeatInfo.closed());
            }
        }
        List<SeatInfo> activeSeats = new ArrayList<>(preset.playerCount());
        for (int i = 0; i < preset.playerCount(); i++) activeSeats.add(seats.get(i));

        TheMahjongDriver.Snapshot snap = new TheMahjongDriver.Snapshot(
                new MatchPhase.AwaitingDiscard(0),
                 false,
                java.util.OptionalInt.empty());
        resetReturningSeats();
        this.randomSeed = seed;
        this.driver = new TheMahjongDriver(
                fixedMatch, buildPlayersFromSeats(activeSeats, fixedMatch.tileSet().tiles().stream().anyMatch(TheMahjongTile::redDora)), new Random(seed), snap);
        this.driver.setAnimationsEnabled(true);
        matchRevision++;
        markChangedAndSynced();
        return StartMatchResult.STARTED;
    }

    public CompoundTag exportRecordSnapshot(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    public StartMatchResult tryApplyRecordSnapshot(CompoundTag snapshot, java.util.UUID hostUuid) {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("tryApplyRecordSnapshot must be called on the server");
        }
        if (driver != null) return StartMatchResult.NOT_IDLE;

        CompoundTag patched = snapshot.copy();
        // A record copies a game, not the source table's administration rights.
        patched.store("TableOwner", UUIDUtil.CODEC, owner().orElse(hostUuid));

        ListTag seatsTag = patched.getListOrEmpty(NBT_SEATS);
        for (int i = 0; i < seatsTag.size(); i++) {
            CompoundTag entry = seatsTag.getCompoundOrEmpty(i);
            boolean hasOccupant = entry.read(NBT_SEAT_OCCUPANT, UUIDUtil.CODEC).isPresent();
            if (hasOccupant) {
                if (i == 0) entry.store(NBT_SEAT_OCCUPANT, UUIDUtil.CODEC, hostUuid);
                else entry.remove(NBT_SEAT_OCCUPANT);
            } else if (i == 0 && entry.getBooleanOr(NBT_SEAT_ENABLED, false)) {
                entry.store(NBT_SEAT_OCCUPANT, UUIDUtil.CODEC, hostUuid);
            }
        }

        loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), patched));
        resetReturningSeats();
        markChangedAndSynced();
        return StartMatchResult.STARTED;
    }

    private void autoSeatNearbyPlayers() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 centerVec = Vec3.atCenterOf(getBlockPos());
        AABB area = new AABB(getBlockPos()).inflate(AUTO_SEAT_RADIUS);
        List<ServerPlayer> nearby = new ArrayList<>(
                serverLevel.getEntitiesOfClass(ServerPlayer.class, area, p -> !p.isSpectator()));
        nearby.sort(Comparator.comparingDouble(p -> p.position().distanceToSqr(centerVec)));
        Set<Integer> taken = new HashSet<>();
        for (ServerPlayer p : nearby) {
            if (taken.size() >= seats.size()) break;
            int chosen = -1;
            double bestDist = Double.MAX_VALUE;
            for (int seat = 0; seat < seats.size(); seat++) {
                if (taken.contains(seat) || !seats.get(seat).enabled()) continue;

                double d = seat;
                if (d < bestDist) {
                    bestDist = d;
                    chosen = seat;
                }
            }
            if (chosen >= 0) {
                seats.set(chosen, SeatInfo.claimed(p.getUUID()));
                taken.add(chosen);
            }
        }
        setChanged();
    }

    public void endGame() {
        if (level == null || level.isClientSide()) {
            throw new IllegalStateException("endGame must be called on the server");
        }
        clearMatchState();
        markChangedAndSynced();
    }

    public boolean endGame(@Nullable TheMahjongDriver expectedDriver, long expectedRevision) {
        if (level == null || level.isClientSide() || isRemoved() || driver == null
                || driver != expectedDriver || matchRevision != expectedRevision) return false;
        endGame();
        return true;
    }

    public boolean endGame(ServerPlayer player, @Nullable TheMahjongDriver expectedDriver, long expectedRevision) {
        return validMenuActor(player) && isHost(player.getUUID()) && endGame(expectedDriver, expectedRevision);
    }

    /** The confirmation must still describe this exact live match and lifecycle. */
    public boolean resetTable(@Nullable TheMahjongDriver expectedDriver, long expectedRevision) {
        if (level == null || level.isClientSide() || isRemoved()
                || driver != expectedDriver || matchRevision != expectedRevision) return false;
        clearMatchState();
        for (int seat = 0; seat < seats.size(); seat++) {
            seats.set(seat, new SeatInfo(seats.get(seat).enabled(), Optional.empty()));
        }
        markChangedAndSynced();
        return true;
    }

    private void clearMatchState() {
        replay=null;replayRecorder=null;appliedReplayRevision=-1;
        resultHandReveals.clear();
        winningHandDisplay = null;
        closeBotPlayers();
        this.driver = null;
        botsSuspended = false;
        returningSeats.clear();
        privateClicks.clear();
        this.randomSeed = 0L;
        handDisplay.clear();
        handDisplayDriver = null;
        clearResultAnimState();
        lastCuteClickGameTime = -1L;
        clientRenderState = null;
        matchRevision++;
    }

    /** Voluntary reveals are public only for the current result, including the final match result. */
    public Set<Integer> voluntarilyShownSeats() {
        return resultHandReveals.shownSeats(driver);
    }

    public List<MahjongTableHumanPlayer.TableButton> tableButtonsForSeat(int seat) {
        if (replay!=null) return List.of();
        if (driver == null || seat < 0 || seat >= driver.match().playerCount()
                || !(driver.playerAt(seat) instanceof MahjongTableHumanPlayer)) return List.of();
        if (ResultHandReveals.canReveal(driver)) {
            return com.riichimahjong.fabric.PublicTable.revealedSeats(this).contains(seat) ? List.of()
                    : List.of(new MahjongTableHumanPlayer.TableButton.ShowHand(driver.currentRound()));
        }
        return MahjongTableHumanPlayer.tableButtons(driver, seat, riichiPendingForSeat(seat));
    }

    public boolean showHand(ServerPlayer player, TheMahjongDriver expectedDriver, TheMahjongRound expectedRound) {
        if (replay!=null) return false;
        if (!(level instanceof ServerLevel) || player.level() != level || player.isSpectator() || isRemoved()
                || driver == null || driver != expectedDriver
                || player.distanceToSqr(getBlockPos().getX() + .5, getBlockPos().getY() + .5,
                        getBlockPos().getZ() + .5) > 64) return false;
        int seat = seatOfOccupant(player.getUUID());
        if (seat < 0 || seat >= driver.match().playerCount()
                || !(driver.playerAt(seat) instanceof MahjongTableHumanPlayer human)
                || !human.occupant().filter(player.getUUID()::equals).isPresent()) return false;
        if (!resultHandReveals.show(driver, expectedRound, seat)) return false;
        markChangedAndSynced();
        return true;
    }

    @Override
    public void onCuteClick(ServerPlayer player, InteractKey key) {
        if (replay!=null) return;
        if (driver == null) return;
        int seat = seatOfOccupant(player.getUUID());
        if (seat < 0) return;
        if (!(driver.playerAt(seat) instanceof MahjongTableHumanPlayer human)) return;
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty()) return;
        if (level != null) lastCuteClickGameTime = level.getGameTime();
        if (key instanceof InteractKey.SeatSlot slot && slot.area() == InteractKey.SeatSlot.AREA_BUTTON) {
            if (slot.seat() != seat) return;
            var buttons = tableButtonsForSeat(seat);
            if (slot.index() < 0 || slot.index() >= buttons.size()) return;
            if (buttons.get(slot.index()) instanceof MahjongTableHumanPlayer.TableButton.ShowHand show) {
                showHand(player, driver, show.round());
                return;
            }
        }
        int displayedDiscard = -1;
        boolean queuedBefore = human.hasQueued();
        if (key instanceof InteractKey.SeatSlot slot && slot.area() == InteractKey.SeatSlot.AREA_HAND) {
            updateHandDisplay();
            int canonical = handDisplay.canonicalIndex(seat, slot.index(), roundOpt.orElseThrow());
            if (canonical < 0) return;
            displayedDiscard = slot.index();
            key = new InteractKey.SeatSlot(slot.seat(), slot.area(), (short)canonical);
        }
        human.onCuteClick(key, driver, seat, player);
        if (displayedDiscard >= 0 && !queuedBefore && human.hasQueued()
                && driver.currentPhase() instanceof MatchPhase.AwaitingDiscard discard && discard.seat() == seat) {
            handDisplay.recordDiscardSlot(seat, displayedDiscard);
        }

        markChangedAndSynced();
    }

    public void recordPrivateClick(ServerPlayer player) {
        if (level != null) privateClicks.put(player.getUUID(),level.getGameTime());
    }

    public boolean recentPrivateClick(ServerPlayer player) {
        if (level == null) return false;
        long now=level.getGameTime();
        privateClicks.values().removeIf(tick->now-tick>1);
        return privateClicks.containsKey(player.getUUID());
    }

    private void resetReturningSeats() {
        seats.stream().flatMap(info -> info.occupant().stream()).findFirst().ifPresent(ownership::claimIfAbsent);
        returningSeats.clear();privateClicks.clear();
        for (int seat=0;seat<seats.size();seat++) {
            var info=seats.get(seat);
            if (info.enabled()&&info.occupant().isPresent()) returningSeats.remember(info.occupant().orElseThrow(),seat);
        }
    }

    public void onTableRightClick(ServerPlayer player) {
        if (replay!=null) return;
        if (driver == null) return;

        if (level != null && level.getGameTime() == lastCuteClickGameTime) return;
        int seat = seatOfOccupant(player.getUUID());
        if (seat < 0) return;
        if (!(driver.playerAt(seat) instanceof MahjongTableHumanPlayer human)) return;
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty()) return;
        human.onTableRightClick(driver, driver.currentPhase(), seat, player);
    }

    private int seatOfOccupant(UUID uuid) {
        for (int i = 0; i < seats.size(); i++) {
            SeatInfo info = seats.get(i);
            if (info.enabled() && info.occupant().isPresent()
                    && info.occupant().get().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    private void padSeatsAtLeast(int wanted) {
        while (seats.size() < wanted) seats.add(SeatInfo.open());
    }

    private List<MahjongPlayerInterface> buildPlayersFromSeats(List<SeatInfo> seats, boolean redFives) {
        List<MahjongPlayerInterface> out = new ArrayList<>(seats.size());
        for (int seat = 0; seat < seats.size(); seat++) out.add(buildPlayer(seats.get(seat), seat, seats.size(), redFives));
        return out;
    }

    private MahjongPlayerInterface buildPlayer(SeatInfo info, int seat, int playerCount, boolean redFives) {
        if (info.occupant().isPresent()) {
            MahjongTableHumanPlayer p = new MahjongTableHumanPlayer();
            p.setOccupant(info.occupant().get());
            p.setAutoDrawAfterSeconds(0.0);
            return p;
        }
        return buildBot(seat, playerCount, redFives);
    }

    private MahjongPlayerInterface buildBot(int seat, int playerCount, boolean redFives) {
        return playerCount == 4 ? new KobalabBot(botDifficulty(seat), redFives) : new RandomThinkingBot();
    }

    private boolean activeRedFives() {
        return driver != null && driver.match().tileSet().tiles().stream().anyMatch(TheMahjongTile::redDora);
    }

    private void replaceController(int seat, MahjongPlayerInterface replacement) {
        MahjongPlayerInterface previous = driver.playerAt(seat);
        driver.replacePlayer(seat, replacement);
        if (previous instanceof KobalabBot bot) bot.close();
    }

    private void closeBotPlayers() {
        if (driver == null) return;
        for (int seat = 0; seat < driver.match().playerCount(); seat++) {
            if (driver.playerAt(seat) instanceof KobalabBot bot) bot.close();
        }
    }

    @Override
    public void setRemoved() {
        matchRevision++;
        closeBotPlayers();
        botsSuspended = true;
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (!botsSuspended) return;
        botsSuspended = false;
        if (driver == null) return;
        for (int seat = 0; seat < driver.match().playerCount(); seat++) {
            if (driver.playerAt(seat) instanceof KobalabBot) {
                replaceController(seat, buildBot(seat, driver.match().playerCount(), activeRedFives()));
            }
        }
    }

    private void serverTick() {
        if(level instanceof ServerLevel sl&&sl.getGameTime()>=nextArchiveAttempt)flushReplayArchives();
        if(replay!=null) {
            if(replay.tick()){applyReplayFrame();markChangedAndSynced();}
            return;
        }
        if (driver == null) return;
        if (!(level instanceof ServerLevel sl)) return;

        updateHandDisplay();

        MatchPhase prevPhase = driver.currentPhase();
        TheMahjongMatch prevMatch = driver.match();

        for (int i = 0; i < driver.match().playerCount(); i++) {
            if (driver.playerAt(i) instanceof MahjongTableHumanPlayer human) {
                human.validateBeforeAdvance(sl);
            }
        }

        boolean animating = resultAnimStage != ResultAnimStage.NONE;
        if (!animating) {
            driver.advance(SECONDS_PER_TICK);
            playAcceptedActionSounds(sl, prevMatch);
        }
        MatchPhase nextPhase = driver.currentPhase();
        if (!(prevPhase instanceof MatchPhase.RoundEnded) && nextPhase instanceof MatchPhase.RoundEnded) {
            winningHandDisplay = WinningHandDisplay.capture(driver.winningTile(), nextPhase);
        } else if (!(nextPhase instanceof MatchPhase.RoundEnded)) {
            winningHandDisplay = null;
        }
        updateHandDisplay();

        boolean playerStateChanged = false;
        for (int i = 0; i < driver.match().playerCount(); i++) {
            if (driver.playerAt(i) instanceof MahjongTableHumanPlayer human) {
                if (human.tickAfterAdvance(sl, this, i, nextPhase)) playerStateChanged = true;
            }
        }

        if (animating) {
            tickResultAnimation(sl);
            if (playerStateChanged) markChangedAndSynced();
            return;
        }

        if (!(prevPhase instanceof MatchPhase.RoundEnded)
                && nextPhase instanceof MatchPhase.RoundEnded re) {
            startResultAnimation(sl, re);
        }
        if (prevMatch != driver.match() || prevPhase != nextPhase || playerStateChanged) {
            markChangedAndSynced();
        }
    }

    private static final long RESULT_ANIM_HEADER_TICKS = 20L;
    private static final long RESULT_ANIM_YAKU_TICKS   = 10L;

    private void playAcceptedActionSounds(ServerLevel level, TheMahjongMatch previous) {
        var before = previous.currentRound().orElse(null);
        var after = driver.match().currentRound().orElse(null);
        if (before == null || after == null || before.players().size() != after.players().size()) return;
        // Only an accepted discard adds to a river; clicks, rejected actions and
        // subsequent claims must not emit a second placement sound.
        for (int seat = 0; seat < before.players().size(); seat++) {
            if (after.players().get(seat).discards().size() > before.players().get(seat).discards().size()) {
                level.playSound(null, getBlockPos(), net.minecraft.sounds.SoundEvents.STONE_PLACE,
                        net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 2.0f);
            }
            // Play once when the riichi declaration discard is accepted.
            if (!before.players().get(seat).riichi() && after.players().get(seat).riichi()) {
                level.playSound(null, getBlockPos(), net.minecraft.sounds.SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(),
                        net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 1.0f);
            }
            // Kakan replaces a Pon without increasing the meld count.
            for (var meld : after.players().get(seat).melds()) {
                if (before.players().get(seat).melds().contains(meld)) continue;
                var sound = switch (meld) {
                    case TheMahjongMeld.Chi chi -> net.minecraft.sounds.SoundEvents.PLAYER_BURP;
                    case TheMahjongMeld.Pon pon -> net.minecraft.sounds.SoundEvents.END_PORTAL_FRAME_FILL;
                    default -> net.minecraft.sounds.SoundEvents.ANVIL_PLACE;
                };
                level.playSound(null, getBlockPos(), sound,
                        net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 1.0f);
            }
        }
    }

    private void startResultAnimation(ServerLevel sl, MatchPhase.RoundEnded phase) {
        resultAnimStage = ResultAnimStage.SHOW_HEADER;
        resultAnimYakuIdx = 0;
        resultAnimNextTick = sl.getGameTime() + RESULT_ANIM_HEADER_TICKS;
        playResultStepSound(sl);
        markChangedAndSynced();
    }

    private void tickResultAnimation(ServerLevel sl) {
        if (resultAnimStage == ResultAnimStage.NONE) return;
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) {
            clearResultAnimState();
            markChangedAndSynced();
            return;
        }
        long now = sl.getGameTime();
        if (now < resultAnimNextTick) return;
        boolean changed = false;
        switch (resultAnimStage) {
            case SHOW_HEADER -> {
                resultAnimStage = ResultAnimStage.SHOW_YAKU_LINES;
                resultAnimNextTick = now + RESULT_ANIM_YAKU_TICKS;
                changed = true;
            }
            case SHOW_YAKU_LINES -> {
                int totalYaku = totalYakuLineCount(re);
                if (resultAnimYakuIdx < totalYaku) {
                    resultAnimYakuIdx++;
                    playResultStepSound(sl);
                    resultAnimNextTick = now + RESULT_ANIM_YAKU_TICKS;
                    changed = true;
                } else {
                    resultAnimStage = ResultAnimStage.SHOW_FINAL;
                    resultAnimNextTick = now + RESULT_ANIM_YAKU_TICKS;
                }
            }
            case SHOW_FINAL -> {
                playFinalWinEffects(sl, re);
                playResultStepSound(sl);
                resultAnimStage = ResultAnimStage.AWAITING_ADVANCE;
                resultAnimNextTick = Long.MAX_VALUE;
                changed = true;
            }
            case AWAITING_ADVANCE, NONE -> {
            }
        }
        if (changed) markChangedAndSynced();
    }

    private static int totalYakuLineCount(MatchPhase.RoundEnded re) {
        if (re.winResults().isEmpty()) return 0;
        var primary = re.winResults().get(0);
        int n = primary.yakuman().size() + primary.yaku().size();

        boolean yakuman = !primary.yakuman().isEmpty();
        if (!yakuman && primary.doraCount() > 0) n += 1;
        return n;
    }

    private void playFinalWinEffects(ServerLevel sl, MatchPhase.RoundEnded re) {
        if (re.winResults().isEmpty()) {
            MahjongRoundResolvedEvent.post(sl, worldPosition, 0);
            return;
        }
        for (var win : re.winResults()) {
            int winnerSeat = -1;
            for (int i = 0; i < win.pointDeltas().size(); i++) {
                if (win.pointDeltas().get(i) > 0) { winnerSeat = i; break; }
            }
            if (winnerSeat < 0) continue;
            UUID winnerUuid = winnerSeat < seats.size() ? seats.get(winnerSeat).occupant().orElse(null) : null;
            ServerPlayer winnerPlayer = winnerUuid == null ? null
                    : sl.getServer().getPlayerList().getPlayer(winnerUuid);
            String winnerName = winnerPlayer != null ? winnerPlayer.getName().getString()
                    : fallbackSeatName(winnerSeat);
            List<String> yakuNames = new ArrayList<>();
            for (var y : win.yaku()) yakuNames.add(y.name());
            List<String> yakumanNames = new ArrayList<>();
            for (var y : win.yakuman()) yakumanNames.add(y.name());
            MahjongWinEffects.playWinEffects(sl, winnerPlayer, winnerName,
                    win.han(), !yakumanNames.isEmpty(), yakuNames, yakumanNames);
            if (winnerPlayer != null && win.han() > 0) {
                giveOrDropStars(sl, winnerPlayer, win.han());
            }
            MahjongRoundResolvedEvent.post(sl, worldPosition, win.han());
        }
    }

    private static void giveOrDropStars(ServerLevel sl, ServerPlayer player, int count) {
        ItemStack stack = new ItemStack(com.riichimahjong.registry.ModItems.MAHJONG_STAR.get(), count);
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    sl, player.getX(), player.getY(), player.getZ(), stack);
            drop.setDefaultPickUpDelay();
            sl.addFreshEntity(drop);
        }
    }

    private static String fallbackSeatName(int seat) {
        return switch (seat) {
            case 0 -> "East";
            case 1 -> "South";
            case 2 -> "West";
            case 3 -> "North";
            default -> "Seat " + seat;
        };
    }

    public void advanceRoundAfterResult() {
        if (replay!=null) return;
        if (driver == null) return;
        if (resultAnimStage != ResultAnimStage.AWAITING_ADVANCE) return;
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded)) {
            clearResultAnimState();
            markChangedAndSynced();
            return;
        }
        var next = com.riichimahjong.fabric.RoundProgression.next(driver);
        clearResultAnimState();
        try {
            driver.advanceRound(next.renchan(), next.nextHonba());
            matchRevision++;
            resultHandReveals.shownSeats(driver);
        } catch (Exception ignored) {
        }
        markChangedAndSynced();
    }

    private void clearResultAnimState() {
        resultAnimStage = ResultAnimStage.NONE;
        resultAnimYakuIdx = 0;
        resultAnimNextTick = 0L;
    }

    private void playResultStepSound(ServerLevel sl) {
        sl.playSound(null, getBlockPos(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.45f, 1.40f);
    }

    public ItemStack takeOneTileFromTableInventory(Item targetItem) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack here = inventory.get(slot);
            if (here.isEmpty() || here.getItem() != targetItem) continue;
            ItemStack one = here.split(1);
            if (here.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
            return one;
        }
        return ItemStack.EMPTY;
    }

    public void restoreToTableInventory(ItemStack stack, Item targetItem) {
        if (stack.isEmpty()) return;
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack here = inventory.get(slot);
            if (here.getItem() == targetItem
                    && here.getCount() < here.getMaxStackSize()) {
                here.grow(stack.getCount());
                return;
            }
        }
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (inventory.get(slot).isEmpty()) {
                inventory.set(slot, stack);
                return;
            }
        }
    }

    public boolean mintTilesFromNothing() { return mintTilesFromNothing; }

    public boolean drawnTileDeliveredForSeat(int seat) {
        if (driver == null || seat < 0 || seat >= driver.match().playerCount()) return false;
        return driver.playerAt(seat) instanceof MahjongTableHumanPlayer h && h.drawnTileDelivered();
    }

    public boolean riichiPendingForSeat(int seat) {
        if (driver == null || seat < 0 || seat >= driver.match().playerCount()) return false;
        return driver.playerAt(seat) instanceof MahjongTableHumanPlayer h && h.riichiPending();
    }

    public boolean isInResultPhase() { return resultAnimStage != ResultAnimStage.NONE; }

    public ResultAnimStage resultAnimStage() { return resultAnimStage; }

    public int resultAnimYakuIdx() { return resultAnimYakuIdx; }

    public boolean deliverToMainHand() { return deliverToMainHand; }

    public void setDeliverToMainHand(boolean enabled) {
        if (this.deliverToMainHand == enabled) return;
        this.deliverToMainHand = enabled;
        markChangedAndSynced();
    }

    public void setMintTilesFromNothing(boolean enabled) {
        if (this.mintTilesFromNothing == enabled) return;
        this.mintTilesFromNothing = enabled;
        markChangedAndSynced();
    }

    private void markChangedAndSynced() {
        setChanged();
        pushClientSync();
    }

    private void pushClientSync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public static <T extends BlockEntity> BlockEntityTicker<T> serverTicker() {
        return (level, pos, blockState, be) -> {
            if (be instanceof MahjongTableBlockEntity table) {
                table.serverTick();
            }
        };
    }

    @Override
    public int getContainerSize() { return INVENTORY_SIZE; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot < 0 || slot >= INVENTORY_SIZE ? ItemStack.EMPTY : inventory.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(inventory, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(inventory, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INVENTORY_SIZE) return;
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(getBlockPos()) != this) return false;
        if (driver != null) return false;
        return player.distanceToSqr(
                getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5, getBlockPos().getZ() + 0.5)
                <= 64.0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return driver == null;
    }

    @Override
    public void clearContent() {
        inventory.clear();
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.riichi_mahjong.mahjong_table_new");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return com.riichimahjong.fabric.PortMenus.inventoryMenu(containerId, playerInv, this,0);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return new CompoundTag(); // Complete match NBT is disk-only, never a public update tag.
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return null;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);

        ownership.write(out);
        // Independent of the live match: ending/resetting a table must not discard an unacknowledged archive.
        replayArchives.write(out.child("ReplayArchives"));
        out.putBoolean(NBT_LAST_POWERED, lastPowered);
        out.putString(NBT_PRESET, preset.name());
        out.putString(NBT_BOT_DIFFICULTY, defaultBotDifficulty.name());
        out.putBoolean(NBT_MINT_TILES, mintTilesFromNothing);
        out.putBoolean(NBT_DELIVER_TO_MAIN_HAND, deliverToMainHand);

        out.putString(NBT_RESULT_ANIM_STAGE, resultAnimStage.name());
        out.putInt(NBT_RESULT_ANIM_YAKU_IDX, resultAnimYakuIdx);

        ValueOutput.ValueOutputList seatsOut = out.childrenList(NBT_SEATS);
        for (int seat = 0; seat < seats.size(); seat++) {
            SeatInfo info = seats.get(seat);
            ValueOutput entry = seatsOut.addChild();
            entry.putBoolean(NBT_SEAT_ENABLED, info.enabled());
            info.occupant().ifPresent(uuid -> entry.store(NBT_SEAT_OCCUPANT, UUIDUtil.CODEC, uuid));
            BotDifficulty override = seatBotDifficulties.get(seat);
            if (override != null) entry.putString(NBT_BOT_DIFFICULTY, override.name());
        }

        ValueOutput.ValueOutputList inventoryOut = out.childrenList(NBT_INVENTORY);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) continue;
            ValueOutput entry = inventoryOut.addChild();
            entry.putInt(NBT_INVENTORY_SLOT, i);
            entry.store(NBT_INVENTORY_ITEM, ItemStack.CODEC, stack);
        }

        if (driver != null && replay==null) {
            DriverNbt.writeDriver(out.child(NBT_DRIVER), driver);
            if(replayRecorder!=null)out.store("ReplayRecording",CompoundTag.CODEC,replayRecorder.toNbt());
            resultHandReveals.write(out.child("ShownHands"), driver);
            if (winningHandDisplay != null && driver.currentPhase() instanceof MatchPhase.RoundEnded) {
                winningHandDisplay.write(out.child("WinningHandDisplay"));
            }
            out.putLong(NBT_RANDOM_SEED, randomSeed);
            var reservations=out.childrenList("ReturningSeats");
            returningSeats.snapshot().forEach((uuid,seat)->{
                var entry=reservations.addChild();entry.store("uuid",UUIDUtil.CODEC,uuid);entry.putInt("Seat",seat);
            });

            ValueOutput.ValueOutputList humans = out.childrenList(NBT_HUMAN_PLAYERS);
            for (int i = 0; i < driver.match().playerCount(); i++) {
                ValueOutput entry = humans.addChild();
                entry.putInt("Seat", i);
                if (driver.playerAt(i) instanceof MahjongTableHumanPlayer h) {
                    h.writeNbt(entry);
                }
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        replay=null;replayRecorder=null;appliedReplayRevision=-1;nextArchiveAttempt=0;
        replayArchives.read(in.childOrEmpty("ReplayArchives"));

        matchRevision++;
        closeBotPlayers();
        botsSuspended = false;
        defaultBotDifficulty = BotDifficulty.parse(in.getString(NBT_BOT_DIFFICULTY).orElse(null));
        seatBotDifficulties.clear();

        lastPowered = in.getBooleanOr(NBT_LAST_POWERED, false);
        mintTilesFromNothing = in.getBooleanOr(NBT_MINT_TILES, true);
        deliverToMainHand = in.getBooleanOr(NBT_DELIVER_TO_MAIN_HAND, true);

        resultAnimStage = in.getString(NBT_RESULT_ANIM_STAGE)
                .map(name -> {
                    try {
                        return ResultAnimStage.valueOf(name);
                    } catch (IllegalArgumentException ignored) {
                        return ResultAnimStage.NONE;
                    }
                })
                .orElse(ResultAnimStage.NONE);
        resultAnimYakuIdx = in.getIntOr(NBT_RESULT_ANIM_YAKU_IDX, 0);
        in.getString(NBT_PRESET).ifPresent(name -> {
            try {
                preset = RuleSetPreset.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                preset = RuleSetPreset.MAHJONG_SOUL_4P;
            }
        });

        in.childrenList(NBT_SEATS).ifPresent(seatsIn -> {
            List<SeatInfo> loaded = new ArrayList<>();
            for (ValueInput entry : seatsIn) {
                boolean enabled = entry.getBooleanOr(
                        NBT_SEAT_ENABLED,
                        entry.getString(NBT_SEAT_LEGACY_TYPE).isPresent());
                UUID uuid = entry.read(NBT_SEAT_OCCUPANT, UUIDUtil.CODEC).orElse(null);
                int seat = loaded.size();
                if (seat < DEFAULT_SEAT_COUNT) {
                    entry.getString(NBT_BOT_DIFFICULTY).ifPresent(name -> {
                        try { seatBotDifficulties.put(seat, BotDifficulty.valueOf(name)); }
                        catch (IllegalArgumentException ignored) { /* Invalid overrides inherit the table default. */ }
                    });
                }
                loaded.add(new SeatInfo(enabled, enabled ? Optional.ofNullable(uuid) : Optional.empty()));
            }
            seats = loaded;
        });
        if (seats.isEmpty()) seats = defaultSeats();
        ownership.read(in, seats.stream().flatMap(info -> info.occupant().stream()).findFirst());

        inventory.clear();
        for (ValueInput entry : in.childrenListOrEmpty(NBT_INVENTORY)) {
            int slot = entry.getIntOr(NBT_INVENTORY_SLOT, 0);
            if (slot < 0 || slot >= INVENTORY_SIZE) continue;
            entry.read(NBT_INVENTORY_ITEM, ItemStack.CODEC).ifPresent(stack -> inventory.set(slot, stack));
        }

        this.driver = null;
        resultHandReveals.clear();
        this.winningHandDisplay = null;
        this.randomSeed = 0L;
        returningSeats.clear();privateClicks.clear();
        Optional<ValueInput> driverIn = in.child(NBT_DRIVER);
        if (driverIn.isPresent()) {
            this.randomSeed = in.getLongOr(NBT_RANDOM_SEED, 0L);
            int playerCount = driverIn.get().childOrEmpty("match").getIntOr("playerCount", 0);
            padSeatsAtLeast(DEFAULT_SEAT_COUNT);

            List<SeatInfo> activeSeats = new ArrayList<>(playerCount);
            for (SeatInfo info : seats) {
                if (info.enabled()) activeSeats.add(info);
                if (activeSeats.size() == playerCount) break;
            }
            while (activeSeats.size() < playerCount) activeSeats.add(SeatInfo.open());
            boolean redFives = false;
            for (ValueInput entry : driverIn.get().childOrEmpty("match").childOrEmpty("tileSet").childrenListOrEmpty("entries")) {
                if (entry.childOrEmpty("tile").getBooleanOr("redDora", false)) redFives = true;
            }
            this.driver = DriverNbt.readDriver(
                    driverIn.get(),
                    buildPlayersFromSeats(activeSeats, redFives),
                    new java.security.SecureRandom());
            this.driver.setAnimationsEnabled(true);
            resultHandReveals.read(in.childOrEmpty("ShownHands"), driver);
            if (driver.currentPhase() instanceof MatchPhase.RoundEnded) {
                winningHandDisplay = in.child("WinningHandDisplay").map(WinningHandDisplay::read).orElse(null);
            }

            for (var entry:in.childrenListOrEmpty("ReturningSeats")) {
                int seat=entry.getIntOr("Seat",-1);var uuid=entry.read("uuid",UUIDUtil.CODEC).orElse(null);
                if (uuid!=null&&seat>=0&&seat<driver.match().playerCount()&&seats.get(seat).enabled()
                        &&seats.get(seat).occupant().filter(id->!id.equals(uuid)).isEmpty()
                        &&returningSeats.allows(uuid,seat)) returningSeats.remember(uuid,seat);
            }
            for (int seat=0;seat<driver.match().playerCount();seat++) {
                var uuid=seats.get(seat).occupant().orElse(null);
                if (uuid!=null&&returningSeats.allows(uuid,seat)) returningSeats.remember(uuid,seat);
            }

            for (ValueInput entry : in.childrenListOrEmpty(NBT_HUMAN_PLAYERS)) {
                int seat = entry.getIntOr("Seat", 0);
                if (seat >= 0 && seat < driver.match().playerCount()
                        && driver.playerAt(seat) instanceof MahjongTableHumanPlayer h) {
                    h.readNbt(entry);
                }
            }
            // A pre-feature save has no complete action history: never mislabel it a full replay.
            try {
                replayRecorder=in.read("ReplayRecording",CompoundTag.CODEC).map(ReplayRecorder::fromNbt).orElse(null);
                attachRecordingObserver();
            } catch(RuntimeException error) {
                replayRecorder=null;
                System.getLogger("riichi_mahjong/replay").log(System.Logger.Level.WARNING,"Cannot resume replay recording",error);
            }
        }
    }
}
