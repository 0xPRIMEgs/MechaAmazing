package MechaAmazing;

import java.util.*;

/** Named joints with independent rest meshes and local animation rotations.
 * Queue rotations, then update() once per frame. All angles are radians.
 * Current meshes are owned by the rig: use rig APIs to modify them.
 * Rebuilds from rest data, preventing accumulated geometry/rotation drift.
 */
public class MARig {
    public final class Joint {
        public final String name;
        private final String parent;
        private int connector;
        private final Joint parentJoint;
        private boolean dirty=true, changed;
        private MAMesh rest, current, cachedRest, posed;
        private final float[] transform=new float[12],frame=new float[4],delta=new float[4],scratch=new float[4];
        private MAVert[] sourceVertices,outputVertices;
        private float[] rotation={0,0,0,1};
        private float[] offset={0,0,0};
        private Joint(String name,String parent,int connector){this.name=name;this.parent=parent;this.connector=connector;this.parentJoint=parent==null?null:joints.get(parent);}
        /** Stable Joint handle; mesh() resolves the current mesh after each update. */
        public MAMesh mesh(){return current;}
        public String parent(){return parent;}
        public int connector(){return connector;}
        public boolean installed(){return rest!=null;}
    }
    private final LinkedHashMap<String,Joint> joints=new LinkedHashMap<>();
    private String rootName;
    private MAMesh root;
    private float x,y,z;
    private float[] worldRotation={0,0,0,1};
    private boolean dirty=true, worldDirty=true;
    private boolean uniformColor;
    private int colorR=255,colorG=255,colorB=255,colorA=255;
    private long poseVersion,colorVersion;
    private long geometryVersion;
    private Joint[] ordered=new Joint[0];

    /** Define parents before children. Connector is an index into the parent's childPoints. */
    public MARig define(String name,String parent,int connector){
        Objects.requireNonNull(name,"name");
        if(joints.containsKey(name)) throw new IllegalArgumentException("Duplicate joint: "+name);
        if(parent==null){if(rootName!=null)throw new IllegalArgumentException("MARig already has a root");rootName=name;}
        else {joint(parent);if(connector<0)throw new IllegalArgumentException("Negative connector");}
        joints.put(name,new Joint(name,parent,connector));ordered=joints.values().toArray(new Joint[0]);geometryVersion++;dirty=true;return this;
    }
    public Joint joint(String name){
        Joint j=joints.get(name);if(j==null)throw new IllegalArgumentException("Unknown joint: "+name);return j;
    }
    public MAMesh mesh(String name){return joint(name).mesh();}
    public MAMesh root(){return root;}
    public Set<String> names(){return Collections.unmodifiableSet(joints.keySet());}

    /** Install/replace a single unassembled part. Its animation rotation is retained. */
    public MARig setPart(String name,MAMesh mesh){return setParts(Collections.singletonMap(name,mesh));}
    public MARig replace(String name,MAMesh mesh){return setPart(name,mesh);}
    /** Batch installation; parts may be supplied in any order. Null removes a part.
     * Descendants of an absent part remain installed but have no current mesh.
     */
    public MARig setParts(Map<String,MAMesh> parts){
        Map<Joint,MAMesh> next=new LinkedHashMap<>(),old=new LinkedHashMap<>();
        for(var entry:parts.entrySet()){
            Joint j=joint(entry.getKey()); MAMesh m=entry.getValue();
            if(m!=null){
                if(m.pivotPoint()==null || !m.children.isEmpty())
                    throw new IllegalArgumentException("Install an unassembled part with a pivot: "+j.name);
                m=new MAMesh(m);
                if(uniformColor)m.setColor(colorR,colorG,colorB,colorA);
                MAMeshMath.orientation(m.pivotPoint());
                for(MAPivot p:m.childPoints) MAMeshMath.orientation(p);
            }
            next.put(j,m);old.put(j,j.rest);
        }
        next.forEach((j,m)->j.rest=m);
        try{validateConnections();}catch(RuntimeException e){old.forEach((j,m)->j.rest=m);throw e;}
        geometryVersion++;dirty=true;worldDirty=true;return update();
    }
    public MARig connector(String name,int index){
        Joint j=joint(name);
        if(j.parent==null || index<0)throw new IllegalArgumentException("Expected child joint and nonnegative connector");
        int old=j.connector;j.connector=index;
        try{validateConnections();}catch(RuntimeException e){j.connector=old;throw e;}
        j.dirty=true;dirty=true;return update();
    }
    private void validateConnections(){
        for(Joint j:joints.values()) if(j.parent!=null && j.rest!=null){
            Joint p=joint(j.parent);
            if(p.rest!=null && j.connector>=p.rest.childPoints.size())
                throw new IllegalArgumentException(p.name+" needs childPoint #"+j.connector+" for "+j.name);
        }
    }
    /** Absolute animation offset from rest, in the joint's local pivot frame. */
    public MARig rotation(String name,float rx,float ry,float rz){
        MAPivot q=new MAPivot().rotationXYZ(rx,ry,rz);
        return rotationQuaternion(name,q.qx(),q.qy(),q.qz(),q.qw());
    }
    public MARig rotationQuaternion(String name,float qx,float qy,float qz,float qw){
        Joint j=joint(name);
        RigidMath.normalized(j.scratch,qx,qy,qz,qw);
        if(!Arrays.equals(j.rotation,j.scratch)){System.arraycopy(j.scratch,0,j.rotation,0,4);j.dirty=true;}
        dirty=true;return this;
    }
    /** Local translation in pose units; zero keeps connectors joined. */
    public MARig translation(String name,float x,float y,float z){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Invalid translation");
        Joint j=joint(name);float[] offset=j.offset;
        if(Float.compare(offset[0],x)!=0||Float.compare(offset[1],y)!=0||Float.compare(offset[2],z)!=0){offset[0]=x;offset[1]=y;offset[2]=z;j.dirty=true;}
        dirty=true;return this;
    }
    public float[] jointRotation(String name){return joint(name).rotation.clone();}
    public float[] jointTranslation(String name){return joint(name).offset.clone();}
    private final int type;
    private float motionUnit=1f, appliedMotionScale=Float.NaN;
    public MARig(){this(0);}
    public MARig(int type){if(type<0)throw new IllegalArgumentException("Invalid rig type");this.type=type;}
    public int type(){return type;}
    /** Application-selected length represented by one pose translation unit. */
    public MARig motionUnit(float unit){if(!Float.isFinite(unit)||unit<=0)throw new IllegalArgumentException("Invalid motion unit");if(motionUnit!=unit){motionUnit=unit;worldDirty=true;}dirty=true;return this;}
    public float motionUnit(){return motionUnit;}
    protected float motionScale(){return motionUnit;}
    /** Place the root pivot at this world position. Applied on update(). */
    public MARig position(float x,float y,float z){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Invalid position");
        if(Float.compare(this.x,x)!=0||Float.compare(this.y,y)!=0||Float.compare(this.z,z)!=0){this.x=x;this.y=y;this.z=z;worldDirty=true;}dirty=true;return this;
    }
    public MARig orientation(float rx,float ry,float rz){
        MAPivot q=new MAPivot().rotationXYZ(rx,ry,rz);
        float[] next=MAMeshMath.orientation(q);
        if(!Arrays.equals(worldRotation,next)){worldRotation=next;worldDirty=true;}dirty=true;return this;
    }
    public MARig resetPose(){for(Joint j:joints.values()){j.rotation=new float[]{0,0,0,1};j.offset=new float[]{0,0,0};j.dirty=true;}dirty=true;return update();}

    /** Reuses posed geometry; updates changed joints and their descendants from rest data. */
    public MARig update(){
        if(!dirty)return this;
        float scale=motionScale();
        if(scale!=appliedMotionScale)worldDirty=true;
        boolean changed=false;
        for(int index=0;index<ordered.length;index++){
            Joint j=ordered[index];
            Joint parent=j.parentJoint;
            j.changed=worldDirty||j.dirty||j.cachedRest!=j.rest||(parent!=null&&parent.changed);
            j.dirty=false;changed|=j.changed;
            if(j.rest==null || (parent!=null && parent.current==null)){j.current=null;continue;}
            if(j.cachedRest!=j.rest){
                j.posed=new MAMesh(j.rest);j.cachedRest=j.rest;
                int n=j.rest.tris.size()*3;j.sourceVertices=new MAVert[n];j.outputVertices=new MAVert[n];
                for(int t=0;t<j.rest.tris.size();t++)for(int v=0;v<3;v++){
                    j.sourceVertices[t*3+v]=j.rest.tris.get(t).verts[v];j.outputVertices[t*3+v]=j.posed.tris.get(t).verts[v];
                }
            }
            MAMesh m=j.posed;j.current=m;m.children.clear();m.childIndex=parent==null?-1:j.connector;
            if(!j.changed){if(parent!=null)parent.current.children.add(m);continue;}
            MAPivot rest=j.rest.pivotPoint;
            float px,py,pz;
            if(parent==null){
                RigidMath.multiply(j.frame,worldRotation,rest.qx(),rest.qy(),rest.qz(),rest.qw());
                px=x;py=y;pz=z;
            }else{
                MAPivot target=parent.current.childPoint(j.connector);
                RigidMath.normalized(j.frame,target.qx(),target.qy(),target.qz(),target.qw());
                px=target.x();py=target.y();pz=target.z();
            }
            RigidMath.vector(j.frame,j.offset[0]*scale,j.offset[1]*scale,j.offset[2]*scale,j.scratch);
            px+=j.scratch[0];py+=j.scratch[1];pz+=j.scratch[2];
            RigidMath.multiply(j.scratch,j.frame,j.rotation[0],j.rotation[1],j.rotation[2],j.rotation[3]);
            RigidMath.multiply(j.delta,j.scratch,-rest.qx(),-rest.qy(),-rest.qz(),rest.qw());
            RigidMath.matrix(j.transform,j.delta,rest.x(),rest.y(),rest.z(),px,py,pz);
            for(int i=0;i<j.sourceVertices.length;i++){
                MAVert src=j.sourceVertices[i],out=j.outputVertices[i];float[] t=j.transform;
                out.x=t[0]*src.x+t[1]*src.y+t[2]*src.z+t[3];
                out.y=t[4]*src.x+t[5]*src.y+t[6]*src.z+t[7];
                out.z=t[8]*src.x+t[9]*src.y+t[10]*src.z+t[11];
            }
            RigidMath.pivot(rest,m.pivotPoint,j.transform,j.delta,j.scratch);
            for(int i=0;i<m.childPoints.size();i++)RigidMath.pivot(j.rest.childPoints.get(i),m.childPoints.get(i),j.transform,j.delta,j.scratch);
            float[] t=j.transform;
            m.x=t[0]*j.rest.x+t[1]*j.rest.y+t[2]*j.rest.z+t[3];
            m.y=t[4]*j.rest.x+t[5]*j.rest.y+t[6]*j.rest.z+t[7];
            m.z=t[8]*j.rest.x+t[9]*j.rest.y+t[10]*j.rest.z+t[11];
            RigidMath.multiply(j.scratch,j.delta,j.rest.qx,j.rest.qy,j.rest.qz,j.rest.qw);
            m.qx=j.scratch[0];m.qy=j.scratch[1];m.qz=j.scratch[2];m.qw=j.scratch[3];
            RigidMath.euler(m);
            if(parent!=null)parent.current.children.add(m);
        }
        root=rootName==null?null:joints.get(rootName).current;
        dirty=false;worldDirty=false;appliedMotionScale=scale;if(changed)poseVersion++;onUpdated();return this;
    }
    /** Replace every installed part's RGBA, including parts installed later. Channels wrap like MAMesh.setColor. */
    public MARig setColor(int r,int g,int b,int a){
        colorR=r&255;colorG=g&255;colorB=b&255;colorA=a&255;uniformColor=true;
        for(Joint j:ordered){
            if(j.rest!=null)j.rest.setColor(colorR,colorG,colorB,colorA);
            if(j.posed!=null)j.posed.setColor(colorR,colorG,colorB,colorA);
        }
        colorVersion++;return this;
    }
    long poseVersion(){return poseVersion;}
    long colorVersion(){return colorVersion;}
    long geometryVersion(){return geometryVersion;}
    MAMesh restMesh(String name){return joint(name).rest;}
    float[] partTransform(String name){return joint(name).transform;}
    protected void onUpdated(){}
    /** Draw inside the same rlgl triangle/texture block used by MAMesh.draw(). */
    public void draw(){update();if(root!=null)root.draw();}

    /** Draw with an additional world-space offset, including all child parts.
     * The offset is added to position(); it does not change the stored position,
     * pose, or geometry. Repeated draws reuse the current pose until it changes.
     * Call inside the same rlgl triangle/texture block used by draw().
     */
    public void draw(float x,float y,float z){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Invalid draw offset");
        update();if(root!=null)root.draw(x,y,z);
    }
}
