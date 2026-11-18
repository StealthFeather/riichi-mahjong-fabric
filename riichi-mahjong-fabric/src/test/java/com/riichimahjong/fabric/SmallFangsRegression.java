package com.riichimahjong.fabric;

import org.joml.Vector3f;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Check the actual displayed cubes throughout the bite, rather than an entity hitbox. */
public final class SmallFangsRegression {
    public static void main(String[] args) {
        double tileHeight=(TableLayout.TILE_MODEL_BOUNDS.maxY-TableLayout.TILE_MODEL_BOUNDS.minY)*TableLayout.TILE_SCALE;
        check(Math.abs(SmallFangsDisplay.TARGET_HEIGHT-tileHeight*8/3)<1e-7,
                "fangs must be exactly twice the previous 4/3 tile height");
        double maximum=0;
        for(int age=0;age<=SmallFangsDisplay.LIFETIME;age++) {
            var parts=SmallFangsDisplay.frame(age);
            check(parts.size()==3,"fangs require the base and both jaws");
            double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
            for(int i=0;i<parts.size();i++) {
                var part=parts.get(i);
                // The JSON models are centred around (8,8,8) by the item renderer.
                double[] lo=i==0?new double[]{-5,0,-5}:new double[]{0,0,0};
                double[] hi=i==0?new double[]{5,12,5}:new double[]{4,14,8};
                for(double x:new double[]{lo[0],hi[0]})
                    for(double y:new double[]{lo[1],hi[1]})
                        for(double z:new double[]{lo[2],hi[2]}) {
                            var corner=part.rotation().transform(new Vector3f((float)x/16,(float)y/16,(float)z/16)).mul(part.scale());
                            double worldY=part.offset().y+corner.y;
                            minY=Math.min(minY,worldY);maxY=Math.max(maxY,worldY);
                        }
            }
            check(minY>=-1e-7,"fangs penetrate the table at age "+age);
            check(maxY<=SmallFangsDisplay.TARGET_HEIGHT+1e-7,"fangs exceed the 8/3 tile-height limit at age "+age);
            maximum=Math.max(maximum,maxY-minY);
        }
        check(Math.abs(maximum-SmallFangsDisplay.TARGET_HEIGHT)<1e-7,"full bite never reaches the requested size");
        var opened=SmallFangsDisplay.frame(8);var closed=SmallFangsDisplay.frame(18);
        check(Math.abs(opened.get(1).rotation().dot(closed.get(1).rotation()))<.99,"upper jaw does not bite");
        check(Math.abs(opened.get(2).rotation().dot(closed.get(2).rotation()))<.99,"lower jaw does not bite");
        check(SmallFangsDisplay.frame(0).stream().allMatch(p->p.scale()==0),"fangs pop in at full scale");
        check(SmallFangsDisplay.frame(SmallFangsDisplay.LIFETIME).stream().allMatch(p->p.scale()==0),"fangs do not shrink away");
        System.out.println("PASS: both fangs jaws bite at a maximum height of 8/3 of a tile, remain grounded and emerge/shrink cleanly");
    }
}
