// Import the pinned upstream server implementation. Client rendering/networking is replaced by the Fabric bridge.
const fs=require('node:fs'),p=require('node:path');
const project=__dirname,up=p.join(project,'upstream/neoforge/src/main/java/com/riichimahjong'),dest=p.join(project,'src/main/java/com/riichimahjong');
const files=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(p.join(d,e.name)):[p.join(d,e.name)]);
const groups=['common/BaseMultipartBlock.java','cuterenderer/InteractKey.java','cuterenderer/CuteClickHandler.java','fovnormalizer','mahjongaltar','mahjongcore','mahjongsolitaire','mahjongtable','mahjongtools','clickers','gimmicks','themahjongcompat','registry'];
const excluded=new Set(['registry/ModCreativeTabs.java','registry/ModFluids.java','registry/ModMenus.java','mahjongtable/MahjongTableMenu.java','mahjongtable/MahjongTableSettingsMenu.java','yakugenerator/EnergyPushPlatform.java','yakugenerator/YakuGeneratorMenu.java','mahjongcore/MahjongTileFuels.java']);
groups.push('yakugenerator');
function body(s,signature,replacement){const at=s.indexOf(signature);if(at<0)throw Error('Missing signature '+signature);const start=s.indexOf('{',at);let depth=1,i=start+1,quote='',line=false,block=false;for(;i<s.length&&depth;i++){const a=s[i],b=s[i+1];if(line){if(a==='\n')line=false;continue;}if(block){if(a==='*'&&b==='/'){block=false;i++;}continue;}if(quote){if(a==='\\'){i++;continue;}if(a===quote)quote='';continue;}if(a==='/'&&b==='/'){line=true;i++;continue;}if(a==='/'&&b==='*'){block=true;i++;continue;}if(a==='"'||a==="'"){quote=a;continue;}if(a==='{')depth++;if(a==='}')depth--;}
return s.slice(0,start)+'{\n'+replacement+'\n    }'+s.slice(i);}
for(const group of groups){const entry=p.join(up,group);for(const f of fs.statSync(entry).isDirectory()?files(entry):[entry]){
 const rel=p.relative(up,f).replaceAll('\\','/');if(rel.includes('/client/')||excluded.has(rel)||!f.endsWith('.java'))continue;
 let s=fs.readFileSync(f,'utf8');s=s.replaceAll('net.neoforged.neoforge.registries.DeferredRegister','com.riichimahjong.fabric.DeferredRegister');
 s=s.replaceAll('net.neoforged.neoforge.common.NeoForge','com.riichimahjong.fabric.PortEvents').replaceAll('NeoForge.EVENT_BUS','PortEvents.BUS');
 s=s.replace('import net.neoforged.bus.api.Event;','').replace('extends Event {','{');
 s=s.replaceAll('new Item(props)','new com.riichimahjong.fabric.PortItem(props)').replaceAll('new BlockItem(','new com.riichimahjong.fabric.PortBlockItem(');
 s=s.replaceAll('extends Item {','extends com.riichimahjong.fabric.PortItem {').replaceAll('extends BlockItem {','extends com.riichimahjong.fabric.PortBlockItem {');
 s=s.replaceAll('new net.minecraft.world.item.BucketItem(','new com.riichimahjong.fabric.PortBucketItem(');
 s=s.replaceAll('new net.minecraft.world.level.block.LiquidBlock(','new com.riichimahjong.fabric.PortLiquidBlock(');
 s=s.replaceAll('extends BaseEntityBlock {','extends BaseEntityBlock implements com.riichimahjong.fabric.VisibleBlock {');
 if(rel==='mahjongcore/MahjongTileBlock.java')s=s.replace('extends Block {','extends Block implements com.riichimahjong.fabric.VisibleBlock {');
 if(rel==='clickers/ClickerBlock.java')s=s.replace('extends Block {','extends Block implements com.riichimahjong.fabric.VisibleBlock {');
 if(rel==='mahjongtools/MahjongRiichiStickEntity.java')s=s.replace('extends ThrowableItemProjectile {','extends ThrowableItemProjectile implements eu.pb4.polymer.core.api.entity.PolymerEntity {\n    @Override public net.minecraft.world.entity.EntityType<?> getPolymerEntityType(xyz.nucleoid.packettweaker.PacketContext context){return net.minecraft.world.entity.EntityTypes.SNOWBALL;}');
 if(rel==='fovnormalizer/NormalizeFovMobEffect.java')s=s.replace('extends MobEffect {','extends MobEffect implements eu.pb4.polymer.core.api.other.PolymerMobEffect {\n    @Override public MobEffect getPolymerReplacement(xyz.nucleoid.packettweaker.PacketContext context){return null;}');
 if(rel==='mahjongtable/MahjongTableBlock.java'){
  s=s.replace('import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;','');
  s=s.replace('serverPlayer.openMenu(table, master);','com.riichimahjong.fabric.PortMenus.settings(serverPlayer,table);');
  s=body(s,'public static void registerEvents()',`        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!(player instanceof ServerPlayer)||player.isSpectator())return InteractionResult.PASS;
            var state=level.getBlockState(hit.getBlockPos());if(!(state.getBlock() instanceof MahjongTableBlock block))return InteractionResult.PASS;
            return block.dispatchUse(state,level,hit.getBlockPos(),player,hand,hit);
        });`);
 }
 if(rel==='mahjongtable/MahjongTableHumanPlayer.java'){
  s=body(s,'public boolean tickAfterAdvance(',`        // Drawing to a real held item would broadcast the tile through equipment packets.
        // The vanilla-client bridge displays it only in this player's private virtual hand.
        drawnTileDeliveryAttempted=false;drawnTileDelivered=false;pendingConsume=null;
        boolean ownTurn=phase instanceof MatchPhase.AwaitingDiscard d&&d.seat()==seatIndex;
        boolean changed=false;if(!ownTurn&&riichiPending){riichiPending=false;changed=true;}
        if(ownTurn)maybeAutoQueueRiichiDiscard(level,table,seatIndex);
        return changed;`);
 }
 if(rel==='mahjongtable/MahjongTableBlockEntity.java'){
  s=body(s,'public CompoundTag getUpdateTag(', '        return new CompoundTag(); // Complete match NBT is disk-only, never a public update tag.');
  s=body(s,'public Packet<ClientGamePacketListener> getUpdatePacket()', '        return null;');
  s=s.replace('this.driver = new TheMahjongDriver(match, buildPlayersFromSeats(activeSeats), new Random(seed));','this.driver = new TheMahjongDriver(match, buildPlayersFromSeats(activeSeats), new java.security.SecureRandom());');
  s=s.replace('new MahjongTableMenu(containerId, playerInv, this)','new MahjongTableMenu(containerId, playerInv, this)');
 }
 // Loader-specific block entity callbacks are handled by Fabric lifecycle events.
 s=s.replaceAll('public void onLoad() {','public void onLoad() {');
 const out=p.join(dest,rel);fs.mkdirSync(p.dirname(out),{recursive:true});fs.writeFileSync(out,'// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.\n'+s);
}}
console.log('Imported upstream server modules.');
