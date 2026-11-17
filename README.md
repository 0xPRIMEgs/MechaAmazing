# MechaAmazing
Mecha Animation System for Java

Prime License:
If you're a Christian you can use this as MIT. If not apply for it's use in an issue. Jesus is Lord.

---

## Cheatsheet

A quick reference for MechaAmazing's public Java API, arranged by class with short descriptions in the style of the [raylib cheatsheet](https://www.raylib.com/cheatsheet/cheatsheet.html).

MechaAmazing builds articulated models from triangle meshes, pivot frames, and named joints. Pose and animation data are separate from geometry, so compatible rigs can share animations.

**API reference:** includes live shared-texture switching, November 16, 2026, based on GitHub commit `a199d52`. The supplied JAR targets Java 17 bytecode; your raylib binding and native dependencies must also be compatible with your Java runtime. Rendering uses `com.raylib.Raylib` and JavaCPP. These dependencies are not bundled in the MechaAmazing JAR.

```java
import MechaAmazing.*;
import static com.raylib.Raylib.*;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Vector;
```

| Class | Purpose |
| --- | --- |
| [MAIO](#maio--loading-and-saving) | Load and save meshes, poses, and animations; import a single GLB mesh. |
| [MARig](#marig--named-joints-and-posing) | Define a hierarchy, install parts, and control joint transforms. |
| [MAHumanoidRig](#mahumanoidrig--17-body-parts) | A predefined 17-part humanoid hierarchy. |
| [MAPose](#mapose--joint-transform-snapshots) | Capture, apply, and blend a set of joint transforms. |
| [MAAnimation](#maanimation--keyframes-and-playback) | Store, edit, and sample timed poses. |
| [MAGpuRig](#magpurig--gpu-rendering) | Draw texture batches; retain one GPU call for a shared texture and reuse uploads across offset draws. |
| [MAMesh](#mamesh--geometry-and-attachments) | Store triangles, a root pivot, connectors, and child meshes. |
| [MAMeshMath](#mameshmath--transforms-and-quaternions) | Transform copies of meshes and work with quaternions. |
| [MAPivot](#mapivot--attachment-frames) | Store an attachment position and orientation. |
| [MAVert / MATri](#mavert-and-matri--vertices-and-triangles) | Build colored, textured triangles. |

### Conventions

- All rotation angles are **radians**. Animation times are **seconds**.
- Quaternion component order is **`qx, qy, qz, qw`**; identity is **`0, 0, 0, 1`**.
- A pose joint record is **`tx, ty, tz, qx, qy, qz, qw`**.
- Joint and connector indices are zero-based. Joint names are case-sensitive.
- Fluent setters return the same object. `MAMeshMath` geometry operations return a **new mesh tree**; keep the return value.
- Queue pose changes, then call `rig.update()` once. Both rig drawing paths also call `update()` automatically.
- Signature blocks below are API references, not complete Java class declarations. All listed methods are public; static methods are marked `static`.

## MAIO — loading and saving

```java
static MAMesh loadMAMesh(String path) throws IOException;             // Load MAP2 or legacy BU02 mesh data.
static void saveMAMesh(MAMesh mesh, String path) throws IOException;  // Save one mesh and its connector frames.
static void saveMAMesh(String path, MAMesh mesh) throws IOException;  // Same operation, reversed arguments.

static MAPose loadPose(String path) throws IOException;              // Load a pose without applying it.
static void loadPose(String path, MARig target) throws IOException;  // Load, apply, and update the target rig.
static void savePose(MAPose pose, String path) throws IOException;   // Save a pose snapshot.
static void savePose(MARig rig, String path) throws IOException;     // Capture and save the rig's local pose.

static MAAnimation loadAnimation(String path) throws IOException;   // Load timed keyframes.
static void saveAnimation(MAAnimation animation, String path)
    throws IOException;                                            // Save an animation with at least one keyframe.

static MAMesh load(String path);                                    // Import one GLB 2.0 mesh.
static Texture texture();                                          // Texture from the latest GLB load, or null.
```

**Mesh files:** Saving `.mamesh` subtracts the root pivot position so the saved root is at `(0, 0, 0)`. It preserves root/connector orientations, vertex positions, and UVs. It does not rotate the geometry into an identity frame. Vertex colors, textures, attached child meshes, and mesh transform metadata are not saved; loaded vertex colors are white. Load textures separately.

**Pose/animation files:** MARIG v2 stores rig type, joint names, local translations, and local rotations. It does not store geometry, world placement, or `motionUnit`. Legacy v1 humanoid files are also readable. Pose and animation file kinds must match the loader; extensions alone do not determine compatibility. Save directories must already exist.

**GLB import:** `load()` supports exactly one mesh with one triangle primitive. It reads mesh-local positions, UVs, colors, and an optional embedded PNG/JPEG base-color texture. Scene node transforms are not applied. The result has an origin pivot and no connectors. Texture loading needs an initialized window/context. Capture `MAIO.texture()` after each GLB load and unload that texture yourself when finished. Import failures throw `IllegalArgumentException`.

## MARig — named joints and posing

```java
MARig();                                                          // Create an empty type-0 rig.
MARig(int type);                                                  // Create an empty rig with a nonnegative type ID.
int type();                                                      // Read the compatibility type ID.

MARig define(String name, String parent, int connector);           // Define a joint; define its parent first.
MARig setPart(String name, MAMesh mesh);                           // Install/replace a copied rest mesh; null removes it.
MARig replace(String name, MAMesh mesh);                           // Alias for setPart().
MARig setParts(Map<String, MAMesh> parts);                         // Batch install/replace; map order is unrestricted.
MARig connector(String name, int index);                          // Change a child joint's parent-connector index.

Set<String> names();                                             // Read-only set of defined joint names.
MARig.Joint joint(String name);                                   // Stable named-joint handle; unknown names throw.
MAMesh mesh(String name);                                         // Current posed mesh, or null if unavailable.
MAMesh root();                                                    // Current root mesh, or null.

MARig rotation(String name, float rx, float ry, float rz);         // Set absolute local rotation relative to rest.
MARig rotationQuaternion(String name,
    float qx, float qy, float qz, float qw);                       // Set local rotation with a normalized quaternion.
MARig translation(String name, float x, float y, float z);         // Set local offset in pose units.
float[] jointRotation(String name);                               // Copy of local quaternion: qx, qy, qz, qw.
float[] jointTranslation(String name);                            // Copy of local translation: x, y, z.

MARig position(float x, float y, float z);                         // Set the root's world position before local offset.
MARig orientation(float rx, float ry, float rz);                   // Set whole-rig rotation.
MARig motionUnit(float unit);                                    // Set positive world length per pose translation unit.
float motionUnit();                                              // Read that length; default is 1.
MARig resetPose();                                               // Reset all local rotations/offsets and update.
MARig update();                                                  // Update changed joints and their descendants.
MARig setColor(int r, int g, int b, int a);                       // Replace all RGBA; reset per-joint color overrides.
MARig setColor(String name, int r, int g, int b, int a);            // Recolor one joint, including future replacements.
MARig setTexture(String name, Texture texture);                   // Installed part only; null uses renderer fallback.

void draw();                                                     // Submit the current rig through rlgl.
void draw(float x, float y, float z);                              // Draw with an extra world offset; does not move the rig.
void drawTextured(Texture fallback);                              // Per-part textures; manages its own rlgl blocks.
void drawTextured(Texture fallback, float x, float y, float z);
```

`MARig.Joint` exposes:

```java
final String name;                  // Public joint name.
MAMesh mesh();                      // Current mesh; resolves again after each rig update.
String parent();                    // Parent joint name, or null for the root.
int connector();                    // Index into the parent's child points.
boolean installed();                // Whether this joint has a rest mesh installed.
```

Define exactly one root with `parent == null`; use `-1` for its unused connector. Install **unassembled** parts with non-null pivots. A missing ancestor hides its descendants without uninstalling them.

Rotation and translation calls set absolute offsets from rest; they do not accumulate each frame. `resetPose()` preserves world position, world orientation, and motion units. `setPart()`, `replace()`, `setParts()`, and `connector()` update immediately; transform setters queue changes.

Treat returned meshes as rig-owned views. Change parts through `setPart()`/`replace()`, change installed appearances through `setTexture(name, ...)`/`setColor(name, ...)`, and animate through rig methods. Do not call texture/color setters on a posed mesh view: that bypasses source data and GPU invalidation. There is no rig copy constructor in this API: construct each character's rig separately from the source parts. Installation copies those parts, giving each rig independent pose state.

## MAHumanoidRig — 17 body parts

```java
MAHumanoidRig();                          // Define the humanoid hierarchy without geometry; type is 1.
MAHumanoidRig(Map<String, MAMesh> parts);  // Define the hierarchy and install supplied parts.
MAHumanoidRig setColor(int r, int g, int b, int a); // Recolor all parts; fluent humanoid return.
MAHumanoidRig setColor(String name, int r, int g, int b, int a);
MAHumanoidRig setTexture(String name, Texture texture);
```

Inherits the full `MARig` API. Each name below is also a public `MAMesh` field, refreshed after rig updates. Treat those fields as read-only views; use `setPart()` to replace geometry.

| Joint / field | Parent | Parent connector |
| --- | --- | --- |
| `groin` | — | — |
| `abs` | `groin` | `0` |
| `chest` | `abs` | `0` |
| `neck` | `chest` | `0` |
| `head` | `neck` | `0` |
| `leftUpperArm` | `chest` | `1` |
| `leftLowerArm` | `leftUpperArm` | `0` |
| `leftHand` | `leftLowerArm` | `0` |
| `rightUpperArm` | `chest` | `2` |
| `rightLowerArm` | `rightUpperArm` | `0` |
| `rightHand` | `rightLowerArm` | `0` |
| `leftThigh` | `groin` | `1` |
| `leftCalf` | `leftThigh` | `0` |
| `leftFoot` | `leftCalf` | `0` |
| `rightThigh` | `groin` | `2` |
| `rightCalf` | `rightThigh` | `0` |
| `rightFoot` | `rightCalf` | `0` |

The constructor does not create shapes or automatically mirror left parts. Supply all desired meshes yourself. Use inherited `connector(name, index)` if your authored connector order differs.

## MAPose — joint transform snapshots

```java
static final int INVALID = -1;  // Uninitialized pose.
static final int ANY = 0;       // Accept any rig type, but still require matching joint names.
static final int HUMANOID = 1;  // MAHumanoidRig type.

MAPose();                                           // Invalid placeholder; not an empty playable pose.
MAPose(int type, Map<String, float[]> joints);        // Copy named 7-float records; normalize rotations.
int type();                                         // Pose compatibility type ID.
Set<String> names();                                // Read-only joint-name set.
float[] joint(String name);                          // Copy of tx, ty, tz, qx, qy, qz, qw.
MAPose copy();                                       // Independent snapshot.

static MAPose capture(MARig rig);                    // Capture all defined joints' local transforms.
void validate(MARig rig);                            // Check type and exact joint-name compatibility.
void queue(MARig rig);                               // Set local transforms without rebuilding geometry.
void apply(MARig rig);                               // Queue the pose and update the rig immediately.
static MAPose interpolate(MAPose a, MAPose b, float t); // Blend translation and quaternion rotation; clamp t to 0..1.
```

`capture()` excludes world position and orientation. Pose application requires **exactly the same joint-name set** as the rig, including defined joints without installed meshes. Blending requires matching pose types and names. Additional positive type IDs are available for custom rigs.

## MAAnimation — keyframes and playback

Extends `MAPose`. Used through the inherited pose-only methods, an animation exposes its **first keyframe**. Use the timed overloads for playback.

```java
MAAnimation();                                    // Empty type-0 animation; add frames before sampling.
MAAnimation(int type);                            // Empty animation with a nonnegative type ID.
int type();                                       // Animation compatibility type ID.

NavigableMap<Double, MAPose> frames();             // Read-only time-sorted keyframe map.
void put(double t, MAPose pose);                   // Snapshot a pose at t; replace a key at the same time.
boolean delete(double t);                         // Remove an exact key time; report whether it existed.
void move(double from, double to);                // Move an existing key; occupied destination throws.
double duration();                                // Last keyframe time, or 0 when empty.

MAPose sample(double t);                          // Interpolate a pose at time t.
void queue(MARig rig, double t);                   // Sample directly into the rig without a temporary pose/map.
void apply(MARig rig, double seconds);             // Queue at time and update immediately.
```

Times must be finite and nonnegative. Sampling holds the first/last pose outside the keyframe range. Translation is linear; rotation uses shortest-path quaternion interpolation. Keyframe types must match the animation type, and all frames must use the same joint names. For a humanoid, use `new MAAnimation(MAPose.HUMANOID)` or `new MAAnimation(rig.type())`.

Playback time and looping belong to your game:

```java
// Initialize elapsed = 0.0 when this animation starts.
elapsed += GetFrameTime();
double duration = animation.duration();
double t = duration > 0.0 ? elapsed % duration : 0.0;
animation.queue(rig, t);
rig.update();
```

This requires at least one keyframe. For one-shot playback, use `Math.min(elapsed, duration)` instead of `%`. For a seamless loop, make the last pose match the first. Calling `queue(rig, 0)` every frame holds the starting pose; it does not advance an internal timer. Inherited `copy()` returns a `MAPose` of the first frame, not a copy of the animation timeline.

## MAGpuRig — GPU rendering

```java
MAGpuRig(MARig rig);                                    // ORDERED, using raylib's white fallback texture.
MAGpuRig(MARig rig, Texture fallback);                  // ORDERED per-part textures, with a shared fallback.
MAGpuRig(MARig rig, Texture fallback, MAGpuRig.TextureMode mode);
MAGpuRig setTexture(Texture t);                         // Change shared fallback; no mesh rebuild/upload. Null = white.
MAGpuRig textureMode(MAGpuRig.TextureMode mode);         // Rebuild batches on the next draw if changed.
MAGpuRig.TextureMode textureMode();
MAGpuRig setColor(int r, int g, int b, int a);           // Global renderer tint; no vertex upload.
MAGpuRig setColor(String name, int r, int g, int b, int a); // Renderer-only part tint; updates colors only.
MAGpuRig clearColor(String name);                       // Remove that renderer's part tint.
void draw();                                           // Update changed data; DrawMesh per texture batch.
void draw(float x, float y, float z);                    // Extra world offset, reusing the same uploaded pose.
void close();                                          // Free owned buffers/material; never unload textures.
```

Create, draw, and close on the graphics thread. Draw inside `BeginMode3D()` and **outside** `rlBegin()`/`rlEnd()`. All textures are **borrowed**: close renderers before unloading textures or closing the window. Multiple renderers may share a rig and textures. A mesh copy, mirror, transform, and rig installation preserve its texture reference; they do not duplicate the GPU texture.

### Changing the shared texture

```java
MAGpuRig gpu = new MAGpuRig(body); // Can draw immediately with white + vertex colors.
gpu.setTexture(bodyTex);
gpu.draw();
gpu.setTexture(alternateBodyTex); // Reuses the same GPU mesh buffers.
gpu.draw();
gpu.setTexture(null);             // Restore raylib's white texture.
```

`setTexture` changes this renderer's shared fallback, the same texture supplied to
the existing constructors. In `ORDERED`/`BATCHED`, explicit part textures keep their
own images. Use `SINGLE` to apply the shared texture to every part. Constructors
also accept `null` for the white fallback. The default white texture is borrowed
from raylib; the renderer allocates no new GPU texture for it.

The setter changes only the material texture/reference. It never rebuilds batches
or uploads positions, UVs, or colors. Normal animation and geometry edits still
perform their usual updates. Load replacement textures once and reuse them; the
setter does not load or unload textures. UVs are unchanged, so alternate skins
should use the same layout.

### Optional per-part textures

```java
// Load textures once, after InitWindow(). Attach references before installing parts.
MAMesh hand = MAIO.loadMAMesh("assets/rightHand.mamesh").texture(handTex);
body.setPart("rightHand", hand);
MAGpuRig gpu = new MAGpuRig(body, bodyTex); // All other untextured parts use bodyTex.

// Change an already installed part without replacing its geometry:
body.setTexture("rightHand", alternateHandTex);
body.setTexture("rightHand", null);       // Return to this renderer's fallback.
```

You can also pass a texture to `new MAMesh(triangles, pivot, texture)`. Both the constructor argument and `.texture(...)` are optional. Texture selection belongs to the mesh, so no parallel `Vector<Texture>` or joint-to-texture map needs to stay synchronized. If you replace a part, the replacement brings its own texture. `setTexture` requires an installed part, including one hidden by a missing ancestor.

| Mode | Behavior | Use |
| --- | --- | --- |
| `ORDERED` (default) | Merge adjacent fallback users or adjacent explicit textures with the same ID, preserving original joint/triangle order. | Safe default, including transparent textures/effects. |
| `BATCHED` | One fallback group plus groups by explicit texture ID, preserving first group encounter order. | Opaque parts, or any scene where reordering is known safe. |
| `SINGLE` | Ignore per-part texture references; use the renderer's fallback for everything. | Force one shared texture/atlas and one batch. |

```java
gpu.textureMode(MAGpuRig.TextureMode.BATCHED); // Explicit opt-in to reordering.
gpu.textureMode(MAGpuRig.TextureMode.SINGLE);  // Ignore overrides for this renderer only.
gpu.textureMode(MAGpuRig.TextureMode.ORDERED);// Restore per-part textures without reordering.
```

`ORDERED` produces **one DrawMesh call** when all visible parts use the shared fallback, or all use explicit textures with the same ID. Fallback users stay separate from explicit textures even when their IDs currently match; this allows `gpu.setTexture(...)` to change only the fallback without splitting or rebuilding a mesh later. Mixed fallback/explicit parts may therefore use extra draw calls. There is no per-frame texture-map lookup or per-part texture scan. Geometry, per-part texture assignments, and mode changes rebuild buffers on the next draw; normal animation streams positions into existing buffers, and repeated offset draws reuse that upload. `gpu.setTexture(...)` never triggers a rebuild. `SINGLE` also ignores per-part texture-only edits without rebuilding. Switching back to a part-texture mode picks up the current assignments.

`BATCHED` does not inspect texture pixels or sort transparency. Use `ORDERED` for alpha-blended effects, including effects made transparent by color/tint. ORDERED preserves the original order; it does not automatically depth-sort transparent surfaces. UVs and texture dimensions are unchanged; parts must have UVs appropriate to their textures. Texture IDs are grouped, so different Java wrappers for the same live GPU texture can share a batch. Do not mutate a bound Texture descriptor or unload/reload its ID behind a rig; use `setTexture` with the replacement resource.

Textures are runtime resources: `.mamesh`, `.marig`, pose, and animation formats remain unchanged and do **not** serialize texture handles or paths. Reassign textures after loading. The existing `.mamesh` format also loads vertex colors as white; set custom colors after loading as before. GLB import retains its existing `MAIO.texture()` output; opt in with `importedMesh.texture(MAIO.texture())` when needed.

### Falcon punch: attached mesh, separate texture, GPU rendering

```java
// Initialize once. The hand asset needs child point 0 at the effect's attachment frame.
MAMesh falcon = MAIO.loadMAMesh("assets/falcon.mamesh").texture(falconTex);
body.define("punchFX", "rightHand", 0);
MAGpuRig gpu = new MAGpuRig(body, bodyTex);

// On entering the effect's active window:
body.setPart("punchFX", falcon);

// Each frame, after queueing animation:
gpu.draw(drawX, drawY, drawZ); // Falcon follows the hand and uses falconTex.

// On leaving the active window, or interruption:
body.setPart("punchFX", null);
```

No separate tracking rig is needed. Do not call `setPart` every frame: installation/removal changes geometry and triggers a buffer rebuild on the next draw. For an independently drawn effect that is shown/hidden very frequently, a persistent separate rig remains an option to avoid those transition rebuilds.

### Colors and transparent trails

RGBA integers use `0..255`; other values wrap to a byte, as in `MAMesh.setColor`. Authored per-mesh/per-vertex colors work by default and do not require extra draw calls.

```java
body.setColor("rightHand", 255, 120, 40, 255); // Replace one joint's vertex RGBA.
body.setColor(255, 255, 255, 255);             // Replace all RGBA, clearing per-joint overrides.

gpu.setColor("rightHand", 255, 80, 80, 255);  // Part tint on THIS renderer only.
gpu.clearColor("rightHand");                 // Restore that part's untinted vertex colors.
trailGpu.setColor(80, 180, 255, 80);           // Multiply all parts by a global renderer tint.
trailGpu.draw(-2f, 0f, 0f);
trailGpu.setColor(255, 255, 255, 255);         // Reset global tint; per-part tints remain.
```

| Operation | Meaning | Lifetime / update cost |
| --- | --- | --- |
| `sourceMesh.setColor(r,g,b,a)` | Replace that source mesh's vertex colors; not its descendants. | Install with `setPart`; copied along with geometry. |
| `rig.setColor(name,r,g,b,a)` | Replace one joint's vertex colors. | Survives animation, hiding, removal and replacement; color-buffer update only. |
| `rig.setColor(r,g,b,a)` | Replace all vertex colors; clear per-joint overrides. | Also applies to future installed parts; color-buffer update only. |
| `gpu.setColor(name,r,g,b,a)` | Multiply one joint's vertex colors for this renderer. | Survives part replacement; color-buffer update only; `clearColor(name)` removes it. |
| `gpu.setColor(r,g,b,a)` | Global material tint, multiplied with vertex colors and any part tint. | No vertex upload; independent across renderers. |

A later per-joint rig color overrides the rig-wide color for that joint; a later whole-rig color resets all joint overrides. Recoloring source vertices is destructive: setting them to white does not restore their old authored colors. Renderer tints are reversible and do not modify the rig or other renderers. Integer per-part tint multiplication rounds down to a byte; final RGBA also includes texture color and global material tint. Part tinting is skipped entirely when no renderer part tints exist, and colors are not recomputed on ordinary pose updates.

The renderer uses the existing unlit DrawMesh/UpdateMeshBuffer path, with no instancing, texture arrays, custom shaders, or new dependencies. CPU posing remains on the CPU; GPU buffers handle rendering. See [PERFORMANCE.md](PERFORMANCE.md) for the measured limits.

## MAMesh — geometry and attachments

```java
MAMesh(Vector<MATri> tris, MAPivot pivotPoint); // Use supplied triangle vector and non-null pivot by reference.
MAMesh(Vector<MATri> tris, MAPivot pivotPoint, Texture texture); // Optional borrowed texture.
MAMesh(MAMesh other);                         // Deep-copy geometry/frames; share texture references.
Texture texture();                           // Null means renderer fallback.
MAMesh texture(Texture texture);             // Set source mesh texture; null clears it.

void draw();                                 // Submit this mesh and descendants inside an rlgl triangle block.
void draw(float x, float y, float z);          // Submit with an extra offset; vertices remain unchanged.
void drawTextured(Texture fallback);          // Honor each mesh's texture; outside rlBegin/rlEnd.
void drawTextured(Texture fallback, float x, float y, float z);
MAMesh setColor(int r, int g, int b, int a);  // Set this mesh's vertex colors; does not recolor descendants.

MAPivot pivotPoint();                        // Get the root attachment frame by reference.
MAMesh pivotPoint(MAPivot point);             // Replace the frame reference; does not move vertices.
MAPivot childPoint(int i);                    // Get a connector frame by index.
MAMesh childPoint(MAPivot point);             // Append a connector frame.
int childPointSize();                        // Count connector frames.
MAMesh childPointRemove(int i);               // Remove a connector; later indices shift.

int childIndex();                            // This mesh's parent-connector index, default -1.
MAMesh childIndex(int i);                     // Set index metadata only.
MAMesh child(int i);                          // Get a child by child-list position, not connector index.
MAMesh child(MAMesh child, int connector);    // Attach a placed copy; return the parent.
MAMesh attachAsChild(MAMesh child, int connector); // Attach a placed copy; return the attached copy.

MAMesh updateBounds();                        // Recalculate bounds of this mesh's triangles only.
```

Scalar accessors and fluent setters:

```java
float x();       MAMesh x(float value);        // Position metadata.
float y();       MAMesh y(float value);
float z();       MAMesh z(float value);

float pitch();   MAMesh pitch(float value);    // Euler X readout / metadata.
float yaw();     MAMesh yaw(float value);      // Euler Y readout / metadata.
float roll();    MAMesh roll(float value);     // Euler Z readout / metadata.

float qx();      MAMesh qx(float value);       // Quaternion orientation metadata.
float qy();      MAMesh qy(float value);
float qz();      MAMesh qz(float value);
float qw();      MAMesh qw(float value);

float scaleX();  MAMesh scaleX(float value);   // Scale bookkeeping.
float scaleY();  MAMesh scaleY(float value);
float scaleZ();  MAMesh scaleZ(float value);

float width();   MAMesh width(float value);    // Cached extent on X.
float height();  MAMesh height(float value);   // Cached extent on Y.
float depth();   MAMesh depth(float value);    // Cached extent on Z.
float centerX(); MAMesh centerX(float value);  // Cached bounds center.
float centerY(); MAMesh centerY(float value);
float centerZ(); MAMesh centerZ(float value);
```

Direct metadata setters do **not** transform geometry. Use `MAMeshMath` to move/rotate/scale a mesh, or `MARig` for an animated assembly. Euler setters also do not update quaternion fields.

Attachment creates a copy aligned to the connector's position **and** orientation. An invalid connector is silently ignored: `child(mesh, i)` returns the parent unchanged; `attachAsChild(mesh, i)` returns the original, unattached input. Validate connector indices before attaching. Keep the hierarchy a tree without cycles or shared child instances.

Bounds are not automatically recalculated after loading, copying, transforming, or posing; the copy constructor does not preserve cached bounds. Call `updateBounds()` before reading them. Bounds exclude attached descendants and draw-only offsets. For a whole rig, update each visible part's bounds and combine its minimum/maximum coordinates:

```java
mesh.updateBounds();
float minX = mesh.centerX() - mesh.width()  * 0.5f;
float maxX = mesh.centerX() + mesh.width()  * 0.5f;
float minY = mesh.centerY() - mesh.height() * 0.5f;
float maxY = mesh.centerY() + mesh.height() * 0.5f;
float minZ = mesh.centerZ() - mesh.depth()  * 0.5f;
float maxZ = mesh.centerZ() + mesh.depth()  * 0.5f;
```

## MAMeshMath — transforms and quaternions

All mesh-returning methods below copy the input tree and transform the copy, including its vertices, pivots, connectors, and descendants. Always use the returned mesh.

```java
static MAMesh rotateX(MAMesh mesh, float angle);  // Model X axis, around the mesh's root pivot.
static MAMesh rotateY(MAMesh mesh, float angle);  // Model Y axis, around the mesh's root pivot.
static MAMesh rotateZ(MAMesh mesh, float angle);  // Model Z axis, around the mesh's root pivot.

static MAMesh rotateX(MAMesh mesh, float angle, float px, float py, float pz); // Explicit rotation center.
static MAMesh rotateY(MAMesh mesh, float angle, float px, float py, float pz);
static MAMesh rotateZ(MAMesh mesh, float angle, float px, float py, float pz);

static MAMesh rotateLocalX(MAMesh mesh, float angle);          // Mesh-oriented X axis, around its root pivot.
static MAMesh rotateLocalY(MAMesh mesh, float angle);          // Mesh-oriented Y axis, around its root pivot.
static MAMesh rotateLocalZ(MAMesh mesh, float angle);          // Mesh-oriented Z axis, around its root pivot.
static MAMesh rotateXYZ(MAMesh mesh, float x, float y, float z); // Apply model-axis X, then Y, then Z.

static MAMesh translate(MAMesh mesh, float x, float y, float z); // Add a model-space displacement.
static MAMesh scale(MAMesh mesh, float sx, float sy, float sz);  // Model-axis scaling around the root pivot.

static MAMesh mirrorX(MAMesh mesh);               // Reflect across x = root-pivot X.
static MAMesh mirrorY(MAMesh mesh);               // Reflect across y = root-pivot Y.
static MAMesh mirrorZ(MAMesh mesh);               // Reflect across z = root-pivot Z.
static MAMesh mirrorX(MAMesh mesh, float planeX);  // Reflect across x = planeX.
static MAMesh mirrorY(MAMesh mesh, float planeY);  // Reflect across y = planeY.
static MAMesh mirrorZ(MAMesh mesh, float planeZ);  // Reflect across z = planeZ.

static MAMesh attach(MAMesh child, MAPivot target); // Align copied root frame to target; does not add it to a parent.

static float[] orientation(MAMesh mesh);           // Normalized mesh quaternion copy.
static float[] orientation(MAPivot pivot);         // Normalized pivot quaternion copy.
static float[] normalized(float[] q);              // Return normalized quaternion; invalid/zero length throws.
static float[] multiply(float[] a, float[] b);      // Return normalized quaternion product a * b.
static float[] vector(float[] q, float x, float y, float z); // Rotate vector by unit quaternion q; return xyz.
```

Mirror methods repair triangle winding and reflect attachment orientations. UVs and colors travel with vertices; UV coordinates are not flipped. Prefer the mirror methods for mirrored parts. Nonuniform scaling is baked into geometry and may introduce shear; scalar metadata alone cannot reconstruct it.

```java
MAMesh left = MAIO.loadMAMesh("assets/leftHand.mamesh");
MAMesh right = MAMeshMath.mirrorX(left);
MAMesh smaller = MAMeshMath.scale(left, 0.5f, 0.5f, 0.5f);
MAMesh turned = MAMeshMath.rotateY(smaller, (float) Math.PI);
```

## MAPivot — attachment frames

```java
MAPivot();                          // Origin position, identity orientation.
MAPivot(MAPivot other);              // Copy a frame.

float x();  MAPivot x(float value);  // Frame position.
float y();  MAPivot y(float value);
float z();  MAPivot z(float value);

float qx(); MAPivot qx(float value); // Raw quaternion component access.
float qy(); MAPivot qy(float value);
float qz(); MAPivot qz(float value);
float qw(); MAPivot qw(float value);

MAPivot rotation(float x, float y, float z, float w); // Set and normalize a quaternion.
MAPivot rotationXYZ(float x, float y, float z);      // Set orientation: Rz(z) * Ry(y) * Rx(x).
```

Changing a pivot frame configures attachment position/orientation; it does not move or rotate mesh vertices. Use `rotation()` or `rotationXYZ()` to set a complete normalized orientation. Raw component setters do not normalize on their own.

## MAVert and MATri — vertices and triangles

### MAVert

```java
MAVert();                          // Position/UV = 0; RGBA = opaque white.
MAVert(MAVert other);               // Copy position, UV, and color.

float x(); MAVert x(float value);   // Vertex position.
float y(); MAVert y(float value);
float z(); MAVert z(float value);
float u(); MAVert u(float value);   // Texture coordinates.
float v(); MAVert v(float value);

byte r(); MAVert r(int value);      // Color channel; supply 0..255.
byte g(); MAVert g(int value);
byte b(); MAVert b(int value);
byte a(); MAVert a(int value);
```

Color getters return signed Java bytes. Use `Byte.toUnsignedInt(vertex.r())` when you need the numeric value `0..255`. Setters cast to `byte`; they do not clamp out-of-range integers.

### MATri

```java
MATri(MAVert a, MAVert b, MAVert c);       // Keep references to three supplied vertices.
MATri(MATri other);                       // Copy the triangle and its vertices.
MATri setColor(int r, int g, int b, int a); // Set all three vertex colors.
void draw();                              // Emit three vertices into the current rlgl block.
void draw(float x, float y, float z);       // Emit vertices with an additional offset.
```

```java
Vector<MATri> triangles = new Vector<>();
triangles.add(new MATri(
    new MAVert().x(-0.5f).y(0f).z(0f).u(0f).v(1f),
    new MAVert().x( 0.5f).y(0f).z(0f).u(1f).v(1f),
    new MAVert().x( 0.0f).y(1f).z(0f).u(0.5f).v(0f)
));
MAMesh triangle = new MAMesh(triangles, new MAPivot());
```

## Quick start — load, animate, draw

These snippets fit into an existing raylib game. Initialize the window and camera yourself, handle or declare `IOException`, and replace the asset paths with your own. The example expects one `.mamesh` file per humanoid joint, authored with the connector order above.

### 1. Load parts and create the renderer

Run once after `InitWindow()`:

```java
MAHumanoidRig humanoid = new MAHumanoidRig();
Map<String, MAMesh> parts = new LinkedHashMap<>();
for (String name : humanoid.names()) {
    parts.put(name, MAIO.loadMAMesh("assets/parts/" + name + ".mamesh"));
}
humanoid.setParts(parts);
humanoid.position(0f, 0f, 0f);

Texture texture = LoadTexture("assets/robot.png");
MAGpuRig renderer = new MAGpuRig(humanoid, texture);
MAAnimation animation = MAIO.loadAnimation("assets/idle.marig");
double elapsed = 0.0;
```

### 2. Advance and draw each frame

```java
elapsed += GetFrameTime();
double duration = animation.duration();
animation.queue(humanoid, duration > 0.0 ? elapsed % duration : 0.0);

// Inside your BeginDrawing()/EndDrawing() frame:
BeginMode3D(camera);
renderer.draw();                 // Calls humanoid.update() automatically.
// renderer.draw(3f, 0f, 0f);    // Optional second draw using the same current pose.
EndMode3D();
```

### 3. Clean up before closing the window

```java
renderer.close();
UnloadTexture(texture);
CloseWindow();
```

### Immediate rlgl drawing

For `MAMesh.draw()`, `MATri.draw()`, or `MARig.draw()`, provide the triangle/texture block yourself:

```java
// Inside your BeginDrawing()/EndDrawing() frame:
BeginMode3D(camera);
rlSetTexture(texture.id());
rlBegin(RL_TRIANGLES);
humanoid.draw();                 // Or mesh.draw(), triangle.draw(), etc.
rlEnd();
rlSetTexture(0);
EndMode3D();
```

Use this block in place of `renderer.draw()` for the legacy immediate path. It keeps your caller-selected texture and ignores per-mesh texture references, preserving existing behavior and overhead. To honor per-mesh textures through rlgl, use `humanoid.drawTextured(texture)` or `mesh.drawTextured(texture)` instead, outside any `rlBegin`/`rlEnd` block. These helpers manage their own texture switches/triangle blocks. `MAGpuRig.draw()` must also stay outside that block.

## Quick recipes

### Create a custom rig

```java
MARig rig = new MARig();
rig.define("base", null, -1);
rig.define("turret", "base", 0);
rig.setParts(Map.of(
    "base", MAIO.loadMAMesh("assets/base.mamesh"),
    "turret", MAIO.loadMAMesh("assets/turret.mamesh")
)); // base.mamesh must have connector 0.

rig.rotation("turret", 0f, (float) Math.PI / 2f, 0f);
rig.position(4f, 0f, 2f);
rig.update();
```

### Build a looping animation from poses

```java
humanoid.resetPose();
MAPose rest = MAPose.capture(humanoid);

humanoid.rotation("leftUpperArm", 0f, 0f, -0.6f);
MAPose raised = MAPose.capture(humanoid);

MAAnimation wave = new MAAnimation(humanoid.type());
wave.put(0.0, rest);
wave.put(0.5, raised);
wave.put(1.0, rest);
rest.apply(humanoid);

MAIO.saveAnimation(wave, "assets/wave.marig");
```

### Apply a pose, then add a procedural adjustment

```java
pose.queue(humanoid);
humanoid.rotation("head", 0f, lookYaw, 0f); // Replace this joint's pose rotation.
humanoid.position(actorX, actorY, actorZ);
humanoid.update();
```

### Blend two compatible poses

```java
MAPose blended = MAPose.interpolate(idlePose, aimPose, blendAmount);
blended.apply(humanoid);
```

### Save and restore a pose

```java
MAIO.savePose(humanoid, "assets/ready.marig");
MAPose ready = MAIO.loadPose("assets/ready.marig");
ready.apply(humanoid);
```

### Attach meshes without a rig

```java
MAMesh parent = MAIO.loadMAMesh("assets/body.mamesh");
MAMesh child = MAIO.loadMAMesh("assets/weapon.mamesh");
MAMesh attached = parent.attachAsChild(child, 0); // Requires parent connector 0.
// parent.draw() now also draws the attached copy.
```


## Build the library

Use JDK 17 or later and your existing JavaCPP-based Jaylib binding. The library keeps package `MechaAmazing`, targets Java 17 bytecode, and does not bundle Jaylib/native libraries.

```sh
# Linux / macOS; optionally set JAVA_HOME to your JDK.
./build.sh /path/to/your/jaylib.jar
```

```bat
rem Windows
build.bat C:\path\to\your\jaylib.jar
```

Output: `lib/MechaAmazing.jar`, also copied to the repository-root `MechaAmazing.jar`. Replace your old MA JAR with this one; keep your existing Jaylib/native dependencies. Do not keep two MA JARs on the same classpath. `src/Main.java` is the original asset-dependent demo and is not included in the library JAR. Its assets are not present in the upstream repository.

## Optimization and compatibility

- Shared-texture rigs still use one DrawMesh call, with original joint/triangle order. Optional part textures use ordered runs or explicit texture batching. Repeated offsets reuse uploaded positions.
- CPU posing updates only changed joints and descendants. Whole-rig movement still updates the whole model. Public meshes and humanoid fields remain current after `update()`.
- Pivot normalization no longer allocates temporary quaternion arrays.
- Existing public/protected API signatures, packages, CPU draw blocks, file formats, joint names and texture ownership are preserved. Per-part texture/color APIs and textured rlgl draw helpers are additive.
- Combined rendering streams positions for changed poses. Very dense rigs trade some upload bandwidth for fewer draw calls; gains depend on geometry, draw count and hardware.

See [PERFORMANCE.md](PERFORMANCE.md) for measured results and limits, and [tests/README.md](tests/README.md) for the reproducible compatibility, rendering, color and benchmark checks. These are synthetic workloads, not a hardware FPS guarantee.
