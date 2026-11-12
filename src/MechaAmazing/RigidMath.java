package MechaAmazing;
/** Allocation-free rigid transforms. Matrix storage: three row-major rows of four. */
final class RigidMath {
 static void normalized(float[] out,float x,float y,float z,float w){
  double n=Math.sqrt((double)x*x+(double)y*y+(double)z*z+(double)w*w);
  if(!Double.isFinite(n)||n==0)throw new IllegalArgumentException("Invalid quaternion");
  out[0]=(float)(x/n);out[1]=(float)(y/n);out[2]=(float)(z/n);out[3]=(float)(w/n);
 }
 static void multiply(float[] out,float[] a,float x,float y,float z,float w){
  normalized(out,a[3]*x+a[0]*w+a[1]*z-a[2]*y,a[3]*y-a[0]*z+a[1]*w+a[2]*x,
   a[3]*z+a[0]*y-a[1]*x+a[2]*w,a[3]*w-a[0]*x-a[1]*y-a[2]*z);
 }
 static void vector(float[] q,float x,float y,float z,float[] out){
  float tx=2*(q[1]*z-q[2]*y),ty=2*(q[2]*x-q[0]*z),tz=2*(q[0]*y-q[1]*x);
  out[0]=x+q[3]*tx+q[1]*tz-q[2]*ty;out[1]=y+q[3]*ty+q[2]*tx-q[0]*tz;out[2]=z+q[3]*tz+q[0]*ty-q[1]*tx;
 }
 static void matrix(float[] t,float[] q,float rx,float ry,float rz,float px,float py,float pz){
  float x=q[0],y=q[1],z=q[2],w=q[3];
  t[0]=1-2*(y*y+z*z);t[1]=2*(x*y-z*w);t[2]=2*(x*z+y*w);
  t[4]=2*(x*y+z*w);t[5]=1-2*(x*x+z*z);t[6]=2*(y*z-x*w);
  t[8]=2*(x*z-y*w);t[9]=2*(y*z+x*w);t[10]=1-2*(x*x+y*y);
  t[3]=px-t[0]*rx-t[1]*ry-t[2]*rz;t[7]=py-t[4]*rx-t[5]*ry-t[6]*rz;t[11]=pz-t[8]*rx-t[9]*ry-t[10]*rz;
 }
 static void pivot(MAPivot src,MAPivot dst,float[] t,float[] q,float[] scratch){
  dst.x(t[0]*src.x()+t[1]*src.y()+t[2]*src.z()+t[3]);
  dst.y(t[4]*src.x()+t[5]*src.y()+t[6]*src.z()+t[7]);
  dst.z(t[8]*src.x()+t[9]*src.y()+t[10]*src.z()+t[11]);
  multiply(scratch,q,src.qx(),src.qy(),src.qz(),src.qw());dst.rotation(scratch[0],scratch[1],scratch[2],scratch[3]);
 }
 static void euler(MAMesh m){
  double sy=Math.max(-1,Math.min(1,2*(m.qw*m.qy-m.qz*m.qx)));
  if(Math.abs(sy)>.999999){m.pitch=0;m.yaw=(float)Math.copySign(Math.PI/2,sy);m.roll=(float)Math.atan2(2*(m.qw*m.qz-m.qx*m.qy),1-2*(m.qx*m.qx+m.qz*m.qz));}
  else{m.pitch=(float)Math.atan2(2*(m.qw*m.qx+m.qy*m.qz),1-2*(m.qx*m.qx+m.qy*m.qy));m.yaw=(float)Math.asin(sy);m.roll=(float)Math.atan2(2*(m.qw*m.qz+m.qx*m.qy),1-2*(m.qy*m.qy+m.qz*m.qz));}
 }
}
