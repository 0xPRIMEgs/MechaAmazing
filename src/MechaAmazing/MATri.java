package MechaAmazing;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;
import com.raylib.Raylib;

public class MATri{
	MAVert[] verts = new MAVert[3];
	
	public MATri(MAVert a, MAVert b, MAVert c){
		verts[0] = a;
		verts[1] = b;
		verts[2] = c;
	}
	
	public MATri(MATri other){
		for(int i = 0; i < 3; i++){
			this.verts[i] = new MAVert(other.verts[i]);
		}
	}
	
	public void draw(){
		for(int i = 0; i < verts.length; i++){
			rlTexCoord2f(verts[i].u(), verts[i].v());
			rlColor4ub(verts[i].r(), verts[i].g(), verts[i].b(), verts[i].a());
			rlVertex3f(verts[i].x(), verts[i].y(), verts[i].z());
		}
	}
	
	/** Submit translated vertex positions without modifying the triangle. */
	public void draw(float x, float y, float z){
		for(int i = 0; i < verts.length; i++){
			rlTexCoord2f(verts[i].u(), verts[i].v());
			rlColor4ub(verts[i].r(), verts[i].g(), verts[i].b(), verts[i].a());
			rlVertex3f(verts[i].x() + x, verts[i].y() + y, verts[i].z() + z);
		}
	}

	public MATri setColor(int r, int g, int b, int a){
		for(int i = 0; i < verts.length; i++){
			verts[i].r = (byte) r;
			verts[i].g = (byte) g;
			verts[i].b = (byte) b;
			verts[i].a = (byte) a;
		}
		
		return this;
	}
}
