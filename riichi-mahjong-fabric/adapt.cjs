// Mechanical 26.2 -> 26.3 API changes; run after prepare.cjs.
const fs=require('node:fs'),p=require('node:path'),root=p.join(__dirname,'src/main/java');
const walk=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(p.join(d,e.name)):[p.join(d,e.name)]);
for(const f of walk(root).filter(f=>f.endsWith('.java'))){
 let s=fs.readFileSync(f,'utf8');
 s=s.replaceAll('org.jetbrains.annotations.Nullable','org.jspecify.annotations.Nullable');
 if(f.endsWith('Block.java'))s=s.replace(/    public static final MapCodec<[\s\S]*?;\r?\n/g,'');
 s=s.replace(/    @Override\r?\n    protected (?:abstract )?MapCodec<[^\n]+ codec\(\)(?:;| \{[\s\S]*?\n    \})/g,'');
 s=s.replaceAll('entity.invulnerableTime = 0;','entity.setInvulnerableTime(0);').replaceAll('entity.hurtMarked = true;','');
 s=s.replaceAll('PushReaction.DESTROY','PushReaction.POPPED');
 s=s.replaceAll('data.contains(TABLE_STATE_TAG)','data.copyTag().contains(TABLE_STATE_TAG)');
 s=s.replaceAll('getPolymerReplacement(xyz.nucleoid.packettweaker.PacketContext context)','getPolymerReplacement(MobEffect effect,net.fabricmc.fabric.api.networking.v1.context.PacketContext context)');
 s=s.replace('PortViews.ensure(player.level(),pos.immutable(),state)','PortViews.ensure(player,pos.immutable())');
 if(f.endsWith('YakuGeneratorBlock.java'))s=s.replace(/        serverPlayer.openMenu\([\s\S]*?\n                pos\);/,'        com.riichimahjong.fabric.PortMenus.generator(serverPlayer,machine);');
 if(f.endsWith('MahjongAltarBlockEntity.java'))s=s.replace('sl.recipeAccess().recipeMap().byType(ModRecipeTypes.MAHJONG_ALTAR.get())','sl.recipeAccess().getRecipes().stream().filter(h -> h.value().getType() == ModRecipeTypes.MAHJONG_ALTAR.get()).map(h -> (RecipeHolder<MahjongAltarRecipe>)(RecipeHolder<?>)h).toList()');
 if(f.endsWith('MahjongRiichiStickEntity.java')&&!s.includes('implements ItemSupplier, '))s=s.replace('implements ItemSupplier {','implements ItemSupplier, eu.pb4.polymer.core.api.entity.PolymerEntity {\n    @Override public EntityType<?> getPolymerEntityType(net.fabricmc.fabric.api.networking.v1.context.PacketContext ctx){return net.minecraft.world.entity.EntityTypes.SNOWBALL;}');
 fs.writeFileSync(f,s);
}
