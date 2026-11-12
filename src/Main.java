import MechaAmazing.*;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;
import com.raylib.Raylib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

public class Main {
    private static final String ASSETS = "assets/";
	static float DEADZONE = 0.2f;
	static float px = 0f;

    private static MAMesh loadPart(String name) throws IOException {
        return MAIO.loadMAMesh(ASSETS + name + ".mamesh");
    }

    // Assumes the authored limb is the LEFT side.
    private static void addPair(
            Map<String, MAMesh> parts,
            String leftSlot, String rightSlot, String filename
    ) throws IOException {
        MAMesh left = loadPart(filename);
        parts.put(leftSlot, left);
        parts.put(rightSlot, MAMeshMath.mirrorX(left));
    }

    private static MAHumanoidRig loadHumanoid() throws IOException {
        Map<String, MAMesh> parts = new LinkedHashMap<>();

        parts.put("groin", loadPart("partsv2/groin"));
        parts.put("abs", loadPart("partsv2/abs"));
        parts.put("chest", loadPart("partsv2/chest"));
        parts.put("head", loadPart("partsv2/head"));
		parts.put("neck", loadPart("partsv2/neck"));
		
        addPair(parts, "leftUpperArm", "rightUpperArm", "partsv2/arm_upper");
        addPair(parts, "leftLowerArm", "rightLowerArm", "partsv2/arm_lower");
        addPair(parts, "leftHand", "rightHand", "partsv2/hand");

        addPair(parts, "leftThigh", "rightThigh", "partsv2/leg_thigh");
        addPair(parts, "leftCalf", "rightCalf", "partsv2/leg_calf");
        addPair(parts, "leftFoot", "rightFoot", "partsv2/foot");

        return new MAHumanoidRig(parts);
    }

    public static void main(String[] args) throws IOException {
        // Load CPU-side rig data before opening the window.
        MAHumanoidRig humanoid = loadHumanoid();

		MAAnimation curAnim = MAIO.loadAnimation(ASSETS + "humanoid_combat_idle.marig.humanoid.anim");

        MAAnimation flex = MAIO.loadAnimation(ASSETS + "humanoid_flex.marig.humanoid.anim");
        MAPose flexPose = MAIO.loadPose(ASSETS + "humanoid_flex.marig.humanoid.pose");
		
		MAAnimation combat_idle = MAIO.loadAnimation(ASSETS + "humanoid_combat_idle.marig.humanoid.anim");
		MAAnimation combat_jab = MAIO.loadAnimation(ASSETS + "humanoid_combat_punch.marig.humanoid.anim");
		MAAnimation combat_move = MAIO.loadAnimation(ASSETS + "humanoid_combat_move.marig.humanoid.anim");

        flex.validate(humanoid);
        flexPose.validate(humanoid);
		combat_idle.validate(humanoid);
		combat_jab.validate(humanoid);
		
        humanoid.position(0, 0, 0).update();

        InitWindow(640, 360, "Mecha Amazing | By William Starkovich | Jesus is Lord | v0.1");
        SetTargetFPS(60);

        Raylib.Camera3D camera = new Raylib.Camera3D()._position(new Raylib.Vector3().x(0).y(0).z(100)).target(new Raylib.Vector3().x(0).y(0).z(0)).up(new Raylib.Vector3().x(0).y(1).z(0)).fovy(60.0f).projection(CAMERA_PERSPECTIVE);

        Texture tex = LoadTexture(ASSETS + "tex2.png");

        MAGpuRig gpu=null;
        try {
            if(rlGetVersion()!=1)gpu=new MAGpuRig(humanoid,tex);
        }catch(RuntimeException|LinkageError e){System.err.println("GPU buffers unavailable; using CPU draw: "+e.getMessage());}
        boolean useGpu=gpu!=null,playing=false,uncapped=false;
        double animationTime=0,updateMs=0,submitMs=0;
        float robotYaw=0;
        try {
            while(!WindowShouldClose()){
                float dt=GetFrameTime();
                if(IsKeyPressed(KEY_F1)){uncapped=!uncapped;SetTargetFPS(uncapped?0:60);}
                if(IsKeyPressed(KEY_F2)&&gpu!=null)useGpu=!useGpu;
                long updateStart=System.nanoTime();
                robotYaw+=0.5f*dt;
                humanoid.orientation(0,robotYaw,0); // Queue world rotation; do not rebuild yet.
                boolean pad = IsGamepadAvailable(0);
				boolean moving = pad && Math.abs(GetGamepadAxisMovement(0, GAMEPAD_AXIS_LEFT_X)) > DEADZONE;
				
				if(moving){
					px += 60 * dt;
				}
				
				// Let flex and jab finish before returning to movement/idle.
				boolean oneShot = playing && (curAnim == flex || curAnim == combat_jab);

				if (!oneShot && (playing || moving)) {
					MAAnimation nextAnim = moving ? combat_move : combat_idle;

					// Reset only when changing animation or resuming playback.
					if (curAnim != nextAnim || !playing) {
						curAnim = nextAnim;
						animationTime = 0;
					}

					playing = true;
				}

				if (playing) {
					animationTime += dt;
					double duration = curAnim.duration();

					if (animationTime >= duration) {
						if (curAnim == flex || curAnim == combat_jab) {
							// One-shot finished: return to movement or idle.
							curAnim = moving ? combat_move : combat_idle;
							animationTime = 0;
						} else {
							// Loop movement/idle, preserving leftover frame time.
							animationTime = duration > 0 ? animationTime % duration : 0;
						}
					}

					curAnim.queue(humanoid, animationTime);
				}
                if(IsKeyPressed(KEY_SPACE)||(pad&&IsGamepadButtonPressed(0,GAMEPAD_BUTTON_RIGHT_FACE_DOWN))){
                    animationTime=0;playing=true;curAnim = flex;curAnim.queue(humanoid,0);
                }
				
				
				/*if(IsKeyPressed(KEY_SPACE)||(pad&&IsGamepadButtonPressed(0,GAMEPAD_BUTTON_RIGHT_FACE_LEFT))){
                    animationTime=0;playing=true;curAnim = combat_idle; curAnim.queue(humanoid,0);
                }*/
				
				if(IsKeyPressed(KEY_SPACE)||(pad&&IsGamepadButtonPressed(0,GAMEPAD_BUTTON_RIGHT_FACE_UP))){
                    animationTime=0;playing=true;curAnim = combat_jab; curAnim.queue(humanoid,0);
                }
				
				
                if(IsKeyPressed(KEY_B)||(pad&&IsGamepadButtonPressed(0,GAMEPAD_BUTTON_RIGHT_FACE_RIGHT))){
                    playing=false;flexPose.queue(humanoid);
                }
                humanoid.update(); // Exactly one pose update after all input and animation changes.
                updateMs=updateMs*.9+(System.nanoTime()-updateStart)/1e6*.1;
                BeginDrawing();ClearBackground(DARKGRAY);BeginMode3D(camera);
                long submitStart=System.nanoTime();
                if(useGpu){
					for (int i = 0; i < 6; i++) {
						gpu.draw(px + (i - 1) * 50f, 0, 0);
					}
				}
                else{
					rlSetTexture(tex.id());rlBegin(RL_TRIANGLES);
					for (int i = 0; i < 6; i++) {
						humanoid.draw(px + (i - 1) * 50f, 0, 0);
					}
					rlEnd();rlSetTexture(0);
                }
                EndMode3D();
                submitMs=submitMs*.9+(System.nanoTime()-submitStart)/1e6*.1;
                DrawText("A/Space: Play flex   B: Hold pose",10,10,16,WHITE);
                DrawFPS(10,32);
                DrawText(String.format(java.util.Locale.ROOT,"%s | update %.2f ms | submit %.2f ms",useGpu?"GPU buffers":"CPU draw",updateMs,submitMs),10,54,14,WHITE);
                DrawText("F1: "+(uncapped?"uncapped":"60 FPS cap")+"   F2: compare CPU/GPU",10,74,14,WHITE);
                EndDrawing();
            }
        }finally{
            if(gpu!=null)gpu.close();UnloadTexture(tex);CloseWindow();
        }
    }
}
