// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.gimmicks;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;

public class VariantPaintingItem extends HangingEntityItem {
    private static final EntityType<Painting> PAINTING_TYPE =
            //? if >=26.2 {
            net.minecraft.world.entity.EntityTypes.PAINTING;
            //?} else
            /*EntityType.PAINTING;*/

    private final List<Identifier> variantIds;

    public VariantPaintingItem(Properties properties, Identifier... variantIds) {
        super(PAINTING_TYPE, properties);
        this.variantIds = List.of(variantIds);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Identifier pick = variantIds.get(ThreadLocalRandom.current().nextInt(variantIds.size()));
        ItemStack stack = context.getItemInHand();

        CompoundTag tag = new CompoundTag();
        tag.putString("variant", pick.toString());
        stack.set(DataComponents.ENTITY_DATA, TypedEntityData.of(PAINTING_TYPE, tag));
        return super.useOn(context);
    }
}
