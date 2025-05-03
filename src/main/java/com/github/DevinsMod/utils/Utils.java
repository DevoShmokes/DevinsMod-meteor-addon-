package com.github.DevinsMod.utils;

import java.util.List;

import com.github.DevinsMod.tracker.ServerSideValues;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.option.KeyBinding;
import org.joml.Matrix4f;
import java.util.ArrayList;
import net.minecraft.util.math.Box;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.render.Frustum;

import net.minecraft.component.ComponentMap;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Utils {

    private static long counter;
    private static double[] lastPoint;
    private static double[] secondLastPoint;


    public static boolean isClassLoaded(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    public static void setPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
        Input.setKeyState(key, pressed);
    }

    public static List<Vec3d> interpolatePath(List<Vec3d> path) {
        List<Vec3d> smoothedPath = new ArrayList<>();

        // Check if path is null or has fewer than 2 elements
        if (path == null || path.size() < 2) {
            return smoothedPath; // Return an empty list if no interpolation is needed
        }

        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d startVec = path.get(i);
            Vec3d endVec = path.get(i + 1);

            double distance = startVec.distanceTo(endVec);
            int steps = Math.max(2, (int) Math.ceil(distance)); // Ensure at least 2 steps for close points
            Vec3d step = endVec.subtract(startVec).multiply(1.0 / steps);

            for (int j = 0; j < steps; j++) {
                smoothedPath.add(startVec.add(step.multiply(j)));
            }
        }
        smoothedPath.add(path.getLast()); // Add the last point
        return smoothedPath;
    }

    public static float encodeDegrees(float degrees, int multiplier) {
        return degrees + (multiplier * 360.0F);
    }


    public static Vec3d getEyePos() {
        return ServerSideValues.serverSidePosition.add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);
    }

    

    public static List<ItemStack> getItemsInContainerItem(ItemStack itemStack) {
        ComponentMap components = itemStack.getComponents();
        if (components.contains(DataComponentTypes.CONTAINER)) {
            ContainerComponent containerComponent = components.get(DataComponentTypes.CONTAINER);
            return containerComponent.stream().toList();
        }
        return new ArrayList<>();
    }

    public static Vec3d getRotationVector(float pitch, float yaw) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d((i * j), (-k), (h * j));
    }

    public static double getDistanceBetweenBoxes(Box box1, Box box2) {
        // Find the closest point on box1 to box2
        Vec3d closestPointOnBox1 = Utils.getClosestPointOnBox(box2.getCenter(), box1);

        // Find the closest point on box2 to box1
        Vec3d closestPointOnBox2 = Utils.getClosestPointOnBox(box1.getCenter(), box2);

        // Calculate the distance between these two closest points
        return closestPointOnBox1.distanceTo(closestPointOnBox2);
    }

    public static boolean isBoxVisible(Box box) {
        Camera camera = mc.gameRenderer.getCamera();
// Get the view and projection matrices
        Matrix4f projectionMatrix = mc.gameRenderer.getBasicProjectionMatrix(mc.options.getFov().getValue());
        Matrix4f viewMatrix = new Matrix4f().rotate(camera.getRotation().conjugate(new org.joml.Quaternionf()));

        // Create a Frustum and update it with camera position
        Frustum frustum = new Frustum(viewMatrix, projectionMatrix);
        frustum.setPosition(camera.getPos().x, camera.getPos().y, camera.getPos().z);

        // Check if the Box is visible
        return frustum.isVisible(box);
    }

    public static Vec3d getClosestPointOnBox(Vec3d point, Box box) {
        double x = Math.max(box.minX, Math.min(box.maxX, point.x));
        double y = Math.max(box.minY, Math.min(box.maxY, point.y));
        double z = Math.max(box.minZ, Math.min(box.maxZ, point.z));
        return new Vec3d(x, y, z);
    }
    public static BlockPos getStaredBlock() {
        return Utils.getStaredBlock(210.0);
    }

    public static BlockPos getStaredBlock(double range) {
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        BlockPos pos = mc.world.raycast(new RaycastContext(cameraPos, cameraPos.add(Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(range)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player)).getBlockPos();
        int maxSearchHeight = 256;
        if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return null;
        for (int y = pos.getY(); y < pos.getY() + maxSearchHeight; y++) {
            BlockPos temppos = new BlockPos(pos.getX(), y, pos.getZ());
            if (mc.player.getHeight() < 0.9 ) {
                return temppos.up();
            }
            if (!mc.world.getBlockState(temppos).isSolid() && !mc.world.getBlockState(temppos.up()).isSolid()) {
                return temppos;
            }
        }
        return null;
    }


    public static boolean isChunkLoaded(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
    }




    private static double[] generatePoint(double radius) {
        // Use current time in milliseconds and time since system boot
        long timeMillis = System.currentTimeMillis();
        long nanoTime = System.nanoTime(); // Time since boot in nanoseconds

        // Add counter to the times to avoid repetition
        long seed1 = timeMillis + Utils.counter;
        long seed2 = nanoTime + Utils.counter;
        Utils.counter++;

        // Create pseudo-random values from the seeds
        double x = Utils.generateRandomFromSeed(seed1, radius);
        double y = Utils.generateRandomFromSeed(seed2, radius);
        double z = Utils.generateRandomFromSeed(seed1 + seed2, radius);

        // Scale the point so that it falls inside the sphere
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        if (magnitude > radius) {
            x = (x / magnitude) * radius;
            y = (y / magnitude) * radius;
            z = (z / magnitude) * radius;
        }

        return new double[] {x, y, z};
    }

    private static boolean isTooCloseToPreviousPoints(double[] point) {
        // Check if the point is within 5 units of the last two points
        if (Utils.lastPoint != null && Utils.distanceBetweenPoints(point, Utils.lastPoint) < 5.0) {
            return true;
        }
        return Utils.secondLastPoint != null && Utils.distanceBetweenPoints(point, Utils.secondLastPoint) < 5.0;
    }

    private static double distanceBetweenPoints(double[] p1, double[] p2) {
        double dx = p1[0] - p2[0];
        double dy = p1[1] - p2[1];
        double dz = p1[2] - p2[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double generateRandomFromSeed(long seed, double range) {
        // Generate a pseudo-random value from the seed
        long shiftedSeed = (seed ^ (seed << 21)) ^ (seed >> 35) ^ (seed << 4);
        // Normalize to the range -1 to 1, and scale to the given range
        return ((shiftedSeed % 2000000000L) / 1000000000.0 - 1) * range;
    }
}

