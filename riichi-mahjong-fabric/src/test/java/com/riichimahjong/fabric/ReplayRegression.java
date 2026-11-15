package com.riichimahjong.fabric;

import com.riichimahjong.replay.ReplayRecord;
import com.riichimahjong.replay.ReplayArchiveQueue;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.riichimahjong.replay.ReplayRecorder;
import com.riichimahjong.replay.ReplaySession;
import com.themahjong.TheMahjongMatch;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.driver.TheMahjongDriver;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.concurrent.CompletableFuture;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Accepted actions, restart continuity, database payload and playback cursor, without a server. */
public final class ReplayRegression {
    private static final UUID VIEWER=UUID.fromString("02151f19-91f8-4d24-b5b1-251c9d5d56d3");
    private static final UUID SUBSTITUTE=UUID.fromString("72151f19-91f8-4d24-b5b1-251c9d5d56d3");
    private static final HolderLookup.Provider REGISTRIES=HolderLookup.Provider.create(Stream.empty());

    private static List<MahjongPlayerInterface> fastPlayers(int count) {
        return IntStream.range(0,count).mapToObj(i->(MahjongPlayerInterface)(request,seconds)->
                request.legalActions().stream().filter(a->a instanceof PlayerAction.Draw
                        ||a instanceof PlayerAction.Discard||a instanceof PlayerAction.Pass).findFirst()).toList();
    }
    private static ReplayRecord complete(int count) {
        var match=(count==4?TheMahjongMatch.defaults():TheMahjongMatch.defaultTenhouSanma())
                .withRoundCount(1).withTargetPoints(count==4?30000:35000);
        var driver=new TheMahjongDriver(match,fastPlayers(count),new Random(17));
        var participants=IntStream.range(0,count).mapToObj(i->new ReplayRecord.Participant(i,i==0?VIEWER:null,i==0?"测试玩家":"BOT",0)).toList();
        var holder=new ReplayRecorder[]{new ReplayRecorder(count==4?"WRC_4P":"TENHOU_SANMA_3P",participants)};
        driver.setStateObserver(d->holder[0].capture(d));
        driver.startMatch();
        driver.advance(.05);
        var draft=ReplayRecord.fromNbt(holder[0].toNbt());
        check(draft.frames().size()>10,"observer lost actions accepted within the same advance()");
        check(draft.frames().stream().anyMatch(f->f.driver().getCompoundOrEmpty("phase").getStringOr("type","").equals("AWAITING_DISCARD")),"draw-to-discard decision frame was lost");
        check(!draft.completed(),"unfinished round became a completed replay");
        boolean rejected=false;
        try{new ReplaySession(draft,VIEWER,false);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"playback accepted an unfinished record");

        // Resume the history the same way the table does after a world save/reload.
        holder[0]=ReplayRecorder.fromNbt(holder[0].toNbt());
        holder[0].remember(0,SUBSTITUTE,"接替玩家");
        int guard=0;
        while(!(driver.currentPhase() instanceof MatchPhase.MatchEnded)) {
            check(guard++<20,"one-wind fixture did not finish: "+driver.currentPhase()+" completed="+driver.match().completedRounds().size());
            if(driver.currentPhase() instanceof MatchPhase.RoundEnded)driver.advanceRound(false,0);
            else driver.advance(.05);
        }
        var regular=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        var replayOnly=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(regular,driver);DriverNbt.writeReplayDriver(replayOnly,driver);
        var regularTag=regular.buildResult();var replayTag=replayOnly.buildResult();
        check(regularTag.getCompoundOrEmpty("match").getListOrEmpty("completedRounds").size()==count,
                "ordinary world save lost completed hands");
        check(!replayTag.getCompoundOrEmpty("match").contains("completedRounds"),
                "replay writer still traverses and writes previous hands");
        regularTag.getCompoundOrEmpty("match").remove("completedRounds");
        check(regularTag.equals(replayTag),"replay writer changed state beyond omitted completed hands");
        var record=holder[0].finish(driver);
        check(record.completed(),"natural end did not create a completed record");
        check(record.participants().stream().anyMatch(p->SUBSTITUTE.equals(p.uuid())),"substitute participant was omitted");
        check(record.participantAt(0).points()==driver.currentRound().players().getFirst().points(),"final scores were not captured");
        check(record.frames().getLast().roundIndex()==count-1,"terminal frame was treated as another round");
        check(record.frames().stream().noneMatch(f->!f.driver().getCompoundOrEmpty("match").getListOrEmpty("completedRounds").isEmpty()),"frames duplicate all previous hands");
        var copy=ReplayRecord.decode(record.encode());
        check(copy.id().equals(record.id())&&copy.participants().equals(record.participants())&&copy.frames().size()==record.frames().size(),"compressed database payload did not round trip");
        var choice=copy.frames().stream().filter(f->f.driver().getCompoundOrEmpty("phase").getStringOr("type","").equals("AWAITING_DISCARD")).findFirst().orElseThrow();
        var paused=choice.restore(REGISTRIES);var before=paused.match();paused.advance(10);
        check(paused.match()==before,"replay driver produced a gameplay action");
        check(paused.currentRound().liveWall().equals(choice.restore(REGISTRIES).currentRound().liveWall()),"hidden wall did not restore exactly");
        return copy;
    }
    private static void controls(ReplayRecord record) {
        var session=new ReplaySession(record,VIEWER,false);
        session.control(ReplaySession.Control.NEXT_ROUND);
        check(session.frame().roundIndex()==1,"next hand did not seek to the next round");
        session.control(ReplaySession.Control.PREVIOUS_ROUND);
        check(session.index()==0,"previous hand did not seek to the initial round");
        session.control(ReplaySession.Control.PREVIOUS);
        check(session.index()==0,"reverse seek underflowed");
        session.control(ReplaySession.Control.ANALYSIS);check(session.analysisEnabled(),"analysis toggle did not apply");
        int visibleFrame=session.index();long visibleRevision=session.revision();
        check(!session.showAll(),"replay initially exposed other players' hands");
        session.control(ReplaySession.Control.REVEAL);check(session.showAll(),"hand visibility toggle did not apply");
        check(session.index()==visibleFrame&&session.revision()==visibleRevision+1,"revealing other hands must refresh the view without changing replay progress");
        session.control(ReplaySession.Control.REVEAL);check(!session.showAll(),"hide other players' hands did not restore selected-seat visibility");
        session.control(ReplaySession.Control.PERSPECTIVE);check(session.perspective()==1,"perspective did not rotate");
        session.control(ReplaySession.Control.PLAY_PAUSE);
        for(int tick=0;tick<21;tick++)session.tick();
        check(session.index()==1&&session.playing(),"auto playback did not advance after one second");
        session.control(ReplaySession.Control.LAST);check(!session.playing(),"seeking did not pause autoplay");
        session.control(ReplaySession.Control.NEXT);check(session.index()==record.frames().size()-1,"forward seek overflowed");
        session.control(ReplaySession.Control.PLAY_PAUSE);check(session.index()==0&&session.playing(),"play from the end did not restart");
    }
    private static void archiveRecovery(ReplayRecord record) {
        var pending=new ReplayArchiveQueue();pending.offer(record);
        pending.pump(r->CompletableFuture.failedFuture(new IllegalStateException("database worker unavailable")),Runnable::run,()->{});
        check(pending.size()==1,"queue rejection discarded a completed replay");
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);pending.write(out);
        var recovered=new ReplayArchiveQueue();
        recovered.read(TagValueInput.create(ProblemReporter.DISCARDING,REGISTRIES,out.buildResult()));
        check(recovered.size()==1,"completed pending replay did not survive a world save");
        var accepted=new CompletableFuture<Void>();int[] calls={0};
        recovered.pump(r->{calls[0]++;return accepted;},Runnable::run,()->{});
        recovered.pump(r->{calls[0]++;return accepted;},Runnable::run,()->{});
        check(calls[0]==1&&recovered.size()==1,"pending archive was duplicated or removed before durable ack");
        accepted.complete(null);
        check(recovered.isEmpty(),"durably accepted archive was not removed from table queue");
    }
    public static void main(String[] args) {
        var four=complete(4);controls(four);archiveRecovery(four);controls(complete(3));
        System.out.println("PASS: complete 3p/4p replay actions, restart continuity, participant history, payload round trip and playback controls");
    }
}
