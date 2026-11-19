package com.riichimahjong.fabric;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.List;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Vanilla-textured fangs built from displays because the vanilla entity has no scale metadata. */
final class SmallFangsDisplay implements RonActorDisplay {
    static final int LIFETIME=22;
    static final double TARGET_HEIGHT=(TableLayout.TILE_MODEL_BOUNDS.maxY-TableLayout.TILE_MODEL_BOUNDS.minY)*TableLayout.TILE_SCALE*8/3;
    // The vanilla base is 12 pixels tall; the closed jaws extend another 14 pixels.
    static final float SCALE=(float)(TARGET_HEIGHT/(26.0/16));
    record Part(String model,Vec3 offset,float scale,Quaternionf rotation) {}
    private final ElementHolder holder;
    private final Vec3 feet;
    private final ItemDisplayElement[] pieces=new ItemDisplayElement[3];
    private final int startedAt;
    private final float size;
    private final float yaw;

    SmallFangsDisplay(ElementHolder holder,Vec3 feet,int startedAt) {
        this(holder,feet,startedAt,0,1,(float)Math.PI/2);
    }

    SmallFangsDisplay(ElementHolder holder,Vec3 feet,int startedAt,int initialAge,float size,float yaw) {
        this.holder=holder;this.feet=feet;this.startedAt=startedAt;this.size=size;this.yaw=yaw;
        var first=frame(initialAge,size,yaw);
        for(int i=0;i<pieces.length;i++) {
            var part=first.get(i);
            var display=new ItemDisplayElement(PortViews.model(part.model()));
            display.setItemDisplayContext(ItemDisplayContext.NONE);
            // ItemDisplayRenderer adds a 180° local Y rotation. Cancel it so both
            // off-centre jaw boxes point into their hinges as in EvokerFangsModel.
            display.setRightRotation(new Quaternionf().rotationY((float)Math.PI));
            display.setDisplaySize(1,1);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(1);
            display.setOverridePos(feet.add(part.offset()));
            display.setScale(new Vector3f(part.scale()));
            display.setLeftRotation(part.rotation());
            pieces[i]=display;
            holder.addElement(display);
        }
    }

    boolean update(int tick) {
        int age=tick-startedAt;
        if(age>=LIFETIME) { destroy();return false; }
        var parts=frame(age,size,yaw);
        for(int i=0;i<pieces.length;i++) {
            var part=parts.get(i);var display=pieces[i];
            display.setOverridePos(feet.add(part.offset()));
            display.setScale(new Vector3f(part.scale()));
            display.setLeftRotation(part.rotation());
            display.startInterpolationIfDirty();
        }
        return true;
    }

    /** Keep the whole fully opened/closed model within 8/3 of one standing tile's height. */
    static List<Part> frame(int age) {
        return frame(age,1,(float)Math.PI/2);
    }

    private static List<Part> frame(int age,float size,float yawAngle) {
        double appear=smooth(age,0,4),disappear=1-smooth(age,18,LIFETIME);
        float scale=(float)(SCALE*size*appear*disappear);
        float opening=(float)((1-smooth(age,8,18))*.35*Math.PI);
        var yaw=new Quaternionf().rotationY(yawAngle);
        var baseRotation=new Quaternionf(yaw).rotateZ((float)Math.PI);
        var hinge=new Vec3(0,12.0/16*scale,0);
        var upperOffset=yaw.transform(new Vector3f(-1.5f,0,-4)).mul(scale/16);
        var lowerOffset=yaw.transform(new Vector3f(1.5f,0,4)).mul(scale/16);
        return List.of(new Part("bridge/win_fangs_base",hinge,scale,baseRotation),
                new Part("bridge/win_fangs_jaw",hinge.add(upperOffset.x,upperOffset.y,upperOffset.z),scale,
                        new Quaternionf(baseRotation).rotateZ((float)Math.PI-opening)),
                new Part("bridge/win_fangs_jaw",hinge.add(lowerOffset.x,lowerOffset.y,lowerOffset.z),scale,
                        new Quaternionf(baseRotation).rotateZ((float)Math.PI+opening).rotateY((float)Math.PI)));
    }

    private static double smooth(int age,int start,int end) {
        double t=Math.clamp((double)(age-start)/(end-start),0,1);
        return t*t*(3-2*t);
    }

    @Override public void update(com.riichimahjong.fabric.wineffect.WinEffectFrame.ActorPose pose) {
        update(pose.age());
    }

    @Override public void destroy() { for(var piece:pieces)holder.removeElement(piece); }
}
