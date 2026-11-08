package com.riichimahjong.yakugenerator;

import java.lang.reflect.*;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/** Optional Team Reborn Energy API integration; retains RF storage when no receiver API is installed. */
public final class EnergyPushPlatform {
    private static BlockApiLookup<Object,Direction> lookup;
    private static Class<?> energyClass;private static Method insert;
    @SuppressWarnings("unchecked") public static void register(){
        try{
            energyClass=Class.forName("team.reborn.energy.api.EnergyStorage");
            lookup=(BlockApiLookup<Object,Direction>)energyClass.getField("SIDED").get(null);
            insert=energyClass.getMethod("insert",long.class,TransactionContext.class);
            lookup.registerForBlockEntity((be,side)->{
                if(be.loaderEnergyPushState==null)be.loaderEnergyPushState=new Handler(be).proxy();return be.loaderEnergyPushState;
            },com.riichimahjong.registry.ModBlockEntities.YAKU_GENERATOR_BLOCK_ENTITY.get());
        }catch(ClassNotFoundException ignored){/* No energy consumers are present without an energy API. */}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported Fabric Energy API",e);}
    }
    public static int tickPush(YakuGeneratorBlockEntity machine){
        if(lookup==null||machine.getLevel()==null)return 0;int total=0;
        for(var dir:Direction.values()){
            int available=machine.getEnergyStored();if(available<=0)break;
            var receiver=lookup.find(machine.getLevel(),machine.getBlockPos().relative(dir),dir.getOpposite());if(receiver==null)continue;
            try(var transaction=Transaction.openOuter()){
                long amount=(long)insert.invoke(receiver,(long)available,transaction);int accepted=(int)Math.clamp(amount,0,available);
                if(accepted>0){transaction.commit();machine.extractEnergy(accepted,false);total+=accepted;}
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Energy receiver failed",e);}
        }return total;
    }
    private static final class Handler extends SnapshotParticipant<Integer> implements InvocationHandler {
        final YakuGeneratorBlockEntity be;Handler(YakuGeneratorBlockEntity be){this.be=be;}
        Object proxy(){return Proxy.newProxyInstance(energyClass.getClassLoader(),new Class<?>[]{energyClass},this);}
        @Override public Object invoke(Object proxy,Method method,Object[] args){return switch(method.getName()){
            case "supportsInsertion"->false;case "supportsExtraction"->true;case "getAmount"->(long)be.getEnergyStored();case "getCapacity"->(long)be.getMaxEnergyStored();case "insert"->0L;
            case "extract"->{long max=(long)args[0];if(max<=0)yield 0L;updateSnapshots((TransactionContext)args[1]);yield (long)be.extractEnergy((int)Math.min(Integer.MAX_VALUE,max),false);}
            case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];case "toString"->"Riichi Mahjong energy output";default->throw new UnsupportedOperationException(method.toString());
        };}
        @Override protected Integer createSnapshot(){return be.getEnergyStored();}
        @Override protected void readSnapshot(Integer amount){be.fabricRestoreEnergy(amount);}
    }
}
