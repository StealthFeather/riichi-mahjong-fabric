import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local test client probe. Uses Minecraft's own render thread and screenshot API. */
public final class ClientProbe {
    static Path root;
    static Object minecraft;
    static int consumed;
    public static void premain(String args,Instrumentation instrumentation){start(args,instrumentation);}
    public static void agentmain(String args,Instrumentation instrumentation){start(args,instrumentation);}
    static void start(String args,Instrumentation instrumentation){
        root=Path.of(args);
        var worker=new Thread(()->{
            while(true)try{
                if(minecraft==null){
                    for(var c:instrumentation.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))minecraft=c.getMethod("getInstance").invoke(null);
                }
                if(minecraft!=null){
                    var lines=Files.exists(root.resolve("client-probe-commands.txt"))?Files.readAllLines(root.resolve("client-probe-commands.txt")):List.<String>of();
                    while(consumed<lines.size()){
                        String command=lines.get(consumed++);
                        Object instance=minecraft;
                        ((Executor)instance).execute(()->{try{run(command);}catch(Throwable error){log(error.toString());error.printStackTrace();}});
                    }
                }
                Thread.sleep(200);
            }catch(Throwable error){log(error.toString());try{Thread.sleep(1000);}catch(InterruptedException ignored){return;}}
        },"riichi-client-probe");worker.setDaemon(true);worker.start();
    }
    static Object call(Object object,String method,Object...args)throws Exception{
        for(var m:object.getClass().getMethods())if(m.getName().equals(method)&&m.getParameterCount()==args.length)return m.invoke(object,args);
        throw new NoSuchMethodException(method);
    }
    static Object field(Object object,String name)throws Exception{return object.getClass().getField(name).get(object);}
    static void log(String message){try{Files.writeString(root.resolve("client-probe.log"),java.time.Instant.now()+" "+message+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){e.printStackTrace();}}
    static String json(Object value){
        if(value==null)return "null";
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map)return "{"+String.join(",",map.entrySet().stream().map(entry->json(entry.getKey().toString())+":"+json(entry.getValue())).toList())+"}";
        if(value instanceof Collection<?> values)return "["+String.join(",",values.stream().map(ClientProbe::json).toList())+"]";
        return "\""+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
    }
    static Object displayData(Object entity,String name)throws Exception{
        var accessor=Class.forName("net.minecraft.world.entity.Display").getDeclaredField(name);accessor.setAccessible(true);
        return call(call(entity,"getEntityData"),"get",accessor.get(null));
    }
    static List<Object> vector(Object vector)throws Exception{return List.of(call(vector,"x"),call(vector,"y"),call(vector,"z"));}
    static void entities(Path output,int sample,int total)throws Exception{
        Object level=field(minecraft,"level"),player=field(minecraft,"player");
        var snapshot=new LinkedHashMap<String,Object>();
        snapshot.put("time",java.time.Instant.now().toString());snapshot.put("sample",sample);snapshot.put("total",total);
        var displays=new ArrayList<Map<String,Object>>();
        if(level!=null){
            Class<?> type=Class.forName("net.minecraft.world.entity.Display$ItemDisplay");
            var getter=type.getDeclaredMethod("getItemStack");getter.setAccessible(true);
            Object modelType=Class.forName("net.minecraft.core.component.DataComponents").getField("ITEM_MODEL").get(null);
            for(Object entity:(Iterable<?>)call(level,"entitiesForRendering"))if(type.isInstance(entity)){
                var item=new LinkedHashMap<String,Object>();Object stack=getter.invoke(entity);
                item.put("id",call(entity,"getId"));item.put("uuid",call(entity,"getUUID").toString());
                item.put("position",List.of(call(entity,"getX"),call(entity,"getY"),call(entity,"getZ")));
                item.put("yaw",call(entity,"getYRot"));item.put("pitch",call(entity,"getXRot"));item.put("tickCount",field(entity,"tickCount"));
                item.put("item",stack.toString());Object model=call(stack,"get",modelType);item.put("itemModel",model==null?null:model.toString());
                item.put("empty",call(stack,"isEmpty"));item.put("translation",vector(displayData(entity,"DATA_TRANSLATION_ID")));
                item.put("scale",vector(displayData(entity,"DATA_SCALE_ID")));
                item.put("leftRotation",displayData(entity,"DATA_LEFT_ROTATION_ID").toString());item.put("rightRotation",displayData(entity,"DATA_RIGHT_ROTATION_ID").toString());
                displays.add(item);
            }
            snapshot.put("gameTime",call(level,"getGameTime"));
        }
        displays.sort(Comparator.comparingInt(item->((Number)item.get("id")).intValue()));
        snapshot.put("playerPosition",player==null?null:List.of(call(player,"getX"),call(player,"getY"),call(player,"getZ")));
        snapshot.put("itemDisplays",displays);
        Files.writeString(output,json(snapshot)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        if(sample==total-1)log("entities complete samples="+total+" count="+displays.size()+" file="+output);
    }
    static void sampleEntities(String command){
        String[] words=command.trim().split("\\s+");
        int count=words.length>1?Integer.parseInt(words[1]):command.startsWith("entities-sample")?30:1;
        int interval=words.length>2?Integer.parseInt(words[2]):200;
        if(count<1||count>300||interval<50||interval>10000)throw new IllegalArgumentException("entities [1..300 samples] [50..10000 interval ms] [label]");
        String label=(words.length>3?words[3]:"capture").replaceAll("[^a-zA-Z0-9_-]","_");
        Path output=root.resolve("client-probe-entities-"+label+"-"+System.currentTimeMillis()+".jsonl");
        log("entities started count="+count+" intervalMs="+interval+" file="+output);
        var worker=new Thread(()->{
            for(int index=0;index<count;index++)try{
                int sample=index;var done=new CompletableFuture<Void>();
                ((Executor)minecraft).execute(()->{try{entities(output,sample,count);done.complete(null);}catch(Throwable error){done.completeExceptionally(error);}});
                done.get(10,TimeUnit.SECONDS);
                if(index+1<count)Thread.sleep(interval);
            }catch(Throwable error){log("entities failed: "+error);return;}
        },"riichi-client-entity-sample");worker.setDaemon(true);worker.start();
    }
    static void run(String command)throws Exception{
        if(command.equals("entities")||command.startsWith("entities ")||command.equals("entities-sample")||command.startsWith("entities-sample ")){sampleEntities(command);return;}
        Object gui=field(minecraft,"gui"),screen=call(gui,"screen");
        if(command.equals("status")){
            Object player=field(minecraft,"player");
            log("screen="+(screen==null?"none":screen.getClass().getName()+" title="+call(call(screen,"getTitle"),"getString"))+" player="+(player==null?"none":call(player,"getName"))+" level="+field(minecraft,"level"));
        }else if(command.equals("screenshot")){
            Class.forName("net.minecraft.client.Screenshot").getMethod("grab",minecraft.getClass(),boolean.class).invoke(null,minecraft,false);
            log("screenshot requested");
        }else if(command.equals("accept-local-pack")){
            if(screen==null)throw new IllegalStateException("No pack screen");
            String title=(String)call(call(screen,"getTitle"),"getString");
            if(!title.toLowerCase(Locale.ROOT).contains("resource")&&!title.contains("资源"))throw new IllegalStateException("Not a resource-pack screen: "+title);
            var base=Class.forName("net.minecraft.client.gui.screens.ConfirmScreen");
            if(!base.isInstance(screen))throw new IllegalStateException("Not a ConfirmScreen");
            var callback=base.getDeclaredField("callback");callback.setAccessible(true);
            Class.forName("it.unimi.dsi.fastutil.booleans.BooleanConsumer").getMethod("accept",boolean.class).invoke(callback.get(screen),true);
            log("accepted isolated server resource pack");
        }else if(command.startsWith("command ")){
            call(call(minecraft,"getConnection"),"sendCommand",command.substring(8));
            log("sent test command: "+command.substring(8));
        }else if(command.equals("quit")){
            call(minecraft,"stop");
        }else if(command.equals("reconnect-local")){
            var addressType=Class.forName("net.minecraft.client.multiplayer.resolver.ServerAddress");
            var dataType=Class.forName("net.minecraft.client.multiplayer.ServerData");
            var type=Class.forName("net.minecraft.client.multiplayer.ServerData$Type");
            Object data=dataType.getConstructor(String.class,String.class,type).newInstance("Local Mahjong Test","127.0.0.1:41570",type.getEnumConstants()[0]);
            for(var method:Class.forName("net.minecraft.client.gui.screens.ConnectScreen").getMethods())if(method.getName().equals("startConnecting"))
                method.invoke(null,screen,minecraft,addressType.getMethod("parseString",String.class).invoke(null,"127.0.0.1:41570"),data,false,null);
            log("reconnecting isolated local server");
        }else if(command.equals("close-screen")){
            call(gui,"setScreen",new Object[]{null});
        }else if(command.equals("use")){
            // Exercise the vanilla client's normal use-item packet path at its current ray target.
            log("use target="+field(minecraft,"hitResult"));
            var use=minecraft.getClass().getDeclaredMethod("startUseItem");use.setAccessible(true);use.invoke(minecraft);
        }
    }
}
