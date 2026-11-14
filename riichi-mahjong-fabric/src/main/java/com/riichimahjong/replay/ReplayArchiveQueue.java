package com.riichimahjong.replay;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Main-thread pending archives. Ack only means the database worker's durable outbox accepted it. */
public final class ReplayArchiveQueue {
    private final Map<UUID,ReplayRecord> pending=new LinkedHashMap<>();
    private final Set<UUID> inFlight=new HashSet<>();
    public void offer(ReplayRecord record) {
        if(!record.completed())throw new IllegalArgumentException("Cannot archive an incomplete replay");
        pending.putIfAbsent(record.id(),record);
    }
    public boolean isEmpty(){return pending.isEmpty();}
    public int size(){return pending.size();}
    public void pump(Function<ReplayRecord,CompletableFuture<Void>> archive,Consumer<Runnable> onServer,Runnable changed) {
        for(var record:List.copyOf(pending.values())) {
            if(!inFlight.add(record.id()))continue;
            try {
                archive.apply(record).whenComplete((ok,error)->onServer.accept(()->{
                    inFlight.remove(record.id());
                    if(error==null&&pending.remove(record.id())!=null)changed.run();
                }));
            } catch(RuntimeException failure) {inFlight.remove(record.id());}
        }
    }
    public void write(ValueOutput out) {
        var entries=out.childrenList("Pending");
        for(var record:pending.values())entries.addChild().store("Record",CompoundTag.CODEC,record.toNbt());
    }
    public void read(ValueInput in) {
        pending.clear();inFlight.clear();
        for(var entry:in.childrenListOrEmpty("Pending"))entry.read("Record",CompoundTag.CODEC).ifPresent(tag->{
            var record=ReplayRecord.fromNbt(tag);if(record.completed())pending.put(record.id(),record);
        });
    }
}
