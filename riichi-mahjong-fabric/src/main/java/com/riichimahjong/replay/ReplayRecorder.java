package com.riichimahjong.replay;

import com.riichimahjong.fabric.WinningHandDisplay;
import com.riichimahjong.fabric.RiichiSkin;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

/** One natural match, including all actions accepted within one advance() call. */
public final class ReplayRecorder {
    private final UUID id;
    private final long startedAt;
    private final String preset;
    private final List<ReplayRecord.Participant> people=new ArrayList<>();
    private final List<ReplayRecord.Frame> frames=new ArrayList<>();
    private boolean finished;

    public ReplayRecorder(String preset,List<ReplayRecord.Participant> participants) {
        this(UUID.randomUUID(),System.currentTimeMillis(),preset,participants,List.of());
    }
    private ReplayRecorder(UUID id,long startedAt,String preset,List<ReplayRecord.Participant> participants,List<ReplayRecord.Frame> frames) {
        this.id=id;this.startedAt=startedAt;this.preset=preset;this.people.addAll(participants);this.frames.addAll(frames);
    }
    public boolean finished(){return finished;}
    /** Human participants remain recorded after leaving or replacement. */
    public java.util.Set<UUID> participantUuids(){return people.stream().map(ReplayRecord.Participant::uuid).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    /** Recover public final-hand presentation from recordings made before it had a separate save field. */
    public java.util.Optional<ReplayRecord.Frame> lastRoundResult() {
        if(frames.isEmpty())return java.util.Optional.empty();
        int lastRound=frames.getLast().roundIndex();
        for(int index=frames.size()-1;index>=0;index--) {
            var frame=frames.get(index);if(frame.roundIndex()!=lastRound)break;
            if(frame.driver().getCompoundOrEmpty("phase").getStringOr("type","").equals("ROUND_ENDED"))
                return java.util.Optional.of(frame);
        }
        return java.util.Optional.empty();
    }
    public void remember(int seat,UUID uuid,String name) {
        if(uuid==null||people.stream().anyMatch(p->uuid.equals(p.uuid())))return;
        var bot=people.stream().filter(p->p.seat()==seat&&p.uuid()==null).findFirst();
        bot.ifPresent(people::remove);
        people.add(new ReplayRecord.Participant(seat,uuid,name,0));
    }
    public void capture(TheMahjongDriver driver) {
        capture(driver, seat -> RiichiSkin.CLASSIC);
    }
    public void capture(TheMahjongDriver driver, java.util.function.IntFunction<RiichiSkin> skinAtSeat) {
        var match=driver.match();var phase=driver.currentPhase();
        if(finished||match.currentRound().isEmpty()||phase instanceof MatchPhase.NotStarted||phase instanceof MatchPhase.Dealing)return;
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeReplayDriver(out,driver);
        var winning=WinningHandDisplay.capture(driver.winningTile(),phase);
        if(winning!=null)winning.write(out.child("ReplayWinningHand"));
        var state=out.buildResult();
        var skins = new CompoundTag();
        for (int seat=0; seat<match.playerCount(); seat++) {
            if (!driver.currentRound().players().get(seat).riichi()) continue;
            var skin=skinAtSeat.apply(seat);
            if (skin!=RiichiSkin.CLASSIC) skins.putString(Integer.toString(seat),skin.name());
        }
        if (!skins.isEmpty()) state.put("RiichiStickSkins",skins);
        int round=Math.max(0,match.completedRounds().size()-(phase instanceof MatchPhase.MatchEnded?1:0));
        var frame=new ReplayRecord.Frame(round,label(driver),state);
        // Reloading a live table may encounter the exact last frame once again.
        if(frames.isEmpty()||!frames.getLast().driver().equals(state))frames.add(frame);
    }
    private static String label(TheMahjongDriver driver) {
        var r=driver.currentRound();var phase=driver.currentPhase();String[] winds={"东","南","西","北"};
        String at=winds[r.roundWind().ordinal()]+r.handNumber()+"局 · "+r.honba()+"本场";
        String state=switch(phase){
            case MatchPhase.AwaitingDraw p->winds[p.seat()]+"家摸牌前";
            case MatchPhase.AwaitingDiscard p->winds[p.seat()]+"家出牌前";
            case MatchPhase.AwaitingClaims p->"等待鸣牌";
            case MatchPhase.RoundEnded p->p.winResults().isEmpty()?"流局":"和牌结算";
            case MatchPhase.MatchEnded p->"对局结束";
            default->"结算";
        };return at+" · "+state;
    }
    public ReplayRecord finish(TheMahjongDriver driver) {
        return finish(driver, seat -> RiichiSkin.CLASSIC);
    }
    public ReplayRecord finish(TheMahjongDriver driver, java.util.function.IntFunction<RiichiSkin> skinAtSeat) {
        if(finished||!(driver.currentPhase() instanceof MatchPhase.MatchEnded))throw new IllegalStateException("Match has not completed naturally");
        capture(driver,skinAtSeat);finished=true;
        var players=driver.currentRound().players();
        var participants=people.stream().map(p->new ReplayRecord.Participant(p.seat(),p.uuid(),p.name(),players.get(p.seat()).points())).toList();
        return new ReplayRecord(id,startedAt,System.currentTimeMillis(),preset,participants,List.copyOf(frames));
    }
    public CompoundTag toNbt() {
        var tag=new ReplayRecord(id,startedAt,0,preset,people,frames).toNbt();tag.putBoolean("Finished",finished);return tag;
    }
    public static ReplayRecorder fromNbt(CompoundTag tag) {
        var record=ReplayRecord.fromNbt(tag);
        var recorder=new ReplayRecorder(record.id(),record.startedAt(),record.preset(),record.participants(),record.frames());
        recorder.finished=tag.getBooleanOr("Finished",false);return recorder;
    }
}
