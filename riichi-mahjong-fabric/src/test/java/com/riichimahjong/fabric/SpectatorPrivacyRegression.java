package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.SpectatorAccess;
import com.riichimahjong.replay.ReplayRecord;
import com.riichimahjong.replay.ReplayRecorder;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Privacy boundaries for live spectators, match transitions and persisted human history. */
public final class SpectatorPrivacyRegression {
    private static final UUID[] PLAYERS={UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000004")};
    private static final UUID VIEWER=UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static net.minecraft.world.level.storage.ValueInput input(CompoundTag tag){return TagValueInput.create(ProblemReporter.DISCARDING,HolderLookup.Provider.create(Stream.empty()),tag);}
    private static CompoundTag save(SpectatorAccess access,boolean live){var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);access.write(out,live);return out.buildResult();}
    private static void liveMatch(int count){
        var access=new SpectatorAccess();var players=Arrays.asList(PLAYERS).subList(0,count);access.begin(players);
        check(!access.canSee(VIEWER,true,false,false),"spectator hands must default closed");
        access.setEnabled(true);
        check(access.canSee(VIEWER,true,false,false),"enabled live match rejected a nonparticipant spectator");
        check(!access.canSee(null,true,false,false),"a null viewer gained access");
        check(!access.canSee(VIEWER,false,false,false),"lobby or replay gained live spectator access");
        check(!access.canSee(VIEWER,true,true,false),"a newly seated spectator retained access");
        check(!access.canSee(VIEWER,true,false,true),"a reserved returning player gained spectator access");
        for(UUID player:players)check(!access.canSee(player,true,false,false),"historical participant viewed opponents' hands");
        access.remember(VIEWER);
        check(!access.canSee(VIEWER,true,true,false),"new participant viewed opponents while seated");
        check(!access.canSee(VIEWER,true,false,true),"new participant viewed opponents after leaving with a reservation");
        check(!access.canSee(VIEWER,true,false,false),"former participant viewed opponents after reservation was removed");
        var restored=new SpectatorAccess();restored.read(input(save(access,true)));
        check(restored.enabled()&&restored.participants().containsAll(players)&&restored.participants().contains(VIEWER),"enabled setting or participant history did not survive reload");
        check(!restored.canSee(VIEWER,true,false,false),"reconnecting former participant gained access after reload");
        restored.setEnabled(false);
        UUID stranger=UUID.fromString("00000000-0000-0000-0000-000000000006");
        check(!restored.canSee(stranger,true,false,false),"disabling the setting failed to revoke access");
        restored.setEnabled(true);restored.begin(players);
        check(restored.canSee(VIEWER,true,false,false),"new match retained an unrelated former participant");
        for(UUID player:players)check(!restored.canSee(player,true,false,false),"new match lost an active participant");
        restored.clearMatch();
        check(restored.enabled()&&!restored.canSee(VIEWER,false,false,false),"ending a match changed the setting or exposed a lobby");
        var idle=new SpectatorAccess();idle.read(input(save(restored,false)));
        check(idle.enabled()&&idle.participants().isEmpty(),"idle save retained prior match identities or lost the table setting");
    }
    private static void historyMigration(){
        var legacy=new SpectatorAccess();legacy.setEnabled(true);legacy.remember(VIEWER);legacy.read(input(new CompoundTag()));
        check(!legacy.enabled()&&legacy.participants().isEmpty(),"legacy save inherited previous table state");
        var recorder=new ReplayRecorder("test",List.of(new ReplayRecord.Participant(0,PLAYERS[0],"first",0),new ReplayRecord.Participant(1,null,"bot",0)));
        recorder.remember(1,PLAYERS[1],"later");recorder.remember(1,PLAYERS[2],"replacement");
        var loaded=ReplayRecorder.fromNbt(recorder.toNbt());
        check(loaded.participantUuids().equals(Set.of(PLAYERS[0],PLAYERS[1],PLAYERS[2])),"old recording lost earlier occupants when seats were replaced");
        legacy.setEnabled(true);loaded.participantUuids().forEach(legacy::remember);
        for(UUID person:loaded.participantUuids())check(!legacy.canSee(person,true,false,false),"migrated historical participant gained spectator access");
    }
    private static void visibilityLifecycle(){
        check(SpectatorVisibility.inRange(true,true,64),"viewer at the eight block boundary was rejected");
        check(!SpectatorVisibility.inRange(true,true,64.0001),"out-of-range viewer retained private hand displays");
        check(!SpectatorVisibility.inRange(false,true,1)&&!SpectatorVisibility.inRange(true,false,1),"offline or other-world viewer retained access");
        var initial=SpectatorVisibility.hiddenFrom(PLAYERS[0],false,List.of(VIEWER));
        check(initial.equals(Set.of(PLAYERS[0],VIEWER)),"concealed tile backs did not exclude owner and authorized spectator");
        var seated=SpectatorVisibility.hiddenFrom(PLAYERS[0],false,List.of());
        check(seated.equals(Set.of(PLAYERS[0]))&&SpectatorVisibility.mustReplace(initial,seated),"spectator entering a seat did not retire the old audience");
        check(SpectatorVisibility.mustReplace(seated,initial),"newly authorized spectator did not force replacement of old audience");
        check(!SpectatorVisibility.mustReplace(initial,Set.of(VIEWER,PLAYERS[0])),"unchanged audiences caused needless replacement");
        check(SpectatorVisibility.hiddenFrom(PLAYERS[0],true,List.of(VIEWER)).isEmpty(),"already-public result hand was hidden from viewers");
        check(SpectatorVisibility.mustReplace(initial,Set.of()),"revealed result failed to replace the concealed audience");
        check(SpectatorVisibility.hiddenFrom(null,false,List.of(VIEWER)).equals(Set.of(VIEWER)),"bot-owned hands did not hide their backs from an authorized spectator");
    }
    public static void main(String[] args){liveMatch(3);liveMatch(4);historyMigration();visibilityLifecycle();System.out.println("PASS: live spectator privacy, seat/reservation lifecycle, replay isolation and durable 3p/4p participant history");}

}
