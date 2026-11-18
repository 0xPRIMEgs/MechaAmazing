# MechaAmazing
Mecha Animation System for Java

Prime License:
If you're a Christian you can use this as MIT. If not apply for it's use in an issue. Jesus is Lord.

---

## Cheatsheet

A quick reference for MechaAmazing's public Java API, arranged by class with short descriptions in the style of the [raylib cheatsheet](https://www.raylib.com/cheatsheet/cheatsheet.html).

MechaAmazing builds articulated models from triangle meshes, pivot frames, and named joints. Pose and animation data are separate from geometry, so compatible rigs can share animations.

This edition combines posing and automatic GPU/CPU drawing in `MARig`. The supplied JAR targets Java 17 bytecode; your raylib binding and native dependencies must also be compatible with your Java runtime. Rendering uses `com.raylib.Raylib` and JavaCPP. These dependencies are not bundled in the MechaAmazing JAR.

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
MARig(Texture sharedTexture);                                     // Borrow a shared texture.
MARig(int type);                                                  // Create an empty rig with a nonnegative type ID.
int type();                                                      // Read the compatibility type ID.

MARig define(String name, String parent, int connector);           // Define a joint; define its parent first.
MARig setPart(String name, MAMesh mesh);                           // Install/replace a copied rest mesh; null removes it.
MARig replace(String name, MAMesh mesh);                           // Alias for setPart().
MARig setParts(Map<String, MAMesh> parts);                         // Batch install/replace; map order is unrestricted.
MARig connector(String name, int index);                          // Change a child joint's parent-connector index.
MARig attach(String partName, MARig child);                       // Snap the child's root pivot to the named part.
MARig attach(String partName, int childPointIndex, MARig child); // Pass null to clear the point.
MARig detach(MARig child);                                        // Release a child from this parent.

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

MARig position(float x, float y, float z);                         // Place the root in world space or relative to its grip.
MARig orientation(float rx, float ry, float rz);                   // Set whole-rig rotation.
MARig motionUnit(float unit);                                    // Set positive world length per pose translation unit.
float motionUnit();                                              // Read that length; default is 1.
MARig resetPose();                                               // Reset all local rotations/offsets and update.
MARig update();                                                  // Update changed joints and their descendants.
MARig setColor(int r, int g, int b, int a);                       // Replace all RGBA; reset per-joint color overrides.
MARig setColor(String name, int r, int g, int b, int a);            // Recolor one joint, including future replacements.
MARig setTexture(String name, Texture texture);                   // Installed part only; null uses renderer fallback.

void draw();                                                     // Draw the rig using its selected backend.
void draw(float x, float y, float z);                              // Draw with an extra world offset; does not move the rig.
void drawTextured(Texture fallback);                              // Override the shared texture for this draw.
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

Treat returned meshes as rig-owned views. Change parts through `setPart()`/`replace()`, change installed appearances through `setTexture(name, ...)`/`setColor(name, ...)`, and animate through rig methods. Do not call texture/color setters on a posed mesh view: that bypasses source data and GPU invalidation. For humanoid fighters, call the loaded body's `copy()` method to give each fighter its own rig.

## MAHumanoidRig — 17 body parts

```java
MAHumanoidRig();                          // Define the humanoid hierarchy without geometry; type is 1.
MAHumanoidRig(Map<String, MAMesh> parts);  // Define the hierarchy and install supplied parts.
MAHumanoidRig copy();                     // Copy the loaded body and pose in memory.
MAHumanoidRig setColor(int r, int g, int b, int a); // Recolor all parts; fluent humanoid return.
MAHumanoidRig setColor(String name, int r, int g, int b, int a);
MAHumanoidRig setTexture(String name, Texture texture);
```

In an avatar copy method, use `this.body = a.body.copy();`. The copy owns its meshes, pivots, pose, and renderer. It borrows the loaded textures and starts with empty attachment slots. Each fighter can equip a different rig at the same hand point. The copy works before a window opens, and its first draw selects its rendering backend. Close each body when its fighter is retired.

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

## Automatic rendering on MARig

Construct and animate rigs before or after creating the window. Call `draw()` or `draw(x, y, z)` inside `BeginMode3D`, outside an `rlBegin` block. Each rig picks its backend on its first draw with visible triangles and a ready window. OpenGL 1.1 uses rlgl immediately. Other backends try to upload mesh buffers. If GPU setup or buffer synchronization fails, the rig releases its GPU resources, draws that frame through rlgl and continues on the CPU path. An early draw before `InitWindow` evaluates the pose and defers selection.

```java
body.setTexture(bodyTex);
body.setTexture("rightHand", handTex);
body.attach("rightHand", 0, sword);
body.draw(0, actorY, actorZ);
sword.draw();
```

`body.isUsingGpu()` is true once GPU buffers are in use. `body.setTexture(null)` restores the white default. `body.drawTextured(trailTex, x, y, z)` borrows a different shared texture for one draw. Each rig owns its buffers. Close each rig on the graphics thread before unloading borrowed textures or closing its window.

| Operation | Effect |
| --- | --- |
| `body.textureMode(MARig.TextureMode.ORDERED)` | Default. Merge adjacent runs of the same texture in joint order. |
| `body.textureMode(MARig.TextureMode.BATCHED)` | Group parts by texture. Use when reordering is acceptable. |
| `body.textureMode(MARig.TextureMode.SINGLE)` | Use the shared texture for every part. |
| `body.setColor(r, g, b, a)` or `body.setColor(part, r, g, b, a)` | Replace the vertex colors on the whole rig or one joint. |
| `body.setTint(r, g, b, a)` or `body.setTint(part, r, g, b, a)` | Multiply the rendered vertex colors. Global and joint tint combine on both backends. |
| `body.clearTint(part)` | Clear the joint multiplier. Set global tint to opaque white to clear the global multiplier. |

### Migrating existing callers

Keep the `MARig` or `MAHumanoidRig` that already holds your parts and poses. Replace `new MAGpuRig(body, tex)` with `body.setTexture(tex)`. Replace the old GPU/CPU branch and its surrounding triangle block with `body.draw(...)`. Map renderer-only `setColor` calls to `body.setTint(...)` and release resources with `body.close()`. Recompile callers against this JAR. Existing mesh and animation assets use the same formats.

For different textures on repeated draws of the same pose, use separate calls on that rig:

```java
body.draw(0, y, z);
body.drawTextured(trailTex, 0, y, trailZ);
```

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

### 1. Load parts and assign a texture

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
humanoid.setTexture(texture);
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
humanoid.draw();                 // Evaluates the current pose and draws it.
// humanoid.draw(3f, 0f, 0f);    // Optional second draw using the same current pose.
EndMode3D();
```

### 3. Clean up before closing the window

```java
humanoid.close();
UnloadTexture(texture);
CloseWindow();
```

### Immediate rlgl drawing

For low-level `MAMesh.draw()` and `MATri.draw()`, provide the rlgl triangle block yourself. The higher-level `MARig.draw()` manages rendering internally.

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

### Hold a sword at an animated child point

```java
// Equip at the first authored child point on the hand.
human.attach("righthand", 0, sword);

// Each frame inside BeginMode3D
human.draw();
sword.draw();

// When unequipping, even if the game will still call sword.draw().
human.attach("righthand", 0, null);
```

The index is zero-based within the named part. Use a child point authored on that mesh. Each update resolves the hand's pose first and uses the selected point's current position and quaternion to place the entire sword by its root pivot. The sword keeps its own joint animation and texture. Both `rightHand` and `righthand` select the humanoid's hand. The two-argument overload `human.attach("righthand", sword)` uses the hand's own pivot.

Both `sword.draw()` and `sword.draw(0, 0, 0)` inherit the parent's draw offset. A nonzero offset on the child supplies its own world-space draw offset. After `human.update()`, the first parent draw places its attachments. The parent can then draw extra copies for afterimages before the sword draws at the original hand. Draw offsets affect rendering only. Use `human.position(x, y, z).update()` when gameplay needs the sword's posed points in world coordinates.

Each indexed point holds one attached rig. Passing `null` removes and deactivates its occupant immediately, including its posed meshes and separate CPU or GPU draws. Attaching a replacement also deactivates the previous occupant. Attach the old sword again to reactivate it. If an attached point temporarily disappears when replacing the hand mesh, the sword resumes following it when that index returns. Clearing the point during that interval releases the sword.

Attaching resets the child's independent position and orientation. Use `sword.position(...)` or `sword.orientation(...)` after attaching to adjust the grip in the selected attachment frame. Attaching the same child to another part or rig transfers it. Removing the hand mesh hides the attached sword until the hand returns. Call `human.detach(sword)` to release the sword, then set its independent placement as needed.

### Attach meshes without a rig

```java
MAMesh parent = MAIO.loadMAMesh("assets/body.mamesh");
MAMesh child = MAIO.loadMAMesh("assets/weapon.mamesh");
MAMesh attached = parent.attachAsChild(child, 0); // Requires parent connector 0.
// parent.draw() now also draws the attached copy.
```


## Build the library

Use JDK 17 or later and your JavaCPP-based Jaylib JAR. Run these commands from the source directory:

```sh
mkdir -p build/classes
javac --release 17 -cp /path/to/jaylib.jar -d build/classes src/MechaAmazing/*.java
jar --create --file MechaAmazing.jar -C build/classes .
```

Use one MechaAmazing JAR on the classpath alongside your existing Jaylib dependency. `src/Main.java` contains the asset-dependent desktop demo. The library JAR contains the `MechaAmazing` package.
