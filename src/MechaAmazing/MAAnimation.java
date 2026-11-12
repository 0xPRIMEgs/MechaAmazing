package MechaAmazing;
import java.util.*;
/** A time-indexed set of MAPoses. As a MAPose, exposes/applies its first keyframe.
 * Samples linearly interpolate translations and slerp rotations. Time is seconds.
 */
public class MAAnimation extends MAPose {
 private final int animationType;
 private final TreeMap<Double,MAPose> frames=new TreeMap<>();
 public MAAnimation(){this(ANY);}
 public MAAnimation(int type){if(type<0)throw new IllegalArgumentException("Invalid animation type");animationType=type;}
 @Override public int type(){return animationType;}
 @Override protected Map<String,float[]> data(){
  if(frames.isEmpty())throw new IllegalStateException("Animation has no keyframes");
  return frames.firstEntry().getValue().data();
 }
 public NavigableMap<Double,MAPose> frames(){return Collections.unmodifiableNavigableMap(frames);}
 private static void valid(double t){if(!Double.isFinite(t)||t<0)throw new IllegalArgumentException("Invalid time");}
 public void put(double t,MAPose pose){
  valid(t);if(pose.type()!=type()||(!frames.isEmpty()&&!names().equals(pose.names())))throw new IllegalArgumentException("Incompatible keyframe type/joints");
  MAPose snapshot=pose.copy();frames.put(t,snapshot);
 }
 public boolean delete(double t){valid(t);return frames.remove(t)!=null;}
 public void move(double from,double to){
  valid(from);valid(to);if(!frames.containsKey(from))throw new IllegalArgumentException("Missing source keyframe");
  if(from==to)return;if(frames.containsKey(to))throw new IllegalArgumentException("Destination occupied");
  frames.put(to,frames.remove(from));
 }
 public double duration(){return frames.isEmpty()?0:frames.lastKey();}
 public MAPose sample(double t){
  valid(t);if(frames.isEmpty())throw new IllegalStateException("Animation has no keyframes");
  var a=frames.floorEntry(t);var b=frames.ceilingEntry(t);
  if(a==null)return frames.firstEntry().getValue();if(b==null)return frames.lastEntry().getValue();
  if(a.getKey().equals(b.getKey()))return a.getValue();
  return MAPose.interpolate(a.getValue(),b.getValue(),(float)((t-a.getKey())/(b.getKey()-a.getKey())));
 }
 /** Sample directly into rig state without allocating a temporary pose/map per frame. */
 public void queue(MARig rig,double t){
  valid(t);if(frames.isEmpty())throw new IllegalStateException("Animation has no keyframes");
  var a=frames.floorEntry(t);var b=frames.ceilingEntry(t);
  if(a==null){frames.firstEntry().getValue().queue(rig);return;}
  if(b==null||a.getKey().equals(b.getKey())){a.getValue().queue(rig);return;}
  MAPose pa=a.getValue(),pb=b.getValue();pa.validate(rig);
  float f=(float)((t-a.getKey())/(b.getKey()-a.getKey()));
  var da=pa.data();var db=pb.data();
  for(var entry:da.entrySet()){
   String name=entry.getKey();float[] x=entry.getValue(),y=db.get(name);
   double dot=0;for(int j=3;j<7;j++)dot+=x[j]*y[j];float sign=dot<0?-1:1;dot=Math.min(1,Math.abs(dot));
   double wa=1-f,wb=f;if(dot<.9995){double angle=Math.acos(dot),sin=Math.sin(angle);wa=Math.sin((1-f)*angle)/sin;wb=Math.sin(f*angle)/sin;}
   rig.translation(name,x[0]+(y[0]-x[0])*f,x[1]+(y[1]-x[1])*f,x[2]+(y[2]-x[2])*f);
   rig.rotationQuaternion(name,(float)(wa*x[3]+wb*sign*y[3]),(float)(wa*x[4]+wb*sign*y[4]),(float)(wa*x[5]+wb*sign*y[5]),(float)(wa*x[6]+wb*sign*y[6]));
  }
 }
 public void apply(MARig rig,double seconds){queue(rig,seconds);rig.update();}
}
