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
    static void run(String command)throws Exception{
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
