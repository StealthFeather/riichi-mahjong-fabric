package com.riichimahjong.analysis;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import java.util.Objects;
import java.util.Optional;

/** Each rule family, table size, scheduled length and entertainment option has its own history. */
public record AnalysisMode(RuleSetPreset preset, MatchLength length,
                           boolean exchange, boolean jokers, boolean blood) {
    public AnalysisMode {
        Objects.requireNonNull(preset);
        Objects.requireNonNull(length);
    }
    public static AnalysisMode of(RuleSetPreset preset, MatchLength length,
                                  boolean exchange, boolean jokers, boolean blood) {
        return new AnalysisMode(preset, length, exchange, jokers, blood);
    }
    public String key() {
        return preset.name()+"|"+length.name()+"|"+(exchange?1:0)+"|"+(jokers?1:0)+"|"+(blood?1:0);
    }
    public String label() {
        return preset.family().label()+" · "+(preset.playerCount()==3?"三麻":"四麻")+" · "+length.label()
                +(exchange?" · 换三张":"")+(jokers?" · 赖子":"")+(blood?" · 血战到底":"");
    }
    public boolean ranked() {
        return !exchange && !jokers && !blood && switch(preset.family()) {
            case MAHJONG_SOUL, JPML, M_LEAGUE -> true;
            default -> false;
        };
    }
    public static Optional<AnalysisMode> fromKey(String key) {
        String[] parts=key.split("\\|",-1);
        if(parts.length!=5)return Optional.empty();
        try {
            for(int i=2;i<5;i++)if(!parts[i].equals("0")&&!parts[i].equals("1"))return Optional.empty();
            return Optional.of(of(RuleSetPreset.valueOf(parts[0]),MatchLength.valueOf(parts[1]),
                    parts[2].equals("1"),parts[3].equals("1"),parts[4].equals("1")));
        } catch(IllegalArgumentException exception) { return Optional.empty(); }
    }
}
