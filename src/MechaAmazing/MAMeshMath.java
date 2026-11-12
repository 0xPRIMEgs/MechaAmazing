package MechaAmazing;

import java.util.function.Consumer;

/** Copy-and-return mesh transforms. Angles are radians, coordinates model-space.
 * Quaternion fields are authoritative. Euler fields are derived XYZ readouts.
 * Geometry, pivotPoint, and childPoints are baked model-space coordinates.
 * Descendants are transformed with their parent. x/y/z are model-space position
 * metadata here, NOT local offsets. Do not apply them again when drawing.
 * Quaternions track orientation; nonuniform model-axis scaling can introduce
 * shear, so scale fields are bookkeeping and cannot reconstruct all geometry.
 * Requires an acyclic mesh tree with non-null pivots. Inputs remain unchanged.
 * Direct Euler setters do not rotate geometry or update the quaternion.
 */
public class MAMeshMath {
    public static MAMesh rotateX(MAMesh mesh, float angle){
        MAMesh b = mesh;
        return rotated(mesh, delta(b, angle, 1, 0, 0, false), b.pivotPoint.x(), b.pivotPoint.y(), b.pivotPoint.z());
    }

    public static MAMesh rotateY(MAMesh mesh, float angle){
        MAMesh b = mesh;
        return rotated(mesh, delta(b, angle, 0, 1, 0, false), b.pivotPoint.x(), b.pivotPoint.y(), b.pivotPoint.z());
    }

    public static MAMesh rotateZ(MAMesh mesh, float angle){
        MAMesh b = mesh;
        return rotated(mesh, delta(b, angle, 0, 0, 1, false), b.pivotPoint.x(), b.pivotPoint.y(), b.pivotPoint.z());
    }

    // Supplied model-space pivot; also rotates this mesh's pivot position.
    public static MAMesh rotateX(MAMesh mesh, float angle, float px, float py, float pz){
        return rotated(mesh, delta(mesh, angle, 1, 0, 0, false), px, py, pz);
    }

    // Supplied model-space pivot; also rotates this mesh's pivot position.
    public static MAMesh rotateY(MAMesh mesh, float angle, float px, float py, float pz){
        return rotated(mesh, delta(mesh, angle, 0, 1, 0, false), px, py, pz);
    }

    // Supplied model-space pivot; also rotates this mesh's pivot position.
    public static MAMesh rotateZ(MAMesh mesh, float angle, float px, float py, float pz){
        return rotated(mesh, delta(mesh, angle, 0, 0, 1, false), px, py, pz);
    }

    public static MAMesh rotateLocalX(MAMesh mesh, float angle){
        MAMesh b = mesh;
        return rotated(mesh, delta(b, angle, 1, 0, 0, true), b.pivotPoint.x(), b.pivotPoint.y(), b.pivotPoint.z());
    }

    public static MAMesh rotateLocalY(MAMesh mesh, float angle){
        MAMesh b = mesh;
        return rotated(mesh, delta(b, angle, 0, 1, 0, true), b.pivotPoint.x(), b.pivotPoint.y(), b.pivotPoint.z());
    }

    public static MAMesh rotateLocalZ(MAMesh mesh, float angle){
        MAMesh b = mesh;
        return rotated(mesh, delta(b, angle, 0, 0, 1, true), b.pivotPoint.x(), b.pivotPoint.y(), b.pivotPoint.z());
    }

    public static MAMesh rotateXYZ(MAMesh mesh, float x, float y, float z){
        return rotateZ(rotateY(rotateX(mesh, x), y), z);
    }

    public static MAMesh translate(MAMesh mesh, float x, float y, float z){
        MAMesh mm = copy(mesh);
        moveVertices(mm, x, y, z);
        forEachMesh(mm, b -> { b.x += x; b.y += y; b.z += z; });
        return mm;
    }

    // Component-wise model-axis scaling about the mesh pivot.
    public static MAMesh scale(MAMesh mesh, float sx, float sy, float sz){
        MAMesh mm = copy(mesh);
        MAMesh b = mm;
        scaleVertices(mm, sx, sy, sz, b.pivotPoint.x(), b.pivotPoint.y(), b.pivotPoint.z());
        final float px=b.pivotPoint.x(), py=b.pivotPoint.y(), pz=b.pivotPoint.z();
        forEachMesh(mm, m -> {
            m.x=px+(m.x-px)*sx; m.y=py+(m.y-py)*sy; m.z=pz+(m.z-pz)*sz;
            m.scaleX*=sx; m.scaleY*=sy; m.scaleZ*=sz;
        });
        return mm;
    }

    /** Mirror across the model-space YZ plane through this mesh's pivot.
     * Returns a deep copy, including descendants; the input is unchanged. */
    public static MAMesh mirrorX(MAMesh mesh){
        return mirrorX(mesh, mesh.pivotPoint().x());
    }
    /** Mirror across the model-space XZ plane through this mesh's pivot. */
    public static MAMesh mirrorY(MAMesh mesh){
        return mirrorY(mesh, mesh.pivotPoint().y());
    }
    /** Mirror across the model-space XY plane through this mesh's pivot. */
    public static MAMesh mirrorZ(MAMesh mesh){
        return mirrorZ(mesh, mesh.pivotPoint().z());
    }
    /** Explicit model-space mirror plane x=planeX (use 0 for the origin plane). */
    public static MAMesh mirrorX(MAMesh mesh, float planeX){return mirrored(mesh,0,planeX);}
    /** Explicit model-space mirror plane y=planeY. */
    public static MAMesh mirrorY(MAMesh mesh, float planeY){return mirrored(mesh,1,planeY);}
    /** Explicit model-space mirror plane z=planeZ. */
    public static MAMesh mirrorZ(MAMesh mesh, float planeZ){return mirrored(mesh,2,planeZ);}

    /* A reflection F has determinant -1, so it cannot itself be a quaternion.
     * Frame convention: R' = F * R * H, H = local-X reflection. This reflects
     * forward (+Z) AND up (+Y), then reconstructs right to keep a proper frame.
     * This convention is the same for all three mirror axes and for every pivot.
     * Geometry is reflected once; swapping triangle vertices 1/2 restores winding.
     * UV/RGBA travel with their complete vertices. No UV values are flipped.
     * Pose metadata uses the same R' with local scaleX negated (H * scale).
     * Existing shear remains baked geometry, as with the other transforms.
     */
    private static MAMesh mirrored(MAMesh mesh, int axis, float plane){
        if(!Float.isFinite(plane)) throw new IllegalArgumentException("Invalid mirror plane.");
        MAMesh result=copy(mesh);
        float sx=axis==0?-1:1, sy=axis==1?-1:1, sz=axis==2?-1:1;
        float px=axis==0?plane:0, py=axis==1?plane:0, pz=axis==2?plane:0;
        scaleVertices(result,sx,sy,sz,px,py,pz);
        forEachPivot(result,p -> {
            float[] q=mirroredOrientation(orientation(p),axis);
            p.rotation(q[0],q[1],q[2],q[3]);
        });
        forEachMesh(result,m -> {
            m.x=px+(m.x-px)*sx; m.y=py+(m.y-py)*sy; m.z=pz+(m.z-pz)*sz;
            float[] q=mirroredOrientation(orientation(m),axis);
            m.qx=q[0];m.qy=q[1];m.qz=q[2];m.qw=q[3];
            // Refresh Euler readouts without changing the reflected position.
            rotatePose(m,new float[]{0,0,0,1},m.x,m.y,m.z);
            m.scaleX=-m.scaleX;
            for(MATri t:m.tris){MAVert v=t.verts[1];t.verts[1]=t.verts[2];t.verts[2]=v;}
        });
        return result;
    }
    private static float[] mirroredOrientation(float[] q,int axis){
        // F*R*F: quaternion vector part transforms as det(F)*F.
        float[] conjugated={axis==0?q[0]:-q[0],axis==1?q[1]:-q[1],axis==2?q[2]:-q[2],q[3]};
        // R'=(F*R*F)*(F*H). F*H is identity, Rz(pi), or Ry(pi).
        return axis==0?normalized(conjugated):multiply(conjugated,
            axis==1?new float[]{0,0,1,0}:new float[]{0,1,0,0});
    }

    private static MAMesh rotated(MAMesh mesh, float[] q, float px, float py, float pz){
        MAMesh mm = copy(mesh);
        rotateVertices(mm, q, px, py, pz);
        forEachMesh(mm, m -> rotatePose(m, q, px, py, pz));
        return mm;
    }

    public static float[] orientation(MAMesh b){
        return normalized(new float[]{b.qx(), b.qy(), b.qz(), b.qw()});
    }

    public static float[] normalized(float[] q){
        double n = Math.sqrt((double)q[0]*q[0]+(double)q[1]*q[1]+(double)q[2]*q[2]+(double)q[3]*q[3]);
        if(!Double.isFinite(n) || n == 0) throw new IllegalArgumentException("Invalid quaternion.");
        return new float[]{(float)(q[0]/n),(float)(q[1]/n),(float)(q[2]/n),(float)(q[3]/n)};
    }

    public static float[] multiply(float[] a, float[] b){
        return normalized(new float[]{
            a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],
            a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],
            a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],
            a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]});
    }

    public static float[] vector(float[] q, float x, float y, float z){
        float tx=2*(q[1]*z-q[2]*y), ty=2*(q[2]*x-q[0]*z), tz=2*(q[0]*y-q[1]*x);
        return new float[]{x+q[3]*tx+q[1]*tz-q[2]*ty,
            y+q[3]*ty+q[2]*tx-q[0]*tz, z+q[3]*tz+q[0]*ty-q[1]*tx};
    }

    static float[] delta(MAMesh b, float angle, float ax, float ay, float az, boolean local){
        if(!Float.isFinite(angle)) throw new IllegalArgumentException("Invalid angle.");
        if(local){
            float[] axis = vector(orientation(b), ax, ay, az);
            ax=axis[0]; ay=axis[1]; az=axis[2];
        }
        float s=(float)Math.sin(angle*0.5f);
        return normalized(new float[]{ax*s,ay*s,az*s,(float)Math.cos(angle*0.5f)});
    }

    static void rotatePose(MAMesh b, float[] delta, float px, float py, float pz){
        float[] p = vector(delta, b.x()-px, b.y()-py, b.z()-pz);
        b.x=px+p[0]; b.y=py+p[1]; b.z=pz+p[2];
        float[] q = multiply(delta, orientation(b));
        b.qx=q[0]; b.qy=q[1]; b.qz=q[2]; b.qw=q[3];
        // Euler decomposition Rz(roll) * Ry(yaw) * Rx(pitch).
        float x=q[0], y=q[1], z=q[2], w=q[3];
        double sy=Math.max(-1.0,Math.min(1.0,2.0*(w*y-z*x)));
        if(Math.abs(sy)>0.999999){
            b.pitch=0; b.yaw=(float)Math.copySign(Math.PI/2,sy);
            b.roll=(float)Math.atan2(2*(w*z-x*y),1-2*(x*x+z*z));
        }else{
            b.pitch=(float)Math.atan2(2*(w*x+y*z),1-2*(x*x+y*y));
            b.yaw=(float)Math.asin(sy);
            b.roll=(float)Math.atan2(2*(w*z+x*y),1-2*(y*y+z*z));
        }
    }

    // All points use the same model-space coordinates. Child meshes must already
    // be placed at their attachment points; childIndex is a link, not a transform.
    private static void forEachVertex(MAMesh mesh, Consumer<MAVert> operation){
        java.util.Set<MAVert> seen = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<MAVert, Boolean>());
        forEachMesh(mesh, m -> {
            for(MATri tri : m.tris){
                for(MAVert v : tri.verts) if(seen.add(v)) operation.accept(v);
            }

        });
    }

    private static void forEachPivot(MAMesh mesh, Consumer<MAPivot> operation){
        java.util.Set<MAPivot> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        forEachMesh(mesh, m -> {
            if(seen.add(m.pivotPoint)) operation.accept(m.pivotPoint);
            for(MAPivot p : m.childPoints) {
                if(p==null) throw new IllegalArgumentException("Null child pivot.");
                if(seen.add(p)) operation.accept(p);
            }
        });
    }

    public static float[] orientation(MAPivot p){
        return normalized(new float[]{p.qx(),p.qy(),p.qz(),p.qw()});
    }

    /** Copy the complete subtree, aligning its root frame with the target frame. */
    public static MAMesh attach(MAMesh child, MAPivot target){
        float[] source=orientation(child.pivotPoint());
        float[] q=multiply(orientation(target),new float[]{-source[0],-source[1],-source[2],source[3]});
        MAPivot p=child.pivotPoint();
        MAMesh placed=rotated(child,q,p.x(),p.y(),p.z());
        return translate(placed,target.x()-p.x(),target.y()-p.y(),target.z()-p.z());
    }

    private static void forEachMesh(MAMesh mesh, Consumer<MAMesh> operation){
        operation.accept(mesh);
        for(MAMesh child : mesh.children) forEachMesh(child, operation);
    }

    private static MAMesh copy(MAMesh mesh){
        validateTree(mesh, java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<MAMesh, Boolean>()));
        MAMesh result = new MAMesh(mesh);
        copyIndices(mesh, result);
        return result;
    }

    private static void validateTree(MAMesh mesh, java.util.Set<MAMesh> seen){
        if(mesh == null || mesh.pivotPoint == null)
            throw new IllegalArgumentException("Every mesh needs a pivot point.");
        if(!seen.add(mesh))
            throw new IllegalArgumentException("Children must form a tree, without cycles or shared meshes.");
        for(MAMesh child : mesh.children) validateTree(child, seen);
    }

    // Preserve links even with the earlier MAMesh copy constructor.
    private static void copyIndices(MAMesh source, MAMesh target){
        target.childIndex = source.childIndex;
        for(int i=0; i<source.children.size(); i++)
            copyIndices(source.children.get(i), target.children.get(i));
    }

    static void rotateVertices(MAMesh mesh, float[] q, float px, float py, float pz){
        forEachPivot(mesh, v -> {
            float[] p=vector(q,v.x()-px,v.y()-py,v.z()-pz);
            v.x(px+p[0]).y(py+p[1]).z(pz+p[2]);
            float[] r=multiply(q,orientation(v));
            v.rotation(r[0],r[1],r[2],r[3]);
        });
        forEachVertex(mesh, v -> {
            float[] p=vector(q,v.x()-px,v.y()-py,v.z()-pz);
            v.x(px+p[0]).y(py+p[1]).z(pz+p[2]);
        });
    }

    static void moveVertices(MAMesh mesh, float x, float y, float z){
        forEachPivot(mesh, v -> v.x(v.x()+x).y(v.y()+y).z(v.z()+z));
        forEachVertex(mesh, v -> {
            v.x(v.x()+x).y(v.y()+y).z(v.z()+z);
        });
    }

    static void scaleVertices(MAMesh mesh, float sx, float sy, float sz, float px, float py, float pz){
        // Scaling changes positions, not orthonormal frame rotations (shear is not a quaternion).
        forEachPivot(mesh, v -> v.x(px+(v.x()-px)*sx).y(py+(v.y()-py)*sy).z(pz+(v.z()-pz)*sz));
        forEachVertex(mesh, v -> {
            v.x(px+(v.x()-px)*sx).y(py+(v.y()-py)*sy).z(pz+(v.z()-pz)*sz);
        });
    }
}
