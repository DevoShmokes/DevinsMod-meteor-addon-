package com.github.DevinsMod.tracker;

import java.util.List;
import java.util.ArrayList;
import baritone.api.BaritoneAPI;
import com.github.DevinsMod.events.UpdateVelocityEvent;
import com.github.DevinsMod.utils.RotationRequest;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;


import java.util.concurrent.CopyOnWriteArrayList;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class RotationManager {
    public static final CopyOnWriteArrayList<RotationRequest> requests = new CopyOnWriteArrayList<>();
    public static float prevBackupYaw;
    public static float prevBackupPitch;
    public static float prevYaw;
    public static float prevPitch;


    @EventHandler(priority = EventPriority.LOWEST)
    public static void onMovePacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesLook()) {
                RotationManager.prevYaw = packet.getYaw(mc.player.getYaw());
                RotationManager.prevPitch = packet.getPitch(mc.player.getPitch());
            }
        }
    }


    @EventHandler
    public static void onUpdateVelocity(UpdateVelocityEvent event) {
        if (!(BaritoneUtils.IS_AVAILABLE && BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing())) {
            RotationRequest rotation = RotationManager.getRotationRequest();
            if (rotation != null) {
                event.cancel();
                event.setVelocity(RotationManager.movementInputToVelocity(rotation.getRotation()[0], event.getMovementInput(), event.getSpeed()));
            }
        }
    }


    private static Vec3d movementInputToVelocity(float yaw, Vec3d movementInput, float speed) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        }
        Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
        float f = MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE);
        float g = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE);
        return new Vec3d(vec3d.x * g - vec3d.z * f, vec3d.y, vec3d.z * g + vec3d.x * f);
    }

    public static void addRequest(RotationRequest newRotation) {
        if (!requests.contains(newRotation)) {
            requests.add(newRotation);
        }
    }

    public static void removeRequest(RotationRequest newRotation) {
        requests.remove(newRotation);
    }

    public static boolean hasRotation() {
        return requests.stream().anyMatch(RotationRequest::isActive);
    }

    public static RotationRequest getRotationRequest() {
        RotationRequest rotationRequest = null;
        int priority = Integer.MIN_VALUE;

        for (RotationRequest request : requests) {
            if (request.getPriority() > priority && request.isActive()) {
                rotationRequest = request;
                priority = request.getPriority();
            }
        }
        return rotationRequest;
    }

}
