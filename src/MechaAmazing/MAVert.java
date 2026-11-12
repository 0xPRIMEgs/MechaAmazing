package MechaAmazing;

public class MAVert{
	byte r = (byte) 255;
	byte g = (byte) 255;
	byte b = (byte) 255;
	byte a = (byte) 255;
	
	float x = 0.0f;
	float y = 0.0f;
	float z = 0.0f;
	
	float u = 0.0f;
	float v = 0.0f;
	
	public MAVert(){}
	
	public MAVert(MAVert other){
		this.r = other.r;
		this.g = other.g;
		this.b = other.b;
		this.a = other.a;

		this.x = other.x;
		this.y = other.y;
		this.z = other.z;

		this.u = other.u;
		this.v = other.v;
	}
	
	public byte r(){ return this.r; }
	public MAVert r(int i){ this.r = (byte) i; return this; }
	
	public byte g(){ return this.g; }
	public MAVert g(int i){ this.g = (byte) i; return this; }

	public byte b(){ return this.b; }
	public MAVert b(int i){ this.b = (byte) i; return this; }

	public byte a(){ return this.a; }
	public MAVert a(int i){ this.a = (byte) i; return this; }
	
	public float x(){ return this.x; }
	public MAVert x(float f){ this.x = f; return this; }
	
	public float y(){ return this.y; }
	public MAVert y(float f){ this.y = f; return this; }

	public float z(){ return this.z; }
	public MAVert z(float f){ this.z = f; return this; }

	public float u(){ return this.u; }
	public MAVert u(float f){ this.u = f; return this; }

	public float v(){ return this.v; }
	public MAVert v(float f){ this.v = f; return this; }
}