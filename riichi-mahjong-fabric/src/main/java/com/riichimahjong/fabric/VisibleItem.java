package com.riichimahjong.fabric;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

public interface VisibleItem extends PolymerItem {
    VisibleItem INSTANCE=new VisibleItem(){};
    @Override default Item getPolymerItem(ItemStack stack,PacketContext context){return Items.PAPER;}
    @Override default Identifier getPolymerItemModel(ItemStack stack,PacketContext context,HolderLookup.Provider registries){return BuiltInRegistries.ITEM.getKey(stack.getItem());}
    // Build an allowlisted packet stack. Never let Polymer embed a full source stack/BE snapshot.
    @Override default ItemStack getPolymerItemStack(ItemStack source,TooltipFlag flags,PacketContext context,HolderLookup.Provider registries){
        // Let Polymer retain the public item identity for creative clicks, but only encode this sanitized copy.
        var out=new ItemStack(source.getItem(),source.getCount());
        out.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,source.getHoverName());
        out.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,source.hasFoil());
        copy(source,out,net.minecraft.core.component.DataComponents.MAX_STACK_SIZE);
        copy(source,out,net.minecraft.core.component.DataComponents.FOOD);
        copy(source,out,net.minecraft.core.component.DataComponents.CONSUMABLE);
        copy(source,out,net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        copy(source,out,net.minecraft.core.component.DataComponents.COOKING_FUEL);
        return PolymerItem.super.getPolymerItemStack(out,flags,context,registries);
    }
    private static <T> void copy(ItemStack source,ItemStack dest,net.minecraft.core.component.DataComponentType<T> component){T value=source.get(component);if(value!=null)dest.set(component,value);}
}
