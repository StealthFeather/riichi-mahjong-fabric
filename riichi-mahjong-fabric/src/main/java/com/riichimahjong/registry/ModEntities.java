// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongtools.MahjongRiichiStickEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.riichimahjong.fabric.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RiichiMahjong.MOD_ID);

    public static final Supplier<EntityType<MahjongRiichiStickEntity>> MAHJONG_RIICHI_STICK_ENTITY =
            ENTITY_TYPES.register(
                    "mahjong_riichi_stick",
                    () -> EntityType.Builder.<MahjongRiichiStickEntity>of(MahjongRiichiStickEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "mahjong_riichi_stick"))));

    private ModEntities() {}
}
