// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable;

import com.riichimahjong.mahjongcore.MahjongRoundResolvedEvent;
import com.riichimahjong.mahjongcore.MahjongWinEffects;
import com.riichimahjong.analysis.MatchAnalysisSession;
import com.riichimahjong.replay.*;
import com.riichimahjong.fabric.RoundResultDisplay;
import com.riichimahjong.cuterenderer.CuteClickHandler;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.riichimahjong.fabric.HandDisplayState;
import com.riichimahjong.fabric.ResultHandReveals;
import com.riichimahjong.fabric.TableOwnership;
import com.riichimahjong.fabric.WinningHandDisplay;
import com.riichimahjong.fabric.WinEffectTriggers;
import com.riichimahjong.fabric.PlayerCosmetics;
import com.riichimahjong.fabric.RiichiSkin;
import com.riichimahjong.fabric.WinResultLabels;
import com.riichimahjong.fabric.ExchangePresentation.Session;
import com.riichimahjong.fabric.wineffect.WinEffectContext;
import com.riichimahjong.fabric.wineffect.DeferredWinQueue;
import com.riichimahjong.fabric.wineffect.WinEffectSession;
import com.riichimahjong.fabric.wineffect.WinEffects;
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
    private final SpectatorParticipants spectatorParticipants = new SpectatorParticipants();
    private final TableOwnership ownership = new TableOwnership();
    private final com.riichimahjong.fabric.SpectatorSeats spectators = new com.riichimahjong.fabric.SpectatorSeats();
    private boolean spectatorHandsVisible;
    private final Map<UUID,Long> privateClicks = new HashMap<>();
    private final HandDisplayState handDisplay = new HandDisplayState();
    private final ResultHandReveals resultHandReveals = new ResultHandReveals();
    private final RoundResultDisplay roundResultDisplay = new RoundResultDisplay();
    @Nullable private WinningHandDisplay winningHandDisplay;
    private String winEffectId = WinEffects.DEFAULT;
    @Nullable private WinEffectSession winEffectSession;
    private final Set<String> consumedWinTileSlots = new HashSet<>();
    private Set<Integer> finalWinningSeats = Set.of();
    private final DeferredWinQueue deferredWinQueue = new DeferredWinQueue();
    private final Set<Integer> completedWinSeats = new HashSet<>();
    private boolean winPlaybackPrepared;
    private List<WinResultLabels.Result> finalResultLabels = List.of();
    @Nullable private Session exchangePresentation;


    public Set<Integer> completedWinPresentationSeats() {
        var seats=new HashSet<>(completedWinSeats);seats.addAll(deferredWinQueue.completed());return Set.copyOf(seats);
    }
    public List<WinResultLabels.Result> winResultLabels() { return finalResultLabels; }
    public Set<Integer> publicWinningSeats() {
        if(driver==null)return Set.of();
        if(!driver.rules().bloodBattle()){
            if(driver.sichuanState()!=null)return driver.sichuanState().winners();
            return finalWinningSeats;
        }
        if(!ResultHandReveals.canReveal(driver))return Set.of();
        var visible=new HashSet<>(completedWinPresentationSeats());
        if(winEffectSession!=null)winEffectSession.context().winners().forEach(w->visible.add(w.seat()));
        return Set.copyOf(visible);
    }
    @Nullable public Session exchangePresentation() { return exchangePresentation; }
    private boolean exchanging() { return exchangePresentation!=null; }
    private boolean exchangeCompleted() {
        if(driver==null)return false;
        if(driver.exchangeState()!=null)return driver.exchangeState().complete();
        return driver.sichuanState()!=null&&driver.rules().exchangeThree()
                &&driver.sichuanState().stage()!=com.themahjong.sichuan.SichuanState.Stage.EXCHANGE;
    }
    private void captureExchange(boolean wasComplete) {
        if(!wasComplete&&exchangeCompleted()) {
            exchangePresentation=com.riichimahjong.fabric.ExchangePresentation.captureAfterExchange(driver);
            handDisplay.clear();
        }
    }
    private void prepareBloodBattlePlayback() {
        if(driver==null||!driver.rules().bloodBattle()||!ResultHandReveals.canReveal(driver)||winPlaybackPrepared)return;
        WinEffectTriggers.history(driver,winEffectId).forEach(deferredWinQueue::add);
        winPlaybackPrepared=true;
        finalResultLabels=WinResultLabels.captureResults(driver);
        playNextDeferredWin();
    }
    private void playNextDeferredWin() {
        var context=deferredWinQueue.next();
        if(context!=null){
            winEffectSession=new WinEffectSession(context);
            finalWinningSeats=context.winners().stream().map(WinEffectContext.Winner::seat).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
    private void recordWinPresentation(WinEffectContext context) {
        if(context==null)return;
        context=personalizeWin(context);
        if(driver.rules().bloodBattle())deferredWinQueue.add(context);
        else{
            winEffectSession=new WinEffectSession(context);
            finalWinningSeats=context.winners().stream().map(WinEffectContext.Winner::seat).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private PlayerCosmetics.Selection seatCosmetics(int seat) {
        if (!(level instanceof ServerLevel sl)) return PlayerCosmetics.Selection.DEFAULT;
        var owner = seats.get(seat).occupant().or(() -> returningSeats.playerAt(seat));
        return PlayerCosmetics.get(sl.getServer(), owner.orElse(null));
    }

    public RiichiSkin riichiSkin(int seat) {
        if (replay != null) return replay.frame().riichiSkin(seat);
        return seatCosmetics(seat).skin();
    }

    private WinEffectContext personalizeWin(WinEffectContext context) {
        return context.withChoices(seat -> seatCosmetics(seat).winEffect(winEffectId));
    }

    public String winEffectId() { return winEffectId; }
    @Nullable public WinEffectSession winEffectSession() { return winEffectSession; }
    public Set<String> consumedWinTileSlots() { return Set.copyOf(consumedWinTileSlots); }
    public void selectWinEffect(String id) {
        if (hasActiveMatch()) return;
        winEffectId = WinEffects.resolve(id).id();
        markChangedAndSynced();
    }

    private void finishWinEffect() {
        if (winEffectSession != null && winEffectSession.effect().consumesSource()) {
            String slot=winEffectSession.context().source().slot();
            if(slot.startsWith("river/"))consumedWinTileSlots.add(slot);
        }
        if(winEffectSession!=null){
            winEffectSession.context().winners().forEach(w->completedWinSeats.add(w.seat()));
            if(driver!=null&&driver.rules().bloodBattle())deferredWinQueue.complete(winEffectSession.context());
        }
        winEffectSession=null;
    }

    private void clearWinPresentation() {
        winEffectSession=null;
        consumedWinTileSlots.clear();
        finalWinningSeats=Set.of();
        deferredWinQueue.clear();completedWinSeats.clear();winPlaybackPrepared=false;finalResultLabels=List.of();
        exchangePresentation=null;
    }
    @Nullable private TheMahjongDriver handDisplayDriver;
    private RuleSetPreset preset = RuleSetPreset.MAHJONG_SOUL_4P;
    private MatchLength matchLength = MatchLength.SOUTH;
    @Nullable private MatchAnalysisSession playerAnalysis;

    private void bindPlayerAnalysis() {
        if (playerAnalysis == null || driver == null) return;
        playerAnalysis.bind(driver, this::setChanged, settlement -> {
            if (!(level instanceof ServerLevel serverLevel)) return;
            var player = serverLevel.getServer().getPlayerList().getPlayer(settlement.player());
            if (player == null) return;
            String message;
            if (settlement.quotedTenths() != null) {
                message = "本场积分 " + String.format(java.util.Locale.ROOT, "%+.1f", settlement.actualTenths()/10.0)
                        + " · 累计 " + String.format(java.util.Locale.ROOT, "%.1f", settlement.totalTenths()/10.0);
            } else message = settlement.botOperated()
                    ? "本场有机器人操作，已记录统计，不累计积分。"
                    : "本场已记录统计，该场不累计积分。";
            player.sendSystemMessage(Component.literal(message));
        });
    }

    private void startPlayerAnalysis() {
        if (playerAnalysis != null) playerAnalysis.close();
        var mode = com.riichimahjong.analysis.AnalysisMode.of(effectivePreset(), matchLength,
                driver.rules().exchangeThree(), driver.rules().jokersEnabled(), driver.rules().bloodBattle());
        var owners = java.util.stream.IntStream.range(0, driver.match().playerCount())
                .mapToObj(seat -> seats.get(seat).occupant().orElse(null)).toList();
        playerAnalysis = new MatchAnalysisSession(mode, owners);
        bindPlayerAnalysis();
        playerAnalysis.beginHand();
    }
    private boolean exchangeThreeTiles;
    private boolean jokersEnabled;
    private boolean bloodBattle;
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

    @Nullable public MatchPhase displayPhase() {
        return driver == null ? null : roundResultDisplay.phase(driver.currentPhase());
    }

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
        roundResultDisplay.clear();
        roundResultDisplay.observe(driver.currentPhase());
        if(driver.currentPhase() instanceof MatchPhase.MatchEnded) {
            // Existing database replays already contain the public result in the previous frame.
            for(int index=replay.index()-1;index>=0;index--) {
                var earlier=replay.record().frames().get(index);
                if(earlier.roundIndex()!=frame.roundIndex())break;
                if(!earlier.driver().getCompoundOrEmpty("phase").getStringOr("type","").equals("ROUND_ENDED"))continue;
                var ended=earlier.restore(level.registryAccess());
                roundResultDisplay.observe(ended.currentPhase());
                var endedInput=TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),earlier.driver());
                winningHandDisplay=endedInput.child("ReplayWinningHand").map(WinningHandDisplay::read).orElse(null);
                break;
            }
        }
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
                replayRecorder.capture(observed, this::riichiSkin);
                if(observed.currentPhase() instanceof MatchPhase.MatchEnded&&!replayRecorder.finished()) {
                    replayArchives.offer(replayRecorder.finish(observed, this::riichiSkin));
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
        handDisplay.update(round, level.getGameTime(),driver.match().ruleSet().jokersEnabled());
        return true;
    }

    public List<TheMahjongTile> handDisplayOrder(int seat) {
        if (!updateHandDisplay()) return List.of();
        return winningHandLayout(seat).map(WinningHandDisplay.Layout::order).orElseGet(() -> handDisplay.hand(seat));
    }

    public int selectedDiscardIndex(int seat) {
        if (driver == null || seat < 0 || seat >= driver.match().playerCount()
                || !(driver.playerAt(seat) instanceof MahjongTableHumanPlayer human)) return -1;
        int canonical = human.selectedDiscardIndex(driver);
        if (canonical < 0 || !updateHandDisplay()) return -1;
        return handDisplay.displayedIndex(seat, canonical, driver.currentRound());
    }

    public void clearDiscardSelection(UUID player) {
        int seat = seatOfOccupant(player);
        if (driver != null && seat >= 0 && seat < driver.match().playerCount()
                && driver.playerAt(seat) instanceof MahjongTableHumanPlayer human) {
            human.clearDiscardSelection();
        }
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
        if(driver==null)return Optional.empty();
        if(driver.sichuanState()!=null || driver.bloodBattleState()!=null){
            var state=driver.sichuanState();
            boolean won=state!=null?state.won(seat):driver.bloodBattleState().won(seat);
            if(!won)return Optional.empty();
            var tile=state!=null?state.winningTile(seat):driver.bloodBattleState().winningTile(seat);
            var round=driver.match().currentRound().orElse(null);
            if(tile==null||round==null)return Optional.empty();
            var hand=new ArrayList<>(round.players().get(seat).currentHand());
            if(!hand.remove(tile))return Optional.empty();
            hand.sort(TheMahjongTile.DISPLAY_ORDER);
            if(driver.match().ruleSet().jokersEnabled())hand.sort(Comparator.comparingInt(t->
                    (state!=null?t.suit()==TheMahjongTile.Suit.DRAGON&&t.rank()==3:com.themahjong.variants.Jokers.isJoker(t))?0:1));
            hand.add(tile);
            var slots=java.util.stream.IntStream.range(0,hand.size()).boxed().toList();
            return Optional.of(new WinningHandDisplay.Layout(List.copyOf(hand),slots,
                    hand.size()>1?List.of(hand.size()-1):List.of()));
        }
        if(winningHandDisplay==null)return Optional.empty();
        if(driver.currentPhase() instanceof MatchPhase.MatchEnded)
            return driver.match().currentRound().flatMap(round -> winningHandDisplay.layout(round,finalWinningSeats,seat));
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

    public MatchLength matchLength() { return matchLength; }

    public boolean exchangeThreeTiles() { return exchangeThreeTiles; }
    public boolean jokersEnabled() { return jokersEnabled; }
    public boolean bloodBattle() { return bloodBattle; }
    public void setBloodBattle(boolean enabled) {
        if (driver != null || bloodBattle == enabled) return;
        bloodBattle = enabled;
        matchRevision++;
        markChangedAndSynced();
    }

    public void setExchangeThreeTiles(boolean enabled) {
        if (driver != null || exchangeThreeTiles == enabled) return;
        exchangeThreeTiles = enabled;
        matchRevision++;
        markChangedAndSynced();
    }

    public void setJokersEnabled(boolean enabled) {
        if (driver != null || jokersEnabled == enabled) return;
        jokersEnabled = enabled;
        matchRevision++;
        markChangedAndSynced();
    }

    public void selectMatchLength(MatchLength length) {
        if (driver != null || length == null || matchLength == length) return;
        matchLength = length;
        matchRevision++;
        markChangedAndSynced();
    }

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
        if (preset.family().supportsThreePlayers() && seats.size() >= 4 && !seats.get(3).enabled()) {
            return preset.family().preset(3);
        }
        return preset;
    }

    public Set<UUID> spectators() { return spectators.players(); }
    public boolean isWatching(UUID uuid) { return spectators.contains(uuid); }
    public void stopWatching(UUID uuid) { spectators.remove(uuid); }
    public void clearSpectators() { spectators.clear(); }
    public boolean spectatorHandsVisible() { return spectatorHandsVisible; }
    public boolean spectatorHandsVisibleTo(UUID player) {
        return driver != null && replay == null && spectatorHandsVisible && isWatching(player) && spectatorCanSeeHands(player);
    }
    public boolean spectatorCanSeeHands(UUID player) {
        return !spectatorParticipants.contains(player) && returningSeats.seat(player).isEmpty()
                && seatOfPlayer(player).isEmpty();
    }
    public void setSpectatorHandsVisible(boolean visible) {
        if (spectatorHandsVisible == visible) return;
        spectatorHandsVisible = visible;
        matchRevision++;
        markChangedAndSynced();
    }
    public boolean watch(ServerPlayer player) {
        if (level != player.level() || isRemoved() || !(level instanceof ServerLevel sl)
                || sl.getServer().getPlayerList().getPlayer(player.getUUID()) != player
                || !sl.hasChunkAt(getBlockPos()) || sl.getBlockEntity(getBlockPos()) != this
                || player.distanceToSqr(getBlockPos().getX()+.5,getBlockPos().getY()+.5,getBlockPos().getZ()+.5)>64
                || seatOfPlayer(player.getUUID()).isPresent()) return false;
        spectators.add(player.getUUID());
        return true;
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

    public boolean transferHost(ServerPlayer player, int seat, UUID expectedOccupant,
            @Nullable TheMahjongDriver expectedDriver, long expectedRevision) {
        if (expectedOccupant == null || !validMenuActor(player) || driver != expectedDriver || matchRevision != expectedRevision
                || seat < 0 || seat >= seats.size() || !seats.get(seat).enabled()
                || seats.get(seat).occupant().filter(expectedOccupant::equals).isEmpty()
                || !(level instanceof ServerLevel sl)) return false;
        var target = sl.getServer().getPlayerList().getPlayer(expectedOccupant);
        if (target == null || !validMenuActor(target)
                || !ownership.transfer(player.getUUID(),player.getUUID(),expectedOccupant)) return false;
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
        if (replay != null) return false;
        if (!validMenuActor(player) || !isHost(player.getUUID())
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
    public Optional<UUID> returningPlayerAtSeat(int seat) { return returningSeats.playerAt(seat); }

    public boolean canClaimSeat(int seat, UUID uuid) {
        if (replay != null) return false;
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
            spectatorParticipants.remember(uuid);
            if (replayRecorder != null) {
                var person = level.getServer().getPlayerList().getPlayer(uuid);
                replayRecorder.remember(seat, uuid, person == null ? "离线玩家" : person.getName().getString());
            }
            replaceController(seat, buildPlayer(SeatInfo.claimed(uuid), seat, driver.match().playerCount(), activeRedFives()));
        }
        seatOfPlayer(uuid).ifPresent(prev -> {
            if (prev != seat) seats.set(prev, SeatInfo.open());
        });
        spectators.remove(uuid);
        seats.set(seat, SeatInfo.claimed(uuid));
        ownership.claimIfAbsent(uuid);
        matchRevision++;
        markChangedAndSynced();
        return true;
    }

    public void releaseSeat(int seat) {
        if (replay != null) return;
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
        if (driver != null || newPreset == null) return;
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
        if (driver != null || !preset.family().supportsThreePlayers()) return;
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
        if (replay != null) return;
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

        TheMahjongMatch match = effective.newMatch(matchLength, exchangeThreeTiles, jokersEnabled, bloodBattle).validate();
        List<SeatInfo> activeSeats = new ArrayList<>(match.playerCount());
        for (int i = 0; i < match.playerCount(); i++) activeSeats.add(seats.get(i));

        resetReturningSeats();
        spectatorParticipants.begin(activeSeats.stream().flatMap(info -> info.occupant().stream()).toList());
        this.randomSeed = seed;
        this.driver = new TheMahjongDriver(match, buildPlayersFromSeats(activeSeats, match.tileSet().tiles().stream().anyMatch(TheMahjongTile::redDora)), new java.security.SecureRandom());
        this.driver.setAnimationsEnabled(true);
        beginRecording(effectivePreset());
        this.driver.startMatch();
        startPlayerAnalysis();
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

        this.matchLength = MatchLength.fromMatch(fixedMatch);

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
        spectatorParticipants.begin(activeSeats.stream().flatMap(info -> info.occupant().stream()).toList());
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
        patched.remove("ReplayArchives");
        patched.remove("ReplayRecording");
        // Imported records are practice, never a second application of the source game's statistics.
        patched.remove("PlayerAnalysis");
        patched.remove("SpectatorMatchParticipants");
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
        replay=null; replayRecorder=null; appliedReplayRevision=-1; roundResultDisplay.clear();
        if (playerAnalysis != null) { playerAnalysis.tick(); playerAnalysis.close(); playerAnalysis = null; }
        clearWinPresentation();
        resultHandReveals.clear();
        winningHandDisplay = null;
        closeBotPlayers();
        this.driver = null;
        botsSuspended = false;
        returningSeats.clear();
        spectatorParticipants.clear();
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
        if (replay != null) return List.of();
        if (winEffectSession != null || exchanging() || driver == null || seat < 0 || seat >= driver.match().playerCount()
                || !(driver.playerAt(seat) instanceof MahjongTableHumanPlayer)) return List.of();
        if (ResultHandReveals.canReveal(driver)) {
            return com.riichimahjong.fabric.PublicTable.revealedSeats(this).contains(seat) ? List.of()
                    : List.of(new MahjongTableHumanPlayer.TableButton.ShowHand(driver.currentRound()));
        }
        return MahjongTableHumanPlayer.tableButtons(driver, seat, riichiPendingForSeat(seat));
    }

    public boolean showHand(ServerPlayer player, TheMahjongDriver expectedDriver, TheMahjongRound expectedRound) {
        if (replay != null) return false;
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
        if (replay != null) return;
        if (driver == null || winEffectSession != null || exchanging()) return;
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
        boolean exchangeWasComplete=exchangeCompleted();
        human.onCuteClick(key, driver, seat, player);
        captureExchange(exchangeWasComplete);
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
        if (replay != null) return;
        if (driver == null || winEffectSession != null || exchanging()) return;

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
        return playerCount == 4 && preset.family() != RuleSetPreset.Family.SICHUAN
                ? new KobalabBot(botDifficulty(seat), redFives) : new RandomThinkingBot();
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
        if (playerAnalysis != null) { playerAnalysis.tick(); playerAnalysis.close(); }
        finishWinEffect();
        if(driver!=null&&driver.rules().bloodBattle()&&ResultHandReveals.canReveal(driver)){
            deferredWinQueue.finishAll();completedWinSeats.addAll(deferredWinQueue.winners());
            for(var context:deferredWinQueue.contexts())if(WinEffects.resolve(context.effectId()).consumesSource()
                    &&context.source().slot().startsWith("river/"))consumedWinTileSlots.add(context.source().slot());
        }
        exchangePresentation=null;
        matchRevision++;
        closeBotPlayers();
        botsSuspended = true;
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        bindPlayerAnalysis();
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
        if (!(level instanceof ServerLevel sl)) return;
        if(sl.getGameTime() >= nextArchiveAttempt) flushReplayArchives();
        if(replay != null) {
            if(replay.tick()){applyReplayFrame();markChangedAndSynced();}
            return;
        }
        if (driver == null) return;
        if (playerAnalysis != null) playerAnalysis.tick();

        updateHandDisplay();
        if(exchangePresentation!=null){
            if(exchangePresentation.advance()){exchangePresentation=null;handDisplay.clear();markChangedAndSynced();}
            return;
        }
        prepareBloodBattlePlayback();

        // A presentation pauses the driver, including blood-battle play and human timeouts.
        // The rule win is already committed; removal/reload can safely finish presentation.
        if (winEffectSession != null) {
            if (winEffectSession.advance()) {
                finishWinEffect();
                if(driver.rules().bloodBattle())playNextDeferredWin();
                if (winEffectSession==null && driver.currentPhase() instanceof MatchPhase.RoundEnded re
                        && resultAnimStage == ResultAnimStage.NONE) startResultAnimation(sl,re);
                markChangedAndSynced();
            }
            return;
        }

        MatchPhase prevPhase = driver.currentPhase();
        TheMahjongMatch prevMatch = driver.match();
        int previousSichuanWins = driver.sichuanState()!=null?driver.sichuanState().wins().size():driver.bloodBattleState()!=null?driver.bloodBattleState().wins().size():0;
        long sichuanRevision = driver.sichuanState()==null ? -1 : driver.sichuanState().revision();
        long exchangeRevision = driver.exchangeState()==null ? -1 : driver.exchangeState().revision();
        long bloodBattleRevision = driver.bloodBattleState()==null ? -1 : driver.bloodBattleState().revision();

        for (int i = 0; i < driver.match().playerCount(); i++) {
            if (driver.playerAt(i) instanceof MahjongTableHumanPlayer human) {
                human.validateBeforeAdvance(sl);
            }
        }

        boolean animating = resultAnimStage != ResultAnimStage.NONE;
        boolean exchangeWasComplete=exchangeCompleted();
        if (!animating) {
            driver.advance(SECONDS_PER_TICK);
            if (playerAnalysis != null) playerAnalysis.tick();
            captureExchange(exchangeWasComplete);
            playAcceptedActionSounds(sl, prevMatch);
        }
        MatchPhase nextPhase = driver.currentPhase();
        if (!(prevPhase instanceof MatchPhase.RoundEnded) && nextPhase instanceof MatchPhase.RoundEnded) {
            winningHandDisplay = WinningHandDisplay.capture(driver.winningTile(), nextPhase);
        } else if (!(nextPhase instanceof MatchPhase.RoundEnded) && !(nextPhase instanceof MatchPhase.MatchEnded)) {
            winningHandDisplay = null;
        }
        updateHandDisplay();

        var effectContext=WinEffectTriggers.capture(driver,prevPhase,previousSichuanWins,winEffectId);
        recordWinPresentation(effectContext);
        if(nextPhase instanceof MatchPhase.RoundEnded){
            finalResultLabels=WinResultLabels.captureResults(driver);
            prepareBloodBattlePlayback();
        }

        boolean playerStateChanged = driver.sichuanState()!=null
                && driver.sichuanState().revision()!=sichuanRevision;
        playerStateChanged |= driver.exchangeState()!=null && driver.exchangeState().revision()!=exchangeRevision;
        playerStateChanged |= driver.bloodBattleState()!=null && driver.bloodBattleState().revision()!=bloodBattleRevision;
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

        if (nextPhase instanceof MatchPhase.RoundEnded re
                && resultAnimStage == ResultAnimStage.NONE && winEffectSession == null) {
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
        if (replay != null) return;
        if (driver == null || winEffectSession!=null || exchanging()) return;
        if (resultAnimStage != ResultAnimStage.AWAITING_ADVANCE) return;
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded)) {
            clearResultAnimState();
            markChangedAndSynced();
            return;
        }
        var next = com.riichimahjong.fabric.RoundProgression.next(driver);
        for(int seat:com.riichimahjong.fabric.PublicTable.revealedSeats(this))
            resultHandReveals.show(driver,driver.currentRound(),seat);
        clearResultAnimState();
        try {
            if (playerAnalysis != null) playerAnalysis.tick();
            roundResultDisplay.observe(driver.currentPhase());
            driver.advanceRound(next.renchan(), next.nextHonba());
            if (playerAnalysis != null) {
                if (driver.currentPhase() instanceof MatchPhase.MatchEnded) playerAnalysis.tick();
                else playerAnalysis.nextHand();
            }
            if (!(driver.currentPhase() instanceof MatchPhase.MatchEnded)) clearWinPresentation();
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
        replayArchives.write(out.child("ReplayArchives"));
        roundResultDisplay.write(out.child("RoundResultDisplay"));
        if (driver != null) spectatorParticipants.write(out);
        if (driver != null && replay == null && replayRecorder != null) out.store("ReplayRecording", CompoundTag.CODEC, replayRecorder.toNbt());
        if (playerAnalysis != null && driver != null) playerAnalysis.write(out.child("PlayerAnalysis"));
        out.putBoolean(NBT_LAST_POWERED, lastPowered);
        out.putString(NBT_PRESET, preset.name());
        matchLength.write(out);
        out.putBoolean("ExchangeThreeTiles", exchangeThreeTiles);
        out.putBoolean("JokersEnabled", jokersEnabled);
        out.putBoolean("BloodBattle", bloodBattle);
        out.putBoolean("SpectatorHandsVisible", spectatorHandsVisible);
        out.putString(NBT_BOT_DIFFICULTY, defaultBotDifficulty.name());
        out.putBoolean(NBT_MINT_TILES, mintTilesFromNothing);
        out.putBoolean(NBT_DELIVER_TO_MAIN_HAND, deliverToMainHand);
        out.putString("WinEffect",winEffectId);
        deferredWinQueue.write(out.child("DeferredWins"));
        WinResultLabels.write(out.child("WinResultLabels"),finalResultLabels);
        var completedToSave=new HashSet<>(completedWinPresentationSeats());
        if(winEffectSession!=null)winEffectSession.context().winners().forEach(w->completedToSave.add(w.seat()));
        if(driver!=null&&driver.rules().bloodBattle()&&ResultHandReveals.canReveal(driver))completedToSave.addAll(deferredWinQueue.winners());
        out.putIntArray("CompletedWinSeats",completedToSave.stream().mapToInt(Integer::intValue).sorted().toArray());
        out.putIntArray("FinalWinningSeats",finalWinningSeats.stream().mapToInt(Integer::intValue).sorted().toArray());
        var consumed=out.childrenList("ConsumedWinTiles");
        var finishedSlots=new HashSet<>(consumedWinTileSlots);
        if(winEffectSession!=null && winEffectSession.effect().consumesSource()
                && winEffectSession.context().source().slot().startsWith("river/"))
            finishedSlots.add(winEffectSession.context().source().slot());
        if(driver!=null&&driver.rules().bloodBattle()&&ResultHandReveals.canReveal(driver))
            for(var context:deferredWinQueue.contexts())if(WinEffects.resolve(context.effectId()).consumesSource()
                    &&context.source().slot().startsWith("river/"))finishedSlots.add(context.source().slot());
        finishedSlots.stream().sorted().forEach(slot->consumed.addChild().putString("Slot",slot));

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

        if (driver != null && replay == null) {
            DriverNbt.writeDriver(out.child(NBT_DRIVER), driver);
            resultHandReveals.write(out.child("ShownHands"), driver);
            if (winningHandDisplay != null && ResultHandReveals.canReveal(driver)) {
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

        replay=null; replayRecorder=null; appliedReplayRevision=-1; nextArchiveAttempt=0;
        replayArchives.read(in.childOrEmpty("ReplayArchives"));
        roundResultDisplay.read(in.childOrEmpty("RoundResultDisplay"));
        matchRevision++;
        if (playerAnalysis != null) { playerAnalysis.close(); playerAnalysis = null; }
        closeBotPlayers();
        botsSuspended = false;
        defaultBotDifficulty = BotDifficulty.parse(in.getString(NBT_BOT_DIFFICULTY).orElse(null));
        seatBotDifficulties.clear();

        lastPowered = in.getBooleanOr(NBT_LAST_POWERED, false);
        exchangeThreeTiles = in.getBooleanOr("ExchangeThreeTiles", false);
        jokersEnabled = in.getBooleanOr("JokersEnabled", false);
        bloodBattle = in.getBooleanOr("BloodBattle", false);
        spectatorHandsVisible = in.getBooleanOr("SpectatorHandsVisible", false);
        spectators.clear();
        spectatorParticipants.read(in);
        mintTilesFromNothing = in.getBooleanOr(NBT_MINT_TILES, true);
        deliverToMainHand = in.getBooleanOr(NBT_DELIVER_TO_MAIN_HAND, true);
        clearWinPresentation();
        winEffectId=WinEffects.resolve(in.getStringOr("WinEffect",WinEffects.DEFAULT)).id();
        deferredWinQueue.read(in.childOrEmpty("DeferredWins"));
        completedWinSeats.addAll(java.util.Arrays.stream(in.getIntArray("CompletedWinSeats").orElseGet(()->new int[0]))
                .filter(seat->seat>=0&&seat<4).boxed().toList());
        finalResultLabels=WinResultLabels.read(in.childOrEmpty("WinResultLabels"));
        finalWinningSeats=java.util.Arrays.stream(in.getIntArray("FinalWinningSeats").orElseGet(()->new int[0]))
                .filter(seat->seat>=0&&seat<4).boxed().collect(java.util.stream.Collectors.toUnmodifiableSet());
        for(var entry:in.childrenListOrEmpty("ConsumedWinTiles")) {
            String slot=entry.getStringOr("Slot","");
            if(slot.matches("river/[0-3]/[0-9]+"))consumedWinTileSlots.add(slot);
        }

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
        if (in.child(NBT_DRIVER).isPresent()
                && in.childOrEmpty(NBT_DRIVER).childOrEmpty("match").getBooleanOr("sichuan", false))
            preset = RuleSetPreset.SICHUAN_4P;
        if (preset.family() == RuleSetPreset.Family.SICHUAN) {
            padSeatsAtLeast(DEFAULT_SEAT_COUNT);
            for (int seat=0; seat<DEFAULT_SEAT_COUNT; seat++)
                if (!seats.get(seat).enabled()) seats.set(seat, SeatInfo.open());
        }
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
            if (ResultHandReveals.canReveal(driver)) {
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

            // Older active tables have no participant history. Restore identities still known to the table.
            returningSeats.snapshot().keySet().forEach(spectatorParticipants::remember);
            seats.stream().flatMap(info -> info.occupant().stream()).forEach(spectatorParticipants::remember);

            for (ValueInput entry : in.childrenListOrEmpty(NBT_HUMAN_PLAYERS)) {
                int seat = entry.getIntOr("Seat", 0);
                if (seat >= 0 && seat < driver.match().playerCount()
                        && driver.playerAt(seat) instanceof MahjongTableHumanPlayer h) {
                    h.readNbt(entry);
                }
            }
        }
        matchLength = MatchLength.read(in, driver == null ? null : driver.match());
        if (driver != null) {
            if (driver.match().ruleSet().sichuan()) preset = RuleSetPreset.SICHUAN_4P;
            else if(driver.match().ruleSet().competitiveProfile()==com.themahjong.competition.CompetitiveRules.Profile.JPML_A)
                preset=RuleSetPreset.JPML_4P;
            else if(driver.match().ruleSet().competitiveProfile()==com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE)
                preset=RuleSetPreset.M_LEAGUE_4P;
            exchangeThreeTiles = driver.match().ruleSet().exchangeThree();
            jokersEnabled = driver.match().ruleSet().jokersEnabled();
            bloodBattle = driver.match().ruleSet().bloodBattle();
            if(driver.rules().bloodBattle()){
                WinEffectTriggers.history(driver,winEffectId).forEach(deferredWinQueue::add);
                if(ResultHandReveals.canReveal(driver)){
                    deferredWinQueue.finishAll();completedWinSeats.addAll(deferredWinQueue.winners());winPlaybackPrepared=true;
                    for(var context:deferredWinQueue.contexts())if(WinEffects.resolve(context.effectId()).consumesSource()
                            &&context.source().slot().startsWith("river/"))consumedWinTileSlots.add(context.source().slot());
                }else completedWinSeats.clear();
            }else if(ResultHandReveals.canReveal(driver))completedWinSeats.addAll(finalWinningSeats);
            if(finalResultLabels.isEmpty()&&ResultHandReveals.canReveal(driver))finalResultLabels=WinResultLabels.captureResults(driver);
            playerAnalysis = MatchAnalysisSession.read(in.childOrEmpty("PlayerAnalysis"));
            bindPlayerAnalysis();
            try {
                replayRecorder=in.read("ReplayRecording", CompoundTag.CODEC).map(ReplayRecorder::fromNbt).orElse(null);
                if (replayRecorder != null) replayRecorder.participantUuids().forEach(spectatorParticipants::remember);
                attachRecordingObserver();
            } catch (RuntimeException error) {
                replayRecorder=null;
                System.getLogger("riichi_mahjong/replay").log(System.Logger.Level.WARNING, "Cannot resume replay recording", error);
            }
        }
    }
}
