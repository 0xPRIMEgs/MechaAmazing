package MechaAmazing;
import java.util.*;

/** Named local joint transforms, independent of geometry and rig subclass.
 * type -1: invalid/uninitialized; 0: any rig type with matching joint names;
 * 1: humanoid; other positive IDs are available for application rig types.
 * Each record is tx,ty,tz,qx,qy,qz,qw. Translation uses MARig.motionUnit().
 * World placement and authored connector frames are not animation data.
 */
public class MAPose {
 public static final int INVALID=-1,ANY=0,HUMANOID=1;
 private final int type;
 private final Map<String,float[]> transforms;
 public MAPose(){type=INVALID;transforms=Map.of();}
 public MAPose(int type,Map<String,float[]> joints){
  if(type<0)throw new IllegalArgumentException("Invalid pose type");this.type=type;
  LinkedHashMap<String,float[]> copy=new LinkedHashMap<>();
  joints.forEach((name,v)->{
   if(name==null||name.isBlank()||v==null||v.length!=7)throw new IllegalArgumentException("Expected named xyz + quaternion");
   for(float f:v)if(!Float.isFinite(f))throw new IllegalArgumentException("Nonfinite pose");
   float[] a=v.clone(),q=MAMeshMath.normalized(Arrays.copyOfRange(v,3,7));System.arraycopy(q,0,a,3,4);copy.put(name,a);
  });
  if(copy.isEmpty())throw new IllegalArgumentException("Pose requires joints");
  transforms=Collections.unmodifiableMap(copy);
 }
 public int type(){return type;}
 protected Map<String,float[]> data(){return transforms;}
 public Set<String> names(){return Collections.unmodifiableSet(data().keySet());}
 public float[] joint(String name){float[] v=data().get(name);if(v==null)throw new IllegalArgumentException("Unknown pose joint: "+name);return v.clone();}
 public MAPose copy(){return new MAPose(type(),data());}
 public static MAPose capture(MARig rig){
  LinkedHashMap<String,float[]> data=new LinkedHashMap<>();
  for(String n:rig.names()){float[] v=new float[7];System.arraycopy(rig.jointTranslation(n),0,v,0,3);System.arraycopy(rig.jointRotation(n),0,v,3,4);data.put(n,v);}
  return new MAPose(rig.type(),data);
 }
 public void validate(MARig rig){
  if(type()<0||(type()!=ANY&&type()!=rig.type()))throw new IllegalArgumentException("Pose/rig type mismatch");
  if(!names().equals(rig.names()))throw new IllegalArgumentException("Pose/rig joint names mismatch");
 }
 /** Queue this pose without rebuilding; call rig.update() once after all frame changes. */
 public void queue(MARig rig){
  validate(rig);for(var entry:data().entrySet()){float[] v=entry.getValue();String n=entry.getKey();rig.translation(n,v[0],v[1],v[2]);rig.rotationQuaternion(n,v[3],v[4],v[5],v[6]);}
 }
 public void apply(MARig rig){queue(rig);rig.update();}
 public static MAPose interpolate(MAPose a,MAPose b,float t){
  if(a.type()<0||a.type()!=b.type()||!a.names().equals(b.names()))throw new IllegalArgumentException("Incompatible poses");
  if(!Float.isFinite(t))throw new IllegalArgumentException("Invalid time");t=Math.max(0,Math.min(1,t));
  LinkedHashMap<String,float[]> data=new LinkedHashMap<>();
  for(String n:a.names()){
   float[] x=a.joint(n),y=b.joint(n),v=new float[7];for(int j=0;j<3;j++)v[j]=x[j]+(y[j]-x[j])*t;
   double dot=0;for(int j=3;j<7;j++)dot+=x[j]*y[j];float sign=dot<0?-1:1;dot=Math.min(1,Math.abs(dot));
   double wa=1-t,wb=t;if(dot<.9995){double angle=Math.acos(dot),sin=Math.sin(angle);wa=Math.sin((1-t)*angle)/sin;wb=Math.sin(t*angle)/sin;}
   for(int j=3;j<7;j++)v[j]=(float)(wa*x[j]+wb*sign*y[j]);data.put(n,v);
  }return new MAPose(a.type(),data);
 }
}
