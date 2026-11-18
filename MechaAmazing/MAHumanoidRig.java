package MechaAmazing;

import java.util.Map;

/** Seventeen semantic body slots; connector indices can be customized.
 * Fields are refreshed by update(), setPart(), replace(), and setParts().
 * Treat them as read-only current views. Do not assign meshes to these fields;
 * use setPart/replace so rest data, hierarchy and animation stay consistent.
 * For a reference retained across frames, keep joint("leftHand"), then mesh().
 * Inherits draw(float x,float y,float z) from MARig for draw-only offsets.
 */
public class MAHumanoidRig extends MARig {
    public MAMesh groin, abs, chest, neck, head, leftUpperArm, leftLowerArm, leftHand, rightUpperArm, rightLowerArm, rightHand, leftThigh, leftCalf, leftFoot, rightThigh, rightCalf, rightFoot;
    public MAHumanoidRig(){
        super(1);
        define("groin",null,-1);
        define("abs","groin",0);
        define("chest","abs",0);
        define("neck","chest",0);
        define("head","neck",0);
        define("leftUpperArm","chest",1);
        define("leftLowerArm","leftUpperArm",0);
        define("leftHand","leftLowerArm",0);
        define("rightUpperArm","chest",2);
        define("rightLowerArm","rightUpperArm",0);
        define("rightHand","rightLowerArm",0);
        define("leftThigh","groin",1);
        define("leftCalf","leftThigh",0);
        define("leftFoot","leftCalf",0);
        define("rightThigh","groin",2);
        define("rightCalf","rightThigh",0);
        define("rightFoot","rightCalf",0);
    }
    public MAHumanoidRig(Map<String,MAMesh> parts){this();setParts(parts);}
    private MAHumanoidRig(MAHumanoidRig source) {
        super(source);
        update();
    }

    /** Copy the loaded body and pose in memory. Each copy starts with empty attachment slots,
     * borrows the same textures and allocates its own renderer buffers on first draw. */
    public MAHumanoidRig copy() {
        return new MAHumanoidRig(this);
    }
    @Override public MAHumanoidRig setColor(int r,int g,int b,int a){super.setColor(r,g,b,a);return this;}
    @Override public MAHumanoidRig setColor(String name,int r,int g,int b,int a){super.setColor(name,r,g,b,a);return this;}
    @Override public MAHumanoidRig setTexture(com.raylib.Raylib.Texture texture){super.setTexture(texture);return this;}
    @Override public MAHumanoidRig setTexture(String name,com.raylib.Raylib.Texture texture){super.setTexture(name,texture);return this;}
    @Override protected void onUpdated(){
        groin=mesh("groin");
        abs=mesh("abs");
        chest=mesh("chest");
        neck=mesh("neck");
        head=mesh("head");
        leftUpperArm=mesh("leftUpperArm");
        leftLowerArm=mesh("leftLowerArm");
        leftHand=mesh("leftHand");
        rightUpperArm=mesh("rightUpperArm");
        rightLowerArm=mesh("rightLowerArm");
        rightHand=mesh("rightHand");
        leftThigh=mesh("leftThigh");
        leftCalf=mesh("leftCalf");
        leftFoot=mesh("leftFoot");
        rightThigh=mesh("rightThigh");
        rightCalf=mesh("rightCalf");
        rightFoot=mesh("rightFoot");
    }
}
