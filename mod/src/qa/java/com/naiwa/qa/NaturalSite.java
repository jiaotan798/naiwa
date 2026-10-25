package com.naiwa.qa;

import com.google.gson.Gson;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 自然场地只记录原生坐标；局部方向统一后复用相同的客户端操作，不搬运或改造地形。 */
final class NaturalSite {
    String kind,label,ground,biome;
    int x,y,z,rotation,depth,clearance,bankStep;
    NaturalSite(String kind,String label,BlockPos p,int rotation) {this.kind=kind;this.label=label;x=p.getX();y=p.getY();z=p.getZ();this.rotation=rotation;}
    Vec3 point(double dx,double dy,double dz) {
        double u=dx-.5,v=dz-.5;
        for(int i=0;i<rotation;i++) {double old=u;u=-v;v=old;}
        return new Vec3(x+.5+u,y+dy,z+.5+v);
    }
    BlockPos block(int dx,int dy,int dz) {return BlockPos.containing(point(dx+.5,dy,dz+.5));}
    Direction direction() {Direction d=Direction.NORTH;for(int i=0;i<rotation;i++)d=d.getClockWise();return d;}
    float yaw(float local) {return local+rotation*90;}
    BlockPos anchor() {return new BlockPos(x,y,z);}
    AABB area() {return new AABB(x-20,y-12,z-20,x+20,y+16,z+20);}
    static NaturalSite read() throws Exception {return new Gson().fromJson(Files.readString(MultiplayerCheck.ROOT.resolve("site.json")),NaturalSite.class);}
}
