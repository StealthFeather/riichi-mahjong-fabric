package com.riichimahjong.fabric.wineffect;

import java.util.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Accepted blood-battle wins wait here until the hand finishes; no playback changes rule data. */
public final class DeferredWinQueue {
    private final List<WinEffectContext> contexts = new ArrayList<>();
    private final Set<Integer> completed = new LinkedHashSet<>();
    private int next;

    public void add(WinEffectContext context) {
        for (var winner : context.winners()) {
            if (contexts.stream().anyMatch(saved -> saved.includes(winner.seat()))) continue;
            contexts.add(new WinEffectContext(context.effectId(),context.source(),List.of(winner)));
        }
    }
    public WinEffectContext next() { return next < contexts.size() ? contexts.get(next++) : null; }
    public void complete(WinEffectContext context) { context.winners().forEach(w -> completed.add(w.seat())); }
    public Set<Integer> completed() { return Set.copyOf(completed); }
    public Set<Integer> winners() {
        var seats = new LinkedHashSet<Integer>();
        contexts.forEach(c -> c.winners().forEach(w -> seats.add(w.seat())));
        return Set.copyOf(seats);
    }
    public List<WinEffectContext> contexts() { return List.copyOf(contexts); }
    public boolean empty() { return contexts.isEmpty(); }
    public void finishAll() { contexts.forEach(this::complete); next = contexts.size(); }
    public void clear() { contexts.clear(); completed.clear(); next = 0; }
    public void write(ValueOutput out) {
        var list=out.childrenList("wins");
        for (var context:contexts) {
            var entry=list.addChild();
            entry.putString("effect",context.effectId());entry.putString("source",context.source().slot());
            entry.putInt("tile",context.source().tileCode());
            var winner=context.winners().getFirst();entry.putInt("seat",winner.seat());entry.putInt("winningTile",winner.tileCode());
        }
    }
    public void read(ValueInput in) {
        clear();
        for (var entry:in.childrenListOrEmpty("wins")) {
            int seat=entry.getIntOr("seat",-1),tile=entry.getIntOr("tile",-1),winning=entry.getIntOr("winningTile",tile);
            String source=entry.getStringOr("source","");
            if(seat<0||seat>3||tile<0||tile>37||winning<0||winning>37
                    ||!source.matches("(hand|river|robbed)/[0-3](/[0-9]+)?"))continue;
            add(new WinEffectContext(WinEffects.resolve(entry.getStringOr("effect",WinEffects.DEFAULT)).id(),
                    new WinEffectContext.Source(source,tile),List.of(new WinEffectContext.Winner(seat,winning))));
        }
    }
}
