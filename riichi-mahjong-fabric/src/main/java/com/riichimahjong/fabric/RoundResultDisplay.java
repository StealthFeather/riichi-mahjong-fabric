package com.riichimahjong.fabric;

import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.driver.MatchPhase;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Preserve an already public final-hand result after the rules move to MatchEnded. */
public final class RoundResultDisplay {
    private MatchPhase.RoundEnded result;

    public void observe(MatchPhase phase) {
        if(phase instanceof MatchPhase.RoundEnded ended)result=ended;
        else if(!(phase instanceof MatchPhase.MatchEnded))result=null;
    }
    public MatchPhase phase(MatchPhase actual) {
        return actual instanceof MatchPhase.MatchEnded&&result!=null?result:actual;
    }
    public void clear(){result=null;}
    public void write(ValueOutput out) {
        if(result!=null)DriverNbt.writePhase(out.child("Phase"),result);
    }
    public void read(ValueInput in) {
        result=null;
        in.child("Phase").map(DriverNbt::readPhase).ifPresent(phase->{
            if(phase instanceof MatchPhase.RoundEnded ended)result=ended;
        });
    }
}
