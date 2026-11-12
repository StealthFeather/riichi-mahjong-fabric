package com.riichimahjong.fabric;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** The display background and hit plane use the same font metrics and rectangle. */
final class TableButtonLayout {
    static final double HEIGHT=10.0/16+.006; // Above the raised table rim, not the felt.
    static final double EDGE_DISTANCE=.965;
    static final double MAX_WIDTH=1.76;
    static final int GAP_PIXELS=3*9; // Three fullwidth glyph advances in minecraft:uniform.
    record Button(int index,String text,int pixels,float scale,double along){
        double pixelSize(){return .025*scale;}
        double width(){return (pixels+1)*pixelSize();}
        double depth(){return 10*pixelSize();}
        Vec3 centre(BlockPos pos,Direction dir){return TableLayout.anchor(pos,dir,along,HEIGHT,EDGE_DISTANCE);}
        Vec3 textPosition(BlockPos pos,Direction dir){
            // TextDisplayRenderer's background spans [-1,-1]..[fontWidth,9],
            // after translating by [1-fontWidth/2,-9] and scaling by .025.
            return TableLayout.anchor(pos,dir,along-.5*pixelSize(),HEIGHT,EDGE_DISTANCE+5*pixelSize());
        }
        Optional<Vec3> hit(BlockPos pos,Direction dir,Vec3 start,Vec3 end){
            var centre=centre(pos,dir);double dy=end.y-start.y;
            if(start.y<centre.y||dy>=-1e-9)return Optional.empty();
            double t=(centre.y-start.y)/dy;if(t<0||t>1)return Optional.empty();
            Vec3 at=start.add(end.subtract(start).scale(t)),delta=at.subtract(centre);
            var line=dir.getCounterClockWise();
            double x=delta.x*line.getStepX()+delta.z*line.getStepZ();
            double z=delta.x*dir.getStepX()+delta.z*dir.getStepZ();
            return Math.abs(x)<=width()*TableLayout.TILE_ENLARGEMENT/2&&Math.abs(z)<=depth()*TableLayout.TILE_ENLARGEMENT/2?Optional.of(at):Optional.empty();
        }
    }
    static List<Button> arrange(List<String> labels){
        if(labels.isEmpty())return List.of();
        int pixels=labels.stream().mapToInt(s->textWidth(s)+1).sum();
        float scale=(float)Math.min(.20,MAX_WIDTH/((pixels+GAP_PIXELS*(labels.size()-1))*.025));
        double gap=GAP_PIXELS*.025*scale;
        double total=pixels*.025*scale+gap*(labels.size()-1),cursor=-total/2;
        var out=new ArrayList<Button>();
        for(int i=0;i<labels.size();i++){
            int width=textWidth(labels.get(i));double size=(width+1)*.025*scale;
            out.add(new Button(i,labels.get(i),width,scale,cursor+size/2));cursor+=size+gap;
        }
        return List.copyOf(out);
    }
    static int textWidth(String text){
        // Forced minecraft:uniform: CJK/fullwidth overrides are 16 texels wide,
        // digit 1 is 5 texels and other digits 6. Unihex advance is width/2+1.
        // These are the complete glyph classes used by PrivateHand.label().
        return text.codePoints().map(c->c==' '?4:c=='1'?3:c>='0'&&c<='9'?4:9).sum();
    }
}
