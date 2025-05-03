package com.github.DevinsMod.mixins;

import com.github.DevinsMod.events.RotationRequestCompletedEvent;
import com.github.DevinsMod.tracker.RotationManager;
import com.github.DevinsMod.utils.RotationRequest;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/* Shay: Must be at least 1000 (default) for Boze */
@Mixin(value = ClientPlayerEntity.class, priority = 1001)
public class ClientPlayerEntityMixin {

    @Shadow
    @Final
    public ClientPlayNetworkHandler networkHandler;
    @Shadow
    private double lastX;
    @Shadow
    private double lastBaseY;
    @Shadow
    private double lastZ;
    @Shadow
    private boolean lastSneaking;
    @Shadow
    private boolean lastHorizontalCollision;
    @Shadow
    private float lastYaw;
    @Shadow
    private float lastPitch;
    @Shadow
    private boolean lastOnGround;
    @Shadow
    private int ticksSinceLastPositionPacketSent;
    @Shadow
    private boolean autoJumpEnabled;

    @Shadow
    private void sendSprintingPacket() {

    }

    @Shadow
    protected boolean isCamera() {
        return false;
    }

    @Inject(method = "sendMovementPackets", at = @At(value = "TAIL"))
    private void hookSendMovementPacket1s(CallbackInfo ci) {
        mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        mc.interactionManager.clickSlot(0, 6, 8, SlotActionType.SWAP, mc.player);
    }

    @Inject(method = "sendMovementPackets", at = @At(value = "HEAD"), cancellable = true)
    private void hookSendMovementPackets(CallbackInfo ci) {
        RotationRequest rotation = RotationManager.getRotationRequest();
        boolean hasRotation = rotation != null;
        if (mc.player != null) {
            ci.cancel();
            if (hasRotation) MeteorClient.EVENT_BUS.post(RotationRequestCompletedEvent.Pre.get(rotation));
            sendSprintingPacket();
            boolean bl = mc.player.isSneaking();
            if (bl != lastSneaking) {
                ClientCommandC2SPacket.Mode mode = bl ? ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY : ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY;
                networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, mode));
                lastSneaking = bl;
            }
            if (isCamera()) {
                double x = mc.player.getX();
                double y = mc.player.getY();
                double z = mc.player.getZ();
                float yaw;
                float pitch;
                if (hasRotation) {
                    yaw = rotation.getRotation()[0];
                    pitch = rotation.getRotation()[1];
                } else {
                    yaw = mc.player.getYaw();
                    pitch = mc.player.getPitch();
                }

                boolean ground = mc.player.isOnGround();

                double d = x - lastX;
                double e = y - lastBaseY;
                double f = z - lastZ;
                double g = yaw - lastYaw;
                double h = pitch - lastPitch;
                ++ticksSinceLastPositionPacketSent;
                boolean shouldUpdatePosition = MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0E-4) || ticksSinceLastPositionPacketSent >= 20;
                boolean shouldUpdateRotation = g != 0.0 || h != 0.0;


                Packet<?> packet = null;

                if ((shouldUpdatePosition && shouldUpdateRotation)) {
                    packet = (new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, ground, mc.player.horizontalCollision));
                } else if (shouldUpdatePosition) {
                    packet = (new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, ground, mc.player.horizontalCollision));
                } else if (shouldUpdateRotation) {
                    packet = (new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, ground, mc.player.horizontalCollision));
                } else if (lastOnGround != mc.player.isOnGround() || lastHorizontalCollision != mc.player.horizontalCollision) {
                    packet = (new PlayerMoveC2SPacket.OnGroundOnly(ground, mc.player.horizontalCollision));
                }


                networkHandler.sendPacket(packet);


                if (shouldUpdatePosition) {
                    lastX = x;
                    lastBaseY = y;
                    lastZ = z;
                    ticksSinceLastPositionPacketSent = 0;
                }
                if (shouldUpdateRotation) {
                    lastYaw = yaw;
                    lastPitch = pitch;
                }
                lastOnGround = ground;
                autoJumpEnabled = false;
                if (hasRotation) MeteorClient.EVENT_BUS.post(RotationRequestCompletedEvent.Post.get(rotation));
            }
        }
    }
}
