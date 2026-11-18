package MechaAmazing;

import java.util.*;
import com.raylib.Raylib.Texture;
import static com.raylib.Raylib.IsWindowReady;

/** Named joints with independent rest meshes and local animation rotations.
 * Queue rotations, then update() once per frame. All angles are radians.
 * Current meshes are owned by the rig: use rig APIs to modify them.
 * Rebuilds from rest data, preventing accumulated geometry/rotation drift.
 */
public class MARig implements AutoCloseable {
    public enum TextureMode { ORDERED, BATCHED, SINGLE }
    private final RigRenderer renderer = new RigRenderer(this);
    private Texture sharedTexture;
    private TextureMode textureMode = TextureMode.ORDERED;
    private boolean closed;
    public final class Joint {
        public final String name;
        private final String parent;
        private int connector;
        private final Joint parentJoint;
        private boolean dirty=true, changed;
        private MAMesh rest, current, cachedRest, posed;
        private int[] colorOverride;
        int[] tint;
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

        private void bindPose(MAMesh mesh) {
            posed = mesh;
            cachedRest = rest;
            int count = rest.tris.size() * 3;
            sourceVertices = new MAVert[count];
            outputVertices = new MAVert[count];
            for (int t = 0; t < rest.tris.size(); t++) {
                System.arraycopy(rest.tris.get(t).verts, 0, sourceVertices, 3 * t, 3);
                System.arraycopy(posed.tris.get(t).verts, 0, outputVertices, 3 * t, 3);
            }
        }
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
    private long geometryVersion,textureVersion;
    private Joint[] ordered=new Joint[0];
    private MARig parentRig;
    private Joint parentPart;
    private static final int PART_PIVOT = -1;
    private int parentPoint = PART_PIVOT;
    private MAPivot lastPointFrame;
    private boolean inactive;
    private final List<MARig> attachedRigs = new ArrayList<>();
    private float lastDrawX, lastDrawY, lastDrawZ;
    // The first draw after a pose update places the attachments. Extra draws can render afterimages.
    private enum Placement { FOLLOW_DRAWS, AWAITING_FIRST_DRAW, CAPTURED }
    private Placement placement = Placement.FOLLOW_DRAWS;
    float renderX, renderY, renderZ;

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

    /** Snap the child's root pivot to a posed part pivot. Both rigs keep their own draw call.
     * The child's joint pose is retained. Subsequent position/orientation calls adjust its grip locally.
     * An exact part name wins; a unique case-insensitive match is also accepted.
     */
    public MARig attach(String partName, MARig child) {
        Objects.requireNonNull(child, "child");
        return attach(attachmentPart(partName), PART_PIVOT, child);
    }

    /** Snap to an animated child point (zero-based). Passing null deactivates its occupant.
     * A point holds one rig. Replacing it deactivates the previous rig. Reattaching activates it.
     */
    public MARig attach(String partName, int childPointIndex, MARig child) {
        Joint part = attachmentPart(partName);
        if (childPointIndex < 0) throw new IllegalArgumentException("Negative child point index");
        if (child != null && part.rest != null && childPointIndex >= part.rest.childPointSize()) {
            throw new IllegalArgumentException(part.name + " has no child point " + childPointIndex);
        }
        return attach(part, childPointIndex, child);
    }

    private MARig attach(Joint part, int index, MARig child) {
        if (closed || (child != null && child.closed)) throw new IllegalStateException("Rig closed");
        for (MARig ancestor = this; ancestor != null; ancestor = ancestor.parentRig) {
            if (ancestor == child) throw new IllegalArgumentException("Rig attachment would create a cycle");
        }
        if (child != null && child.parentPart == part && child.parentPoint == index) return this;
        if (index != PART_PIVOT) {
            for (int i = 0; i < attachedRigs.size(); i++) {
                MARig occupant = attachedRigs.get(i);
                if (occupant.parentPart == part && occupant.parentPoint == index) {
                    release(occupant, true);
                    break;
                }
            }
        }
        if (child == null) return this;
        if (child.parentRig != null) child.parentRig.attachedRigs.remove(child);
        child.parentRig = this;
        child.parentPart = part;
        child.parentPoint = index;
        child.inactive = false;
        attachedRigs.add(child);
        child.position(0, 0, 0).orientation(0, 0, 0);
        child.worldDirty = true;
        child.recordDrawOffset(lastDrawX, lastDrawY, lastDrawZ);
        return update();
    }

    private Joint attachmentPart(String name) {
        Objects.requireNonNull(name, "partName");
        Joint match = joints.get(name);
        if (match != null) return match;
        for (Joint part : ordered) {
            if (part.name.equalsIgnoreCase(name)) {
                if (match != null) throw new IllegalArgumentException("Ambiguous joint: " + name);
                match = part;
            }
        }
        if (match == null) throw new IllegalArgumentException("Unknown joint: " + name);
        return match;
    }

    /** Release a child. Its local placement becomes its independent world placement. */
    public MARig detach(MARig child) {
        if (child != null && child.parentRig == this) release(child, false);
        return this;
    }

    private void release(MARig child, boolean deactivate) {
        attachedRigs.remove(child);
        child.parentRig = null;
        child.parentPart = null;
        child.parentPoint = PART_PIVOT;
        child.inactive = deactivate;
        child.dirty = child.worldDirty = true;
        child.recordDrawOffset(0, 0, 0);
        child.update();
    }

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
                if(j.colorOverride!=null){int[] c=j.colorOverride;m.setColor(c[0],c[1],c[2],c[3]);}
                else if(uniformColor)m.setColor(colorR,colorG,colorB,colorA);
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
    public MARig(Texture texture){this(); sharedTexture = texture;}
    public MARig(int type){if(type<0)throw new IllegalArgumentException("Invalid rig type");this.type=type;}
    // The concrete rig calls update() after construction to bind its public part views.
    MARig(MARig source) {
        type = source.type;
        sharedTexture = source.sharedTexture;
        textureMode = source.textureMode;
        renderer.copyTintFrom(source.renderer);
        uniformColor = source.uniformColor;
        colorR = source.colorR;
        colorG = source.colorG;
        colorB = source.colorB;
        colorA = source.colorA;
        motionUnit = source.motionUnit;
        appliedMotionScale = source.appliedMotionScale;
        x = source.x;
        y = source.y;
        z = source.z;
        worldRotation = source.worldRotation.clone();
        rootName = source.rootName;
        // A standalone copy uses its own local placement if the source was held by another rig.
        worldDirty = source.worldDirty || source.parentRig != null || source.inactive;
        for (Joint original : source.ordered) {
            Joint joint = new Joint(original.name, original.parent, original.connector);
            joints.put(joint.name, joint);
            joint.rotation = original.rotation.clone();
            joint.offset = original.offset.clone();
            joint.colorOverride = original.colorOverride == null ? null : original.colorOverride.clone();
            joint.tint = original.tint == null ? null : original.tint.clone();
            if (original.rest != null) joint.rest = original.rest.copyRigPart();
            if (original.current != null) {
                joint.bindPose(original.current.copyRigPart());
                joint.dirty = original.dirty;
                System.arraycopy(original.transform, 0, joint.transform, 0, joint.transform.length);
            }
        }
        ordered = joints.values().toArray(new Joint[0]);
    }
    public int type(){return type;}
    /** Application-selected length represented by one pose translation unit. */
    public MARig motionUnit(float unit){if(!Float.isFinite(unit)||unit<=0)throw new IllegalArgumentException("Invalid motion unit");if(motionUnit!=unit){motionUnit=unit;worldDirty=true;}dirty=true;return this;}
    public float motionUnit(){return motionUnit;}
    protected float motionScale(){return motionUnit;}
    /** Place the root pivot in world space, or in the parent pivot's frame while attached. */
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
        if (!attachedRigs.isEmpty()) placement = Placement.AWAITING_FIRST_DRAW;
        updateHierarchy();
        return this;
    }

    private void updateHierarchy() {
        MARig top = this;
        while (top.parentRig != null) top = top.parentRig;
        top.updateTree(false);
    }

    private void updateTree(boolean parentChanged) {
        MAPivot grip = attachmentFrame();
        if (parentPoint != PART_PIVOT) parentChanged |= samplePoint(grip);
        boolean reposed = dirty || parentChanged;
        if (reposed) {
            worldDirty |= parentChanged;
            updatePose(grip);
        }
        for (int i = 0; i < attachedRigs.size(); i++) {
            MARig child = attachedRigs.get(i);
            child.updateTree(reposed && child.parentPart.changed);
        }
    }

    private MAPivot attachmentFrame() {
        if (parentPart == null || parentPart.current == null) return null;
        MAMesh part = parentPart.current;
        if (parentPoint == PART_PIVOT) return part.pivotPoint();
        return parentPoint < part.childPointSize() ? part.childPoint(parentPoint) : null;
    }

    // A caller can also animate a posed child point in place between joint-pose updates.
    private boolean samplePoint(MAPivot point) {
        if (point == null) {
            boolean wasPresent = lastPointFrame != null;
            lastPointFrame = null;
            return wasPresent;
        }
        if (lastPointFrame == null) {
            lastPointFrame = new MAPivot(point);
            return true;
        }
        MAPivot last = lastPointFrame;
        if (last.x == point.x && last.y == point.y && last.z == point.z
                && last.qx == point.qx && last.qy == point.qy && last.qz == point.qz && last.qw == point.qw) return false;
        last.x = point.x;
        last.y = point.y;
        last.z = point.z;
        last.qx = point.qx;
        last.qy = point.qy;
        last.qz = point.qz;
        last.qw = point.qw;
        return true;
    }

    private void updatePose(MAPivot grip) {
        boolean hidden = inactive || (parentPart != null && grip == null);
        float scale=motionScale();
        if(scale!=appliedMotionScale)worldDirty=true;
        boolean changed=false;
        for(int index=0;index<ordered.length;index++){
            Joint j=ordered[index];
            Joint parent=j.parentJoint;
            j.changed=worldDirty||j.dirty||j.cachedRest!=j.rest||(parent!=null&&parent.changed);
            j.dirty=false;changed|=j.changed;
            if(hidden || j.rest==null || (parent!=null && parent.current==null)){j.current=null;continue;}
            if(j.cachedRest!=j.rest) j.bindPose(new MAMesh(j.rest));
            MAMesh m=j.posed;j.current=m;m.children.clear();m.childIndex=parent==null?-1:j.connector;
            if(!j.changed){if(parent!=null)parent.current.children.add(m);continue;}
            MAPivot rest=j.rest.pivotPoint;
            float px,py,pz;
            if(parent==null && grip!=null){
                RigidMath.normalized(j.frame, grip.qx(), grip.qy(), grip.qz(), grip.qw());
                RigidMath.vector(j.frame, x, y, z, j.scratch);
                px = grip.x() + j.scratch[0];
                py = grip.y() + j.scratch[1];
                pz = grip.z() + j.scratch[2];
                RigidMath.multiply(j.frame, j.frame, worldRotation[0], worldRotation[1], worldRotation[2], worldRotation[3]);
            }else if(parent==null){
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
        MAMesh nextRoot=rootName==null?null:joints.get(rootName).current;
        if ((root==null) != (nextRoot==null)) geometryVersion++;
        root=nextRoot;
        dirty=false;worldDirty=false;appliedMotionScale=scale;if(changed)poseVersion++;onUpdated();
    }
    /** Replace every installed part's RGBA, including parts installed later. Channels wrap like MAMesh.setColor. */
    public MARig setColor(int r,int g,int b,int a){
        colorR=r&255;colorG=g&255;colorB=b&255;colorA=a&255;uniformColor=true;
        for(Joint j:ordered){
            j.colorOverride=null;
            if(j.rest!=null)j.rest.setColor(colorR,colorG,colorB,colorA);
            if(j.posed!=null)j.posed.setColor(colorR,colorG,colorB,colorA);
        }
        colorVersion++;return this;
    }
    /** Recolor one joint, including future replacements; a later whole-rig setColor resets overrides. */
    public MARig setColor(String name,int r,int g,int b,int a){
        Joint j=joint(name);
        j.colorOverride=new int[]{r&255,g&255,b&255,a&255};
        if(j.rest!=null)j.rest.setColor(r,g,b,a);
        if(j.posed!=null)j.posed.setColor(r,g,b,a);
        colorVersion++;return this;
    }
    /** Set an installed part's borrowed texture; null restores the renderer's fallback.
     * Replacing the part uses the replacement mesh's own texture.
     */
    public MARig setTexture(String name,Texture texture){
        Joint j=joint(name);
        if(j.rest==null)throw new IllegalStateException("Part is not installed: "+name);
        if(j.rest.texture()!=texture){
            j.rest.texture(texture);
            if(j.posed!=null)j.posed.texture(texture);
            textureVersion++;
        }
        return this;
    }
    long poseVersion(){return poseVersion;}
    long colorVersion(){return colorVersion;}
    long geometryVersion(){return geometryVersion;}
    long textureVersion(){return textureVersion;}
    MAMesh restMesh(String name){return joint(name).rest;}
    float[] partTransform(String name){return joint(name).transform;}
    protected void onUpdated(){}

    // Resolve the world placement once for rlgl, textured rlgl and GPU draws.
    void prepareDraw(float x, float y, float z) {
        if (dirty && placement == Placement.CAPTURED) placement = Placement.AWAITING_FIRST_DRAW;
        updateHierarchy();
        if (parentRig != null && x == 0 && y == 0 && z == 0) {
            x = parentRig.lastDrawX;
            y = parentRig.lastDrawY;
            z = parentRig.lastDrawZ;
        }
        renderX = x;
        renderY = y;
        renderZ = z;
        if (placement != Placement.CAPTURED || attachedRigs.isEmpty()) {
            recordDrawOffset(x, y, z);
            if (placement == Placement.AWAITING_FIRST_DRAW) placement = Placement.CAPTURED;
        }
        // The attachment has consumed its parent's placement. The same pose can now draw another instance.
        for (MARig p = parentRig; p != null; p = p.parentRig) {
            if (p.placement == Placement.CAPTURED) p.placement = Placement.FOLLOW_DRAWS;
        }
    }

    private void recordDrawOffset(float x, float y, float z) {
        lastDrawX = x;
        lastDrawY = y;
        lastDrawZ = z;
        for (int i = 0; i < attachedRigs.size(); i++) attachedRigs.get(i).recordDrawOffset(x, y, z);
    }

    /** Set the borrowed shared texture. Null selects raylib's default white texture. */
    public MARig setTexture(Texture texture) {
        sharedTexture = texture;
        return this;
    }

    public MARig textureMode(TextureMode mode) {
        textureMode = Objects.requireNonNull(mode, "mode");
        return this;
    }
    public TextureMode textureMode(){return textureMode;}

    /** Multiply the rendered colors while preserving the authored vertex colors. */
    public MARig setTint(int r, int g, int b, int a) {
        renderer.setTint(r, g, b, a);
        return this;
    }
    public MARig setTint(String name, int r, int g, int b, int a) {
        Joint joint = joint(name);
        int[] next = {r & 255, g & 255, b & 255, a & 255};
        if (!Arrays.equals(joint.tint, next)) {
            joint.tint = next;
            colorVersion++;
        }
        return this;
    }
    public MARig clearTint(String name) {
        Joint joint = joint(name);
        if (joint.tint != null) {
            joint.tint = null;
            colorVersion++;
        }
        return this;
    }

    /** True after this rig successfully initializes its GPU buffers, until fallback or close. */
    public boolean isUsingGpu(){return renderer.isUsingGpu();}

    /** Inside BeginMode3D, the rig manages its own GPU or immediate-mode draw calls. */
    public void draw(){draw(0, 0, 0);}
    public void draw(float x, float y, float z){render(sharedTexture, x, y, z);}

    /** Override the shared texture for just this draw, on either backend. */
    public void drawTextured(Texture fallback){drawTextured(fallback, 0, 0, 0);}
    public void drawTextured(Texture fallback, float x, float y, float z){
        render(Objects.requireNonNull(fallback, "fallback"), x, y, z);
    }

    private void render(Texture texture, float x, float y, float z) {
        if (closed) throw new IllegalStateException("Rig closed");
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) throw new IllegalArgumentException("Invalid draw offset");
        if (!IsWindowReady()) {
            updateHierarchy();
            return;
        }
        prepareDraw(x, y, z);
        if (root != null) renderer.draw(texture);
    }

    /** On the graphics thread, release this rig before closing its window. Textures are borrowed. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        renderer.close();
        if (parentRig != null) parentRig.attachedRigs.remove(this);
        parentRig = null;
        parentPart = null;
        while (!attachedRigs.isEmpty()) release(attachedRigs.get(attachedRigs.size() - 1), true);
        sharedTexture = null;
    }
}
