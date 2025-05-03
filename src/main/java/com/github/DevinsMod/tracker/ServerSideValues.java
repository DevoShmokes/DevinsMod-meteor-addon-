package com.github.DevinsMod.tracker;
import com.github.DevinsMod.tasks.Task;
import lombok.Getter;
import net.minecraft.util.math.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.BlockState;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.network.packet.c2s.query.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;

public class ServerSideValues {

    private static final int INITIAL_SIZEp = 8;

    private static long[] timesp = new long[ServerSideValues.INITIAL_SIZEp];
    private static long[] countsp = new long[ServerSideValues.INITIAL_SIZEp];
    private static final long intervalp = (long) (7.0 * 1.0e9);
    private static long lastHandshakeTime;
    private static long minTimep;
    private static long sump;
    private static int headp; // inclusive
    public static int ticks;
    private static int tailp; // exclusive
    public static boolean hasMoved, clientIsFloating;
    @Getter
    static long lastLimitedPacket = -1;
    public static Vec3d tickpos;
    public static Vec3d serverSidePosition = new Vec3d(0,0,0);
    public static int i, i2, aboveGroundTickCount, limitedPackets;
    private static int receivedMovePacketCount, knownMovePacketCount, knownMovePacketCount2, receivedMovePacketCount2, lasttick, allowedPlayerTicks;
    public static String uptimeString = "Unknown";

    public static int delta() {
        return ServerSideValues.predictallowedPlayerTicks() - ServerSideValues.getTotalI();
    }
    public static int predictallowedPlayerTicks() {
        int TempAllowedPlayerTicks = ServerSideValues.allowedPlayerTicks;
        TempAllowedPlayerTicks += (System.currentTimeMillis() / 50) - ServerSideValues.lasttick;
        return Math.max(TempAllowedPlayerTicks, 1);
    }



    @EventHandler(priority = EventPriority.LOWEST)
    private static void onTick(TickEvent.Pre event) {
        ++ServerSideValues.ticks;
        if (ServerSideValues.clientIsFloating) {
            ++ServerSideValues.aboveGroundTickCount;
        } else {
            ServerSideValues.aboveGroundTickCount = 0;
        }
        ServerSideValues.hasMoved = false;
        if (mc.player == null) return;
        ServerSideValues.tickpos = ServerSideValues.serverSidePosition;
        ServerSideValues.knownMovePacketCount = ServerSideValues.receivedMovePacketCount;
        ServerSideValues.knownMovePacketCount2 = ServerSideValues.receivedMovePacketCount2;
        ServerSideValues.i = 0;
        ServerSideValues.i2 = 0;
        // System.out.println("allowed: " + allowedPlayerTicks);
    }


    @EventHandler
    private static void onJoinServer(GameJoinedEvent event) {
        System.out.println("Joined server");
        ServerSideValues.lasttick = 0;
        ServerSideValues.lastLimitedPacket = -1;
        ServerSideValues.tickpos = new Vec3d(0,0,0);
        ServerSideValues.hasMoved = false;
        ServerSideValues.i = 0;
        ServerSideValues.i2 = 0;
        ServerSideValues.allowedPlayerTicks = 1;
    }

    public static String formatUptime(long nanoTime) {
        // Convert nanoseconds to milliseconds
        long millis = TimeUnit.NANOSECONDS.toMillis(nanoTime);

        // Define time constants
        long millisInASecond = 1000L;
        long millisInAMinute = millisInASecond * 60;
        long millisInAnHour = millisInAMinute * 60;
        long millisInADay = millisInAnHour * 24;
        long millisInAYear = millisInADay * 365;

        // Calculate each unit
        long years = millis / millisInAYear;
        millis %= millisInAYear;

        long days = millis / millisInADay;
        millis %= millisInADay;

        long hours = millis / millisInAnHour;
        millis %= millisInAnHour;

        long minutes = millis / millisInAMinute;

        // Construct the formatted uptime string
        StringBuilder uptime = new StringBuilder();
        if (years > 0) {
            uptime.append(years).append("Y ");
        }
        if (days > 0) {
            uptime.append(days).append("D ");
        }
        if (hours > 0) {
            uptime.append(hours).append("H ");
        }
        if (minutes > 0) {
            uptime.append(minutes).append("M");
        }

        return uptime.toString().trim();
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    private static void onRecievePacket(PacketEvent.Receive event) {
        if (event.packet instanceof KeepAliveS2CPacket packet) {
            ServerSideValues.uptimeString = ServerSideValues.formatUptime(packet.getId() * 1000000L);
        }
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            ServerSideValues.serverSidePosition = packet.change().position();
            if (ServerSideValues.tickpos == null) ServerSideValues.tickpos = ServerSideValues.serverSidePosition;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST - 10)
    private static void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof HandshakeC2SPacket) ServerSideValues.lastHandshakeTime = System.nanoTime();
        if (event.packet instanceof LoginHelloC2SPacket) {
            Task.endAllTasks();
            ServerSideValues.sump = 0;
            ServerSideValues.headp = 0;
            ServerSideValues.tailp = 0;
            ServerSideValues.countsp = new long[ServerSideValues.INITIAL_SIZEp];
            ServerSideValues.timesp = new long[ServerSideValues.INITIAL_SIZEp];
            ServerSideValues.updateAndAdd(1, ServerSideValues.lastHandshakeTime);
        }
        if (!(event.packet instanceof QueryPingC2SPacket || event.packet instanceof QueryRequestC2SPacket || event.packet instanceof HandshakeC2SPacket))
            ServerSideValues.updateAndAdd(1, System.nanoTime());

        if (event.packet instanceof PlayerMoveC2SPacket packet) {
            //ChatUtils.info("handling move packet");
            ServerSideValues.handleMovePacket(packet, true);
        }

        if (event.packet instanceof VehicleMoveC2SPacket packet) {
            serverSidePosition = packet.position();
        }

        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            if(!ServerSideValues.handleUse()) {
                event.cancel();
            }
        }
        if (event.packet instanceof PlayerInteractItemC2SPacket packet) {
            if (!ServerSideValues.handleUse()) {
                event.cancel();
            }
        }
    }


    public static void manageCircularBuffer(long currTime) {
        ServerSideValues.minTimep = currTime - ServerSideValues.intervalp;

        // Loop to remove outdated values from the circular buffer.
        while (ServerSideValues.headp != ServerSideValues.tailp && ServerSideValues.timesp[ServerSideValues.headp] < ServerSideValues.minTimep) {
            ServerSideValues.sump -= ServerSideValues.countsp[ServerSideValues.headp];
            ServerSideValues.countsp[ServerSideValues.headp] = 0;
            ServerSideValues.headp = (ServerSideValues.headp + 1) % ServerSideValues.timesp.length;
        }

        if (currTime - ServerSideValues.minTimep >= 0) {
            int nextTail = (ServerSideValues.tailp + 1) % ServerSideValues.timesp.length;

            if (nextTail == ServerSideValues.headp) {
                ServerSideValues.resizeCircularBuffer();
            }
        }
    }

    private static void resizeCircularBuffer() {
        int oldLength = ServerSideValues.timesp.length;
        long[] newTimes = new long[oldLength * 2];
        long[] newCounts = new long[oldLength * 2];
        int size;

        if (ServerSideValues.tailp >= ServerSideValues.headp) {
            size = ServerSideValues.tailp - ServerSideValues.headp;
            System.arraycopy(ServerSideValues.timesp, ServerSideValues.headp, newTimes, 0, size);
            System.arraycopy(ServerSideValues.countsp, ServerSideValues.headp, newCounts, 0, size);
        } else {
            int firstPartSize = oldLength - ServerSideValues.headp;
            size = firstPartSize + ServerSideValues.tailp;

            System.arraycopy(ServerSideValues.timesp, ServerSideValues.headp, newTimes, 0, firstPartSize);
            System.arraycopy(ServerSideValues.timesp, 0, newTimes, firstPartSize, ServerSideValues.tailp);
            System.arraycopy(ServerSideValues.countsp, ServerSideValues.headp, newCounts, 0, firstPartSize);
            System.arraycopy(ServerSideValues.countsp, 0, newCounts, firstPartSize, ServerSideValues.tailp);
        }

        ServerSideValues.timesp = newTimes;
        ServerSideValues.countsp = newCounts;
        ServerSideValues.headp = 0;
        ServerSideValues.tailp = size;
    }

    public static void updateAndAdd(long count, long currTime) {
        ServerSideValues.manageCircularBuffer(currTime);

        if (currTime - ServerSideValues.minTimep < 0) return;
        int nextTail = (ServerSideValues.tailp + 1) % ServerSideValues.timesp.length;

        ServerSideValues.timesp[ServerSideValues.tailp] = currTime;
        ServerSideValues.countsp[ServerSideValues.tailp] += count;
        ServerSideValues.sump += count;
        ServerSideValues.tailp = nextTail;
    }

    public static Boolean canSendPackets(long count, long currTime) {
        long predictedSum = ServerSideValues.sump;
        long[] tmpCounts = ServerSideValues.countsp.clone();
        int tmpHead = ServerSideValues.headp;
        int tmpTail = ServerSideValues.tailp;

        ServerSideValues.manageCircularBuffer(currTime);

        tmpCounts[tmpTail] += count;
        predictedSum += count;
        double rate = predictedSum / (ServerSideValues.intervalp * 1.0E-9);
        return rate < 100;
    }



    public static void handleMovePacketSafe(PlayerMoveC2SPacket packet) {
        ++ServerSideValues.receivedMovePacketCount2;
        ServerSideValues.i2 = (ServerSideValues.receivedMovePacketCount2 - ServerSideValues.knownMovePacketCount2) - (ServerSideValues.receivedMovePacketCount - ServerSideValues.knownMovePacketCount);
        ServerSideValues.handleMovePacket(packet, false);
    }
    public static int getTotalI() {
        return ServerSideValues.i2 + ServerSideValues.i;
    }
    public static void handleMovePacket(PlayerMoveC2SPacket packet, Boolean setI) {
        Vec3d targetpos = new Vec3d(packet.getX(ServerSideValues.serverSidePosition.x), packet.getY(ServerSideValues.serverSidePosition.y), packet.getZ(ServerSideValues.serverSidePosition.z));
        boolean hasPos = packet.changesPosition();
        boolean hasRot = packet.changesLook();

        Vec3d currentDelta = new Vec3d(targetpos.x - ServerSideValues.serverSidePosition.x, targetpos.y - ServerSideValues.serverSidePosition.y, targetpos.z - ServerSideValues.serverSidePosition.z);
        double currentDeltaSquared = (currentDelta.x * currentDelta.x + currentDelta.y * currentDelta.y + currentDelta.z * currentDelta.z);
        Vec3d tickDelta = new Vec3d(targetpos.x - ServerSideValues.tickpos.x, targetpos.y - ServerSideValues.tickpos.y, targetpos.z - ServerSideValues.tickpos.z);
        double tickDeltaSquared = (tickDelta.x * tickDelta.x + tickDelta.y * tickDelta.y + tickDelta.z * tickDelta.z);
        double d10 = Math.max(currentDeltaSquared, tickDeltaSquared);

        if (d10 > 0) ServerSideValues.hasMoved = true;

        if (setI) {
            ++ServerSideValues.receivedMovePacketCount;
            ServerSideValues.i = ServerSideValues.receivedMovePacketCount - ServerSideValues.knownMovePacketCount;
        }
        ServerSideValues.allowedPlayerTicks += (System.currentTimeMillis() / 50) - ServerSideValues.lasttick;
        ServerSideValues.allowedPlayerTicks = Math.max(ServerSideValues.allowedPlayerTicks, 1);
        ServerSideValues.lasttick = (int) (System.currentTimeMillis() / 50);
        if (ServerSideValues.getTotalI() > Math.max(ServerSideValues.allowedPlayerTicks, 5)) {
            ChatUtils.error("Packet spam detected, server reset i value");
            ServerSideValues.i = 0;
            ServerSideValues.i2 = 1;
        }

        if (hasRot || d10 > 0) {
            ServerSideValues.allowedPlayerTicks -= 1;
        } else {
            ServerSideValues.allowedPlayerTicks = 20;
        }


//        String packetType = "unknown";
//        if (packet instanceof PlayerMoveC2SPacket.Full) packetType = "Full";
//        if (packet instanceof PlayerMoveC2SPacket.OnGroundOnly) packetType = "OnGroundOnly";
//        if (packet instanceof PlayerMoveC2SPacket.PositionAndOnGround) packetType = "PositionAndOnGround";
//        if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround) packetType = "LookAndOnGround";
//        if (d10 > 0) {
//            System.out.println(packetType + " allowed: " + allowedPlayerTicks + " i: " + (i2 + i) + " MOVED TO: " + (int) targetpos.x + " " + (int) targetpos.y + " " + (int) targetpos.z);
//        } else {
//            System.out.println(packetType + " allowed: " + allowedPlayerTicks + " i: " + (i2 + i));
//        }

        if (hasPos) ServerSideValues.serverSidePosition = targetpos;

        ServerSideValues.clientIsFloating = currentDelta.y >= -0.03125D && ServerSideValues.noBlocksAround();
    }


    public static final int threshhold = 310;

    public static Boolean handleUse() {
        if (ServerSideValues.lastLimitedPacket != -1 && System.currentTimeMillis() - ServerSideValues.lastLimitedPacket < ServerSideValues.threshhold && ServerSideValues.limitedPackets++ >= 8) {
            return false;
        }
        if (ServerSideValues.lastLimitedPacket == -1 || System.currentTimeMillis() - ServerSideValues.lastLimitedPacket >= ServerSideValues.threshhold) { // Paper
            ServerSideValues.lastLimitedPacket = System.currentTimeMillis();
            ServerSideValues.limitedPackets = 0;
            return true;
        }
        return true;
    }


    public static boolean canPlace() {
        return ServerSideValues.lastLimitedPacket == -1 || System.currentTimeMillis() - ServerSideValues.lastLimitedPacket >= ServerSideValues.threshhold || (ServerSideValues.limitedPackets + 1) < 9;
    }


    public static boolean noBlocksAround() {
        // Paper start - stop using streams, this is already a known fixed problem in Entity#move
        Box box = mc.player.getBoundingBox().expand(0.0625D).stretch(0.0D, -0.55D, 0.0D);
        int minX = MathHelper.floor(box.minX);
        int minY = MathHelper.floor(box.minY);
        int minZ = MathHelper.floor(box.minZ);
        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        if(mc.world == null) return false;
        for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    pos.set(x, y, z);
                    BlockState type = mc.world.getBlockState(pos);
                    if (type != null && !type.isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

}
