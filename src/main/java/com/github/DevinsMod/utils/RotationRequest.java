package com.github.DevinsMod.utils;


import com.github.DevinsMod.modules.RotationManager;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RotationRequest {

    @Getter
    private final int priority;
    private float rawYaw, rawPitch;
    private Box box;
    private Vec3d vec3d;
    private Direction direction;
    private Entity target;
    @Getter
    @Setter
    private boolean isActive;
    @Getter
    private RotationType type;
    @Getter
    @Setter
    private boolean movementFix = true;
    public RotationRequest(int priority, boolean isActive) {
        this.priority = priority;
        this.isActive = isActive;
        RotationManager manager = Modules.get().get(RotationManager.class);
        this.movementFix = manager == null || manager.movementFix.get();
    }

    public float[] getRotation() {
        if (type == null) {
            throw new IllegalArgumentException("Rotation type not set");
        }
        switch (type) {
            case VEC3D:
                return getRotationFromVec3d(vec3d);
            case RAW_INPUTS:
                return new float[]{rawYaw, rawPitch};
            case CLOSEST_POINT_ON_BOX:
                return getRotationFromBox(box);
            case DIRECTION:
                return getRotationFromDirection(direction);
            case TARGET:
                if (target == null || target.getBoundingBox() == null)
                    return new float[]{mc.player.getYaw(), mc.player.getPitch()};
                return getRotationFromBox(target.getBoundingBox());
            default:
                throw new IllegalArgumentException("Invalid rotation type");
        }
    }

    public float[] getRotationFromVec3d(Vec3d target) {
        Vec3d cameraPos = mc.player.getEyePos();
        Vec3d direction = target.subtract(cameraPos);
        float yaw = (float) (MathHelper.atan2(direction.z, direction.x) * (180 / Math.PI)) - 90;
        float pitch = (float) -(MathHelper.atan2(direction.y, MathHelper.sqrt((float) (direction.x * direction.x + direction.z * direction.z))) * (180 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public float[] getRotationFromBox(Box box) {
        Vec3d cameraPos = mc.player.getEyePos();
        Vec3d closestPoint = Utils.getClosestPointOnBox(cameraPos, box);
        return getRotationFromVec3d(closestPoint);
    }

    public float[] getRotationFromDirection(Direction direction) {
        Vec3d target = new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
        return getRotationFromVec3d(mc.player.getEyePos().add(target));
    }

    public void setTarget(Entity target) {
        this.target = target;
        type = RotationType.TARGET;
    }


    public enum RotationType {
        VEC3D,
        RAW_INPUTS,
        CLOSEST_POINT_ON_BOX,
        DIRECTION,
        TARGET
    }

}
