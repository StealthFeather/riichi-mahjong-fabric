package com.riichimahjong.fabric;

public enum RiichiSkin {
    CLASSIC("经典白棒", "table_riichi_stick"),
    JADE("翠玉立直棒", "table_riichi_stick_jade"),
    GOLD("金色立直棒", "table_riichi_stick_gold");

    private final String label;
    private final String model;
    RiichiSkin(String label, String model) {
        this.label = label;
        this.model = "bridge/" + model;
    }
    public String label() { return label; }
    public String model() { return model; }
    public static RiichiSkin parse(String name) {
        for (var skin : values()) if (skin.name().equals(name)) return skin;
        return CLASSIC;
    }
}
