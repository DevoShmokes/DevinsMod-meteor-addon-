package com.github.DevinsMod.tracker;
import com.github.DevinsMod.utils.LongBitInStream;
import lombok.Getter;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.PacketByteBuf;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.*;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import meteordevelopment.meteorclient.events.packets.PacketEvent;

public class WorldTracker {
    @Getter
    static List<BlockPos> enderChestPositions = new ArrayList<>();
    static List<Integer> targetBlockStateIds = new ArrayList<>();
    static {
        for (BlockState blockState : Block.STATE_IDS) {
            int stateId = Block.STATE_IDS.getRawId(blockState);
        }
    }

    private static void handleChunkSections(PacketByteBuf buf, int chunkX, int chunkZ) {
        int chunkHeight;
        boolean overworld = mc.world.getRegistryKey() == World.OVERWORLD;
        if (overworld) {
            chunkHeight = 24;
        } else {
            chunkHeight = 16;
        }
        for (int i = 0; i < chunkHeight; i++) {
            int yOffset;
            if (overworld) {
                yOffset = i * 16 - 64;
            } else {
                yOffset = i * 16;
            }
            WorldTracker.handleChunkSection(buf, yOffset, chunkX * 16, chunkZ * 16);
        }
    }

    private static void handleChunkSection(PacketByteBuf buf, int yOffset, int x, int z) {
        int nonEmptyBlockCount = buf.readShort();
        WorldTracker.readPalettedBlockContainer(buf, yOffset, x, z);
        WorldTracker.readPalettedBiomeContainer(buf);
    }

    private static void readPalettedBlockContainer(PacketByteBuf buf, int yOffset, int chunkX, int chunkZ) {
        int[] indirectPalette = new int[0];
        int singleType = 0;
        byte bitsPerEntry = buf.readByte();
        if (bitsPerEntry == 0) {
            singleType = WorldTracker.readSingleValuePalette(buf);
            //  System.out.println("Whole chunk section is " + Block.STATE_IDS.get(test).getBlock().getName().getString());
        } else if (bitsPerEntry <= 8) {
            indirectPalette = WorldTracker.readIndirectPalette(buf);
//            StringBuilder blockNames = new StringBuilder("Chunk section consists of ");
//            for (int i = 0; i < palette.length; i++) {
//                String blockName = Block.STATE_IDS.get(palette[i]).getBlock().getName().getString();
//                blockNames.append(blockName);
//                if (i < palette.length - 1) {
//                    blockNames.append(", ");
//                }
//            }
//            System.out.println(blockNames);
        } else {
            // System.out.println("Direct palette");
            WorldTracker.readDirectPalette(buf);
        }
        long[] data = new long[buf.readVarInt()];
        for (int i = 0; i < data.length; i++) {
            data[i] = buf.readLong();
        }
        LongBitInStream in = new LongBitInStream(data);
        if (bitsPerEntry == 0) {
            BlockState state = Block.STATE_IDS.get(singleType);
        } else if (bitsPerEntry <= 8) {
            for (int y = yOffset; y < 16 + yOffset; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = Block.STATE_IDS.get(indirectPalette[in.readBits(bitsPerEntry)]);
                        if (state.getBlock() == Blocks.ENDER_CHEST) {
                            BlockPos actualBlockPos = new BlockPos(chunkX + x, y, chunkZ + z);
                            if (!WorldTracker.enderChestPositions.contains(actualBlockPos)) WorldTracker.enderChestPositions.add(actualBlockPos);
                        }
                    }
                }
            }
        } else {
            for (int y = yOffset; y < 16 + yOffset; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = Block.STATE_IDS.get(in.readBits(bitsPerEntry));
                        if (state.getBlock() == Blocks.ENDER_CHEST) {
                            BlockPos actualBlockPos = new BlockPos(chunkX + x, y, chunkZ + z);
                            if (!WorldTracker.enderChestPositions.contains(actualBlockPos)) WorldTracker.enderChestPositions.add(actualBlockPos);
                        }
                    }
                }
            }
        }
    }


    private static void readPalettedBiomeContainer(PacketByteBuf buf) {
        byte bitsPerEntry = buf.readByte();
        if (bitsPerEntry == 0) {
            WorldTracker.readSingleValuePalette(buf);
        } else if (bitsPerEntry <= 3) {
            WorldTracker.readIndirectPalette(buf);
        } else {
            WorldTracker.readDirectPalette(buf);
        }
        long[] data = new long[buf.readVarInt()];
        for (int i = 0; i < data.length; i++) {
            data[i] = buf.readLong();
        }
    }

    private static int readSingleValuePalette(PacketByteBuf buf) {
        return buf.readVarInt();
    }

    private static int[] readIndirectPalette(PacketByteBuf buf) {
        int[] palette = new int[buf.readVarInt()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = buf.readVarInt();
        }
        return palette;
    }

    private static void readDirectPalette(PacketByteBuf buf) {
        // no idea what the fuck this does
    }


    @EventHandler
    private static void onReadPacket(PacketEvent.Receive event) {
        if (mc.world == null) return;
        if (event.packet instanceof ChunkDataS2CPacket packet) {
            WorldTracker.handleChunkSections(packet.getChunkData().getSectionsDataBuf(), packet.getChunkX(), packet.getChunkZ());
        }
    }
}
