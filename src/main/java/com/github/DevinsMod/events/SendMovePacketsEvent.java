package com.github.DevinsMod.events;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.meteorclient.events.Cancellable;

@Getter
public class SendMovePacketsEvent extends Cancellable {
    private static final SendMovePacketsEvent INSTANCE = new SendMovePacketsEvent();
    @Setter
    net.minecraft.network.packet.Packet<?> packet;
    boolean shouldUpdatePosition;
    boolean shouldUpdateRotation;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    boolean onGround;


    public static SendMovePacketsEvent get(net.minecraft.network.packet.Packet<?> packet, boolean shouldUpdatePosition, boolean shouldUpdateRotation, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        SendMovePacketsEvent.INSTANCE.setCancelled(false);
        SendMovePacketsEvent.INSTANCE.x = x;
        SendMovePacketsEvent.INSTANCE.y = y;
        SendMovePacketsEvent.INSTANCE.z = z;
        SendMovePacketsEvent.INSTANCE.yaw = yaw;
        SendMovePacketsEvent.INSTANCE.pitch = pitch;
        SendMovePacketsEvent.INSTANCE.onGround = onGround;
        SendMovePacketsEvent.INSTANCE.shouldUpdatePosition = shouldUpdatePosition;
        SendMovePacketsEvent.INSTANCE.shouldUpdateRotation = shouldUpdateRotation;
        SendMovePacketsEvent.INSTANCE.packet = packet;
        return SendMovePacketsEvent.INSTANCE;
    }
}
