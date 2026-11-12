package MechaAmazing;

import static com.raylib.Raylib.*;
import com.raylib.Raylib.*;
import org.bytedeco.javacpp.*;
import java.util.*;

/** Static rigid-part VBOs using raylib's standard DrawMesh path, including OpenGL 2.1.
 * Create/draw/close on the GL thread. Draw inside BeginMode3D, outside rlBegin/rlEnd.
 * Texture is borrowed; close this renderer before unloading that texture/window.
 * setParts/replace invalidates buffers automatically. Source vertex colors are uploaded.
 */
public final class MAGpuRig implements AutoCloseable {
 private final MARig rig;
 private final Texture texture;
 private Material material;
 private final List<Part> parts=new ArrayList<>();
 private long version=-1;
 private boolean closed;
 private static final class Part {
  final String name;final Mesh mesh;final Matrix matrix=new Matrix();
  Part(String name,Mesh mesh){this.name=name;this.mesh=mesh;}
 }
 public MAGpuRig(MARig rig,Texture texture){
  if(!IsWindowReady())throw new IllegalStateException("Create the renderer after InitWindow");
  this.rig=Objects.requireNonNull(rig);this.texture=Objects.requireNonNull(texture);
  material=LoadMaterialDefault();material.maps().texture(texture);
  try{rebuild();}catch(RuntimeException|Error e){close();throw e;}
 }
 private static FloatPointer floats(int count){
  Pointer p=MemAlloc(Math.multiplyExact(count,4));if(p==null||p.isNull())throw new OutOfMemoryError("Mesh buffer");
  return new FloatPointer(p);
 }
 private static Mesh upload(MAMesh source){
  int count=Math.multiplyExact(source.tris.size(),3);
  Mesh mesh=new Mesh();
  // Zero the native struct, including version-dependent optional fields.
  Pointer.memset(mesh,0,mesh.sizeof());
  mesh.vertexCount(count).triangleCount(count/3);
  try{
   mesh.vertices(floats(Math.multiplyExact(count,3)));
   mesh.texcoords(floats(Math.multiplyExact(count,2)));
   mesh.normals(floats(Math.multiplyExact(count,3)));
   Pointer colorMemory=MemAlloc(Math.multiplyExact(count,4));
   if(colorMemory==null||colorMemory.isNull())throw new OutOfMemoryError("Mesh colors");
   mesh.colors(new BytePointer(colorMemory));
   FloatPointer xyz=mesh.vertices(),uv=mesh.texcoords(),normals=mesh.normals();BytePointer colors=mesh.colors();
   int i=0;
   for(MATri tri:source.tris){
    MAVert a=tri.verts[0],b=tri.verts[1],c=tri.verts[2];
    float ax=b.x-a.x,ay=b.y-a.y,az=b.z-a.z,bx=c.x-a.x,by=c.y-a.y,bz=c.z-a.z;
    float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
    double len=Math.sqrt((double)nx*nx+(double)ny*ny+(double)nz*nz);if(len>0){nx/=len;ny/=len;nz/=len;}
    for(MAVert v:tri.verts){
     xyz.put(i*3L,v.x).put(i*3L+1,v.y).put(i*3L+2,v.z);
     uv.put(i*2L,v.u).put(i*2L+1,v.v);
     normals.put(i*3L,nx).put(i*3L+1,ny).put(i*3L+2,nz);
     colors.put(i*4L,v.r).put(i*4L+1,v.g).put(i*4L+2,v.b).put(i*4L+3,v.a);i++;
    }
   }
   UploadMesh(mesh,false);
   // A VAO is optional on OpenGL 2.1; only require the vertex buffer itself.
   if(mesh.vboId()==null||mesh.vboId().isNull()||mesh.vboId().get(0)==0)throw new IllegalStateException("Vertex buffer upload failed");
   return mesh;
  }catch(RuntimeException|Error e){UnloadMesh(mesh);mesh.close();throw e;}
 }
 private void releaseParts(){for(Part p:parts){UnloadMesh(p.mesh);p.mesh.close();p.matrix.close();}parts.clear();}
 private void rebuild(){
  releaseParts();
  for(String name:rig.names()){
   MAMesh source=rig.restMesh(name);if(source!=null&&!source.tris.isEmpty())parts.add(new Part(name,upload(source)));
  }
  version=rig.geometryVersion();
 }
 /** For immutable per-part geometry. Pose transforms are updated on every draw. */
 public void draw(){draw(0f,0f,0f);}
 /** Draw this rig with an additional world-space offset added to its position().
  * Does not change the rig's stored position, pose, or geometry, or re-upload VBOs.
  * Repeated draws reuse the current pose until it changes.
  * Call inside BeginMode3D, outside rlBegin/rlEnd, just like draw().
  */
 public void draw(float x,float y,float z){
  if(closed)throw new IllegalStateException("Renderer closed");
  if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Invalid draw offset");
  rig.update();if(version!=rig.geometryVersion())rebuild();
  for(int i=0;i<parts.size();i++){
   Part p=parts.get(i);if(rig.mesh(p.name)==null)continue;
   float[] t=rig.partTransform(p.name);Matrix m=p.matrix;
   m.m0(t[0]).m4(t[1]).m8(t[2]).m12(t[3]+x);
   m.m1(t[4]).m5(t[5]).m9(t[6]).m13(t[7]+y);
   m.m2(t[8]).m6(t[9]).m10(t[10]).m14(t[11]+z);
   m.m3(0).m7(0).m11(0).m15(1);
   DrawMesh(p.mesh,material,m);
  }
 }
 @Override public void close(){
  if(closed)return;closed=true;releaseParts();
  if(material!=null){material.maps().texture().id(0);UnloadMaterial(material);material.close();material=null;}
 }
}
