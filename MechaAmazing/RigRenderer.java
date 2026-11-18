package MechaAmazing;

import static com.raylib.Raylib.*;

import com.raylib.Raylib.Material;
import com.raylib.Raylib.Matrix;
import com.raylib.Raylib.Mesh;
import com.raylib.Raylib.Texture;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.Pointer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rendering resources owned by one MARig. Nothing native is allocated until a draw. */
final class RigRenderer implements AutoCloseable {
    private final MARig rig;
    private final List<Batch> batches = new ArrayList<>();
    private MARig.TextureMode mode;
    private long geometryVersion = -1, textureVersion = -1, poseVersion = -1, colorVersion = -1;
    private Material material;
    private Matrix matrix;
    private Texture white;
    private Texture boundTexture;
    private boolean cpuOnly;
    private boolean tintChanged = true;
    private final int[] tint = {255, 255, 255, 255};

    RigRenderer(MARig rig){this.rig = rig;}
    boolean isUsingGpu(){return material != null;}

    void copyTintFrom(RigRenderer source) {
        System.arraycopy(source.tint, 0, tint, 0, tint.length);
        tintChanged = true;
    }

    void setTint(int r, int g, int b, int a) {
        if (tint[0] == (r & 255) && tint[1] == (g & 255) && tint[2] == (b & 255) && tint[3] == (a & 255)) return;
        tint[0] = r & 255;
        tint[1] = g & 255;
        tint[2] = b & 255;
        tint[3] = a & 255;
        tintChanged = true;
    }

    void draw(Texture fallback) {
        if (geometryVersion != rig.geometryVersion() || mode != rig.textureMode()
                || (mode != MARig.TextureMode.SINGLE && textureVersion != rig.textureVersion())) {
            rebuildBatches();
        }
        if (batches.isEmpty()) return;
        if (!cpuOnly) {
            try {
                if (material == null) {
                    cpuOnly = rlGetVersion() == 1;
                    if (!cpuOnly) openGpu();
                }
                if (!cpuOnly) syncBuffers();
            } catch (RuntimeException | LinkageError | OutOfMemoryError failure) {
                // All uploads and updates precede submission, so CPU can render this same frame once.
                cpuOnly = true;
                releaseGpu();
            }
        }
        if (cpuOnly) drawCpu(fallback);
        else drawGpu(fallback);
    }

    private void rebuildBatches() {
        for (Batch batch : batches) batch.release();
        batches.clear();
        mode = rig.textureMode();
        Map<Integer, Batch> byTexture = mode == MARig.TextureMode.BATCHED ? new LinkedHashMap<>() : null;
        Batch last = null;
        for (String name : rig.names()) {
            MARig.Joint joint = rig.joint(name);
            MAMesh part = joint.mesh();
            if (part == null || part.tris.isEmpty()) continue;
            Texture texture = mode == MARig.TextureMode.SINGLE ? null : part.texture();
            // Null is its own batch key so changing the fallback later does not recolor explicit textures.
            Integer key = texture == null ? null : texture.id();
            Batch batch = byTexture == null ? last : byTexture.get(key);
            if (batch == null || !java.util.Objects.equals(batch.key, key)) {
                batch = new Batch(texture, key);
                batches.add(batch);
                if (byTexture != null) byTexture.put(key, batch);
            }
            batch.parts.add(joint);
            last = batch;
        }
        geometryVersion = rig.geometryVersion();
        textureVersion = rig.textureVersion();
    }

    private void openGpu() {
        matrix = new Matrix().m0(1).m4(0).m8(0).m12(0).m1(0).m5(1).m9(0).m13(0)
                .m2(0).m6(0).m10(1).m14(0).m3(0).m7(0).m11(0).m15(1);
        material = LoadMaterialDefault();
        if (material == null || material.isNull() || material.maps() == null || material.maps().isNull()
                || material.shader().id() == 0) throw new IllegalStateException("Default GPU material unavailable");
        Texture source = material.maps().texture();
        white = new Texture().id(source.id()).width(source.width()).height(source.height())
                .format(source.format()).mipmaps(source.mipmaps());
    }

    private void syncBuffers() {
        boolean moved = poseVersion != rig.poseVersion();
        boolean recolored = colorVersion != rig.colorVersion();
        for (Batch batch : batches) {
            if (batch.mesh == null) {
                batch.upload();
                continue;
            }
            if (moved) {
                batch.copyPositions();
                UpdateMeshBuffer(batch.mesh, 0, batch.positions, Math.multiplyExact(batch.xyz.length, 4), 0);
            }
            if (recolored) {
                batch.copyColors();
                UpdateMeshBuffer(batch.mesh, 3, batch.colors, batch.rgba.length, 0);
            }
        }
        poseVersion = rig.poseVersion();
        colorVersion = rig.colorVersion();
    }

    private void drawGpu(Texture fallback) {
        matrix.m12(rig.renderX).m13(rig.renderY).m14(rig.renderZ);
        if (tintChanged) {
            material.maps().color().r((byte)tint[0]).g((byte)tint[1]).b((byte)tint[2]).a((byte)tint[3]);
            tintChanged = false;
        }
        Texture shared = fallback == null ? white : fallback;
        for (Batch batch : batches) {
            Texture texture = batch.texture == null ? shared : batch.texture;
            if (texture != boundTexture) {
                material.maps().texture(texture);
                boundTexture = texture;
            }
            DrawMesh(batch.mesh, material, matrix);
        }
    }

    private void drawCpu(Texture fallback) {
        boolean legacy = rlGetVersion() == 1;
        int shared = fallback == null ? rlGetTextureIdDefault() : fallback.id();
        boolean globalTint = tint[0] != 255 || tint[1] != 255 || tint[2] != 255 || tint[3] != 255;
        try {
            for (Batch batch : batches) {
                int textureId = batch.texture == null ? shared : batch.texture.id();
                rlSetTexture(textureId);
                rlBegin(RL_TRIANGLES);
                try {
                    if (!legacy) rlSetTexture(textureId);
                    for (MARig.Joint part : batch.parts) {
                        for (MATri tri : part.mesh().tris) {
                            if (globalTint || part.tint != null) drawTinted(tri, part.tint);
                            else tri.draw(rig.renderX, rig.renderY, rig.renderZ);
                        }
                    }
                } finally {
                    rlEnd();
                }
            }
        } finally {
            rlSetTexture(0);
        }
    }

    private void drawTinted(MATri tri, int[] partTint) {
        for (MAVert v : tri.verts) {
            rlTexCoord2f(v.u, v.v);
            rlColor4ub((byte)(channel(v.r, partTint, 0) * tint[0] / 255), (byte)(channel(v.g, partTint, 1) * tint[1] / 255),
                    (byte)(channel(v.b, partTint, 2) * tint[2] / 255), (byte)(channel(v.a, partTint, 3) * tint[3] / 255));
            rlVertex3f(v.x + rig.renderX, v.y + rig.renderY, v.z + rig.renderZ);
        }
    }

    private static int channel(byte base, int[] partTint, int index) {
        int value = base & 255;
        return partTint == null ? value : value * partTint[index] / 255;
    }

    private static Pointer allocate(int bytes) {
        Pointer pointer = MemAlloc(bytes);
        if (pointer == null || pointer.isNull()) throw new OutOfMemoryError("Mesh buffer");
        return pointer;
    }

    private static final class Batch {
        final Texture texture;
        final Integer key;
        final List<MARig.Joint> parts = new ArrayList<>();
        Mesh mesh;
        FloatPointer positions;
        BytePointer colors;
        MAVert[] vertices;
        float[] xyz;
        byte[] rgba;

        Batch(Texture texture, Integer key) {
            this.texture = texture;
            this.key = key;
        }

        void upload() {
            int count = 0;
            for (MARig.Joint part : parts) count = Math.addExact(count, Math.multiplyExact(part.mesh().tris.size(), 3));
            vertices = new MAVert[count];
            xyz = new float[Math.multiplyExact(count, 3)];
            rgba = new byte[Math.multiplyExact(count, 4)];
            float[] texcoords = new float[Math.multiplyExact(count, 2)];
            int i = 0;
            for (MARig.Joint part : parts) {
                for (MATri tri : part.mesh().tris) {
                    for (MAVert v : tri.verts) {
                        vertices[i] = v;
                        texcoords[i * 2] = v.u;
                        texcoords[i * 2 + 1] = v.v;
                        i++;
                    }
                }
            }
            mesh = new Mesh();
            Pointer.memset(mesh, 0, mesh.sizeof());
            mesh.vertexCount(count).triangleCount(count / 3);
            positions = new FloatPointer(allocate(Math.multiplyExact(xyz.length, 4)));
            mesh.vertices(positions);
            FloatPointer uv = new FloatPointer(allocate(Math.multiplyExact(texcoords.length, 4)));
            mesh.texcoords(uv);
            colors = new BytePointer(allocate(rgba.length));
            mesh.colors(colors);
            uv.put(texcoords);
            copyPositions();
            copyColors();
            UploadMesh(mesh, true);
            if (mesh.vboId() == null || mesh.vboId().isNull() || mesh.vboId().get(0) == 0
                    || mesh.vboId().get(1) == 0 || mesh.vboId().get(3) == 0) {
                throw new IllegalStateException("Vertex buffer upload failed");
            }
        }

        void copyPositions() {
            int i = 0;
            for (MAVert v : vertices) {
                xyz[i++] = v.x;
                xyz[i++] = v.y;
                xyz[i++] = v.z;
            }
            positions.put(xyz);
        }

        void copyColors() {
            int i = 0;
            for (MARig.Joint part : parts) {
                for (MATri tri : part.mesh().tris) {
                    for (MAVert v : tri.verts) {
                        rgba[i++] = (byte)channel(v.r, part.tint, 0);
                        rgba[i++] = (byte)channel(v.g, part.tint, 1);
                        rgba[i++] = (byte)channel(v.b, part.tint, 2);
                        rgba[i++] = (byte)channel(v.a, part.tint, 3);
                    }
                }
            }
            colors.put(rgba);
        }

        void release() {
            if (mesh != null) {
                UnloadMesh(mesh);
                mesh.close();
                mesh = null;
            }
            positions = null;
            colors = null;
            vertices = null;
            xyz = null;
            rgba = null;
        }
    }

    private void releaseGpu() {
        for (Batch batch : batches) batch.release();
        if (material != null && !material.isNull()) {
            if (material.maps() != null && !material.maps().isNull()) {
                material.maps().texture().id(0);  // The texture belongs to the caller or to raylib.
                UnloadMaterial(material);
            }
            material.close();
        }
        material = null;
        boundTexture = null;
        if (white != null) {white.close(); white = null;}
        if (matrix != null) {matrix.close(); matrix = null;}
    }

    @Override public void close() {
        releaseGpu();
        batches.clear();
    }
}
