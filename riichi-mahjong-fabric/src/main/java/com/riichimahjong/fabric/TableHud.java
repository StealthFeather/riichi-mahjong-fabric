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
        for(int i=0;i<4;i++)bars.add(new ServerBossEvent(UUID.randomUUID(),Component.empty(),i==0?BossEvent.BossBarColor.BLUE:i==3?BossEvent.BossBarColor.YELLOW:BossEvent.BossBarColor.GREEN,BossEvent.BossBarOverlay.PROGRESS));
    }
    void update(MahjongTableBlockEntity table,PublicTable board){
        var level=(ServerLevel)table.getLevel();
        Set<ServerPlayer> audience=new HashSet<>();
        if(table.driver()!=null)for(var seat:board.seats())if(seat.owner()!=null){var p=level.getServer().getPlayerList().getPlayer(seat.owner());if(p!=null&&PortViews.near(p,level,table.getBlockPos()))audience.add(p);}
        String scores1="",scores2="";
        for(int i=0;i<board.seats().size();i++){
            var s=board.seats().get(i);if(s.handCount()==0&&s.points()==0)continue;
            String text=(board.turnSeat()==i?"▶ ":"")+TableSeating.wind(s.wind())+" · "+s.name()+" "+s.points()+(s.riichi()?" 立直":"");
            if(i<2)scores1+=(scores1.isEmpty()?"":"   |   ")+text;else scores2+=(scores2.isEmpty()?"":"   |   ")+text;
        }
        String result=board.label().replace('\n',' ').trim();
        String[] names={board.roundLabel(),scores1,scores2,result};
        int players=(int)board.seats().stream().filter(s->s.handCount()>0).count();
        for(int i=0;i<bars.size();i++){
            var bar=bars.get(i);bar.setName(Component.literal(names[i]));bar.setVisible(!names[i].isEmpty());
            bar.setProgress(i==0?Math.clamp(board.liveWall()/(players==3?55f:70f),0f,1f):1f);
            for(var p:List.copyOf(bar.getPlayers()))if(!audience.contains(p))bar.removePlayer(p);
            for(var p:audience)if(!bar.getPlayers().contains(p))bar.addPlayer(p);
        }
    }
    void destroy(){bars.forEach(ServerBossEvent::removeAllPlayers);}
}
