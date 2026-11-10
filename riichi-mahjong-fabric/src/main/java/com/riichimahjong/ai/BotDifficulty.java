package com.riichimahjong.ai;

import java.util.Locale;

/** Mod difficulty labels; the upstream strategies are not calibrated ranking levels. */
public enum BotDifficulty {
    EASY("初级"), NORMAL("中级"), HARD("高级");

    private final String label;
    BotDifficulty(String label) { this.label = label; }
    public String label() { return label; }
    public BotDifficulty next() { return values()[(ordinal() + 1) % values().length]; }
    public static BotDifficulty parse(String value) {
        if (value == null) return NORMAL;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return NORMAL; }
    }
}
