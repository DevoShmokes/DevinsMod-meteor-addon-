package com.github.DevinsMod.events;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.events.Cancellable;

public class UpdateVelocityEvent extends Cancellable {
    private static final UpdateVelocityEvent INSTANCE = new UpdateVelocityEvent();
    @Getter
    Vec3d movementInput;
    @Getter
    float speed;
    float yaw;
    @Setter
    @Getter
    Vec3d velocity;

    public static UpdateVelocityEvent get(Vec3d movementInput, float speed, float yaw, Vec3d velocity) {
        UpdateVelocityEvent.INSTANCE.setCancelled(false);
        UpdateVelocityEvent.INSTANCE.movementInput = movementInput;
        UpdateVelocityEvent.INSTANCE.speed = speed;
        UpdateVelocityEvent.INSTANCE.yaw = yaw;
        UpdateVelocityEvent.INSTANCE.velocity = velocity;
        return UpdateVelocityEvent.INSTANCE;
    }

}
