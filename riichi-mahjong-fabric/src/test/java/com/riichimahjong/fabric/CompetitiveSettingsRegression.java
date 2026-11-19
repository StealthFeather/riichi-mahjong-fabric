package com.riichimahjong.fabric;

import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.*;
import com.themahjong.competition.CompetitiveRules.Profile;
import com.themahjong.driver.*;
import com.themahjong.yaku.WinResult;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Cross-layer tournament selection, old-save compatibility and private AI/display boundaries. */
public final class CompetitiveSettingsRegression {
    private static final HolderLookup.Provider REGISTRIES=HolderLookup.Provider.create(Stream.empty());
    private static CompoundTag save(TheMahjongMatch match) {
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,REGISTRIES);
        MatchNbt.writeMatch(out,match);return out.buildResult();
    }
    private static TheMahjongMatch load(CompoundTag nbt) {
        return MatchNbt.readMatch(TagValueInput.create(ProblemReporter.DISCARDING,REGISTRIES,nbt));
    }
    private static void presetsAndNbt() {
        for(var preset:List.of(RuleSetPreset.JPML_4P,RuleSetPreset.M_LEAGUE_4P)) {
            boolean jpml=preset==RuleSetPreset.JPML_4P;
            for(var length:MatchLength.values())for(boolean exchange:List.of(false,true))
                for(boolean jokers:List.of(false,true))for(boolean blood:List.of(false,true)) {
                    var match=preset.newMatch(length,exchange,jokers,blood).validate();var rules=match.ruleSet();
                    check(match.playerCount()==4&&match.startingPoints()==(jpml?30000:25000)
                            &&match.targetPoints()==30000,"tournament factory lost its real starting points");
                    check(match.roundCount()==length.roundCount()&&rules.exchangeThree()==exchange
                            &&rules.jokersEnabled()==jokers&&rules.bloodBattle()==blood,"table variants were not retained");
                    check(rules.competitiveProfile()==(jpml?Profile.JPML_A:Profile.M_LEAGUE),"variants lost competitive profile");
                    int red=match.tileSet().copiesPerTile().entrySet().stream().filter(e->e.getKey().redDora()&&!com.themahjong.variants.Jokers.isJoker(e.getKey())).mapToInt(Map.Entry::getValue).sum();
                    check(red==(jpml?0:3),"tournament wall has the wrong number of red fives");
                    check(match.equals(load(save(match))),"new tournament profile did not survive match NBT");
                    var active=match.startRound(new Random(31));
                    check(active.equals(load(save(active))),"active tournament save changed rules, points or wall");
                }
            check(!preset.family().supportsThreePlayers()&&preset.family().preset(3)==preset,"four-player tournament allowed a sanma selection");
        }
        for(var preset:List.of(RuleSetPreset.WRC_4P,RuleSetPreset.MAHJONG_SOUL_4P,RuleSetPreset.SICHUAN_4P)) {
            var match=preset.newMatch();var nbt=save(match);nbt.remove("competitiveProfile");
            check(load(nbt).equals(match),"a legacy save changed rules after adding profiles");
            nbt.putString("competitiveProfile","unknown_future_profile");
            check(load(nbt).equals(match),"unknown profile did not use legacy-compatible STANDARD");
        }
    }
    private static void aiAndPublicDora() {
        var original=AiControllerRegression.request();
        for(var preset:List.of(RuleSetPreset.JPML_4P,RuleSetPreset.M_LEAGUE_4P)) {
            boolean jpml=preset==RuleSetPreset.JPML_4P;var rules=preset.newMatch().ruleSet();
            var request=new DecisionRequest(original.seat(),original.phase(),original.legalActions(),original.round(),rules);
            var snapshot=AiSnapshot.create(request,BotDifficulty.NORMAL);var options=snapshot.getAsJsonObject("rules");
            check(options.get("redFives").getAsBoolean()==!jpml&&options.get("ippatsuAllowed").getAsBoolean()==!jpml
                    &&options.get("uraDoraAllowed").getAsBoolean()==!jpml&&options.get("kanDoraAllowed").getAsBoolean()==!jpml,
                    "AI evaluation did not receive the actual tournament bonus rules");
            check(options.get("countedYakumanAllowed").getAsBoolean()==jpml,"AI counted-yakuman evaluation differs from tournament profile");
            check(!snapshot.has("uraDoraIndicators")&&!snapshot.has("hands")&&!snapshot.has("wall"),"tournament AI leaks private zones");
            var r=original.round();var players=new ArrayList<>(r.players());var p=players.get(0);
            players.set(0,new TheMahjongPlayer(p.points(),TheMahjongPlayer.RiichiState.RIICHI,true,p.currentHand(),p.melds(),p.discards(),p.temporaryFuritenTiles()));
            var round=new TheMahjongRound(r.roundWind(),r.handNumber(),r.honba(),r.riichiSticks(),r.dealerSeat(),r.state(),r.currentTurnSeat(),-1,r.activeTile(),r.liveWall(),r.rinshanTiles(),Collections.nCopies(5,tile(0,1)),Collections.nCopies(5,tile(1,6)),1,players,List.of());
            var win=new WinResult(List.of(),List.of(),1,30,0,List.of(1000,-1000,0,0));
            var phase=new MatchPhase.RoundEnded(List.of(win));
            check(DoraTiles.uraVisible(round,phase,rules)==!jpml,"public ura visibility ignores tournament rules");
            check(DoraTiles.tiles(round,phase,rules).contains(PublicTable.code(tile(1,7)))==!jpml,"hidden/disabled ura affected hand highlighting");
        }
    }
    private static void responsibilityNbt() {
        var base=RuleSetPreset.M_LEAGUE_4P.newMatch().startRound(new Random(7));var r=base.currentRound().orElseThrow();
        var players=new ArrayList<>(r.players());
        var fourth=new TheMahjongMeld.Daiminkan(Collections.nCopies(4,tile(3,4)),3,2,4,true);
        players.set(0,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,List.of(tile(4,1),tile(4,1)),
                List.of(new TheMahjongMeld.Ankan(Collections.nCopies(4,tile(0,1))),
                        new TheMahjongMeld.Ankan(Collections.nCopies(4,tile(1,2))),
                        new TheMahjongMeld.Ankan(Collections.nCopies(4,tile(2,3))),fourth),List.of(),List.of()));
        var round=new TheMahjongRound(r.roundWind(),r.handNumber(),r.honba(),0,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(tile(4,1)),r.liveWall(),List.of(),r.doraIndicators(),r.uraDoraIndicators(),5,players,List.of());
        var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        var restored=load(save(match));
        check(match.equals(restored),"saving a fourth open kan lost its responsibility marker");
        check(((TheMahjongMeld.Daiminkan)restored.currentRound().orElseThrow().players().get(0).melds().getLast()).completesSuukantsu(),
                "fourth-kan responsibility was silently disabled on reload");
    }
    public static void main(String[] args) {
        presetsAndNbt();aiAndPublicDora();responsibilityNbt();
        System.out.println("PASS: tournament variants and live/legacy NBT retain profiles; AI and public dora follow actual bonus rules without hidden information");
    }
}
