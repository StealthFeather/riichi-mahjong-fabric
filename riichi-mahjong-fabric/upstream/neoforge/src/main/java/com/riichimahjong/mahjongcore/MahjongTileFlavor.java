package com.riichimahjong.mahjongcore;

import com.themahjong.TheMahjongTile;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class MahjongTileFlavor {
    private MahjongTileFlavor() {}

    private static final Identifier PIN_DAMAGE_ID =
            Identifier.fromNamespaceAndPath("riichi_mahjong", "pin_attack_damage");
    private static final Identifier PIN_SPEED_ID =
            Identifier.fromNamespaceAndPath("riichi_mahjong", "pin_attack_speed");
    private static final double SWORD_ATTACK_SPEED = 1.6;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;

    private static final int SECOND_TICKS = 20;
    private static final int MINUTE_TICKS = 60 * SECOND_TICKS;

    public static Item.Properties applyPropertiesForCode(Item.Properties properties, int code) {
        properties.stacksTo(1);
        List<MobEffectInstance> effects = new ArrayList<>();
        properties.food(foodForCode(code, effects), consumableWith(effects));
        if (isPinCode(code)) {
            int rank = pinRank(code);
            properties.attributes(pinAttackModifiers(rank));
        }
        return properties;
    }

    private static Consumable consumableWith(List<MobEffectInstance> effects) {
        Consumable.Builder consumable = Consumables.defaultFood();
        for (MobEffectInstance effect : effects) {
            consumable.onConsume(new ApplyStatusEffectsConsumeEffect(effect, 1.0f));
        }
        return consumable.build();
    }

    private static FoodProperties foodForCode(int code, List<MobEffectInstance> effects) {
        FoodProperties.Builder builder = new FoodProperties.Builder().alwaysEdible();
        if (code == MahjongTileItems.CODE_MAN_5_AKA) {
            applyNumberedFood(builder, effects, TheMahjongTile.Suit.MANZU, 5);
            effects.add(new MobEffectInstance(MobEffects.LUCK, MINUTE_TICKS, 0));
            return builder.build();
        }
        if (code == MahjongTileItems.CODE_PIN_5_AKA) {
            applyNumberedFood(builder, effects, TheMahjongTile.Suit.PINZU, 5);
            effects.add(new MobEffectInstance(MobEffects.LUCK, MINUTE_TICKS, 0));
            return builder.build();
        }
        if (code == MahjongTileItems.CODE_SOU_5_AKA) {
            applyNumberedFood(builder, effects, TheMahjongTile.Suit.SOUZU, 5);
            effects.add(new MobEffectInstance(MobEffects.LUCK, MINUTE_TICKS, 0));
            return builder.build();
        }

        if (code >= 0 && code <= 8) {
            applyNumberedFood(builder, effects, TheMahjongTile.Suit.MANZU, code + 1);
        } else if (code >= 9 && code <= 17) {
            applyNumberedFood(builder, effects, TheMahjongTile.Suit.PINZU, code - 9 + 1);
        } else if (code >= 18 && code <= 26) {
            applyNumberedFood(builder, effects, TheMahjongTile.Suit.SOUZU, code - 18 + 1);
        } else if (code >= 27 && code <= 30) {
            applyWindFood(builder, effects, code - 27);
        } else if (code >= 31 && code <= 33) {
            applyDragonFood(builder, effects, code - 31);
        } else {
            builder.nutrition(1).saturationModifier(0.1f);
        }
        return builder.build();
    }

    private static void applyNumberedFood(FoodProperties.Builder builder, List<MobEffectInstance> effects,
                                          TheMahjongTile.Suit suit, int rank) {
        int hunger = (rank + 1) / 2;
        float saturation = (float) (0.3 * rank);
        builder.nutrition(hunger).saturationModifier(saturation);

        Holder<MobEffect> effect = switch (suit) {
            case MANZU -> MobEffects.STRENGTH;
            case PINZU -> MobEffects.RESISTANCE;
            case SOUZU -> MobEffects.SPEED;
            default -> null;
        };
        if (effect == null) return;

        int amplifier = rank >= 7 ? 1 : 0;
        int duration;
        if (rank <= 3) duration = 5 * SECOND_TICKS * rank;
        else if (rank <= 6) duration = 30 * SECOND_TICKS;
        else duration = MINUTE_TICKS;
        effects.add(new MobEffectInstance(effect, duration, amplifier));
    }

    private static void applyWindFood(FoodProperties.Builder builder, List<MobEffectInstance> effects, int windIdx) {
        builder.nutrition(5).saturationModifier(4.0f);
        Holder<MobEffect> effect = switch (windIdx) {
            case 0 -> MobEffects.SLOW_FALLING;
            case 1 -> MobEffects.FIRE_RESISTANCE;
            case 2 -> MobEffects.WATER_BREATHING;
            case 3 -> MobEffects.CONDUIT_POWER;
            default -> null;
        };
        if (effect != null) {
            effects.add(new MobEffectInstance(effect, MINUTE_TICKS, 0));
        }
    }

    private static void applyDragonFood(FoodProperties.Builder builder, List<MobEffectInstance> effects, int dragonIdx) {
        switch (dragonIdx) {
            case 0 -> {
                builder.nutrition(6).saturationModifier(5.0f);
                effects.add(new MobEffectInstance(MobEffects.REGENERATION, MINUTE_TICKS, 0));
            }
            case 1 -> {
                builder.nutrition(7).saturationModifier(6.0f);
                effects.add(new MobEffectInstance(MobEffects.LUCK, 2 * MINUTE_TICKS, 0));
            }
            case 2 -> {
                builder.nutrition(8).saturationModifier(7.0f);
                effects.add(new MobEffectInstance(MobEffects.STRENGTH, MINUTE_TICKS, 1));
                effects.add(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MINUTE_TICKS, 0));
            }
            default -> builder.nutrition(1).saturationModifier(0.1f);
        }
    }

    private static boolean isPinCode(int code) {
        return (code >= 9 && code <= 17) || code == MahjongTileItems.CODE_PIN_5_AKA;
    }

    private static int pinRank(int code) {
        if (code == MahjongTileItems.CODE_PIN_5_AKA) return 5;
        return code - 9 + 1;
    }

    private static ItemAttributeModifiers pinAttackModifiers(int rank) {
        double damageBonus = rank - PLAYER_BASE_ATTACK_DAMAGE;
        double speedBonus = SWORD_ATTACK_SPEED - PLAYER_BASE_ATTACK_SPEED;
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(PIN_DAMAGE_ID, damageBonus,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(PIN_SPEED_ID, speedBonus,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }
}
