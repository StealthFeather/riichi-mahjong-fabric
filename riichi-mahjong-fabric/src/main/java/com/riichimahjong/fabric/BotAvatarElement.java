package com.riichimahjong.fabric;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.EntityElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/** Vanilla client entities owned entirely by the table's virtual holder. */
final class BotAvatarElement {
    private static final SwingAnimation DISCARD_SWING=new SwingAnimation(SwingAnimationType.WHACK,6);
    private final ElementHolder holder;
    private final EntityElement<ArmorStand> seat;
    private final EntityElement<Mannequin> avatar;
    private final float inwardYaw;

    /** Position is the exact mannequin model root, including its lowered sitting height. */
    BotAvatarElement(ElementHolder holder,ServerLevel level,Vec3 position,float inwardYaw){
        this.holder=holder;this.inwardYaw=inwardYaw;
        var mannequin=new Mannequin(EntityTypes.MANNEQUIN,level);
        var data=new CompoundTag();data.putBoolean("hide_description",true);data.putBoolean("immovable",true);
        mannequin.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));
        mannequin.setNoGravity(true);mannequin.setSilent(true);
        mannequin.setYRot(inwardYaw);mannequin.setYBodyRot(inwardYaw);mannequin.setYHeadRot(inwardYaw);
        mannequin.setPos(position.x,position.y,position.z);
        var vehicle=new ArmorStand(EntityTypes.ARMOR_STAND,level);
        var seatData=new CompoundTag();seatData.putBoolean("Marker",true);
        vehicle.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),seatData));
        vehicle.setInvisible(true);vehicle.setNoGravity(true);vehicle.setSilent(true);
        vehicle.setYRot(inwardYaw);vehicle.setYBodyRot(inwardYaw);vehicle.setYHeadRot(inwardYaw);
        // Riding triggers the vanilla player's bent-leg pose. Pose.SITTING does not.
        // No entity is registered with ServerLevel, so neither is saved, ticked or collidable.
        if(!mannequin.startRiding(vehicle,true,false))throw new IllegalStateException("Cannot seat virtual mahjong mannequin");
        var offset=vehicle.getPassengerRidingPosition(mannequin).subtract(vehicle.position())
                .subtract(mannequin.getVehicleAttachmentPoint(vehicle));
        var vehiclePosition=position.subtract(offset);
        vehicle.setPos(vehiclePosition.x,vehiclePosition.y,vehiclePosition.z);
        seat=new EntityElement<>(vehicle,level);seat.setOverridePos(vehiclePosition);
        avatar=new EntityElement<>(mannequin,level);avatar.setOverridePos(position);
        // The mannequin's pairing data repeats its mount packet after its own spawn.
        // Keep this order so new watchers always know the vehicle before its rider.
        holder.addElement(seat);holder.addElement(avatar);
    }

    void setInteractionHandler(VirtualElement.InteractionHandler handler){avatar.setInteractionHandler(handler);}

    void lookAt(Vec3 target){
        var entity=avatar.entity();
        var delta=target.subtract(entity.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        entity.setYRot(inwardYaw);entity.setYBodyRot(inwardYaw);entity.setYHeadRot(yaw);
        entity.setXRot(Math.clamp(pitch,-80f,80f));
    }

    void swing(){
        // Virtual entities have no ServerLevel tracker; send to this holder's audience.
        avatar.tick();
        holder.sendPacket(new ClientboundSwingAnimationPacket(avatar.entity().getId(),InteractionHand.MAIN_HAND,DISCARD_SWING));
    }

    void destroy(){
        holder.removeElement(avatar);holder.removeElement(seat);
    }
}
