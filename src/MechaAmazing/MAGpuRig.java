package MechaAmazing;

import static com.raylib.Raylib.*;
import com.raylib.Raylib.*;
import org.bytedeco.javacpp.*;
import java.util.*;

/** One dynamic mesh for all visible rigid parts, using raylib's OpenGL 2.1-compatible DrawMesh.
 * Create/draw/close on the GL thread, inside BeginMode3D and outside rlBegin/rlEnd.
 * Texture is borrowed; close this renderer before unloading that texture/window.
 */
public final class MAGpuRig implements AutoCloseable {
 private final MARig rig;
 private Material material;
 private Mesh mesh;
 private final Matrix matrix=new Matrix();
 private FloatPointer positions;
 private BytePointer colors;
 private MAVert[] vertices=new MAVert[0];
 private float[] xyz=new float[0];
 private byte[] rgba=new byte[0];
 private long geometryVersion=-1,poseVersion=-1,colorVersion=-1;
 private boolean closed;

 public MAGpuRig(MARig rig,Texture texture){
  if(!IsWindowReady())throw new IllegalStateException("Create the renderer after InitWindow");
  this.rig=Objects.requireNonNull(rig);Objects.requireNonNull(texture);
  matrix.m0(1).m4(0).m8(0).m12(0).m1(0).m5(1).m9(0).m13(0)
        .m2(0).m6(0).m10(1).m14(0).m3(0).m7(0).m11(0).m15(1);
  material=LoadMaterialDefault();material.maps().texture(texture);
  try{rig.update();rebuild();}catch(RuntimeException|Error e){close();throw e;}
 }
 private static Pointer allocate(int bytes){
  Pointer p=MemAlloc(bytes);if(p==null||p.isNull())throw new OutOfMemoryError("Mesh buffer");return p;
 }
 private void releaseMesh(){
  if(mesh!=null){UnloadMesh(mesh);mesh.close();mesh=null;}
  positions=null;colors=null;
 }
 private void rebuild(){
  releaseMesh();
  List<MAVert> visible=new ArrayList<>();
  // Preserve the original renderer's joint/triangle order, including transparent geometry.
  for(String name:rig.names()){
   MAMesh current=rig.mesh(name);
   if(current!=null)for(MATri t:current.tris)Collections.addAll(visible,t.verts);
  }
  vertices=visible.toArray(new MAVert[0]);
  int count=vertices.length;
  xyz=new float[Math.multiplyExact(count,3)];rgba=new byte[Math.multiplyExact(count,4)];
  if(count>0){
   mesh=new Mesh();Pointer.memset(mesh,0,mesh.sizeof());
   try{
    mesh.vertexCount(count).triangleCount(count/3);
    positions=new FloatPointer(allocate(Math.multiplyExact(xyz.length,4)));mesh.vertices(positions);
    FloatPointer uv=new FloatPointer(allocate(Math.multiplyExact(count,8)));mesh.texcoords(uv);
    colors=new BytePointer(allocate(rgba.length));mesh.colors(colors);
    float[] texcoords=new float[Math.multiplyExact(count,2)];
    for(int i=0;i<count;i++){texcoords[i*2]=vertices[i].u;texcoords[i*2+1]=vertices[i].v;}
    uv.put(texcoords);copyPositions();copyColors();
    // The default unlit raylib material does not read normals: no unused normal VBO.
    UploadMesh(mesh,true);
    if(mesh.vboId()==null||mesh.vboId().isNull()||mesh.vboId().get(0)==0)
     throw new IllegalStateException("Vertex buffer upload failed");
   }catch(RuntimeException|Error e){releaseMesh();throw e;}
  }
  geometryVersion=rig.geometryVersion();poseVersion=rig.poseVersion();colorVersion=rig.colorVersion();
 }
 private void copyPositions(){
  for(int i=0;i<vertices.length;i++){MAVert v=vertices[i];int n=i*3;xyz[n]=v.x;xyz[n+1]=v.y;xyz[n+2]=v.z;}
  positions.put(xyz);
 }
 private void copyColors(){
  for(int i=0;i<vertices.length;i++){MAVert v=vertices[i];int n=i*4;rgba[n]=v.r;rgba[n+1]=v.g;rgba[n+2]=v.b;rgba[n+3]=v.a;}
  colors.put(rgba);
 }
 /** Renderer-only RGBA tint (multiplied by vertex/texture colors); white restores the original look. */
 public MAGpuRig setColor(int r,int g,int b,int a){
  if(closed)throw new IllegalStateException("Renderer closed");
  material.maps().color().r((byte)r).g((byte)g).b((byte)b).a((byte)a);return this;
 }
 public void draw(){draw(0f,0f,0f);}
 /** Extra world offset; repeated draws reuse the same posed VBO until the rig changes. */
 public void draw(float x,float y,float z){
  if(closed)throw new IllegalStateException("Renderer closed");
  if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Invalid draw offset");
  rig.update();
  if(geometryVersion!=rig.geometryVersion())rebuild();
  if(mesh==null)return;
  if(poseVersion!=rig.poseVersion()){
   copyPositions();UpdateMeshBuffer(mesh,0,positions,Math.multiplyExact(xyz.length,4),0);poseVersion=rig.poseVersion();
  }
  if(colorVersion!=rig.colorVersion()){
   copyColors();UpdateMeshBuffer(mesh,3,colors,rgba.length,0);colorVersion=rig.colorVersion();
  }
  matrix.m12(x).m13(y).m14(z);
  DrawMesh(mesh,material,matrix);
 }
 @Override public void close(){
  if(closed)return;closed=true;releaseMesh();matrix.close();
  vertices=new MAVert[0];xyz=new float[0];rgba=new byte[0];
  if(material!=null){material.maps().texture().id(0);UnloadMaterial(material);material.close();material=null;}
 }
}
