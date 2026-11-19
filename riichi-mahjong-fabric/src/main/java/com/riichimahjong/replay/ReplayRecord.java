package com.riichimahjong.replay;

import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.TheMahjongDriver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** Server-only, versioned full-information replay. Never place this payload in an item or packet. */
public record ReplayRecord(UUID id,long startedAt,long endedAt,String preset,
        List<Participant> participants,List<Frame> frames) {
    public static final int VERSION=1;
    public static final long MAX_DECODE_BYTES=256L*1024*1024;

    public record Participant(int seat,UUID uuid,String name,int points) {
        public Participant {
            if(seat<0||seat>3)throw new IllegalArgumentException("Invalid replay seat");
            if(name==null||name.isBlank())name="BOT";
        }
    }

    public record Frame(int roundIndex,String label,CompoundTag driver) {
        public Frame {
            if(roundIndex<0)throw new IllegalArgumentException("Invalid replay round");
            if(label==null)label="";
            if(driver==null)throw new IllegalArgumentException("Missing replay frame");
            driver=driver.copy();
        }
        public com.riichimahjong.fabric.RiichiSkin riichiSkin(int seat) {
            return com.riichimahjong.fabric.RiichiSkin.parse(driver.getCompoundOrEmpty("RiichiStickSkins").getStringOr(Integer.toString(seat), ""));
        }
        /** A fresh driver with controllers that can never produce an action. */
        public TheMahjongDriver restore(HolderLookup.Provider registries) {
            int count=driver.getCompoundOrEmpty("match").getIntOr("playerCount",0);
            if(count<3||count>4)throw new IllegalArgumentException("Invalid replay player count");
            List<MahjongPlayerInterface> paused=IntStream.range(0,count)
                    .mapToObj(i->(MahjongPlayerInterface)(request,seconds)->Optional.empty()).toList();
            return DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,registries,driver),paused,new Random(0));
        }
    }

    public ReplayRecord {
        if(id==null||preset==null||startedAt<0||endedAt<0)throw new IllegalArgumentException("Invalid replay metadata");
        participants=List.copyOf(participants);
        frames=List.copyOf(frames);
    }

    public int playerCount() {
        return frames.isEmpty()?Math.min(4,(int)participants.stream().map(Participant::seat).distinct().count())
                :frames.getFirst().driver().getCompoundOrEmpty("match").getIntOr("playerCount",0);
    }
    public Participant participantAt(int seat) {
        return participants.stream().filter(p->p.seat()==seat).findFirst()
                .orElse(new Participant(seat,null,seat>=playerCount()?"未启用":"BOT",0));
    }
    public boolean completed() {
        return endedAt>=startedAt&&endedAt>0&&!frames.isEmpty()
                &&frames.getLast().driver().getCompoundOrEmpty("phase").getStringOr("type","").equals("MATCH_ENDED");
    }

    public CompoundTag toNbt() {
        var tag=new CompoundTag();tag.putInt("Version",VERSION);tag.putString("Id",id.toString());
        tag.putLong("StartedAt",startedAt);tag.putLong("EndedAt",endedAt);tag.putString("Preset",preset);
        var people=new ListTag();
        for(var p:participants){var entry=new CompoundTag();entry.putInt("Seat",p.seat());
            if(p.uuid()!=null)entry.putString("Uuid",p.uuid().toString());
            entry.putString("Name",p.name());entry.putInt("Points",p.points());people.add(entry);}
        tag.put("Participants",people);
        var steps=new ListTag();
        for(var f:frames){var entry=new CompoundTag();entry.putInt("Round",f.roundIndex());
            entry.putString("Label",f.label());entry.put("Driver",f.driver().copy());steps.add(entry);}
        tag.put("Frames",steps);return tag;
    }
    public static ReplayRecord fromNbt(CompoundTag tag) {
        if(tag.getIntOr("Version",0)!=VERSION)throw new IllegalArgumentException("Unsupported replay version");
        var people=new java.util.ArrayList<Participant>();var steps=new java.util.ArrayList<Frame>();
        for(var value:tag.getListOrEmpty("Participants")){if(!(value instanceof CompoundTag p))continue;
            String uuid=p.getStringOr("Uuid","");people.add(new Participant(p.getIntOr("Seat",-1),
                    uuid.isEmpty()?null:UUID.fromString(uuid),p.getStringOr("Name","BOT"),p.getIntOr("Points",0)));}
        for(var value:tag.getListOrEmpty("Frames")){if(!(value instanceof CompoundTag f))continue;
            steps.add(new Frame(f.getIntOr("Round",0),f.getStringOr("Label",""),f.getCompoundOrEmpty("Driver")));}
        return new ReplayRecord(UUID.fromString(tag.getStringOr("Id","")),tag.getLongOr("StartedAt",0),
                tag.getLongOr("EndedAt",0),tag.getStringOr("Preset",""),people,steps);
    }
    public byte[] encode() {
        try{var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(toNbt(),bytes);return bytes.toByteArray();}
        catch(IOException e){throw new IllegalStateException("Cannot encode replay",e);}
    }
    public static ReplayRecord decode(byte[] bytes) {
        try{return fromNbt(NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtAccounter.create(MAX_DECODE_BYTES)));}
        catch(IOException e){throw new IllegalArgumentException("Cannot decode replay",e);}
    }
}
