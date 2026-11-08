package com.riichimahjong.mahjongsolitaire;

import com.mojang.logging.LogUtils;
import com.riichimahjong.cuterenderer.CuteClickHandler;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.TheMahjongTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class MahjongSolitaireBlockEntity extends BlockEntity implements CuteClickHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int LIQUID_XP_PER_PAIR_MB = 10;

    public static final int LIQUID_XP_CAPACITY_MB = 1000;

    private int liquidXpStored;

    @Nullable private String boardId;

    private TheMahjongTile[] tiles = new TheMahjongTile[0];

    private int[] tileIds = new int[0];
    private long randomSeed;

    private final Map<UUID, Integer> selections = new HashMap<>();

    private boolean lastPowered;

    private boolean automatedContext = false;

    public boolean wasPowered() { return lastPowered; }
    public void setPowered(boolean powered) {
        if (this.lastPowered != powered) {
            this.lastPowered = powered;
            markChangedAndSynced();
        }
    }

    public MahjongSolitaireBlockEntity(BlockPos pos, BlockState state) {
        super(com.riichimahjong.registry.ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable public MahjongSolitaireBoard board() {
        if (boardId == null) return null;
        for (var b : MahjongSolitaireBoards.all()) {
            if (b.id().equals(boardId)) return b;
        }
        return null;
    }

    @Nullable
    public TheMahjongTile tileAt(int slot) {
        return (slot < 0 || slot >= tiles.length) ? null : tiles[slot];
    }

    public int tileIdAt(int slot) {
        return (slot < 0 || slot >= tileIds.length) ? -1 : tileIds[slot];
    }

    public long randomSeed() { return randomSeed; }

    @Nullable public transient Object clientRenderState;

    public int slotCount() { return tiles.length; }

    public int remainingTiles() {
        int n = 0;
        for (TheMahjongTile t : tiles) if (t != null) n++;
        return n;
    }

    public boolean isBoardEmpty() {
        return tiles.length == 0 || remainingTiles() == 0;
    }

    public int selectionFor(UUID player) {
        Integer s = selections.get(player);
        return s == null ? -1 : s;
    }

    public boolean isFree(int slotIndex) {
        MahjongSolitaireBoard b = board();
        if (b == null) return false;
        if (slotIndex < 0 || slotIndex >= tiles.length) return false;
        if (tiles[slotIndex] == null) return false;
        var s = b.slots().get(slotIndex);
        boolean above = anyTileIn(b, s.x(), s.x() + 1, s.z(), s.z() + 1, s.y() + 1);
        boolean left  = anyTileIn(b, s.x() - 1, s.x() - 1, s.z(), s.z() + 1, s.y());
        boolean right = anyTileIn(b, s.x() + 2, s.x() + 2, s.z(), s.z() + 1, s.y());
        return !above && (!left || !right);
    }

    private boolean anyTileIn(MahjongSolitaireBoard b, int xLo, int xHi, int zLo, int zHi, int y) {
        var slots = b.slots();
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == null) continue;
            var t = slots.get(i);
            if (t.y() != y) continue;
            int tx0 = t.x(), tx1 = t.x() + 1;
            int tz0 = t.z(), tz1 = t.z() + 1;
            if (tx1 < xLo || tx0 > xHi) continue;
            if (tz1 < zLo || tz0 > zHi) continue;
            return true;
        }
        return false;
    }

    public void resetBoard() {
        if (level == null || level.isClientSide()) return;
        List<MahjongSolitaireBoard> all = MahjongSolitaireBoards.all();
        if (all.isEmpty()) {
            LOGGER.warn("solitaire reset: no boards available");
            this.boardId = null;
            this.tiles = new TheMahjongTile[0];
            this.tileIds = new int[0];
            selections.clear();
            markChangedAndSynced();
            return;
        }
        Random rng = new Random();
        this.randomSeed = rng.nextLong();
        Random shuffler = new Random(randomSeed);
        MahjongSolitaireBoard pick = all.get(shuffler.nextInt(all.size()));
        this.boardId = pick.id();
        this.tiles = dealSolvable(pick, shuffler);

        this.tileIds = deriveDefaultTileIds(tiles);
        selections.clear();
        markChangedAndSynced();
    }

    private static int[] deriveDefaultTileIds(TheMahjongTile[] tiles) {
        int[] ids = new int[tiles.length];
        for (int i = 0; i < tiles.length; i++) ids[i] = tiles[i] != null ? i : -1;
        return ids;
    }

    private static final List<TheMahjongTile> STANDARD_DECK_TILES = buildStandardDeckTiles();

    private static List<TheMahjongTile> buildStandardDeckTiles() {
        List<TheMahjongTile> out = new ArrayList<>(34);
        for (TheMahjongTile.Suit suit : TheMahjongTile.Suit.values()) {
            for (int rank = 1; rank <= suit.maxRank(); rank++) {
                out.add(new TheMahjongTile(suit, rank, false));
            }
        }
        return out;
    }

    private static TheMahjongTile[] dealSolvable(MahjongSolitaireBoard board, Random rng) {
        int slotCount = board.slots().size();
        int usable = slotCount - (slotCount % 2);
        boolean[] present = new boolean[slotCount];
        for (int i = 0; i < usable; i++) present[i] = true;

        TheMahjongTile[] dealt = new TheMahjongTile[slotCount];

        Map<TheMahjongTile, Integer> usage = new HashMap<>();
        ArrayList<Integer> freeBuf = new ArrayList<>();
        ArrayList<TheMahjongTile> tileBuf = new ArrayList<>();

        while (true) {
            freeBuf.clear();
            for (int i = 0; i < slotCount; i++) {
                if (present[i] && isFreeInState(board, present, i)) freeBuf.add(i);
            }
            if (freeBuf.size() < 2) break;
            int a = freeBuf.get(rng.nextInt(freeBuf.size()));
            int b;
            do { b = freeBuf.get(rng.nextInt(freeBuf.size())); } while (b == a);

            TheMahjongTile tile = pickTile(usage, rng, tileBuf);
            dealt[a] = tile;
            dealt[b] = tile;
            usage.merge(tile, 2, Integer::sum);
            present[a] = false;
            present[b] = false;
        }
        return dealt;
    }

    private static TheMahjongTile pickTile(Map<TheMahjongTile, Integer> usage, Random rng,
                                            ArrayList<TheMahjongTile> buf) {
        buf.clear();
        for (TheMahjongTile t : STANDARD_DECK_TILES) {
            if (usage.getOrDefault(t, 0) < 4) buf.add(t);
        }
        if (!buf.isEmpty()) {
            return buf.get(rng.nextInt(buf.size()));
        }

        return STANDARD_DECK_TILES.get(rng.nextInt(STANDARD_DECK_TILES.size()));
    }

    private static boolean isFreeInState(MahjongSolitaireBoard board, boolean[] present, int slotIndex) {
        var s = board.slots().get(slotIndex);
        boolean above = anyPresentTileIn(board, present, s.x(), s.x() + 1, s.z(), s.z() + 1, s.y() + 1);
        boolean left  = anyPresentTileIn(board, present, s.x() - 1, s.x() - 1, s.z(), s.z() + 1, s.y());
        boolean right = anyPresentTileIn(board, present, s.x() + 2, s.x() + 2, s.z(), s.z() + 1, s.y());
        return !above && (!left || !right);
    }

    private static boolean anyPresentTileIn(MahjongSolitaireBoard board, boolean[] present,
                                            int xLo, int xHi, int zLo, int zHi, int y) {
        var slots = board.slots();
        for (int i = 0; i < slots.size(); i++) {
            if (!present[i]) continue;
            var t = slots.get(i);
            if (t.y() != y) continue;
            int tx0 = t.x(), tx1 = t.x() + 1;
            int tz0 = t.z(), tz1 = t.z() + 1;
            if (tx1 < xLo || tx0 > xHi) continue;
            if (tz1 < zLo || tz0 > zHi) continue;
            return true;
        }
        return false;
    }

    @Override
    public void onCuteClick(ServerPlayer player, InteractKey key) {
        if (key instanceof InteractKey.Named named && "HINT".equals(named.name())) {
            handleHintClick(player);
            return;
        }
        if (!(key instanceof InteractKey.Slot s)) return;
        int slotIndex = s.index();
        if (slotIndex < 0 || slotIndex >= tiles.length) return;
        if (tiles[slotIndex] == null) return;
        if (!isFree(slotIndex)) {
            player.playSound(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.4f, 0.6f);
            return;
        }
        UUID uuid = player.getUUID();
        Integer prev = selections.get(uuid);
        if (prev == null) {
            selections.put(uuid, slotIndex);
            markChangedAndSynced();
            return;
        }
        if (prev == slotIndex) {
            selections.remove(uuid);
            markChangedAndSynced();
            return;
        }
        if (prev >= 0 && prev < tiles.length
                && tiles[prev] != null
                && tiles[prev].matchesSuitRank(tiles[slotIndex])
                && isFree(prev)) {
            removePair(prev, slotIndex);

            spawnExperienceOrb(1);
            player.playSound(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 1.4f);
            return;
        }

        selections.put(uuid, slotIndex);
        markChangedAndSynced();
    }

    private void removePair(int a, int b) {
        tiles[a] = null;
        tiles[b] = null;
        tileIds[a] = -1;
        tileIds[b] = -1;
        selections.values().removeIf(idx -> idx == a || idx == b);
        if (remainingTiles() == 0) {
            dropFakeStarsOnClear();
        } else if (!hasAvailablePair()) {
            shuffleRemaining();
        }
        markChangedAndSynced();
    }

    private boolean hasAvailablePair() {
        if (tiles.length == 0) return false;
        return revealOneHintPairOn(tiles.clone());
    }

    private static int hintPairsFor(ItemStack stack) {
        if (stack.is(Items.IRON_INGOT)) return 1;
        if (stack.is(Items.GOLD_INGOT)) return 2;
        if (stack.is(Items.DIAMOND))    return 4;
        return 0;
    }

    public static boolean isHintItem(ItemStack stack) {
        return hintPairsFor(stack) > 0;
    }

    public int tryInsertHintItem(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return 0;
        int perItem = hintPairsFor(stack);
        if (perItem <= 0) return 0;
        if (level == null || level.isClientSide()) return 0;
        int max = stack.getCount();
        int consumed = 0;
        if (simulate) {
            TheMahjongTile[] copy = tiles.clone();
            for (int i = 0; i < max; i++) {
                int revealed = 0;
                for (int j = 0; j < perItem; j++) {
                    if (revealOneHintPairOn(copy)) revealed++;
                    else break;
                }
                if (revealed < perItem) break;
                consumed++;
            }
        } else {
            automatedContext = true;
            try {
                for (int i = 0; i < max; i++) {
                    TheMahjongTile[] preCheck = tiles.clone();
                    int simulated = 0;
                    for (int j = 0; j < perItem; j++) {
                        if (revealOneHintPairOn(preCheck)) simulated++;
                        else break;
                    }
                    if (simulated < perItem) break;
                    int realRevealed = 0;
                    for (int j = 0; j < perItem; j++) {
                        if (!revealOneHintPair()) break;
                        fillLiquidXpVoidingOverflow(LIQUID_XP_PER_PAIR_MB);
                        realRevealed++;
                    }
                    if (realRevealed == 0) break;
                    consumed++;
                }
            } finally {
                automatedContext = false;
            }
        }
        return consumed;
    }

    public int getLiquidXpStored() {
        return liquidXpStored;
    }

    public int extractLiquidXp(int maxMb, boolean simulate) {
        int drained = Math.max(0, Math.min(maxMb, liquidXpStored));
        if (!simulate && drained > 0) {
            liquidXpStored -= drained;
            markChangedAndSynced();
        }
        return drained;
    }

    private void fillLiquidXpVoidingOverflow(int amountMb) {
        int accepted = Math.max(0, Math.min(amountMb, LIQUID_XP_CAPACITY_MB - liquidXpStored));
        if (accepted > 0) {
            liquidXpStored += accepted;
            markChangedAndSynced();
        }
    }

    private void shuffleRemaining() {
        MahjongSolitaireBoard board = board();
        if (board == null) return;
        List<Integer> presentSlots = new ArrayList<>();

        Map<Long, java.util.Deque<Integer>> idsByKey = new HashMap<>();
        Map<Long, java.util.Deque<TheMahjongTile>> tilesByKey = new HashMap<>();
        for (int i = 0; i < tiles.length; i++) {
            TheMahjongTile t = tiles[i];
            if (t == null) continue;
            presentSlots.add(i);
            long key = suitRankKey(t);
            idsByKey.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).add(tileIds[i]);
            tilesByKey.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).add(t);
        }
        if (presentSlots.size() < 2) return;

        TheMahjongTile[] newTiles = tiles.clone();
        int[] newIds = tileIds.clone();
        for (int s : presentSlots) { newTiles[s] = null; newIds[s] = -1; }
        boolean[] working = new boolean[tiles.length];
        for (int s : presentSlots) working[s] = true;

        Random rng = new Random();
        ArrayList<Integer> freeBuf = new ArrayList<>();
        ArrayList<Long> keyBuf = new ArrayList<>();
        while (true) {
            freeBuf.clear();
            for (int i = 0; i < tiles.length; i++) {
                if (working[i] && isFreeInState(board, working, i)) freeBuf.add(i);
            }
            if (freeBuf.size() < 2) break;
            int a = freeBuf.get(rng.nextInt(freeBuf.size()));
            int b;
            do { b = freeBuf.get(rng.nextInt(freeBuf.size())); } while (b == a);

            keyBuf.clear();
            for (var e : idsByKey.entrySet()) {
                if (e.getValue().size() >= 2) keyBuf.add(e.getKey());
            }
            if (keyBuf.isEmpty()) break;
            long key = keyBuf.get(rng.nextInt(keyBuf.size()));
            java.util.Deque<Integer> ids = idsByKey.get(key);
            java.util.Deque<TheMahjongTile> pool = tilesByKey.get(key);

            newTiles[a] = pool.poll();
            newTiles[b] = pool.poll();
            newIds[a] = ids.poll();
            newIds[b] = ids.poll();
            working[a] = false;
            working[b] = false;
        }

        for (int s : presentSlots) {
            if (newTiles[s] == null) {
                LOGGER.info("solitaire shuffle: surviving subset unsolvable, auto-completing the board");
                java.util.Arrays.fill(tiles, null);
                java.util.Arrays.fill(tileIds, -1);
                selections.clear();
                dropFakeStarsOnClear();
                return;
            }
        }
        tiles = newTiles;
        tileIds = newIds;
        selections.clear();
    }

    private static long suitRankKey(TheMahjongTile t) {
        return ((long) t.suit().ordinal() << 4) | (long) t.rank();
    }

    private boolean revealOneHintPair() {
        if (tiles.length == 0) return false;
        Map<Long, Integer> firstByKey = new HashMap<>();
        for (int i = 0; i < tiles.length; i++) {
            TheMahjongTile t = tiles[i];
            if (t == null) continue;
            if (!isFree(i)) continue;
            long key = suitRankKey(t);
            Integer prev = firstByKey.get(key);
            if (prev != null) {
                removePair(prev, i);
                return true;
            }
            firstByKey.put(key, i);
        }
        return false;
    }

    private void handleHintClick(ServerPlayer player) {
        if (!hasAvailablePair()) return;
        boolean creative = player.getAbilities().instabuild;
        Inventory inv = player.getInventory();
        int paySlot = -1;
        int payPairs = 0;
        if (!creative) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                int p = hintPairsFor(inv.getItem(i));

                if (p > 0 && (payPairs == 0 || p < payPairs)) {
                    paySlot = i;
                    payPairs = p;
                }
            }
            if (paySlot < 0) {
                player.sendSystemMessage(Component.translatable(
                        "riichi_mahjong.solitaire.hint_cost"));
                return;
            }
        } else {
            payPairs = 1;
        }
        int revealed = 0;
        for (int j = 0; j < payPairs; j++) {
            if (!revealOneHintPair()) break;
            revealed++;
        }
        if (revealed == 0) return;
        if (!creative) inv.getItem(paySlot).shrink(1);
        spawnExperienceOrb(revealed);
    }

    private void spawnExperienceOrb(int amount) {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos p = getBlockPos();
        ExperienceOrb.award(sl, new net.minecraft.world.phys.Vec3(
                p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5), amount);
    }

    private void dropFakeStarsOnClear() {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos p = getBlockPos();
        double cx = p.getX() + 0.5;
        double cy = p.getY() + 0.6;
        double cz = p.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            ItemEntity e = new ItemEntity(sl, cx, cy, cz,
                    new ItemStack(com.riichimahjong.registry.ModItems.MAHJONG_FAKE_STAR.get()));
            e.setDefaultPickUpDelay();
            sl.addFreshEntity(e);
        }
        emitBoardClearEffects();
    }

    private void emitBoardClearEffects() {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos p = getBlockPos();
        double cx = p.getX() + 0.5, cy = p.getY() + 0.5, cz = p.getZ() + 0.5;
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                cx, cy + 0.25, cz, 18, 0.7, 0.2, 0.7, 0.0);
        sl.sendParticles(ParticleTypes.FIREWORK,
                cx, cy + 0.4, cz, 8, 0.5, 0.15, 0.5, 0.05);
        if (!automatedContext) {
            sl.playSound(null, p,
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6f, 1.4f);
        }
    }

    private int countAvailableHintsSimulated() {
        if (tiles.length == 0) return 0;
        TheMahjongTile[] copy = tiles.clone();
        int count = 0;
        while (revealOneHintPairOn(copy)) count++;
        return count;
    }

    private boolean revealOneHintPairOn(TheMahjongTile[] state) {
        MahjongSolitaireBoard board = board();
        if (board == null) return false;
        boolean[] present = new boolean[state.length];
        for (int i = 0; i < state.length; i++) present[i] = state[i] != null;
        Map<Long, Integer> firstByKey = new HashMap<>();
        for (int i = 0; i < state.length; i++) {
            if (!present[i]) continue;
            if (!isFreeInState(board, present, i)) continue;
            long key = suitRankKey(state[i]);
            Integer prev = firstByKey.get(key);
            if (prev != null) {
                state[prev] = null;
                state[i] = null;
                return true;
            }
            firstByKey.put(key, i);
        }
        return false;
    }

    private void markChangedAndSynced() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);

            BlockPos master = getBlockPos();
            net.minecraft.world.level.block.Block block = getBlockState().getBlock();
            int r = com.riichimahjong.common.BaseMultipartBlock.CENTER;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    level.updateNeighbourForOutputSignal(master.offset(dx, 0, dz), block);
                }
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.boardId = in.getString("BoardId").orElse(null);

        List<TheMahjongTile> loaded = new ArrayList<>();
        for (ValueInput entry : in.childrenListOrEmpty("Tiles")) {
            loaded.add(entry.getString("suit").isPresent() ? MatchNbt.readTile(entry) : null);
        }
        this.tiles = loaded.toArray(new TheMahjongTile[0]);

        this.tileIds = in.getIntArray("TileIds")
                .filter(ids -> ids.length == this.tiles.length)
                .orElseGet(() -> deriveDefaultTileIds(tiles));
        this.randomSeed = in.getLongOr("Seed", 0L);
        this.lastPowered = in.getBooleanOr("LastPowered", false);
        this.liquidXpStored = Math.max(0, Math.min(LIQUID_XP_CAPACITY_MB, in.getIntOr("LiquidXp", 0)));
        selections.clear();
        for (ValueInput entry : in.childrenListOrEmpty("Selections")) {
            entry.read("U", UUIDUtil.CODEC).ifPresent(uuid ->
                    entry.getInt("S").ifPresent(slot -> selections.put(uuid, slot)));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (boardId != null) out.putString("BoardId", boardId);

        ValueOutput.ValueOutputList tileList = out.childrenList("Tiles");
        for (TheMahjongTile tile : tiles) {
            ValueOutput entry = tileList.addChild();
            if (tile != null) {
                MatchNbt.writeTile(entry, tile);
            }
        }
        out.putIntArray("TileIds", tileIds);
        out.putLong("Seed", randomSeed);
        out.putBoolean("LastPowered", lastPowered);
        out.putInt("LiquidXp", liquidXpStored);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        ListTag list = new ListTag();
        for (var entry : selections.entrySet()) {
            CompoundTag selection = new CompoundTag();
            selection.store("U", UUIDUtil.CODEC, entry.getKey());
            selection.putInt("S", entry.getValue());
            list.add(selection);
        }
        if (!list.isEmpty()) tag.put("Selections", list);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
