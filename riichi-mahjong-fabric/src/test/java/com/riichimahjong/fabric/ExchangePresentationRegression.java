package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.driver.bots.StupidActiveBot;
import com.themahjong.sichuan.SichuanState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static com.riichimahjong.fabric.RulesRegression.check;

/** A swap is accepted once, then its exact selected physical tiles may be displayed in flight. */
public final class ExchangePresentationRegression {
    private static List<MahjongPlayerInterface> bots(int count) {
        return java.util.stream.IntStream.range(0,count).mapToObj(i->(MahjongPlayerInterface)new StupidActiveBot()).toList();
    }
    private static TheMahjongDriver start(RuleSetPreset preset,boolean jokers,int seed) {
        var driver=new TheMahjongDriver(preset.newMatch(MatchLength.SOUTH,true,jokers,false),bots(preset.playerCount()),new Random(seed));
        driver.startMatch();return driver;
    }
    private static Map<TheMahjongTile,Integer> bag(List<TheMahjongTile> tiles) {
        var out=new HashMap<TheMahjongTile,Integer>();tiles.forEach(tile->out.merge(tile,1,Integer::sum));return out;
    }
    private static boolean completed(TheMahjongDriver driver) { return !ExchangePresentation.pending(driver); }
    private static ExchangePresentation.Session exchange(TheMahjongDriver driver) {
        check(ExchangePresentation.pending(driver),"fixture did not start in exchange");
        for(int tick=0;tick<12&&!completed(driver);tick++)driver.advance(30);
        check(completed(driver),"BOT exchange failed to complete");
        check(driver.currentRound().players().stream().allMatch(p->p.currentHand().size()==13),
                "driver drew before the accepted thirteen-tile exchange could be animated");
        if(driver.sichuanState()!=null)check(driver.sichuanState().stage()==SichuanState.Stage.VOID_SUIT,
                "Sichuan automatically chose dingque before yielding its exchange presentation");
        else check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw,"riichi exchange did not yield before drawing");
        var session=ExchangePresentation.captureAfterExchange(driver);
        check(session!=null,"accepted exchange cannot be reconstructed for display");return session;
    }
    private static void exactPlansAndRandomDirections() {
        for(var preset:RuleSetPreset.values())for(boolean jokers:new boolean[]{false,true}) {
            var directions=new HashSet<Integer>();
            for(int seed=0;seed<36;seed++) {
                var driver=start(preset,jokers,seed);
                var original=driver.currentRound();var session=exchange(driver);
                directions.add(session.direction());
                check(session.flights().size()==13*preset.playerCount(),"presentation lost a hand tile");
                check(session.flights().stream().filter(ExchangePresentation.Flight::exchanged).count()==3L*preset.playerCount(),
                        "presentation did not select three physical tiles per seat");
                for(int seat=0;seat<preset.playerCount();seat++) {
                    check(bag(session.beforeOrder(seat)).equals(bag(original.players().get(seat).currentHand())),
                            "reconstructed pre-exchange hand changed an ordinary/red/joker identity");
                    check(bag(session.afterOrder(seat)).equals(bag(driver.currentRound().players().get(seat).currentHand())),
                            "presentation end differs from accepted rules hand");
                    var targets=new HashSet<Integer>();
                    for(var flight:session.flights())if(flight.targetSeat()==seat) {
                        check(targets.add(flight.targetIndex()),"two equal physical copies overlap the same target slot");
                        check(session.afterOrder(seat).get(flight.targetIndex()).equals(flight.tile()),"flight changed its physical tile");
                        check(flight.targetSeat()==(flight.sourceSeat()+(flight.exchanged()?session.direction():0))%session.playerCount(),
                                "flight follows a different exchange direction than the rules");
                    }
                    check(targets.size()==13,"presentation leaves a target hand slot empty");
                }
                var accepted=driver.currentRound();
                for(int tick=0;tick<ExchangePresentation.DURATION_TICKS-1;tick++)check(!session.advance(),"exchange animation ended too early");
                check(session.advance(),"exchange animation exceeded its fixed duration");
                check(driver.currentRound().equals(accepted),"animation mutated the already accepted rules exchange");
            }
            check(directions.equals(preset.playerCount()==4?Set.of(1,2,3):Set.of(1,2)),
                    "random exchange directions omitted clockwise/counterclockwise/opposite: "+preset+" "+directions);
        }
        check(ExchangePresentation.directionLabel(4,2).equals("对家互换"),"opposite exchange label is incorrect");
        check(!ExchangePresentation.directionLabel(3,1).equals("对家互换")&&!ExchangePresentation.directionLabel(3,2).equals("对家互换"),
                "three-player exchange invented an opposite seat");
    }
    private static void trajectoryAndInterruptions() {
        var pos=new BlockPos(-713,79,222);var state=Blocks.STONE.defaultBlockState();
        for(var preset:List.of(RuleSetPreset.MAHJONG_SOUL_4P,RuleSetPreset.MAHJONG_SOUL_SANMA_3P,RuleSetPreset.SICHUAN_4P)) {
            var driver=start(preset,true,19);var session=exchange(driver);
            for(var flight:session.flights()) {
                var start=ExchangeRenderer.pose(session,flight,pos,state,0);
                var end=ExchangeRenderer.pose(session,flight,pos,state,ExchangePresentation.DURATION_TICKS);
                var startDir=PortViews.outward(flight.sourceSeat(),state);var endDir=PortViews.outward(flight.targetSeat(),state);
                check(start.position().distanceTo(TableLayout.handAnchor(pos,startDir,TableLayout.handAlong(flight.sourceIndex(),List.of()),false))<1e-6,
                        "exchange tile does not lift from its exact selected hand slot");
                check(end.position().distanceTo(TableLayout.handAnchor(pos,endDir,TableLayout.handAlong(flight.targetIndex(),List.of()),false))<1e-6,
                        "exchange tile does not land at its accepted sorted hand slot");
                if(flight.exchanged()) {
                    var lifted=ExchangeRenderer.pose(session,flight,pos,state,ExchangePresentation.LIFT_END);
                    check(lifted.position().y>start.position().y+.15,"selected exchange tile never floats above its hand");
                    check(ExchangeRenderer.faceSeat(flight,0)==flight.sourceSeat()
                            &&ExchangeRenderer.faceSeat(flight,ExchangePresentation.TRAVEL_END)==flight.targetSeat(),
                            "private exchange face does not transfer from sender to recipient");
                }
                for(int tick=0;tick<=ExchangePresentation.DURATION_TICKS;tick++) {
                    var pose=ExchangeRenderer.pose(session,flight,pos,state,tick);
                    check(Double.isFinite(pose.position().x)&&Double.isFinite(pose.position().y)&&Double.isFinite(pose.position().z),
                            "exchange animation produced a non-finite position");
                    var q=new Quaternionf(pose.rotation()).rotateY((float)Math.PI);
                    var bounds=TableLayout.TILE_MODEL_BOUNDS;
                    for(double x:new double[]{bounds.minX,bounds.maxX})for(double y:new double[]{bounds.minY,bounds.maxY})for(double z:new double[]{bounds.minZ,bounds.maxZ}) {
                        double worldY=pose.position().y+q.transform(new Vector3f((float)x,(float)y,(float)z)).y*pose.scale();
                        check(worldY>=pos.getY()+TableLayout.TABLE_TOP-1e-6,"floating exchange tile intersects the table");
                    }
                }
            }
            // No animation state is saved: the accepted swap remains final and is not repeated on reload.
            var accepted=driver.currentRound();
            var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);DriverNbt.writeDriver(out,driver);
            var input=TagValueInput.create(ProblemReporter.DISCARDING,HolderLookup.Provider.create(Stream.empty()),out.buildResult());
            var restored=DriverNbt.readDriver(input,bots(preset.playerCount()),new Random(4));
            check(completed(restored)&&restored.currentRound().equals(accepted),"interrupted exchange was repeated or lost on reload");
            int wall=restored.currentRound().liveWall().size();restored.advance(30);
            check(restored.currentRound().liveWall().size()<=wall,"resuming final exchange added tiles back to the wall");
        }
    }
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        exactPlansAndRandomDirections();trajectoryAndInterruptions();
        System.out.println("PASS: random exchange directions, exact selected/red/joker identities, 52-tick floating paths, private sender/recipient handoff, pre-draw yield and interrupted NBT retain one accepted swap");
    }
}
