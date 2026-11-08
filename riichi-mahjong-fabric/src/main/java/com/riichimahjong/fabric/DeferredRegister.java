package com.riichimahjong.fabric;

import java.util.*;
import java.util.function.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.item.PolymerItem;

/** Small Fabric registry adapter preserving the upstream supplier-based registration order. */
public class DeferredRegister<T> {
    final ResourceKey<? extends Registry<T>> key;final String namespace;
    final List<Entry<? extends T>> entries=new ArrayList<>();
    DeferredRegister(ResourceKey<? extends Registry<T>> key,String namespace){this.key=key;this.namespace=namespace;}
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key,String namespace){return new DeferredRegister<>(key,namespace);}
    public static Blocks createBlocks(String namespace){return new Blocks(namespace);}
    public static Items createItems(String namespace){return new Items(namespace);}
    public <S extends T> Supplier<S> register(String name,Supplier<S> factory){var e=new Entry<S>(Identifier.fromNamespaceAndPath(namespace,name),factory);entries.add(e);return e;}
    @SuppressWarnings("unchecked") public void flush(){for(var e:entries){var value=e.get();var registry=(Registry<T>)BuiltInRegistries.REGISTRY.getValue(key.identifier());Registry.register(registry,e.id,value);if(value instanceof Item item){PolymerItem.registerOverlay(item,VisibleItem.INSTANCE);if(item instanceof BlockItem bi)bi.registerBlocks(Item.BY_BLOCK,item);}if(value instanceof Block block)PolymerBlock.registerOverlay(block,VisibleBlock.INSTANCE);if(value instanceof net.minecraft.sounds.SoundEvent sound)eu.pb4.polymer.core.api.other.PolymerSoundEvent.registerOverlay(sound);else if(!(value instanceof Item)&&!(value instanceof Block))eu.pb4.polymer.core.api.utils.PolymerSyncedObject.setSyncedObject(registry,value,(original,ctx)->null);}}
    static final class Entry<S> implements Supplier<S>{final Identifier id;final Supplier<S> factory;S value;Entry(Identifier id,Supplier<S> factory){this.id=id;this.factory=factory;}public S get(){if(value==null)value=factory.get();return value;}}
    public static final class Blocks extends DeferredRegister<Block>{
        Blocks(String namespace){super(Registries.BLOCK,namespace);}
        public <B extends Block> Supplier<B> registerBlock(String name,Function<BlockBehaviour.Properties,B> factory,UnaryOperator<BlockBehaviour.Properties> properties){return register(name,()->factory.apply(properties.apply(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(namespace,name))))));}
    }
    public static final class Items extends DeferredRegister<Item>{
        Items(String namespace){super(Registries.ITEM,namespace);}
        public <I extends Item> Supplier<I> registerItem(String name,Function<Item.Properties,I> factory,UnaryOperator<Item.Properties> properties){return register(name,()->factory.apply(properties.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath(namespace,name))))));}
        public <I extends Item> Supplier<I> registerItem(String name,Function<Item.Properties,I> factory){return registerItem(name,factory,p->p);}
        public Supplier<BlockItem> registerSimpleBlockItem(String name,Supplier<Block> block){return registerItem(name,p->new PortBlockItem(block.get(),p.useBlockDescriptionPrefix()));}
    }
}
