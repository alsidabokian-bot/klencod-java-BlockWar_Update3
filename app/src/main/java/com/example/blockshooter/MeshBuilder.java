package com.example.blockshooter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Builds a vertex buffer for a rectangular column range of the World (one chunk).
 * Only exposed faces are emitted.
 * Vertex layout: position(3) normal(3) color(3) = 9 floats.
 */
public class MeshBuilder {
    public static final int FLOATS_PER_VERTEX = 9;

    // face order: +Y, -Y, +X, -X, +Z, -Z ; corners wound counter-clockwise seen from outside
    private static final int[][] NORMALS = {
        {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int[][][] CORNERS = {
        {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
        {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
        {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}},
        {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
        {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
        {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}}
    };
    private static final int[] TRI = {0, 1, 2, 0, 2, 3};

    public FloatBuffer buffer;
    public int vertexCount;

    /** Columns x0..x1-1, z0..z1-1 (all heights). */
    public static MeshBuilder build(World w, int x0, int z0, int x1, int z1) {
        int faces = 0;
        for (int y = 0; y < w.sy; y++)
            for (int z = z0; z < z1; z++)
                for (int x = x0; x < x1; x++) {
                    if (w.get(x, y, z) == World.AIR) continue;
                    for (int f = 0; f < 6; f++) {
                        if (!hidden(w, x + NORMALS[f][0], y + NORMALS[f][1], z + NORMALS[f][2])) faces++;
                    }
                }

        MeshBuilder mb = new MeshBuilder();
        mb.vertexCount = faces * 6;
        ByteBuffer bb = ByteBuffer.allocateDirect(Math.max(4, mb.vertexCount * FLOATS_PER_VERTEX * 4));
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();

        for (int y = 0; y < w.sy; y++)
            for (int z = z0; z < z1; z++)
                for (int x = x0; x < x1; x++) {
                    int type = w.get(x, y, z);
                    if (type == World.AIR) continue;
                    float[] c = World.COLORS[type];
                    float v = 0.93f + 0.07f * hash(x, y, z);
                    for (int f = 0; f < 6; f++) {
                        int[] n = NORMALS[f];
                        if (hidden(w, x + n[0], y + n[1], z + n[2])) continue;
                        for (int k = 0; k < 6; k++) {
                            int[] p = CORNERS[f][TRI[k]];
                            fb.put(x + p[0]).put(y + p[1]).put(z + p[2]);
                            fb.put(n[0]).put(n[1]).put(n[2]);
                            fb.put(c[0] * v).put(c[1] * v).put(c[2] * v);
                        }
                    }
                }
        fb.position(0);
        mb.buffer = fb;
        return mb;
    }

    /** A face is hidden when the neighbouring cell is solid (below the world counts as solid). */
    private static boolean hidden(World w, int x, int y, int z) {
        if (y < 0) return true;
        return w.get(x, y, z) != World.AIR;
    }

    private static float hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= (h >>> 13);
        h *= 0x5bd1e995;
        h ^= (h >>> 15);
        return (h & 0xFFFF) / 65535f;
    }
}
