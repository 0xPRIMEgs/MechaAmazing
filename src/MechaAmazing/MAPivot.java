package MechaAmazing;

/** A model-space attachment frame, independent of render vertices.
 * Quaternion setters configure the frame; they do not move mesh geometry.
 * Rotation angles are radians. Mesh transforms move/rotate these frames too.
 */
public class MAPivot{
	float qx = 0.0f; // Quaternion value
	float qy = 0.0f; // Quaternion value
	float qz = 0.0f; // Quaternion value
	float qw = 1.0f; // Quaternion value
	
	float x = 0.0f;
	float y = 0.0f;
	float z = 0.0f;
	
	public MAPivot(){}
	
	public MAPivot(MAPivot other){
		this.qx = other.qx;
		this.qy = other.qy;
		this.qz = other.qz;
		this.qw = other.qw;

		this.x = other.x;
		this.y = other.y;
		this.z = other.z;
	}
	
	public float qx(){ return this.qx; }
	public MAPivot qx(float f){ this.qx = f; return this; }

	public float qy(){ return this.qy; }
	public MAPivot qy(float f){ this.qy = f; return this; }

	public float qz(){ return this.qz; }
	public MAPivot qz(float f){ this.qz = f; return this; }

	public float qw(){ return this.qw; }
	public MAPivot qw(float f){ this.qw = f; return this; }
	
	public float x(){ return this.x; }
	public MAPivot x(float f){ this.x = f; return this; }
	
	public float y(){ return this.y; }
	public MAPivot y(float f){ this.y = f; return this; }

	public float z(){ return this.z; }
	public MAPivot z(float f){ this.z = f; return this; }

    public MAPivot rotation(float x, float y, float z, float w) {
        float[] q=MAMeshMath.normalized(new float[]{x,y,z,w});
        qx=q[0]; qy=q[1]; qz=q[2]; qw=q[3]; return this;
    }

    /** Set frame orientation: Rz(z) * Ry(y) * Rx(x), radians. */
    public MAPivot rotationXYZ(float x, float y, float z) {
        float sx=(float)Math.sin(x/2), cx=(float)Math.cos(x/2);
        float sy=(float)Math.sin(y/2), cy=(float)Math.cos(y/2);
        float sz=(float)Math.sin(z/2), cz=(float)Math.cos(z/2);
        return rotation(sx*cy*cz-cx*sy*sz, cx*sy*cz+sx*cy*sz,
                        cx*cy*sz-sx*sy*cz, cx*cy*cz+sx*sy*sz);
    }
}
