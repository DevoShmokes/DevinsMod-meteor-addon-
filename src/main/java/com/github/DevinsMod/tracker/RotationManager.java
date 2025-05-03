package com.github.DevinsMod.tracker;

import com.github.DevinsMod.utils.RotationRequest;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.concurrent.CopyOnWriteArrayList;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RotationManager {
    public static final CopyOnWriteArrayList<RotationRequest> requests = new CopyOnWriteArrayList<>();
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
