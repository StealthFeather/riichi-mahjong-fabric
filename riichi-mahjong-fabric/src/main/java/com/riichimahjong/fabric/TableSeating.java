package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** A shared, authenticated seating path for block clicks, virtual seats, menus and commands. */
public final class TableSeating {
    public static String wind(int seat){return switch(seat){case 0->"东";case 1->"南";case 2->"西";case 3->"北";default->"未知";};}
    public static boolean join(ServerPlayer player,MahjongTableBlockEntity table,int seat){
        if(!available(player,table))return false;
        if(table.hasActiveMatch())return message(player,"对局已经开始，暂时不能入座。请等待结束或由房主结束对局。");
        var seats=table.seats();
        if(seat<0||seat>=seats.size())return message(player,"没有这个座位。");
        var info=seats.get(seat);
        if(!info.enabled())return message(player,wind(seat)+"家座位已关闭，请在牌桌设置中启用。");
        if(info.occupant().filter(id->!id.equals(player.getUUID())).isPresent())return message(player,wind(seat)+"家已有玩家，请选择其他空位。");
        if(table.seatOfPlayer(player.getUUID()).orElse(-1)==seat){
            message(player,"你已入座："+wind(seat)+"家。潜行右键桌子中央开始对局。");return true;
        }
        if(!table.claimSeat(seat,player.getUUID()))return message(player,"入座失败，座位状态已变化，请重新选择。");
        message(player,"已入座："+wind(seat)+"家。潜行右键桌子中央开始对局，/mahjong leave 离座。");return true;
    }
    public static boolean joinNearest(ServerPlayer player,MahjongTableBlockEntity table){
        if(!available(player,table))return false;
        int current=table.seatOfPlayer(player.getUUID()).orElse(-1);
        if(current>=0){message(player,"你已入座："+wind(current)+"家。潜行右键桌子中央开始对局。");return true;}
        if(table.hasActiveMatch())return message(player,"对局已经开始，暂时不能入座。请等待本局结束。");
        int chosen=-1;double best=Double.MAX_VALUE;var seats=table.seats();
        for(int seat=0;seat<seats.size();seat++){
            var info=seats.get(seat);if(!info.enabled()||info.occupant().isPresent())continue;
            double distance=PortViews.anchor(table.getBlockPos(),PortViews.outward(seat,table.getBlockState()),0,.5,1.52).distanceToSqr(player.position());
            if(distance<best){best=distance;chosen=seat;}
        }
        return chosen<0?message(player,"没有可用空位，请在牌桌设置中查看座位。"):join(player,table,chosen);
    }
    public static int sideOfPlayer(ServerPlayer player,MahjongTableBlockEntity table){
        int chosen=0;double best=Double.MAX_VALUE;
        for(int seat=0;seat<table.seats().size();seat++){
            double distance=PortViews.anchor(table.getBlockPos(),PortViews.outward(seat,table.getBlockState()),0,.5,1.52).distanceToSqr(player.position());
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
