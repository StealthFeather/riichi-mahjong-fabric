package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** A shared, authenticated seating path for block clicks, virtual seats, menus and commands. */
public final class TableSeating {
    public static boolean leave(ServerPlayer player,MahjongTableBlockEntity table){
        if(!available(player,table))return false;
        int seat=table.seatOfPlayer(player.getUUID()).orElse(-1);
        if(seat<0)return message(player,"你没有在这张牌桌入座。");
        boolean active=table.hasActiveMatch();table.releaseSeat(seat);
        player.sendSystemMessage(Component.empty(),true);
        player.sendSystemMessage(Component.literal(active?"已退出当前牌局，BOT 暂代。右键桌子或 /mahjong join 可接回原座位。":"已离座。"));
        return true;
    }
    public static String wind(int seat){return switch(seat){case 0->"东";case 1->"南";case 2->"西";case 3->"北";default->"未知";};}
    public static boolean join(ServerPlayer player,MahjongTableBlockEntity table,int seat){
        if(!available(player,table))return false;
        var seats=table.seats();
        if(seat<0||seat>=seats.size())return message(player,"没有这个座位。");
        var info=seats.get(seat);
        if(!info.enabled())return message(player,wind(seat)+"家座位已关闭，请在牌桌设置中启用。");
        if(info.occupant().filter(id->!id.equals(player.getUUID())).isPresent())return message(player,wind(seat)+"家已有玩家，请选择其他空位。");
        if(table.seatOfPlayer(player.getUUID()).orElse(-1)==seat){
            message(player,"你已入座："+wind(seat)+"家。");return true;
        }
        if(table.hasActiveMatch()&&!table.canClaimSeat(seat,player.getUUID()))return message(player,"此座位为其他玩家保留，或你已有原座位。请右键桌子或 /mahjong join 接回原座位。");
        if(!table.claimSeat(seat,player.getUUID()))return message(player,"入座失败，座位状态已变化，请重新选择。");
        message(player,table.hasActiveMatch()?"已接管："+wind(seat)+"家，保留当前手牌与分数。":"已入座："+wind(seat)+"家。潜行右键桌子中央开始对局，/mahjong leave 离座。");return true;
    }
    public static boolean joinNearest(ServerPlayer player,MahjongTableBlockEntity table){
        if(!available(player,table))return false;
        int current=table.seatOfPlayer(player.getUUID()).orElse(-1);
        if(current>=0){message(player,"你已入座："+wind(current)+"家。");return true;}
        if(table.hasActiveMatch()){
            int returning=table.returningSeat(player.getUUID()).orElse(-1);
            if(returning>=0)return join(player,table,returning);
        }
        int chosen=-1;double best=Double.MAX_VALUE;var seats=table.seats();
        for(int seat=0;seat<seats.size();seat++){
            var info=seats.get(seat);if(info.occupant().isPresent()||!table.canClaimSeat(seat,player.getUUID()))continue;
            double distance=PortViews.anchor(table.getBlockPos(),PortViews.outward(seat,table.getBlockState()),0,.5,1.25).distanceToSqr(player.position());
            if(distance<best){best=distance;chosen=seat;}
        }
        return chosen<0?message(player,"没有可接管的空位；其他玩家的原座位会为其保留。"):join(player,table,chosen);
    }
    public static int sideOfPlayer(ServerPlayer player,MahjongTableBlockEntity table){
        int chosen=0;double best=Double.MAX_VALUE;
        for(int seat=0;seat<table.seats().size();seat++){
            double distance=PortViews.anchor(table.getBlockPos(),PortViews.outward(seat,table.getBlockState()),0,.5,1.25).distanceToSqr(player.position());
            if(distance<best){best=distance;chosen=seat;}
        }return chosen;
    }
    private static boolean available(ServerPlayer player,MahjongTableBlockEntity table){
        if(player.isSpectator())return message(player,"旁观模式不能入座。");
        if(table.isRemoved()||table.getLevel()!=player.level()||!PortViews.near(player,player.level(),table.getBlockPos())||player.level().getBlockEntity(table.getBlockPos())!=table)return message(player,"请靠近有效的麻将桌再入座。");
        return true;
    }
    private static boolean message(ServerPlayer player,String text){player.sendSystemMessage(Component.literal(text));return false;}
}
