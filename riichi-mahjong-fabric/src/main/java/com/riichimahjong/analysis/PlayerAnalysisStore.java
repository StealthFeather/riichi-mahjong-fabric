package com.riichimahjong.analysis;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** World-local, UUID-keyed statistics. Every mutation is committed atomically before it is published. */
public final class PlayerAnalysisStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static PlayerAnalysisStore active;
    private final Path directory;
    private final Map<UUID,PlayerData> players=new HashMap<>();

    public PlayerAnalysisStore(Path directory) { this.directory=Objects.requireNonNull(directory); }
    public static void start(MinecraftServer server) {
        active=new PlayerAnalysisStore(server.getWorldPath(LevelResource.ROOT).resolve("data/riichi-analysis"));
    }
    /** Null before world startup and after shutdown. No statistics can be written to another world. */
    public static PlayerAnalysisStore get() { return active; }
    public static void stop() { active=null; }

    public record HandResult(UUID player,boolean won,boolean selfDrawn,boolean dealtIn,List<String> yaku) {
        public HandResult { Objects.requireNonNull(player); yaku=List.copyOf(yaku); }
    }
    public record MatchUpdate(boolean recorded,long previousTenths,long currentTenths,long actualDeltaTenths) {}
    public record Profile(long hands,long wins,long tsumo,long ron,long dealtIn,long matches,
                          Map<String,Long> yaku,long scoreTenths,List<Double> recent) {
        public Profile {
            yaku=Collections.unmodifiableMap(new TreeMap<>(yaku));
            recent=Collections.unmodifiableList(new ArrayList<>(recent));
        }
        /** A window is shown only when all of its most recent completed games have usable analysis. */
        public OptionalDouble average(int count) {
            if(count<=0||recent.size()<count)return OptionalDouble.empty();
            double total=0;
            for(int index=recent.size()-count;index<recent.size();index++) {
                Double score=recent.get(index);
                if(score==null||!Double.isFinite(score))return OptionalDouble.empty();
                total+=score;
            }
            return OptionalDouble.of(total/count);
        }
    }

    private static final class PlayerData {
        int version=1;
        Map<String,ModeData> modes=new LinkedHashMap<>();
        Set<String> applied=new HashSet<>();
    }
    private static final class ModeData {
        long hands,wins,tsumo,ron,dealtIn,matches,scoreTenths;
        Map<String,Long> yaku=new TreeMap<>();
        List<Recent> recent=new ArrayList<>();
    }
    private static final class Recent {
        String event;
        Double score;
        Recent(String event,Double score) { this.event=event;this.score=score; }
    }

    public synchronized boolean recordHand(String eventId,AnalysisMode mode,List<HandResult> results) {
        Objects.requireNonNull(eventId);Objects.requireNonNull(mode);
        boolean changed=false;
        var seen=new HashSet<UUID>();
        for(var result:results)if(!seen.add(result.player()))throw new IllegalArgumentException("Duplicate player in one hand");
        for(var result:results) {
            var original=player(result.player());
            String key="hand:"+eventId;
            if(original.applied.contains(key))continue;
            var next=copy(original);
            var data=next.modes.computeIfAbsent(mode.key(),ignored->new ModeData());
            data.hands++;
            if(result.won()) {
                data.wins++;
                if(result.selfDrawn())data.tsumo++;else data.ron++;
                // Each listed yaku is one occurrence in this winning hand, even when worth multiple han.
                new LinkedHashSet<>(result.yaku()).forEach(yaku->data.yaku.merge(yaku,1L,Long::sum));
            }
            if(result.dealtIn())data.dealtIn++;
            next.applied.add(key);
            commit(result.player(),next);
            changed=true;
        }
        return changed;
    }

    public synchronized MatchUpdate recordMatch(String eventId,AnalysisMode mode,UUID player,
                                                 Integer deltaTenths,Long matched,Long decisions) {
        Objects.requireNonNull(eventId);Objects.requireNonNull(mode);Objects.requireNonNull(player);
        var original=player(player);
        var existing=original.modes.get(mode.key());
        long previous=existing==null?0:existing.scoreTenths;
        String key="match:"+eventId;
        if(original.applied.contains(key))return new MatchUpdate(false,previous,previous,0);
        var next=copy(original);
        var data=next.modes.computeIfAbsent(mode.key(),ignored->new ModeData());
        data.matches++;
        if(deltaTenths!=null&&mode.ranked()) {
            data.scoreTenths=deltaTenths>0&&previous>Long.MAX_VALUE-deltaTenths
                    ?Long.MAX_VALUE:Math.max(0,previous+(long)deltaTenths);
        }
        data.recent.add(new Recent(eventId,consistency(matched,decisions)));
        while(data.recent.size()>50)data.recent.removeFirst();
        next.applied.add(key);
        commit(player,next);
        return new MatchUpdate(true,previous,data.scoreTenths,data.scoreTenths-previous);
    }

    /** Allows a late asynchronous analysis to finish the original game's history without settling it twice. */
    public synchronized boolean recordConsistency(String eventId,AnalysisMode mode,UUID player,long matched,long decisions) {
        Double score=consistency(matched,decisions);
        if(score==null)return false;
        var original=player(player);
        var data=original.modes.get(mode.key());
        if(data==null||data.recent.stream().noneMatch(entry->entry.event.equals(eventId)))return false;
        var next=copy(original);
        for(var entry:next.modes.get(mode.key()).recent)if(entry.event.equals(eventId))entry.score=score;
        commit(player,next);
        return true;
    }

    public synchronized Profile profile(UUID player,AnalysisMode mode) {
        var data=player(player).modes.get(mode.key());
        if(data==null)data=new ModeData();
        return new Profile(data.hands,data.wins,data.tsumo,data.ron,data.dealtIn,data.matches,
                data.yaku,data.scoreTenths,data.recent.stream().map(entry->entry.score).toList());
    }
    public synchronized List<AnalysisMode> modes(UUID player) {
        return player(player).modes.keySet().stream().map(AnalysisMode::fromKey)
                .flatMap(Optional::stream).sorted(Comparator.comparing(AnalysisMode::key)).toList();
    }
    private static Double consistency(Long matched,Long decisions) {
        if(matched==null||decisions==null||decisions<=0||matched<0||matched>decisions)return null;
        return 100.0*matched/decisions;
    }
    private PlayerData player(UUID uuid) {
        return players.computeIfAbsent(uuid,key->{
            Path path=directory.resolve(key+".json");
            if(!Files.exists(path))return new PlayerData();
            try {
                PlayerData data=JSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),PlayerData.class);
                if(data==null||data.version!=1||data.modes==null||data.applied==null)
                    throw new IOException("Unsupported or incomplete statistics document");
                for(var entry:data.modes.entrySet()) {
                    var mode=entry.getValue();
                    if(AnalysisMode.fromKey(entry.getKey()).isEmpty()||mode==null||mode.yaku==null||mode.recent==null
                            ||mode.scoreTenths<0)throw new IOException("Invalid statistics mode");
                }
                return data;
            } catch(IOException|JsonParseException exception) {
                throw new IllegalStateException("Cannot read Mahjong statistics for "+key,exception);
            }
        });
    }
    private static PlayerData copy(PlayerData data) { return JSON.fromJson(JSON.toJson(data),PlayerData.class); }
    private void commit(UUID player,PlayerData next) {
        Path target=directory.resolve(player+".json");
        Path temporary=null;
        try {
            Files.createDirectories(directory);
            temporary=Files.createTempFile(directory,player+"-",".tmp");
            Files.writeString(temporary,JSON.toJson(next),StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException exception) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
            players.put(player,next);
        } catch(IOException exception) {
            throw new IllegalStateException("Cannot save Mahjong statistics for "+player,exception);
        } finally {
            if(temporary!=null)try { Files.deleteIfExists(temporary); }catch(IOException ignored) {}
        }
    }
}
