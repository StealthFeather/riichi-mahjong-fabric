// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.yakugenerator;

import com.mojang.logging.LogUtils;
import com.riichimahjong.mahjongcore.MahjongRoundResolvedEvent;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.mahjongcore.MahjongWinEffects;
import com.riichimahjong.registry.ModBlockEntities;
import com.riichimahjong.registry.ModSounds;
import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.TheMahjongPlayer.RiichiState;
import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.HandShape;
import com.themahjong.yaku.NonYakuman;
import com.themahjong.yaku.ShantenCalculator;
import com.themahjong.yaku.WinCalculator;
import com.themahjong.yaku.WinContext;
import com.themahjong.yaku.WinResult;
import com.themahjong.yaku.Yakuman;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class YakuGeneratorBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int TILE_SLOTS = 14;
    public static final int TILE_KIND_COUNT = 34;
    public static final int TILE_POOL_SIZE = 136;
    public static final int MAX_ENERGY = 100_000_000;
    public static final int HAN_FOR_YAKUMAN = 13;
    private static final double NO_YAKU_EFFECT_RADIUS = 4.5;

    private static final int ENERGY_PAIR = 1;
    private static final int ENERGY_SEQUENCE = 2;
    private static final int ENERGY_TRIPLET = 4;
    private static final int ENERGY_KAN = 8;

    private static final String TAG_TILES = "YgTiles";
    private static final String TAG_ENERGY = "YgEnergy";
    private static final String TAG_GEN_TICKS = "YgTicks";
    private static final String TAG_GEN_RFPT = "YgRfpt";
    private static final String TAG_LAST_HAN = "YgLastHan";
    private static final String TAG_LAST_RFPT = "YgLastRfpt";
    private static final String TAG_LAST_DURATION = "YgLastDur";
    private static final String TAG_LAST_YAKUMAN = "YgLastYakuman";
    private static final String TAG_REMAINING = "YgRemain";
    private static final String TAG_DRAWS_USED = "YgDrawsUsed";
    private static final String TAG_AUTO_SORT = "YgAutoSort";
    private static final String TAG_REMAIN_TILE = "tile";
    private static final String TAG_REMAIN_COUNT = "count";

    private static final List<TheMahjongTile> STANDARD_DECK_TILES = buildStandardDeckTiles();

    private static List<TheMahjongTile> buildStandardDeckTiles() {
        List<TheMahjongTile> out = new ArrayList<>(TILE_KIND_COUNT);
        for (TheMahjongTile.Suit suit : TheMahjongTile.Suit.values()) {
            for (int rank = 1; rank <= suit.maxRank(); rank++) {
                out.add(new TheMahjongTile(suit, rank, false));
            }
        }
        return out;
    }

    private final TheMahjongTile[] tiles = new TheMahjongTile[TILE_SLOTS];

    private final Map<TheMahjongTile, Integer> remainingCopies = new HashMap<>();
    private int drawsUsed;

    private int energyStored;
    private int generationTicksRemaining;
    private int currentRfPerTick;
    private int lastHan;
    private int lastRfPerTick;
    private int lastDurationTicks;
    private boolean lastYakuman;
    private boolean autoSortOnReroll = true;

    public Object loaderEnergyPushState;

    private int cachedComparatorSignal = -1;

    public YakuGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.YAKU_GENERATOR_BLOCK_ENTITY.get(), pos, state);
        setupNewRound(RandomSource.create());
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide() && !isRoundDataValid()) {
            setupNewRound(level.getRandom());
            setChanged();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, YakuGeneratorBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        boolean changed = false;
        if (generationTicksRemaining > 0 && currentRfPerTick > 0) {
            int accepted = addEnergy(currentRfPerTick);
            if (accepted > 0) {
                changed = true;
            }
            generationTicksRemaining--;
            changed = true;
            if (generationTicksRemaining <= 0) {
                generationTicksRemaining = 0;
                currentRfPerTick = 0;
            }
        }
        if (energyStored > 0 && EnergyPushPlatform.tickPush(this) > 0) {
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    public void resetMachine() {
        if (level == null || level.isClientSide()) {
            return;
        }
        setupNewRound(level.getRandom());
        generationTicksRemaining = 0;
        currentRfPerTick = 0;
        lastHan = 0;
        lastRfPerTick = 0;
        lastDurationTicks = 0;
        lastYakuman = false;
        setChanged();
    }

    public void rerollSlot(int slot) {
        if (level == null || level.isClientSide() || slot < 0 || slot >= getSlotCount()) {
            return;
        }
        if (isRoundExhausted()) {
            setupNewRound(level.getRandom());
            setChanged();
            return;
        }
        TheMahjongTile draw = drawTileFromRemaining(level.getRandom());
        if (draw == null) {
            setupNewRound(level.getRandom());
            setChanged();
            return;
        }
        tiles[slot] = draw;
        drawsUsed++;
        if (autoSortOnReroll) {
            sortActiveSlots();
        }
        if (level instanceof ServerLevel sl) {
            playTilePlaceSound(sl);
        }
        invalidateComparatorSignal();
        setChanged();
    }

    public void tsumo(ServerPlayer actor) {
        if (level == null || level.isClientSide() || actor == null) {
            return;
        }
        applyTsumoResult(actor, evaluateCurrentHand(), true);
    }

    public void tsumoAutomated() {
        if (level == null || level.isClientSide()) {
            return;
        }
        applyTsumoResult(null, evaluateCurrentHand(), true);
    }

    public boolean discardForClicker(int accuracyPct) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        int slotCount = getSlotCount();
        if (slotCount <= 0) return false;
        int chosen;
        if (accuracyPct >= 100 || level.getRandom().nextInt(100) < accuracyPct) {
            chosen = findBestDiscardSlot();
        } else {
            chosen = level.getRandom().nextInt(slotCount);
        }
        if (chosen < 0) chosen = 0;
        rerollSlot(chosen);
        return true;
    }

    public int getComparatorSignal() {
        if (cachedComparatorSignal < 0) {
            cachedComparatorSignal = computeComparatorSignal();
        }
        return cachedComparatorSignal;
    }

    private int computeComparatorSignal() {
        if (level == null) return 0;
        TsumoResult result;
        try {
            result = evaluateCurrentHand();
        } catch (RuntimeException ignored) {
            return 0;
        }
        if (result.han <= 0) return 0;
        return Math.max(1, Math.min(15, result.han));
    }

    private void invalidateComparatorSignal() {
        cachedComparatorSignal = -1;
        if (level != null && !level.isClientSide()) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private int findBestDiscardSlot() {
        YakuGeneratorBlock.Tier tier = resolveTier();
        int slotCount = tier.slotCount();
        boolean useStandard = (tier == YakuGeneratorBlock.Tier.TIER_3);
        int target = switch (tier) {
            case TIER_1 -> 1;
            case TIER_2 -> 3;
            case TIER_3 -> 0;
        };

        int[] counts = new int[TILE_KIND_COUNT];
        for (int i = 0; i < slotCount; i++) {
            TheMahjongTile t = tiles[i];
            if (t == null) continue;
            int code = MahjongTileItems.codeForTile(t);
            if (code >= 0 && code < TILE_KIND_COUNT) counts[code]++;
        }

        int bestSlot = 0;
        int bestShanten = Integer.MAX_VALUE;
        int bestTiebreak = Integer.MIN_VALUE;
        for (int i = 0; i < slotCount; i++) {
            TheMahjongTile t = tiles[i];
            if (t == null) continue;
            int code = MahjongTileItems.codeForTile(t);
            counts[code]--;
            int s;
            if (useStandard) {
                s = ShantenCalculator.shanten(counts);
            } else {
                ComboBest best = bestCombos(counts, 0);
                int combos = best == null ? 0 : best.combos;
                s = combos >= target ? -1 : (target - combos);
            }
            counts[code]++;
            int tieScore = uselessnessScore(t, counts, code);
            if (s < bestShanten || (s == bestShanten && tieScore > bestTiebreak)) {
                bestSlot = i;
                bestShanten = s;
                bestTiebreak = tieScore;
            }
        }
        return bestSlot;
    }

    private static int uselessnessScore(TheMahjongTile t, int[] counts, int selfCode) {
        int score = 0;
        if (t.honor()) score += 4;
        if (t.terminal()) score += 2;

        if (counts[selfCode] <= 1) score += 2;
        if (t.suit().isNumber()) {
            int suitBase = selfCode - (t.rank() - 1);
            int rank = t.rank();
            boolean nearby = false;
            for (int dr = -2; dr <= 2; dr++) {
                if (dr == 0) continue;
                int r2 = rank + dr;
                if (r2 >= 1 && r2 <= 9) {
                    int idx = suitBase + (r2 - 1);
                    if (counts[idx] > 0) { nearby = true; break; }
                }
            }
            if (!nearby) score += 3;
        }
        return score;
    }

    public void debugTriggerOutcome(ServerPlayer actor, int outcomeIndex) {
        if (level == null || level.isClientSide() || actor == null) {
            return;
        }
        TsumoResult result = switch (outcomeIndex) {
            case 0 -> TsumoResult.noWin();
            case 1 -> new TsumoResult(3, false, List.of("RIICHI"), List.of());
            case 2 -> new TsumoResult(5, false, List.of("HONITSU"), List.of());
            case 3 -> new TsumoResult(7, false, List.of("CHINITSU"), List.of());
            case 4 -> new TsumoResult(9, false, List.of("SANANKO"), List.of());
            case 5 -> new TsumoResult(11, false, List.of("JUNCHAN"), List.of());
            case 6 -> new TsumoResult(HAN_FOR_YAKUMAN, true, List.of("DAISANGEN"), List.of("DAISANGEN"));
            default -> null;
        };
        if (result == null) {
            return;
        }
        applyTsumoResult(actor, result, false);
    }

    private void applyTsumoResult(@Nullable ServerPlayer actor, TsumoResult result, boolean setupNewRoundAfterResolution) {
        lastHan = result.han;
        lastYakuman = result.yakuman;
        if (level instanceof ServerLevel sl) {
            MahjongRoundResolvedEvent.post(sl, worldPosition, lastHan);
        }
        if (result.han <= 0) {
            triggerNoYakuBacklash(actor);
            generationTicksRemaining = 0;
            currentRfPerTick = 0;
            lastRfPerTick = 0;
            lastDurationTicks = 0;
            if (setupNewRoundAfterResolution || isRoundExhausted()) {
                setupNewRound(level.getRandom());
            }
            setChanged();
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            String name = actor != null ? actor.getName().getString() : "Yaku Generator";
            MahjongWinEffects.playWinEffects(
                    serverLevel,
                    actor,
                    name,
                    result.han(),
                    result.yakuman(),
                    result.yakuNames(),
                    result.yakumanNames(),
                    actor != null);
        }
        Reward reward = rewardForHan(result.han, result.yakuman);
        currentRfPerTick = reward.rfPerTick;
        generationTicksRemaining = reward.durationTicks;
        lastRfPerTick = reward.rfPerTick;
        lastDurationTicks = reward.durationTicks;
        if (setupNewRoundAfterResolution) {
            setupNewRound(level.getRandom());
        }
        setChanged();
    }

    private void triggerNoYakuBacklash(@Nullable ServerPlayer actor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;
        serverLevel.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        applyNoYakuBlastDamageAndKnockback(serverLevel, actor, new Vec3(x, y, z));
        if (serverLevel.getServer() != null) {
            String name = actor != null ? actor.getName().getString() : "A Yaku Generator";
            serverLevel.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(
                            Component.literal(name + " has no Yaku, what a loser"),
                            false);
        }
    }

    private void applyNoYakuBlastDamageAndKnockback(ServerLevel serverLevel, @Nullable ServerPlayer actor, Vec3 center) {
        AABB area = new AABB(center, center).inflate(NO_YAKU_EFFECT_RADIUS);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator())) {
            double dx = entity.getX() - center.x;
            double dy = entity.getY(0.5) - center.y;
            double dz = entity.getZ() - center.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > NO_YAKU_EFFECT_RADIUS) {
                continue;
            }
            double strength = 1.0 - (distance / NO_YAKU_EFFECT_RADIUS);
            float damage = (float) (8.0 + (strength * 28.0));

            entity.setInvulnerableTime(0);
            entity.hurt(serverLevel.damageSources().generic(), damage);

            Vec3 pushDir;
            if (distance < 1.0e-4) {
                pushDir = new Vec3(0.0, 1.0, 0.0);
            } else {
                pushDir = new Vec3(dx / distance, 0.2, dz / distance).normalize();
            }
            double horizontalPush = 2.6 * strength + 0.6;
            double verticalPush = 0.7 * strength + 0.25;
            entity.push(pushDir.x * horizontalPush, verticalPush, pushDir.z * horizontalPush);
            
        }
    }

    public int getTileCode(int slot) {
        if (slot < 0 || slot >= TILE_SLOTS) {
            return 0;
        }
        TheMahjongTile t = tiles[slot];
        return t == null ? 0 : MahjongTileItems.codeForTile(t);
    }

    public int getEnergyStored() {
        return energyStored;
    }
    public void fabricRestoreEnergy(int amount){energyStored=amount;setChanged();}

    public int getGenerationTicksRemaining() {
        return generationTicksRemaining;
    }

    public int getCurrentRfPerTick() {
        return currentRfPerTick;
    }

    public int getLastHan() {
        return lastHan;
    }

    public int getLastRfPerTick() {
        return lastRfPerTick;
    }

    public int getLastDurationTicks() {
        return lastDurationTicks;
    }

    public boolean isLastYakuman() {
        return lastYakuman;
    }

    public int getDrawLimit() {
        return resolveTier().drawLimit();
    }

    public int getDrawsUsed() {
        return drawsUsed;
    }

    public int getDrawsRemaining() {
        return Math.max(0, getDrawLimit() - drawsUsed);
    }

    public int getTierIndex() {
        return resolveTier().index();
    }

    public int getSlotCount() {
        return resolveTier().slotCount();
    }

    public boolean isAutoSortOnReroll() {
        return autoSortOnReroll;
    }

    public void toggleAutoSortOnReroll() {
        if (level == null || level.isClientSide()) {
            return;
        }
        autoSortOnReroll = !autoSortOnReroll;
        if (autoSortOnReroll) {
            sortActiveSlots();
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ValueOutput.ValueOutputList tileList = out.childrenList(TAG_TILES);
        for (TheMahjongTile tile : tiles) {
            ValueOutput entry = tileList.addChild();
            if (tile != null) {
                MatchNbt.writeTile(entry, tile);
            }
        }
        ValueOutput.ValueOutputList remainList = out.childrenList(TAG_REMAINING);
        for (var entry : remainingCopies.entrySet()) {
            ValueOutput e = remainList.addChild();
            MatchNbt.writeTile(e.child(TAG_REMAIN_TILE), entry.getKey());
            e.putInt(TAG_REMAIN_COUNT, entry.getValue());
        }
        out.putInt(TAG_DRAWS_USED, drawsUsed);
        out.putInt(TAG_ENERGY, energyStored);
        out.putInt(TAG_GEN_TICKS, generationTicksRemaining);
        out.putInt(TAG_GEN_RFPT, currentRfPerTick);
        out.putInt(TAG_LAST_HAN, lastHan);
        out.putInt(TAG_LAST_RFPT, lastRfPerTick);
        out.putInt(TAG_LAST_DURATION, lastDurationTicks);
        out.putBoolean(TAG_LAST_YAKUMAN, lastYakuman);
        out.putBoolean(TAG_AUTO_SORT, autoSortOnReroll);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        Arrays.fill(tiles, null);
        int slot = 0;
        for (ValueInput entry : in.childrenListOrEmpty(TAG_TILES)) {
            if (slot >= TILE_SLOTS) break;
            tiles[slot++] = entry.getString("suit").isPresent() ? MatchNbt.readTile(entry) : null;
        }
        remainingCopies.clear();
        for (ValueInput entry : in.childrenListOrEmpty(TAG_REMAINING)) {
            TheMahjongTile tile = MatchNbt.readTile(entry.childOrEmpty(TAG_REMAIN_TILE));
            remainingCopies.put(tile, entry.getIntOr(TAG_REMAIN_COUNT, 0));
        }
        drawsUsed = Math.max(0, in.getIntOr(TAG_DRAWS_USED, 0));
        energyStored = clamp(in.getIntOr(TAG_ENERGY, 0), 0, MAX_ENERGY);
        generationTicksRemaining = Math.max(0, in.getIntOr(TAG_GEN_TICKS, 0));
        currentRfPerTick = Math.max(0, in.getIntOr(TAG_GEN_RFPT, 0));
        lastHan = Math.max(0, in.getIntOr(TAG_LAST_HAN, 0));
        lastRfPerTick = Math.max(0, in.getIntOr(TAG_LAST_RFPT, 0));
        lastDurationTicks = Math.max(0, in.getIntOr(TAG_LAST_DURATION, 0));
        lastYakuman = in.getBooleanOr(TAG_LAST_YAKUMAN, false);
        autoSortOnReroll = in.getBooleanOr(TAG_AUTO_SORT, true);
        if (!isRoundDataValid()) {
            RandomSource random = level != null ? level.getRandom() : RandomSource.create();
            setupNewRound(random);
        }
        invalidateComparatorSignal();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    private int addEnergy(int amount) {
        int accepted = Math.max(0, Math.min(amount, MAX_ENERGY - energyStored));
        if (accepted > 0) {
            energyStored += accepted;
        }
        return accepted;
    }

    public int extractEnergy(int maxAmount, boolean simulate) {
        int drained = Math.max(0, Math.min(maxAmount, energyStored));
        if (!simulate && drained > 0) {
            energyStored -= drained;
            setChanged();
        }
        return drained;
    }

    public int getMaxEnergyStored() {
        return MAX_ENERGY;
    }

    private void setupNewRound(RandomSource random) {
        remainingCopies.clear();
        for (TheMahjongTile t : STANDARD_DECK_TILES) {
            remainingCopies.put(t, 4);
        }
        Arrays.fill(tiles, null);
        int slotCount = getSlotCount();
        for (int i = 0; i < slotCount; i++) {
            tiles[i] = drawTileFromRemaining(random);
        }
        drawsUsed = 0;
        sortActiveSlots();
        invalidateComparatorSignal();
    }

    private void sortActiveSlots() {
        int slotCount = getSlotCount();
        if (slotCount > 1) {
            Arrays.sort(tiles, 0, slotCount, java.util.Comparator.nullsLast(TheMahjongTile.DISPLAY_ORDER));
        }
    }

    @Nullable
    private TheMahjongTile drawTileFromRemaining(RandomSource random) {
        int total = remainingWallTiles();
        if (total <= 0) {
            return null;
        }
        int pick = random.nextInt(total);
        for (TheMahjongTile t : STANDARD_DECK_TILES) {
            int count = remainingCopies.getOrDefault(t, 0);
            if (count <= 0) continue;
            if (pick < count) {
                remainingCopies.put(t, count - 1);
                return t;
            }
            pick -= count;
        }
        return null;
    }

    private int remainingWallTiles() {
        int total = 0;
        for (int count : remainingCopies.values()) {
            total += Math.max(0, count);
        }
        return total;
    }

    private boolean isRoundExhausted() {
        return drawsUsed >= getDrawLimit() || remainingWallTiles() <= 0;
    }

    private boolean isRoundDataValid() {
        if (drawsUsed < 0 || drawsUsed > getDrawLimit()) {
            return false;
        }
        int slotCount = getSlotCount();
        for (int i = 0; i < slotCount; i++) {
            if (tiles[i] == null) return false;
        }
        for (int count : remainingCopies.values()) {
            if (count < 0 || count > 4) return false;
        }
        return true;
    }

    private void playTilePlaceSound(ServerLevel sl) {
        float pitch = 0.95f + (sl.getRandom().nextFloat() * 0.1f);
        sl.playSound(
                null,
                worldPosition,
                ModSounds.TILE_PLACE_SOUND.get(),
                SoundSource.BLOCKS,
                0.65f,
                pitch);
    }

    private YakuGeneratorBlock.Tier resolveTier() {
        if (getBlockState().getBlock() instanceof YakuGeneratorBlock block) {
            return block.tier();
        }
        return YakuGeneratorBlock.Tier.TIER_2;
    }

    private TsumoResult evaluateCurrentHand() {
        YakuGeneratorBlock.Tier tier = resolveTier();
        return switch (tier) {
            case TIER_1 -> evaluateCombos(tier.slotCount(), 1, 4);
            case TIER_2 -> evaluateCombos(tier.slotCount(), 3, 7);
            case TIER_3 -> evaluateMahjongHand();
        };
    }

    private TsumoResult evaluateCombos(int slotCount, int minCombos, int maxHan) {
        int[] counts = new int[TILE_KIND_COUNT];
        for (int i = 0; i < slotCount; i++) {
            TheMahjongTile t = tiles[i];
            if (t == null) continue;
            int code = MahjongTileItems.codeForTile(t);
            if (code >= 0 && code < TILE_KIND_COUNT) {
                counts[code]++;
            }
        }
        ComboBest best = bestCombos(counts, 0);
        if (best == null || best.combos < minCombos) {
            return TsumoResult.noWin();
        }
        int han = clamp(best.energy, 1, maxHan);
        return new TsumoResult(han, false, best.names, List.of());
    }

    private TsumoResult evaluateMahjongHand() {
        List<TheMahjongTile> hand = new ArrayList<>(TILE_SLOTS);
        for (int i = 0; i < TILE_SLOTS; i++) {
            TheMahjongTile t = tiles[i];
            if (t == null) {
                return TsumoResult.noWin();
            }
            hand.add(t);
        }
        try {
            Set<TheMahjongTile> distinct = new HashSet<>(hand);
            TheMahjongRuleSet rules = TheMahjongRuleSet.wrc();
            WinResult best = null;
            for (TheMahjongTile winTile : distinct) {
                List<HandShape> decomp = HandShape.decomposeForWin(hand, List.of(), winTile);
                if (decomp.isEmpty()) {
                    continue;
                }
                WinContext ctx = WinContext.tsumo(
                        false, false, RiichiState.NONE, false,
                        winTile, TheMahjongTile.Wind.EAST, TheMahjongTile.Wind.EAST,
                        false, false);
                WinResult r = WinCalculator.calculateBest(
                        decomp, ctx, hand, List.of(), List.of(),
                        4, 0, 0, 0, 0, 0, rules);
                if (r.yaku().isEmpty() && r.yakuman().isEmpty()) {
                    continue;
                }
                if (best == null || compareWinResults(r, best) > 0) {
                    best = r;
                }
            }
            if (best == null) {
                return TsumoResult.noWin();
            }
            boolean yakuman = !best.yakuman().isEmpty();
            if (yakuman) {
                List<String> names = best.yakuman().stream().map(Yakuman::name).toList();
                return new TsumoResult(HAN_FOR_YAKUMAN, true, names, names);
            }
            int han = Math.max(1, Math.min(HAN_FOR_YAKUMAN - 1, best.han()));
            List<String> yakuNames = best.yaku().stream().map(NonYakuman::name).toList();
            return new TsumoResult(han, false, yakuNames, List.of());
        } catch (RuntimeException ignored) {
            return TsumoResult.noWin();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable unexpected) {
            LOGGER.warn("Yaku generator tsumo evaluation failed; treating as no-win hand", unexpected);
            return TsumoResult.noWin();
        }
    }

    private static int compareWinResults(WinResult a, WinResult b) {
        boolean aYakuman = !a.yakuman().isEmpty();
        boolean bYakuman = !b.yakuman().isEmpty();
        if (aYakuman != bYakuman) {
            return aYakuman ? 1 : -1;
        }
        return Integer.compare(a.han(), b.han());
    }

    private Reward rewardForHan(int han, boolean yakuman) {
        if (yakuman || han >= HAN_FOR_YAKUMAN) {
            return new Reward(100_000, 20 * 60);
        }
        if (han <= 0) {
            return new Reward(0, 0);
        }
        double progress = (double) (han - 1) / (double) (HAN_FOR_YAKUMAN - 1);
        int rfPerTick = (int) Math.round(10.0 * Math.pow(10_000.0, progress));
        int durationTicks = (int) Math.round(20.0 * (10.0 + (50.0 * progress)));
        return new Reward(rfPerTick, durationTicks);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ComboBest bestCombos(int[] counts, int idx) {
        while (idx < TILE_KIND_COUNT && counts[idx] == 0) {
            idx++;
        }
        if (idx >= TILE_KIND_COUNT) {
            return new ComboBest(0, 0, new ArrayList<>());
        }

        ComboBest best = null;

        counts[idx]--;
        ComboBest sub = bestCombos(counts, idx);
        counts[idx]++;
        best = pickBetter(best, sub);

        if (counts[idx] >= 2) {
            counts[idx] -= 2;
            ComboBest s = bestCombos(counts, idx);
            counts[idx] += 2;
            best = pickBetter(best, addCombo(s, ENERGY_PAIR, "PAIR"));
        }

        if (counts[idx] >= 3) {
            counts[idx] -= 3;
            ComboBest s = bestCombos(counts, idx);
            counts[idx] += 3;
            best = pickBetter(best, addCombo(s, ENERGY_TRIPLET, "TRIPLET"));
        }

        if (counts[idx] >= 4) {
            counts[idx] -= 4;
            ComboBest s = bestCombos(counts, idx);
            counts[idx] += 4;
            best = pickBetter(best, addCombo(s, ENERGY_KAN, "KAN"));
        }

        if (idx < 27 && (idx % 9) <= 6
                && counts[idx + 1] > 0 && counts[idx + 2] > 0) {
            counts[idx]--;
            counts[idx + 1]--;
            counts[idx + 2]--;
            ComboBest s = bestCombos(counts, idx);
            counts[idx]++;
            counts[idx + 1]++;
            counts[idx + 2]++;
            best = pickBetter(best, addCombo(s, ENERGY_SEQUENCE, "SEQUENCE"));
        }
        return best;
    }

    private static ComboBest pickBetter(ComboBest a, ComboBest b) {
        if (a == null) return b;
        if (b == null) return a;
        if (b.combos != a.combos) return b.combos > a.combos ? b : a;
        return b.energy > a.energy ? b : a;
    }

    private static ComboBest addCombo(ComboBest sub, int energy, String name) {
        if (sub == null) return null;
        List<String> names = new ArrayList<>(sub.names.size() + 1);
        names.addAll(sub.names);
        names.add(name);
        return new ComboBest(sub.combos + 1, sub.energy + energy, names);
    }

    private record ComboBest(int combos, int energy, List<String> names) {}

    private record TsumoResult(int han, boolean yakuman, List<String> yakuNames, List<String> yakumanNames) {
        private static TsumoResult noWin() {
            return new TsumoResult(0, false, List.of(), List.of());
        }
    }

    private record Reward(int rfPerTick, int durationTicks) {
    }
}
