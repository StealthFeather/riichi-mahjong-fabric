package com.riichimahjong.fabric;

import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Full records stay on the server. Items hold only an opaque reference, stripped from packets. */
public final class RecordVault {
    private static Path directory;
    public static void start(MinecraftServer server){directory=server.getWorldPath(LevelResource.ROOT).resolve("data/riichi-records");}
    public static String put(CompoundTag data){
        if(directory==null)throw new IllegalStateException("Record vault is not loaded");
        String id=UUID.randomUUID().toString();
        try{Files.createDirectories(directory);NbtIo.writeCompressed(data,directory.resolve(id+".nbt"));return id;}
        catch(IOException e){throw new IllegalStateException("Cannot save Mahjong record",e);}
    }
    public static CompoundTag get(String id){
        if(directory==null||id.isEmpty())return null;
        try{UUID uuid=UUID.fromString(id);Path file=directory.resolve(uuid+".nbt");return Files.isRegularFile(file)?NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()):null;}
        catch(IOException|IllegalArgumentException e){return null;}
    }
}
