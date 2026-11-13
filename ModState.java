package com.example.knifeclient;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** Tracks which model parts to hide/tint/scale while the local player is being drawn. */
public final class ModState {
    public static boolean nextIsSelf = false;
    public static boolean renderingSelf = false;
    public static int tintColor = 0xFFFFFFFF;

    private static ModelPart head, hat, rightLeg, rightPants, tintLeg, tintPants;
    private static PlayerEntityModel scaledModel;

    public static void begin(PlayerEntityModel model) {
        end();
        renderingSelf = nextIsSelf;
        if (!renderingSelf) return;

        if (Modules.HEADLESS.enabled) {
            head = model.head;
            hat = Modules.HEADLESS_HAT.value ? model.hat : null;
        } else if (Modules.HEADSIZE.enabled) {
            float s = (float) Modules.HEAD_SIZE.value;
            setScale(model.head, s);
            setScale(model.hat, s);
            scaledModel = model;
        }

        if (Modules.KORBLOX.enabled) {
            if (Modules.KORBLOX_MODE.index == 0) {
                rightLeg = model.rightLeg;
                rightPants = model.rightPants;
            } else {
                tintLeg = model.rightLeg;
                tintPants = model.rightPants;
                tintColor = 0xFF000000 | Modules.KORBLOX_COLOR.rgb(0.0);
            }
        }
    }

    public static void end() {
        renderingSelf = false;
        head = hat = rightLeg = rightPants = tintLeg = tintPants = null;
        if (scaledModel != null) {
            setScale(scaledModel.head, 1f);
            setScale(scaledModel.hat, 1f);
            scaledModel = null;
        }
    }

    private static void setScale(ModelPart p, float s) {
        p.xScale = s;
        p.yScale = s;
        p.zScale = s;
    }

    public static boolean isHidden(ModelPart part) {
        return part == head || part == hat || part == rightLeg || part == rightPants;
    }

    public static boolean isTinted(ModelPart part) {
        return part == tintLeg || part == tintPants;
    }
}
