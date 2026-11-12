package MechaAmazing;

import static com.raylib.Raylib.*;
import com.raylib.Raylib;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Single-mesh GLB 2.0 importer with embedded PNG/JPEG textures.
 * Requires exactly one mesh and one TRIANGLES primitive.
 * Imports raw mesh-local coordinates; scene node transforms are not applied.
 * The returned mesh has an origin pivot and no child connections.
 * Call after InitWindow when loading textures. The caller owns loaded textures.
 */
public final class MAIO {
    private MAIO() {}
    /** MARIG binary format, big-endian. Version 2:
     * int magic 0x4D415247, int version=2, int rigType, byte kind (1 pose,2 anim),
     * int jointCount, jointCount writeUTF names, int keyframeCount;
     * each key: double seconds, then jointCount records of seven floats
     * tx,ty,tz,qx,qy,qz,qw. A pose is one key at t=0. Times strictly increase.
     * Names bind animation to semantic joints; type 0 accepts matching names on
     * any rig type, positive types require matching MARig.type(). No app state,
     * geometry, colors, editor frame rate, or world placement is serialized.
     * Version 1 humanoid files are readable as type 1; their offsets retain the
     * original normalized units, so choose rig.motionUnit() when applying them.
     * File contents determine compatibility, not the filename extension.
     */
    public static void savePose(MAPose pose,String path) throws java.io.IOException {
        MAAnimation one=new MAAnimation(pose.type());one.put(0,pose);writeRigData(one,path,false);
    }
    public static void savePose(MARig rig,String path) throws java.io.IOException {savePose(MAPose.capture(rig),path);}
    public static void saveAnimation(MAAnimation animation,String path) throws java.io.IOException {writeRigData(animation,path,true);}
    public static MAPose loadPose(String path) throws java.io.IOException {return readRigData(path,false).sample(0);}
    public static void loadPose(String path,MARig target) throws java.io.IOException {loadPose(path).apply(target);}
    public static MAAnimation loadAnimation(String path) throws java.io.IOException {return readRigData(path,true);}

    private static void writeRigData(MAAnimation animation,String path,boolean isAnimation) throws java.io.IOException {
        int count=animation.frames().size();
        if(count<1||count>100000)throw new java.io.IOException("Invalid keyframe count");
        java.util.List<String> names=new java.util.ArrayList<>(animation.names());
        if(names.size()>4096)throw new java.io.IOException("Too many joints");
        long estimated=(long)count*(8L+28L*names.size())+32;
        for(String name:names)estimated+=2L+3L*name.length();
        if(estimated>64L*1024*1024)throw new java.io.IOException("Rig file exceeds 64 MiB limit");
        Path target=Path.of(path).toAbsolutePath(),temp=Files.createTempFile(target.getParent(),".marig-",".tmp");
        try {
            try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(temp)))){
                out.writeInt(0x4D415247);out.writeInt(2);out.writeInt(animation.type());out.writeByte(isAnimation?2:1);
                out.writeInt(names.size());for(String name:names)out.writeUTF(name);out.writeInt(count);
                for(var entry:animation.frames().entrySet()){
                    out.writeDouble(entry.getKey());for(String name:names)for(float value:entry.getValue().joint(name))out.writeFloat(value);
                }
            }
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static MAAnimation readRigData(String path,boolean isAnimation) throws java.io.IOException {
        long size=Files.size(Path.of(path));if(size>64L*1024*1024)throw new java.io.IOException("Rig file too large");
        try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(Path.of(path))))){
            if(in.readInt()!=0x4D415247)throw new java.io.IOException("Invalid MARIG magic");
            int version=in.readInt(),type;
            if(version==1){if(!in.readUTF().equals("humanoid"))throw new java.io.IOException("Unknown legacy rig type");type=1;}
            else if(version==2)type=in.readInt();else throw new java.io.IOException("Unsupported MARIG version");
            if(type<0||in.readUnsignedByte()!=(isAnimation?2:1))throw new java.io.IOException("Invalid rig type or file kind");
            int joints=in.readInt();if(joints<1||joints>4096||(version==1&&joints!=17))throw new java.io.IOException("Invalid joint count");
            java.util.List<String> names=new java.util.ArrayList<>();java.util.Set<String> unique=new java.util.HashSet<>();
            for(int i=0;i<joints;i++){String name=in.readUTF();if(name.isBlank()||!unique.add(name))throw new java.io.IOException("Invalid or duplicate joint name");names.add(name);}
            int count=in.readInt();if(count<1||count>100000||(!isAnimation&&count!=1)||(8L+28L*joints)*count>size)throw new java.io.IOException("Invalid keyframe count");
            MAAnimation result=new MAAnimation(type);double previous=-1;
            for(int frame=0;frame<count;frame++){
                double t=in.readDouble();if(!Double.isFinite(t)||t<0||t<=previous||(!isAnimation&&t!=0))throw new java.io.IOException("Invalid keyframe time");previous=t;
                java.util.Map<String,float[]> data=new java.util.LinkedHashMap<>();
                for(String name:names){float[] v=new float[7];for(int j=0;j<7;j++)v[j]=in.readFloat();data.put(name,v);}
                result.put(t,new MAPose(type,data));
            }
            if(in.read()!=-1)throw new java.io.IOException("Trailing MARIG data");return result;
        }catch(IllegalArgumentException e){throw new java.io.IOException("Invalid MARIG data",e);}
    }

    private static Texture texture;
    public static Texture texture() { return texture; }

    public static MAMesh load(String path) {
        texture = null;
        try {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            ByteBuffer file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (file.remaining() < 20 || file.getInt() != 0x46546c67 || file.getInt() != 2 || file.getInt() != bytes.length)
                throw new IllegalArgumentException("Not a valid GLB 2.0 file");
            int jsonLength = file.getInt(), jsonType = file.getInt();
            if (jsonType != 0x4e4f534a || jsonLength < 0 || jsonLength > file.remaining())
                throw new IllegalArgumentException("Missing GLB JSON chunk");
            byte[] jsonBytes = new byte[jsonLength]; file.get(jsonBytes);
            if (file.remaining() < 8) throw new IllegalArgumentException("Missing GLB BIN chunk");
            int binLength = file.getInt(), binType = file.getInt();
            if (binType != 0x004e4942 || binLength < 0 || binLength > file.remaining())
                throw new IllegalArgumentException("Invalid GLB BIN chunk");
            byte[] binBytes = new byte[binLength]; file.get(binBytes);
            ByteBuffer bin = ByteBuffer.wrap(binBytes).order(ByteOrder.LITTLE_ENDIAN);
            Map<String,Object> root = obj(new Json(new String(jsonBytes, StandardCharsets.UTF_8)).parse());
            List<Object> meshes = arr(root.get("meshes"));
            if (meshes.size() != 1) throw new IllegalArgumentException("Expected exactly one GLB mesh; found " + meshes.size());
            List<Object> primitives = arr(obj(meshes.get(0)).get("primitives"));
            if (primitives.size() != 1) throw new IllegalArgumentException("Only one mesh primitive is supported");
            Map<String,Object> primitive = obj(primitives.get(0));
            if (num(primitive.getOrDefault("mode", 4)).intValue() != 4)
                throw new IllegalArgumentException("Only TRIANGLES primitives are supported");
            List<Object> accessors = arr(root.get("accessors"));
            List<Object> views = arr(root.get("bufferViews"));
            Map<String,Object> attributes = obj(primitive.get("attributes"));
            float[][] positions = floats(bin, accessors, views, index(attributes, "POSITION"), 3);
            float[][] uvs = attributes.containsKey("TEXCOORD_0") ? floats(bin, accessors, views, index(attributes, "TEXCOORD_0"), 2) : null;
            float[][] colors = attributes.containsKey("COLOR_0") ? floats(bin, accessors, views, index(attributes, "COLOR_0"), -1) : null;
            int[] indices = primitive.containsKey("indices") ? indices(bin, accessors, views, num(primitive.get("indices")).intValue()) : new int[positions.length];
            if (!primitive.containsKey("indices")) for (int i=0;i<indices.length;i++) indices[i]=i;
            if (indices.length % 3 != 0) throw new IllegalArgumentException("Index count is not divisible by three");
            Vector<MATri> tris = new Vector<>();
            for (int i=0;i<indices.length;i+=3) {
                MAVert[] v = new MAVert[3];
                for (int j=0;j<3;j++) {
                    int k=indices[i+j];
                    if (k<0 || k>=positions.length) throw new IllegalArgumentException("Vertex index out of bounds");
                    v[j]=new MAVert().x(positions[k][0]).y(positions[k][1]).z(positions[k][2]);
                    if (uvs!=null) v[j].u(uvs[k][0]).v(uvs[k][1]);
                    if (colors!=null) {
                        v[j].r(channel(colors[k][0])).g(channel(colors[k][1])).b(channel(colors[k][2]));
                        if (colors[k].length==4) v[j].a(channel(colors[k][3]));
                    }
                }
                tris.add(new MATri(v[0],v[1],v[2]));
            }
            texture = null;
            if (primitive.containsKey("material")) {
                List<Object> materials = arr(root.get("materials"));
                Map<String,Object> mat = obj(materials.get(num(primitive.get("material")).intValue()));
                Map<String,Object> pbr = mat.containsKey("pbrMetallicRoughness") ? obj(mat.get("pbrMetallicRoughness")) : Map.of();
                if (pbr.containsKey("baseColorTexture")) {
                    int texIndex = num(obj(pbr.get("baseColorTexture")).get("index")).intValue();
                    Map<String,Object> tex = obj(arr(root.get("textures")).get(texIndex));
                    int imageIndex = num(tex.get("source")).intValue();
                    Map<String,Object> image = obj(arr(root.get("images")).get(imageIndex));
                    if (!image.containsKey("bufferView")) throw new IllegalArgumentException("Texture image must be embedded in GLB BIN");
                    Map<String,Object> view = obj(views.get(num(image.get("bufferView")).intValue()));
                    int offset = integer(view,"byteOffset",0), length = integer(view,"byteLength",-1);
                    if (offset<0 || length<0 || (long)offset+length>binBytes.length) throw new IllegalArgumentException("Invalid embedded image bounds");
                    String mime = (String)image.get("mimeType");
                    if (!"image/png".equals(mime) && !"image/jpeg".equals(mime))
                        throw new IllegalArgumentException("Only embedded PNG/JPEG textures are supported");
                    String suffix = "image/png".equals(mime) ? ".png" : ".jpg";
                    Path temp = Files.createTempFile("maglb-texture-", suffix);
                    try {
                        Files.write(temp, Arrays.copyOfRange(binBytes,offset,offset+length));
                        texture = LoadTexture(temp.toString());
                    } finally { Files.deleteIfExists(temp); }
                }
            }
            return new MAMesh(tris, new MAPivot().x(0f).y(0f).z(0f));
        } catch (Exception e) { throw new IllegalArgumentException("Could not load GLB: " + path + " (" + e.getMessage() + ")",e); }
    }
    /**
     * MAMESH MAP2 format, BIG-ENDIAN int32/float32:
     * int magic 0x4D415032, int version=1;
     * float root quaternion qx,qy,qz,qw; int childPointCount;
     * childPointCount records: float x,y,z,qx,qy,qz,qw;
     * int vertexCount (multiple of 3); vertexCount records: float x,y,z,u,v.
     * Total size: 32 + 28*childPointCount + 20*vertexCount bytes.
     * Root position is implicitly zero: save subtracts the root pivot position
     * from vertices and child points, without changing the source or rotating it.
     * Quaternion frames remain in the same axes as the saved geometry.
     * Every three vertices form a triangle. UVs retained; colors load white.
     * Textures, actual child meshes and mesh animation/scale metadata are omitted.
     * Loader also accepts legacy BU02: int childCount, childCount xyz triples,
     * int vertexCount, vertexCount xyzuv records (8+12*C+20*V bytes).
     * Legacy pivots receive identity orientations. Old loaders cannot read MAP2.
     */
    private static final int MAMESH_MAGIC=0x4D415032;
    public static void saveMAMesh(MAMesh mesh, String path) throws java.io.IOException {
        if (mesh == null || mesh.pivotPoint() == null)
            throw new IllegalArgumentException("Mesh needs a pivot point");
        MAPivot pivot=mesh.pivotPoint();
        meshFinite(pivot.x()); meshFinite(pivot.y()); meshFinite(pivot.z());
        int vertexCount=Math.multiplyExact(mesh.tris.size(),3);
        Path target=Path.of(path).toAbsolutePath();
        Path temp=Files.createTempFile(target.getParent(),".mamesh-",".tmp");
        try {
            try (java.io.DataOutputStream out=new java.io.DataOutputStream(
                    new java.io.BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAMESH_MAGIC); out.writeInt(1);
                writeMeshRotation(out,pivot);
                out.writeInt(mesh.childPoints.size());
                for (MAPivot point : mesh.childPoints) {
                    writeMeshPoint(out,point,pivot); writeMeshRotation(out,point);
                }
                out.writeInt(vertexCount);
                for (MATri tri : mesh.tris) {
                    if (tri == null || tri.verts == null || tri.verts.length != 3)
                        throw new IllegalArgumentException("Every triangle must have three vertices");
                    for (MAVert v : tri.verts) {
                        writeMeshPoint(out,v,pivot);
                        writeMeshFloat(out,v.u()); writeMeshFloat(out,v.v());
                    }
                }
            }
            try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    public static void saveMAMesh(String path, MAMesh mesh) throws java.io.IOException {
        saveMAMesh(mesh,path);
    }

    public static MAMesh loadMAMesh(String path) throws java.io.IOException {
        Path file=Path.of(path);
        long size=Files.size(file);
        if (size < 8) throw new java.io.IOException("Truncated MAMESH file");
        boolean framed;
        // Validate lengths before allocating from file-controlled counts.
        try (java.io.RandomAccessFile in=new java.io.RandomAccessFile(file.toFile(),"r")) {
            int first=in.readInt(); framed=first==MAMESH_MAGIC;
            int children;
            if(framed){
                if(size<32 || in.readInt()!=1) throw new java.io.IOException("Unsupported MAMESH version");
                in.seek(24); children=in.readInt();
            }else children=first;
            long countOffset=(framed?28L:4L)+(framed?28L:12L)*children;
            if(children<0 || countOffset>size-4) throw new java.io.IOException("Invalid MAMESH child count");
            in.seek(countOffset);
            int vertices=in.readInt();
            if(vertices<0 || vertices%3!=0 || countOffset+4+20L*vertices!=size)
                throw new java.io.IOException("Invalid MAMESH vertex count or file length");
        }
        try (java.io.DataInputStream in=new java.io.DataInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            MAPivot rootPivot=new MAPivot();
            int children=in.readInt();
            if(framed){ in.readInt(); readMeshRotation(in,rootPivot); children=in.readInt(); }
            Vector<MAPivot> points=new Vector<>();
            for(int i=0;i<children;i++){
                MAPivot point=new MAPivot().x(readMeshFloat(in)).y(readMeshFloat(in)).z(readMeshFloat(in));
                if(framed) readMeshRotation(in,point);
                points.add(point);
            }
            int vertices=in.readInt();
            Vector<MATri> tris=new Vector<>();
            for (int i=0;i<vertices;i+=3) {
                MAVert[] v=new MAVert[3];
                for (int j=0;j<3;j++) v[j]=readMeshPoint(in).u(readMeshFloat(in)).v(readMeshFloat(in));
                tris.add(new MATri(v[0],v[1],v[2]));
            }
            if (in.read()!=-1) throw new java.io.IOException("Trailing MAMESH data");
            MAMesh result=new MAMesh(tris,rootPivot);
            result.childPoints.addAll(points);
            return result;
        } catch (IllegalArgumentException e) { throw new java.io.IOException("Invalid MAMESH data",e); }
    }

    private static void meshFinite(float f) {
        if (!Float.isFinite(f)) throw new IllegalArgumentException("Non-finite MAMESH coordinate");
    }
    private static void writeMeshFloat(java.io.DataOutput out,float f) throws java.io.IOException {
        meshFinite(f); out.writeFloat(f);
    }
    private static void writeMeshPoint(java.io.DataOutput out,MAVert v,MAPivot pivot) throws java.io.IOException {
        if (v==null) throw new IllegalArgumentException("Null MAMESH point");
        writeMeshFloat(out,v.x()-pivot.x()); writeMeshFloat(out,v.y()-pivot.y()); writeMeshFloat(out,v.z()-pivot.z());
    }
    private static void writeMeshPoint(java.io.DataOutput out,MAPivot v,MAPivot pivot) throws java.io.IOException {
        if(v==null) throw new IllegalArgumentException("Null MAMESH pivot");
        writeMeshFloat(out,v.x()-pivot.x()); writeMeshFloat(out,v.y()-pivot.y()); writeMeshFloat(out,v.z()-pivot.z());
    }
    private static void writeMeshRotation(java.io.DataOutput out,MAPivot p) throws java.io.IOException {
        for(float q : MAMeshMath.orientation(p)) writeMeshFloat(out,q);
    }
    private static void readMeshRotation(java.io.DataInput in,MAPivot p) throws java.io.IOException {
        p.rotation(readMeshFloat(in),readMeshFloat(in),readMeshFloat(in),readMeshFloat(in));
    }
    private static float readMeshFloat(java.io.DataInput in) throws java.io.IOException {
        float f=in.readFloat(); meshFinite(f); return f;
    }
    private static MAVert readMeshPoint(java.io.DataInput in) throws java.io.IOException {
        return new MAVert().x(readMeshFloat(in)).y(readMeshFloat(in)).z(readMeshFloat(in));
    }

    private static int channel(float f) { return Math.max(0,Math.min(255,Math.round(f*255))); }
    private static int index(Map<String,Object> m,String key) { if (!m.containsKey(key)) throw new IllegalArgumentException("Missing attribute " + key); return num(m.get(key)).intValue(); }
    private static int integer(Map<String,Object> m,String k,int def) { return m.containsKey(k)?num(m.get(k)).intValue():def; }
    private static Number num(Object o) { return (Number)o; }
    @SuppressWarnings("unchecked") private static Map<String,Object> obj(Object o) { return (Map<String,Object>)o; }
    @SuppressWarnings("unchecked") private static List<Object> arr(Object o) { return (List<Object>)o; }
    private static class Access {
        int count,components,type,offset,stride,componentSize; boolean normalized;
        Access(List<Object> accessors,List<Object> views,int id,ByteBuffer bin) {
            Map<String,Object> a=obj(accessors.get(id));
            if (a.containsKey("sparse")) throw new IllegalArgumentException("Sparse accessors unsupported");
            Map<String,Object> v=obj(views.get(integer(a,"bufferView",-1)));
            if (integer(v,"buffer",0)!=0) throw new IllegalArgumentException("Only GLB BIN buffer 0 supported");
            count=integer(a,"count",0); type=integer(a,"componentType",0);
            String shape=(String)a.get("type");
            components=switch(shape) { case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4" -> 4; default -> throw new IllegalArgumentException("Unsupported accessor type "+shape); };
            componentSize=switch(type) { case 5120,5121 -> 1; case 5122,5123 -> 2; case 5125,5126 -> 4; default -> throw new IllegalArgumentException("Unsupported component type "+type); };
            offset=integer(v,"byteOffset",0)+integer(a,"byteOffset",0);
            stride=integer(v,"byteStride",components*componentSize);
            normalized=Boolean.TRUE.equals(a.get("normalized"));
            if (count<0 || offset<0 || stride<components*componentSize || (count>0 && (long)offset+(long)(count-1)*stride+components*componentSize>bin.limit()))
                throw new IllegalArgumentException("Accessor outside BIN bounds");
        }
        double get(ByteBuffer b,int row,int col) {
            int p=offset+row*stride+col*componentSize;
            return switch(type) {
                case 5120 -> normalized?Math.max(-1.0,b.get(p)/127.0):b.get(p);
                case 5121 -> normalized?(b.get(p)&255)/255.0:(b.get(p)&255);
                case 5122 -> normalized?Math.max(-1.0,b.getShort(p)/32767.0):b.getShort(p);
                case 5123 -> normalized?(b.getShort(p)&65535)/65535.0:(b.getShort(p)&65535);
                case 5125 -> (b.getInt(p)&0xffffffffL);
                case 5126 -> b.getFloat(p);
                default -> throw new IllegalArgumentException("Unsupported component type");
            };
        }
    }
    private static float[][] floats(ByteBuffer bin,List<Object> a,List<Object> v,int id,int expected) {
        Access x=new Access(a,v,id,bin);
        if (expected>0 && x.components!=expected) throw new IllegalArgumentException("Wrong accessor vector size");
        float[][] out=new float[x.count][x.components];
        for(int i=0;i<x.count;i++) for(int j=0;j<x.components;j++) out[i][j]=(float)x.get(bin,i,j);
        return out;
    }
    private static int[] indices(ByteBuffer bin,List<Object> a,List<Object> v,int id) {
        Access x=new Access(a,v,id,bin);
        if (x.components!=1 || (x.type!=5121 && x.type!=5123 && x.type!=5125)) throw new IllegalArgumentException("Unsupported index accessor");
        int[] out=new int[x.count]; for(int i=0;i<out.length;i++) out[i]=(int)x.get(bin,i,0); return out;
    }
    /** Small JSON reader, avoids external JSON dependencies. */
    private static final class Json {
        final String s; int p;
        Json(String s){this.s=s;}
        Object parse(){Object v=value(); ws(); if(p!=s.length()) throw new IllegalArgumentException("Trailing JSON"); return v;}
        void ws(){while(p<s.length() && Character.isWhitespace(s.charAt(p)))p++;}
        char next(){if(p>=s.length())throw new IllegalArgumentException("Unexpected end of JSON");return s.charAt(p++);}
        void expect(char c){ws();if(next()!=c)throw new IllegalArgumentException("Expected "+c);}
        Object value(){ws(); if(p>=s.length())throw new IllegalArgumentException("Unexpected end of JSON"); char c=s.charAt(p);
            if(c=='{'){p++;Map<String,Object> m=new HashMap<>();ws();if(p<s.length()&&s.charAt(p)=='}'){p++;return m;}do{String k=string();expect(':');m.put(k,value());ws();c=next();if(c=='}')return m;if(c!=',')throw new IllegalArgumentException("Invalid object");}while(true);}
            if(c=='['){p++;List<Object> a=new ArrayList<>();ws();if(p<s.length()&&s.charAt(p)==']'){p++;return a;}do{a.add(value());ws();c=next();if(c==']')return a;if(c!=',')throw new IllegalArgumentException("Invalid array");}while(true);}
            if(c=='"')return string();
            if(s.startsWith("true",p)){p+=4;return true;}if(s.startsWith("false",p)){p+=5;return false;}if(s.startsWith("null",p)){p+=4;return null;}
            int start=p;while(p<s.length()&&"-+0123456789.eE".indexOf(s.charAt(p))>=0)p++;
            if(start==p)throw new IllegalArgumentException("Invalid JSON value");return Double.parseDouble(s.substring(start,p));
        }
        String string(){ws();expect('"');StringBuilder b=new StringBuilder();while(true){char c=next();if(c=='"')return b.toString();if(c=='\\'){c=next();switch(c){case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'b'->b.append('\b');case 'f'->b.append('\f');case 'u'->{int n=Integer.parseInt(s.substring(p,p+4),16);p+=4;b.append((char)n);}default->b.append(c);}}else b.append(c);}}
    }
}
