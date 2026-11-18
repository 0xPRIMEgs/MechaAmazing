package MechaAmazing;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;
import com.raylib.Raylib;

import java.util.Vector;

public class MAMesh{
	Vector<MATri> tris = new Vector<MATri>();
	
	MAPivot pivotPoint;
	private Raylib.Texture texture;
	Vector<MAPivot> childPoints = new Vector<MAPivot>();
	Vector<MAMesh> children = new Vector<MAMesh>();
	
	int childIndex = -1;
	
	float scaleX = 1.0f;
    float scaleY = 1.0f;
    float scaleZ = 1.0f;
	
	float yaw = 0.0f;
    float pitch = 0.0f;
    float roll = 0.0f;
    float qx = 0.0f; // Quaternion value
	float qy = 0.0f; // Quaternion value
	float qz = 0.0f; // Quaternion value
	float qw = 1.0f; // Quaternion value

    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
	
	float width = 1f;
	float height = 1f;
	float depth = 1f;
	
	float centerX = 0f;
	float centerY = 0f;
	float centerZ = 0f;
	
	public MAMesh(Vector<MATri> tris, MAPivot pivotPoint){
		this.tris = tris;
		this.pivotPoint = java.util.Objects.requireNonNull(pivotPoint, "pivotPoint");
	}

	/** Optional borrowed texture; null uses the renderer's fallback. */
	public MAMesh(Vector<MATri> tris, MAPivot pivotPoint, Raylib.Texture texture){
		this(tris, pivotPoint);
		this.texture = texture;
	}

	public Raylib.Texture texture(){ return texture; }
	/** For an installed rig part, use MARig.setTexture(name, texture) instead. */
	public MAMesh texture(Raylib.Texture texture){ this.texture = texture; return this; }
	
	public MAMesh(MAMesh other){
		this(other, other.children);
	}

	private MAMesh(MAMesh other, Iterable<MAMesh> childrenToCopy){
		this.texture = other.texture;
		this.childIndex = other.childIndex;
		
		this.scaleX = other.scaleX;
		this.scaleY = other.scaleY;
		this.scaleZ = other.scaleZ;

		this.yaw = other.yaw;
		this.pitch = other.pitch;
		this.roll = other.roll;

		this.qx = other.qx;
		this.qy = other.qy;
		this.qz = other.qz;
		this.qw = other.qw;

		this.x = other.x;
		this.y = other.y;
		this.z = other.z;
		
		this.tris = new Vector<MATri>();
		this.pivotPoint = new MAPivot(other.pivotPoint);
		
		this.childPoints = new Vector<MAPivot>();
		this.children = new Vector<MAMesh>();
		
		for(MATri t : other.tris){
			this.tris.add(new MATri(t));
		}
		
		for(MAPivot v : other.childPoints){
			this.childPoints.add(new MAPivot(v));
		}
		
		for(MAMesh m : childrenToCopy){
			this.children.add(new MAMesh(m));
		}
	}

	// The rig wires the copied joints together after each part has been copied once.
	MAMesh copyRigPart(){
		MAMesh copy = new MAMesh(this, java.util.Collections.emptyList());
		copy.width = width;
		copy.height = height;
		copy.depth = depth;
		copy.centerX = centerX;
		copy.centerY = centerY;
		copy.centerZ = centerZ;
		return copy;
	}
	
	public void draw(){
		for(MATri tri : this.tris){
			tri.draw();
		}

		for(MAMesh child : this.children){
			child.draw();
		}
	}
	
	/** Draw this mesh and its descendants with an offset; stored vertices are unchanged. */
	public void draw(float x, float y, float z){
		for(MATri tri : this.tris){
			tri.draw(x, y, z);
		}
		for(MAMesh child : this.children){
			child.draw(x, y, z);
		}
	}

	/** Draw with per-mesh textures outside rlBegin/rlEnd; textures are never unloaded. */
	public void drawTextured(Raylib.Texture fallback){ drawTextured(fallback, 0f, 0f, 0f); }
	public void drawTextured(Raylib.Texture fallback, float x, float y, float z){
		java.util.Objects.requireNonNull(fallback, "fallback");
		if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
			throw new IllegalArgumentException("Invalid draw offset");
		try { drawTexturedParts(fallback.id(), x, y, z, rlGetVersion() == 1); }
		finally { rlSetTexture(0); }
	}
	private void drawTexturedParts(int fallback, float x, float y, float z, boolean legacy){
		if(!tris.isEmpty()){
			int id = texture == null ? fallback : texture.id();
			rlSetTexture(id);
			rlBegin(RL_TRIANGLES);
			// A new rlgl draw slot can reset mode/texture; GL 1.1 binds only before glBegin.
			if(!legacy) rlSetTexture(id);
			try { for(MATri tri : tris) tri.draw(x, y, z); }
			finally { rlEnd(); }
		}
		for(MAMesh child : children) child.drawTexturedParts(fallback, x, y, z, legacy);
	}

	public MAMesh setColor(int r, int g, int b, int a){
		for(MATri tri : this.tris){
			tri.setColor(r, g, b, a);
		}
		return this;
	}
	
	public int childIndex(){ return this.childIndex; }
	public MAMesh childIndex(int i){ this.childIndex = i; return this; }

	public MAPivot pivotPoint(){ return this.pivotPoint; }
	public MAMesh pivotPoint(MAPivot m){ this.pivotPoint = m; return this; }
	
	public MAPivot childPoint(int i){ return this.childPoints.get(i); }
	public MAMesh childPoint(MAPivot m){ this.childPoints.add(m); return this; }
	public int childPointSize() { return this.childPoints.size(); }
	public MAMesh childPointRemove(int i){ this.childPoints.remove(i); return this; }
	
	
	public MAMesh child(int i){ return this.children.get(i); }
	public MAMesh child(MAMesh c, int i){
		if(i < 0 || i >= this.childPoints.size()) return this;

		MAMesh placed = MAMeshMath.attach(c, this.childPoint(i));

		this.children.add(placed.childIndex(i));
		return this;
	}
	
	public MAMesh attachAsChild(MAMesh c, int i){
		if(i < 0 || i >= this.childPoints.size()) return c;

		MAMesh placed = MAMeshMath.attach(c, this.childPoint(i));

		this.children.add(placed.childIndex(i));
		return placed;
	}
	
	public float scaleX(){ return this.scaleX; }
    public MAMesh scaleX(float f){ this.scaleX = f; return this; }

    public float scaleY(){ return this.scaleY; }
    public MAMesh scaleY(float f){ this.scaleY = f; return this; }

    public float scaleZ(){ return this.scaleZ; }
    public MAMesh scaleZ(float f){ this.scaleZ = f; return this; }

    public float yaw(){ return this.yaw; }
    public MAMesh yaw(float f){ this.yaw = f; return this; }

    public float pitch(){ return this.pitch; }
    public MAMesh pitch(float f){ this.pitch = f; return this; }

    public float roll(){ return this.roll; }
    public MAMesh roll(float f){ this.roll = f; return this; }

    public float qx(){ return this.qx; }
	public MAMesh qx(float f){ this.qx = f; return this; }

	public float qy(){ return this.qy; }
	public MAMesh qy(float f){ this.qy = f; return this; }

	public float qz(){ return this.qz; }
	public MAMesh qz(float f){ this.qz = f; return this; }

	public float qw(){ return this.qw; }
	public MAMesh qw(float f){ this.qw = f; return this; }

    public float x(){ return this.x; }
    public MAMesh x(float f){ this.x = f; return this; }

    public float y(){ return this.y; }
    public MAMesh y(float f){ this.y = f; return this; }

    public float z(){ return this.z; }
    public MAMesh z(float f){ this.z = f; return this; }
	
	public float width(){ return this.width; }
	public MAMesh width(float f){ this.width = f; return this; }

	public float height(){ return this.height; }
	public MAMesh height(float f){ this.height = f; return this; }

	public float depth(){ return this.depth; }
	public MAMesh depth(float f){ this.depth = f; return this; }

	public float centerX(){ return this.centerX; }
	public MAMesh centerX(float f){ this.centerX = f; return this; }

	public float centerY(){ return this.centerY; }
	public MAMesh centerY(float f){ this.centerY = f; return this; }

	public float centerZ(){ return this.centerZ; }
	public MAMesh centerZ(float f){ this.centerZ = f; return this; }
	
	public MAMesh updateBounds(){
		if(this.tris.isEmpty()){
			this.width = this.height = this.depth = 0f;
			this.centerX = this.centerY = this.centerZ = 0f;
			return this;
		}

		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;

		for(MATri tri : this.tris){
			for(MAVert v : tri.verts){
				minX = Math.min(minX, v.x());
				minY = Math.min(minY, v.y());
				minZ = Math.min(minZ, v.z());

				maxX = Math.max(maxX, v.x());
				maxY = Math.max(maxY, v.y());
				maxZ = Math.max(maxZ, v.z());
			}
		}

		this.width = maxX - minX;
		this.height = maxY - minY;
		this.depth = maxZ - minZ;

		this.centerX = (minX + maxX) * 0.5f;
		this.centerY = (minY + maxY) * 0.5f;
		this.centerZ = (minZ + maxZ) * 0.5f;

		return this;
	}
}
