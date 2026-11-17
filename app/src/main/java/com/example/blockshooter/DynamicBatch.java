package com.example.blockshooter;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Collects boxes each frame and draws them in one call (bullets, particles, gun, soldiers).
 * Vertex layout matches MeshBuilder: position(3) normal(3) color(3).
 * Draw with face culling disabled, so winding order does not matter.
 */
public class DynamicBatch {
    private static final int MAX_VERTS = 36 * 900;
    private static final int FPV = MeshBuilder.FLOATS_PER_VERTEX;
    private static final int[] TRI = {0, 1, 2, 0, 2, 3};

    private final float[] data = new float[MAX_VERTS * FPV];
    private final FloatBuffer fb;
    private final float[] axes = new float[9];
    private final float[] corner = new float[12];
    private int count = 0;
    private int vertexCount = 0;

    public DynamicBatch() {
        fb = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public void begin() {
        count = 0;
        vertexCount = 0;
    }

    public void axisBox(float cx, float cy, float cz, float hx, float hy, float hz,
                        float r, float g, float b) {
        box(cx, cy, cz, hx, 0, 0, 0, hy, 0, 0, 0, hz, r, g, b);
    }

    /** Box from a centre and three half-extent vectors (u, v, w). */
    public void box(float cx, float cy, float cz,
                    float ux, float uy, float uz,
                    float vx, float vy, float vz,
                    float wx, float wy, float wz,
                    float r, float g, float b) {
        if (vertexCount + 36 > MAX_VERTS) return;
        axes[0] = ux; axes[1] = uy; axes[2] = uz;
        axes[3] = vx; axes[4] = vy; axes[5] = vz;
        axes[6] = wx; axes[7] = wy; axes[8] = wz;
        for (int a = 0; a < 3; a++) {
            int ia = a * 3, ib = ((a + 1) % 3) * 3, ic = ((a + 2) % 3) * 3;
            float len = (float) Math.sqrt(axes[ia] * axes[ia] + axes[ia + 1] * axes[ia + 1] + axes[ia + 2] * axes[ia + 2]);
            if (len < 1e-6f) continue;
            for (int s = -1; s <= 1; s += 2) {
                float nx = s * axes[ia] / len, ny = s * axes[ia + 1] / len, nz = s * axes[ia + 2] / len;
                float px = cx + s * axes[ia], py = cy + s * axes[ia + 1], pz = cz + s * axes[ia + 2];
                for (int i = 0; i < 4; i++) {
                    int sB = (i == 0 || i == 3) ? -1 : 1;
                    int sC = (i < 2) ? -1 : 1;
                    corner[i * 3]     = px + sB * axes[ib]     + sC * axes[ic];
                    corner[i * 3 + 1] = py + sB * axes[ib + 1] + sC * axes[ic + 1];
                    corner[i * 3 + 2] = pz + sB * axes[ib + 2] + sC * axes[ic + 2];
                }
                for (int k = 0; k < 6; k++) {
                    int q = TRI[k] * 3;
                    data[count++] = corner[q];
                    data[count++] = corner[q + 1];
                    data[count++] = corner[q + 2];
                    data[count++] = nx; data[count++] = ny; data[count++] = nz;
                    data[count++] = r;  data[count++] = g;  data[count++] = b;
                }
                vertexCount += 6;
            }
        }
    }

    public void flush(int aPos, int aNormal, int aColor) {
        if (vertexCount == 0) return;
        fb.clear();
        fb.put(data, 0, count);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        int stride = FPV * 4;
        fb.position(0);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, fb);
        fb.position(3);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, fb);
        fb.position(6);
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, stride, fb);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
    }
}
