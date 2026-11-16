package com.example.knifeclient;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nametags: a name / health / gear tag above players (and optionally mobs), visible through walls.
 *
 * It works by remembering the camera matrices at the end of world rendering, projecting each
 * entity's head position to the screen, and drawing the tag with the normal HUD draw calls.
 * That is why it needs no custom render layer and also shows the gear icons.
 */
public final class Nametags {
    private Nametags() { }

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3d cam = Vec3d.ZERO;
    private static boolean have = false;

    public static void init() {
        WorldRenderEvents.END.register(ctx -> {
            VIEW.set(ctx.positionMatrix());
            PROJ.set(ctx.projectionMatrix());
            cam = ctx.camera().getPos();
            have = true;
        });
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> render(ctx, tickCounter.getTickDelta(false)));
    }

    /** World position -> {screenX, screenY} in scaled GUI coordinates, or null when behind the camera. */
    public static float[] project(Vec3d p, int sw, int sh) {
        Vector4f v = toClip(p.x, p.y, p.z);
        if (v.w <= 0.05f) return null;
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        if (nx < -1.3f || nx > 1.3f || ny < -1.3f || ny > 1.3f) return null;
        return new float[]{(nx * 0.5f + 0.5f) * sw, (1f - (ny * 0.5f + 0.5f)) * sh};
    }

    private static Vector4f toClip(double x, double y, double z) {
        Vector4f v = new Vector4f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1f);
        v.mul(VIEW);
        v.mul(PROJ);
        return v;
    }

    /** True once the camera matrices have been captured for this session. */
    public static boolean ready() { return have; }

    /**
     * Projects a 3D line segment to the screen, clipping it against the near plane so lines that
     * pass behind you still draw correctly. Returns {x1, y1, x2, y2} or null if fully behind.
     */
    public static float[] segment(Vec3d a, Vec3d b, int sw, int sh) {
        Vector4f A = toClip(a.x, a.y, a.z);
        Vector4f B = toClip(b.x, b.y, b.z);
        float eps = 0.05f;
        if (A.w < eps && B.w < eps) return null;
        if (A.w < eps) {
            A.lerp(B, (eps - A.w) / (B.w - A.w));
        } else if (B.w < eps) {
            B.lerp(A, (eps - B.w) / (A.w - B.w));
        }
        return new float[]{
                (A.x / A.w * 0.5f + 0.5f) * sw, (1f - (A.y / A.w * 0.5f + 0.5f)) * sh,
                (B.x / B.w * 0.5f + 0.5f) * sw, (1f - (B.y / B.w * 0.5f + 0.5f)) * sh};
    }

    private static void render(DrawContext ctx, float tickProgress) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Modules.NAMETAGS.enabled || mc.world == null || mc.player == null || mc.options.hudHidden) return;
        if (!have) {
            if (Modules.NT_DEBUG.value) ctx.drawText(mc.textRenderer, "[Nametags] waiting for camera matrices (world render event not fired yet)", 4, 4, 0xFFFF5555, true);
            return;
        }
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        double range = Modules.NT_RANGE.value;
        int seen = 0, drawn = 0;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || e instanceof ArmorStandEntity) continue;
            boolean isPlayer = e instanceof PlayerEntity;
            if (!isPlayer && !Modules.NT_MOBS.value) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > range) continue;
            seen++;

            Vec3d head = e.getLerpedPos(tickProgress).add(0.0, e.getHeight() + 0.4, 0.0);
            float[] s = project(head, sw, sh);
            if (s == null) continue;

            float scale = (float) Modules.NT_SCALE.value;
            if (Modules.NT_DISTSCALE.value) {
                scale *= (float) Math.max(0.55, Math.min(1.6, 10.0 / Math.max(dist, 1.0)));
            }
            draw(ctx, mc, le, s[0], s[1], scale);
            drawn++;
        }
        if (Modules.NT_DEBUG.value) {
            ctx.drawText(mc.textRenderer, "[Nametags] matrices=" + have + " in range=" + seen + " drawn=" + drawn,
                    4, 4, 0xFFFFFF55, true);
        }
    }

    private static void draw(DrawContext ctx, MinecraftClient mc, LivingEntity le, float sx, float sy, float scale) {
        TextRenderer tr = mc.textRenderer;
        String name = le.getDisplayName().getString();
        float hp = le.getHealth() + le.getAbsorptionAmount();
        float maxHp = Math.max(1f, le.getMaxHealth());
        String hpText = Modules.NT_HEALTH.value ? String.format(Locale.ROOT, " %.1f", hp) : "";

        int nameW = tr.getWidth(name);
        int hpW = tr.getWidth(hpText);
        int w = nameW + hpW + 8;
        int h = 12;
        int nameColor = 0xFF000000 | Modules.NT_COLOR.rgb(0.0);
        int hpColor = 0xFF000000 | ColorGroup.hsb(Math.max(0f, Math.min(1f, hp / maxHp)) * 0.33f, 0.9f, 1f);

        List<ItemStack> gear = new ArrayList<>();
        if (Modules.NT_ARMOR.value) {
            EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot slot : slots) {
                ItemStack st = le.getEquippedStack(slot);
                if (!st.isEmpty()) gear.add(st);
            }
        }

        ctx.getMatrices().push();
        ctx.getMatrices().translate(sx, sy, 0f);
        ctx.getMatrices().scale(scale, scale, 1f);

        int x = -w / 2;
        int y = -h - 2;
        if (Modules.NT_BG.value) ctx.fill(x, y, x + w, y + h, 0x90000000);
        ctx.drawText(tr, name, x + 4, y + 2, nameColor, true);
        if (!hpText.isEmpty()) ctx.drawText(tr, hpText, x + 4 + nameW, y + 2, hpColor, true);
        // health bar along the bottom of the tag
        float frac = Math.max(0f, Math.min(1f, hp / maxHp));
        ctx.fill(x, y + h - 1, x + w, y + h, 0x80000000);
        ctx.fill(x, y + h - 1, x + (int) (w * frac), y + h, hpColor);

        if (!gear.isEmpty()) {
            int gw = gear.size() * 17;
            int gx = -gw / 2;
            int gy = y - 19;
            if (Modules.NT_BG.value) ctx.fill(gx - 1, gy - 1, gx + gw, gy + 17, 0x60000000);
            for (int i = 0; i < gear.size(); i++) {
                ctx.drawItem(gear.get(i), gx + i * 17, gy);
            }
        }
        ctx.getMatrices().pop();
    }
}
