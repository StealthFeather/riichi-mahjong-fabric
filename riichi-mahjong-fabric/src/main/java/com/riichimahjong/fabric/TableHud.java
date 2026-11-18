package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import java.util.*;

/** Player-scoped vanilla HUD; no chat history or concealed information. */
final class TableHud {
    final List<ServerBossEvent> bars=new ArrayList<>();
    TableHud(){
        for(int i=0;i<3;i++)bars.add(new ServerBossEvent(UUID.randomUUID(),Component.empty(),i==0?BossEvent.BossBarColor.BLUE:BossEvent.BossBarColor.GREEN,BossEvent.BossBarOverlay.PROGRESS));
    }
    void update(MahjongTableBlockEntity table,PublicTable board){
        var level=(ServerLevel)table.getLevel();
        Set<ServerPlayer> audience=new HashSet<>();
        if(table.driver()!=null){
            for(var seat:board.seats())if(seat.owner()!=null){var p=level.getServer().getPlayerList().getPlayer(seat.owner());if(p!=null&&PortViews.near(p,level,table.getBlockPos()))audience.add(p);}
            for(var uuid:table.spectators()){
                var player=level.getServer().getPlayerList().getPlayer(uuid);
                if(player!=null&&SpectatorSeats.receivesHud(true,table.seatOfPlayer(uuid).isPresent(),table.isWatching(uuid),
                        PortViews.viewingNear(player,level,table.getBlockPos())))audience.add(player);
            }
        }
        if(table.replay()!=null)for(var p:level.players())
            if(PortViews.near(p,level,table.getBlockPos()))audience.add(p);
        Map<Integer,Integer> grades=table.replay()!=null&&table.replay().analysisEnabled()
                ?com.riichimahjong.replay.ReplayAnalysis.roundScores(table.replay().record().id())
                        .getOrDefault(table.replay().frame().roundIndex(),Map.of()):Map.of();
        var tournament=new HashMap<Integer,com.themahjong.competition.CompetitiveStandings.Result>();
        if(table.driver()!=null)com.themahjong.competition.CompetitiveStandings.forMatch(table.driver().match())
                .forEach(result->tournament.put(result.seat(),result));
        String scores1="",scores2="";
        for(int i=0;i<board.seats().size();i++){
            var s=board.seats().get(i);if(s.handCount()==0&&s.points()==0)continue;
            String text=(board.turnSeat()==i?"▶ ":"")+TableSeating.wind(s.wind())+" · "+s.name()+" "+s.points()+(s.riichi()?" 立直":"");
            if(tournament.containsKey(i))text+=" · 赛点 "+tournament.get(i).formatted();
            if(grades.containsKey(i))text+=" · 本局一致性评分 "+grades.get(i);
            if(i<2)scores1+=(scores1.isEmpty()?"":"   |   ")+text;else scores2+=(scores2.isEmpty()?"":"   |   ")+text;
        }
        String status=board.label().replace('\n',' ').trim();
        String[] names={board.roundLabel()+(status.isEmpty()?"":" · "+status),scores1,scores2};
        for(int i=0;i<bars.size();i++){
            var bar=bars.get(i);bar.setName(Component.literal(names[i]));bar.setVisible(!names[i].isEmpty());
            int initialWall=board.totalTiles()-(board.sichuan()?52:board.openingWallDraws())-(board.sichuan()?0:14);
            bar.setProgress(i==0?Math.clamp(board.liveWall()/(float)Math.max(1,initialWall),0f,1f):1f);
            for(var p:List.copyOf(bar.getPlayers()))if(!audience.contains(p))bar.removePlayer(p);
            for(var p:audience)if(!bar.getPlayers().contains(p))bar.addPlayer(p);
        }
    }
    void destroy(){bars.forEach(ServerBossEvent::removeAllPlayers);}
}
