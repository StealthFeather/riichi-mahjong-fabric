package com.riichimahjong.mahjongsolitaire;

import java.util.List;

public record MahjongSolitaireBoard(
        String id,
        String name,
        String category,
        List<Slot> slots,
        int minX, int maxX, int minZ, int maxZ, int maxLayer) {
    public static final int TILE_GRID = 2;

    public record Slot(int x, int y, int z) {}

    public int width()  { return maxX - minX + TILE_GRID; }
    public int depth()  { return maxZ - minZ + TILE_GRID; }
    public int layers() { return maxLayer + 1; }
}
