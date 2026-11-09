// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable;

import com.riichimahjong.mahjongcore.MahjongRoundResolvedEvent;
import com.riichimahjong.mahjongcore.MahjongWinEffects;
import com.riichimahjong.cuterenderer.CuteClickHandler;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.riichimahjong.fabric.HandDisplayState;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongMatch;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.driver.bots.StupidActiveBot;

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
    private static final String NBT_MINT_TILES = "MintTilesFromNothing";
    private static final String NBT_DELIVER_TO_MAIN_HAND = "DeliverToMainHand";
    private static final String NBT_HUMAN_PLAYERS = "HumanPlayers";
    private static final String NBT_RESULT_ANIM_STAGE = "ResultAnimStage";
    private static final String NBT_RESULT_ANIM_YAKU_IDX = "ResultAnimYakuIdx";

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

    private boolean lastPowered;
    private List<SeatInfo> seats = defaultSeats();
    private final ReturningSeats returningSeats = new ReturningSeats();
    private final Map<UUID,Long> privateClicks = new HashMap<>();
    private final HandDisplayState handDisplay = new HandDisplayState();
    @Nullable private TheMahjongDriver handDisplayDriver;
    private RuleSetPreset preset = RuleSetPreset.MAHJONG_SOUL_4P;
    private long randomSeed;

    private boolean mintTilesFromNothing = true;

    private boolean deliverToMainHand = true;

    @Nullable
    private TheMahjongDriver driver;

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

    private void updateHandDisplay() {
        if (handDisplayDriver != driver) { handDisplay.clear(); handDisplayDriver = driver; }
        if (driver != null && level != null) driver.match().currentRound().ifPresent(round -> handDisplay.update(round, level.getGameTime()));
    }

    public List<TheMahjongTile> handDisplayOrder(int seat) {
        updateHandDisplay();
        return handDisplay.hand(seat);
    }

    public boolean hasSeparatedDrawnTile(int seat) {
        updateHandDisplay();
        return handDisplay.separated(seat);
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

    public RuleSetPreset effectivePreset() {
        if (preset == RuleSetPreset.MAHJONG_SOUL_4P
                && seats.size() >= 4 && !seats.get(3).enabled()) {
            return RuleSetPreset.MAHJONG_SOUL_SANMA_3P;
        }
        return preset;
    }

    public OptionalInt returningSeat(UUID uuid) { return returningSeats.seat(uuid); }

    public boolean canClaimSeat(int seat, UUID uuid) {
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
            driver.replacePlayer(seat,buildPlayer(SeatInfo.claimed(uuid)));
        }
        seatOfPlayer(uuid).ifPresent(prev -> {
            if (prev != seat) seats.set(prev, SeatInfo.open());
        });
        seats.set(seat, SeatInfo.claimed(uuid));
        markChangedAndSynced();
        return true;
    }

    public void releaseSeat(int seat) {
        if (seat < 0 || seat >= seats.size()) return;
        SeatInfo info = seats.get(seat);
        if (info.occupant().isPresent()) {
            if (driver != null && seat < driver.match().playerCount()) {
                returningSeats.remember(info.occupant().orElseThrow(),seat);
                driver.replacePlayer(seat, StupidActiveBot.humanLike());
            }
            seats.set(seat, SeatInfo.open());
            markChangedAndSynced();
        }
    }

    public void selectPreset(RuleSetPreset newPreset) {
        if (driver != null) return;
        this.preset = newPreset;
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
        this.driver = new TheMahjongDriver(match, buildPlayersFromSeats(activeSeats), new java.security.SecureRandom());
        this.driver.setAnimationsEnabled(true);
        this.driver.startMatch();
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
                fixedMatch, buildPlayersFromSeats(activeSeats), new Random(seed), snap);
        this.driver.setAnimationsEnabled(true);
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
        if (driver == null) return;
        this.driver = null;
        returningSeats.clear();
        privateClicks.clear();
        this.randomSeed = 0L;
        markChangedAndSynced();
    }

    @Override
    public void onCuteClick(ServerPlayer player, InteractKey key) {
        if (driver == null) return;
        int seat = seatOfOccupant(player.getUUID());
        if (seat < 0) return;
        if (!(driver.playerAt(seat) instanceof MahjongTableHumanPlayer human)) return;
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty()) return;
        if (level != null) lastCuteClickGameTime = level.getGameTime();
        if (key instanceof InteractKey.SeatSlot slot && slot.area() == InteractKey.SeatSlot.AREA_HAND) {
            updateHandDisplay();
            int canonical = handDisplay.canonicalIndex(seat, slot.index(), roundOpt.orElseThrow());
            if (canonical < 0) return;
            key = new InteractKey.SeatSlot(slot.seat(), slot.area(), (short)canonical);
        }
        human.onCuteClick(key, driver, seat, player);

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
        returningSeats.clear();privateClicks.clear();
        for (int seat=0;seat<seats.size();seat++) {
            var info=seats.get(seat);
            if (info.enabled()&&info.occupant().isPresent()) returningSeats.remember(info.occupant().orElseThrow(),seat);
        }
    }

    public void onTableRightClick(ServerPlayer player) {
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

    private static List<MahjongPlayerInterface> buildPlayersFromSeats(List<SeatInfo> seats) {
        List<MahjongPlayerInterface> out = new ArrayList<>(seats.size());
        for (SeatInfo info : seats) out.add(buildPlayer(info));
        return out;
    }

    private static MahjongPlayerInterface buildPlayer(SeatInfo info) {
        if (info.occupant().isPresent()) {
            MahjongTableHumanPlayer p = new MahjongTableHumanPlayer();
            p.setOccupant(info.occupant().get());
            p.setAutoDrawAfterSeconds(0.0);
            return p;
        }
        return StupidActiveBot.humanLike();
    }

    private void serverTick() {
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
        }
        MatchPhase nextPhase = driver.currentPhase();
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
        if (driver == null) return;
        if (resultAnimStage != ResultAnimStage.AWAITING_ADVANCE) return;
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) {
            clearResultAnimState();
            markChangedAndSynced();
            return;
        }
        boolean renchan = computeRenchan(re);
        int nextHonba = 0;
        clearResultAnimState();
        try {
            driver.advanceRound(renchan, nextHonba);
        } catch (Exception ignored) {
        }
        markChangedAndSynced();
    }

    private boolean computeRenchan(MatchPhase.RoundEnded re) {
        var roundOpt = driver.match().currentRound();
        if (roundOpt.isEmpty() || re.winResults().isEmpty()) return false;
        var primary = re.winResults().get(0);
        int winnerSeat = -1;
        for (int i = 0; i < primary.pointDeltas().size(); i++) {
            if (primary.pointDeltas().get(i) > 0) { winnerSeat = i; break; }
        }
        return winnerSeat >= 0 && winnerSeat == roundOpt.get().dealerSeat();
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

        out.putBoolean(NBT_LAST_POWERED, lastPowered);
        out.putString(NBT_PRESET, preset.name());
        out.putBoolean(NBT_MINT_TILES, mintTilesFromNothing);
        out.putBoolean(NBT_DELIVER_TO_MAIN_HAND, deliverToMainHand);

        out.putString(NBT_RESULT_ANIM_STAGE, resultAnimStage.name());
        out.putInt(NBT_RESULT_ANIM_YAKU_IDX, resultAnimYakuIdx);

        ValueOutput.ValueOutputList seatsOut = out.childrenList(NBT_SEATS);
        for (SeatInfo info : seats) {
            ValueOutput entry = seatsOut.addChild();
            entry.putBoolean(NBT_SEAT_ENABLED, info.enabled());
            info.occupant().ifPresent(uuid -> entry.store(NBT_SEAT_OCCUPANT, UUIDUtil.CODEC, uuid));
        }

        ValueOutput.ValueOutputList inventoryOut = out.childrenList(NBT_INVENTORY);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.get(i);
            if (stack.isEmpty()) continue;
            ValueOutput entry = inventoryOut.addChild();
            entry.putInt(NBT_INVENTORY_SLOT, i);
            entry.store(NBT_INVENTORY_ITEM, ItemStack.CODEC, stack);
        }

        if (driver != null) {
            DriverNbt.writeDriver(out.child(NBT_DRIVER), driver);
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
                loaded.add(new SeatInfo(enabled, enabled ? Optional.ofNullable(uuid) : Optional.empty()));
            }
            seats = loaded;
        });
        if (seats.isEmpty()) seats = defaultSeats();

        inventory.clear();
        for (ValueInput entry : in.childrenListOrEmpty(NBT_INVENTORY)) {
            int slot = entry.getIntOr(NBT_INVENTORY_SLOT, 0);
            if (slot < 0 || slot >= INVENTORY_SIZE) continue;
            entry.read(NBT_INVENTORY_ITEM, ItemStack.CODEC).ifPresent(stack -> inventory.set(slot, stack));
        }

        this.driver = null;
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
            this.driver = DriverNbt.readDriver(
                    driverIn.get(),
                    buildPlayersFromSeats(activeSeats),
                    new java.security.SecureRandom());
            this.driver.setAnimationsEnabled(true);

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
        }
    }
}
