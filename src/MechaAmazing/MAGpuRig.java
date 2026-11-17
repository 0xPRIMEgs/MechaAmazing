package MechaAmazing;

import static com.raylib.Raylib.*;
import com.raylib.Raylib.*;
import org.bytedeco.javacpp.*;
import java.util.*;

/** Cached dynamic meshes using OpenGL 2.1-compatible DrawMesh.
 * Create/draw/close on the GL thread; draw inside BeginMode3D, outside rlBegin/rlEnd.
 * All textures are borrowed. Close renderers before unloading textures or the window.
 */
public final class MAGpuRig implements AutoCloseable {
 public enum TextureMode {
  /** Honor part textures; merge adjacent equal texture sources without changing draw order. */
  ORDERED,
  /** One fallback batch plus one per explicit texture. Only for safe reordering (e.g. opaque parts). */
  BATCHED,
  /** Ignore all part textures and use the fallback in one batch. */
  SINGLE
 }
 private final MARig rig;
 private Texture fallback;
 private Texture defaultTexture;
 private Material material;
 private final Matrix matrix=new Matrix();
 private Batch[] batches=new Batch[0];
 private TextureMode textureMode;
 private boolean batchesDirty=true,closed;
 private Map<String,int[]> partColors;
 private long partColorVersion,uploadedPartColorVersion=-1;
 private long geometryVersion=-1,poseVersion=-1,colorVersion=-1,textureVersion=-1;

 /** Use raylib's white texture until setTexture is called; part textures still apply. */
 public MAGpuRig(MARig rig){this(rig,null,TextureMode.ORDERED);}
 public MAGpuRig(MARig rig,Texture texture){this(rig,texture,TextureMode.ORDERED);}
 public MAGpuRig(MARig rig,Texture texture,TextureMode mode){
  if(!IsWindowReady())throw new IllegalStateException("Create the renderer after InitWindow");
  this.rig=Objects.requireNonNull(rig,"rig");
  textureMode=Objects.requireNonNull(mode,"mode");
  matrix.m0(1).m4(0).m8(0).m12(0).m1(0).m5(1).m9(0).m13(0)
        .m2(0).m6(0).m10(1).m14(0).m3(0).m7(0).m11(0).m15(1);
  try{
   material=LoadMaterialDefault();
   // Copy the descriptor: the material's texture slot is replaced while drawing.
   Texture white=material.maps().texture();
   defaultTexture=new Texture().id(white.id()).width(white.width()).height(white.height())
     .mipmaps(white.mipmaps()).format(white.format());
   fallback=texture==null?defaultTexture:texture;
   rig.update();rebuild();
  }
  catch(RuntimeException|Error e){close();throw e;}
 }
 private static Pointer allocate(int bytes){
  Pointer p=MemAlloc(bytes);if(p==null||p.isNull())throw new OutOfMemoryError("Mesh buffer");return p;
 }
 private void checkOpen(){if(closed)throw new IllegalStateException("Renderer closed");}

 private final class Batch implements AutoCloseable {
  // Null means the renderer's live fallback, not an explicit part texture.
  final Texture texture;
  final List<String> names=new ArrayList<>();
  final List<MAMesh> parts=new ArrayList<>();
  Mesh mesh;
  FloatPointer positions;
  BytePointer colors;
  MAVert[] vertices;
  float[] xyz;
  byte[] rgba;
  Batch(Texture texture){this.texture=texture;}
  Texture effectiveTexture(){return texture==null?fallback:texture;}
  void upload(){
   List<MAVert> visible=new ArrayList<>();
   for(MAMesh part:parts)for(MATri t:part.tris)Collections.addAll(visible,t.verts);
   vertices=visible.toArray(new MAVert[0]);int count=vertices.length;
   xyz=new float[Math.multiplyExact(count,3)];rgba=new byte[Math.multiplyExact(count,4)];
   mesh=new Mesh();Pointer.memset(mesh,0,mesh.sizeof());
   mesh.vertexCount(count).triangleCount(count/3);
   positions=new FloatPointer(allocate(Math.multiplyExact(xyz.length,4)));mesh.vertices(positions);
   FloatPointer uv=new FloatPointer(allocate(Math.multiplyExact(count,8)));mesh.texcoords(uv);
   colors=new BytePointer(allocate(rgba.length));mesh.colors(colors);
   float[] texcoords=new float[Math.multiplyExact(count,2)];
   for(int i=0;i<count;i++){texcoords[i*2]=vertices[i].u;texcoords[i*2+1]=vertices[i].v;}
   uv.put(texcoords);copyPositions();copyColors();
   // The unlit material does not read normals, so no unused normal VBO.
   UploadMesh(mesh,true);
   if(mesh.vboId()==null||mesh.vboId().isNull()||mesh.vboId().get(0)==0)
    throw new IllegalStateException("Vertex buffer upload failed");
  }
  void copyPositions(){
   for(int i=0;i<vertices.length;i++){MAVert v=vertices[i];int n=i*3;xyz[n]=v.x;xyz[n+1]=v.y;xyz[n+2]=v.z;}
   positions.put(xyz);
  }
  void copyColors(){
   if(partColors==null){
    for(int i=0;i<vertices.length;i++){MAVert v=vertices[i];int n=i*4;rgba[n]=v.r;rgba[n+1]=v.g;rgba[n+2]=v.b;rgba[n+3]=v.a;}
   }else{
    int n=0;
    for(int p=0;p<parts.size();p++){
     int[] tint=partColors.get(names.get(p));
     for(MATri tri:parts.get(p).tris)for(MAVert v:tri.verts){
      rgba[n++]=tint==null?v.r:(byte)((v.r&255)*tint[0]/255);
      rgba[n++]=tint==null?v.g:(byte)((v.g&255)*tint[1]/255);
      rgba[n++]=tint==null?v.b:(byte)((v.b&255)*tint[2]/255);
      rgba[n++]=tint==null?v.a:(byte)((v.a&255)*tint[3]/255);
     }
    }
   }
   colors.put(rgba);
  }
  @Override public void close(){
   if(mesh!=null){UnloadMesh(mesh);mesh.close();mesh=null;}
   positions=null;colors=null;
  }
 }
 private void rebuild(){
  List<Batch> next=new ArrayList<>();
  Map<Integer,Batch> byTexture=textureMode==TextureMode.BATCHED?new LinkedHashMap<>():null;
  for(String name:rig.names()){
   MAMesh current=rig.mesh(name);
   if(current==null||current.tris.isEmpty())continue;
   Texture texture=textureMode==TextureMode.SINGLE?null:current.texture();
   // Keep fallback users separate from explicit textures, even for equal IDs:
   // a later setTexture must not change an explicitly textured part.
   Integer textureId=texture==null?null:texture.id();
   Batch batch;
   if(byTexture!=null){
    batch=byTexture.get(textureId);
    if(batch==null){batch=new Batch(texture);byTexture.put(textureId,batch);next.add(batch);}
   }else{
    batch=next.isEmpty()?null:next.get(next.size()-1);
    if(batch==null||!Objects.equals(batch.texture==null?null:batch.texture.id(),textureId)){
     batch=new Batch(texture);next.add(batch);
    }
   }
   batch.names.add(name);batch.parts.add(current);
  }
  Batch[] built=next.toArray(new Batch[0]);
  try{for(Batch b:built)b.upload();}
  catch(RuntimeException|Error e){for(Batch b:built)b.close();throw e;}
  for(Batch b:batches)b.close();
  batches=built;
  if(batches.length==1)material.maps().texture(batches[0].effectiveTexture());
  geometryVersion=rig.geometryVersion();poseVersion=rig.poseVersion();colorVersion=rig.colorVersion();
  textureVersion=rig.textureVersion();uploadedPartColorVersion=partColorVersion;batchesDirty=false;
 }
 /** Changing mode rebuilds batches on the next draw, never on ordinary pose changes. */
 public MAGpuRig textureMode(TextureMode mode){
  checkOpen();Objects.requireNonNull(mode,"mode");
  if(textureMode!=mode){textureMode=mode;batchesDirty=true;}return this;
 }
 public TextureMode textureMode(){return textureMode;}
 /** Set the borrowed shared texture without rebuilding or uploading mesh buffers.
  * Null restores raylib's white texture. Explicit part textures still apply in
  * ORDERED/BATCHED mode; SINGLE uses this texture for the whole rig.
  */
 public MAGpuRig setTexture(Texture t){
  checkOpen();fallback=t==null?defaultTexture:t;
  if(batches.length==1)material.maps().texture(batches[0].effectiveTexture());
  return this;
 }
 /** Renderer-only global RGBA multiplier; white restores the untinted appearance. */
 public MAGpuRig setColor(int r,int g,int b,int a){
  checkOpen();material.maps().color().r((byte)r).g((byte)g).b((byte)b).a((byte)a);return this;
 }
 /** Renderer-only part tint, multiplied by vertex colors and the global renderer tint. */
 public MAGpuRig setColor(String name,int r,int g,int b,int a){
  checkOpen();rig.joint(name);
  if(partColors==null)partColors=new HashMap<>();
  int[] next={r&255,g&255,b&255,a&255};
  if(!Arrays.equals(partColors.get(name),next)){partColors.put(name,next);partColorVersion++;}
  return this;
 }
 public MAGpuRig clearColor(String name){
  checkOpen();rig.joint(name);
  if(partColors!=null&&partColors.remove(name)!=null){
   partColorVersion++;if(partColors.isEmpty())partColors=null;
  }
  return this;
 }
 public void draw(){draw(0f,0f,0f);}
 /** Extra world offset; repeated draws reuse the same posed VBOs until the rig changes. */
 public void draw(float x,float y,float z){
  checkOpen();
  if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Invalid draw offset");
  rig.update();
  if(batchesDirty||geometryVersion!=rig.geometryVersion()||
     (textureMode!=TextureMode.SINGLE&&textureVersion!=rig.textureVersion()))rebuild();
  boolean positionsChanged=poseVersion!=rig.poseVersion();
  boolean colorsChanged=colorVersion!=rig.colorVersion()||uploadedPartColorVersion!=partColorVersion;
  matrix.m12(x).m13(y).m14(z);
  for(Batch b:batches){
   if(positionsChanged){b.copyPositions();UpdateMeshBuffer(b.mesh,0,b.positions,Math.multiplyExact(b.xyz.length,4),0);}
   if(colorsChanged){b.copyColors();UpdateMeshBuffer(b.mesh,3,b.colors,b.rgba.length,0);}
   if(batches.length>1)material.maps().texture(b.effectiveTexture());
   DrawMesh(b.mesh,material,matrix);
  }
  poseVersion=rig.poseVersion();colorVersion=rig.colorVersion();uploadedPartColorVersion=partColorVersion;
 }
 @Override public void close(){
  if(closed)return;closed=true;
  for(Batch b:batches)b.close();batches=new Batch[0];matrix.close();partColors=null;
  if(material!=null){material.maps().texture().id(0);UnloadMaterial(material);material.close();material=null;}
  if(defaultTexture!=null){defaultTexture.close();defaultTexture=null;}fallback=null;
 }
}
