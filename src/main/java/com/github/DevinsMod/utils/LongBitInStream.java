package com.github.DevinsMod.utils;

public class LongBitInStream {

    long[] data;
    int longIndex;
    long atLong;
    int bitsPeeled;
    static long[] masks = new long[65];
    static {
        long v = 0;
        for (int i = 0; i < 65; i++) {
            LongBitInStream.masks[i] = v;
            v = v << 1 | 1;
        }
    }

    public LongBitInStream(long[] data) {
        this.data = data;
        if (data.length > 0) {
            atLong = data[0];
        }
    }

    public int readBits(byte bitCount) {
        int result = 0;
        if (bitsPeeled + bitCount > 64) {
            bitsPeeled = 0;
            longIndex++;
            atLong = data[longIndex];
        }
        result = (int) (atLong & LongBitInStream.masks[bitCount]);
        atLong = atLong >> bitCount;
        bitsPeeled += bitCount;
        return result;
    }

}
