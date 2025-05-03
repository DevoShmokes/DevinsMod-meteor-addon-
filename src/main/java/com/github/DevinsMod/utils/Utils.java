package com.github.DevinsMod.utils;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class Utils {


    public static Vec3d getClosestPointOnBox(Vec3d point, Box box) {
        double x = Math.max(box.minX, Math.min(box.maxX, point.x));
        double y = Math.max(box.minY, Math.min(box.maxY, point.y));
        double z = Math.max(box.minZ, Math.min(box.maxZ, point.z));
        return new Vec3d(x, y, z);
    }


}

