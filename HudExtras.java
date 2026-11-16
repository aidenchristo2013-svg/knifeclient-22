package com.example.knifeclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Drawing for the extra HUD modules and the 2D overlays (crosshair, tracers, waypoints, chunk borders). */
final class HudExtras {
    private HudExtras() { }

    private record Line(String text, int rgb) { }

    private static final String[] COMPASS_NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    static void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        float td = mc.getRenderTickCounter().getTickDelta(false);

        // ---- overlays
        if (Features.VIGNETTE.enabled) vignette(ctx, mc, sw, sh);
        if (Nametags.ready()) {
            if (Features.CHUNKB.enabled) chunkBorders(ctx, mc, sw, sh);
            if (Features.TRACERS.enabled) tracers(ctx, mc, sw, sh, td);
            if (Features.WAYPOINTS.enabled) waypoints(ctx, mc, sw, sh);
        }
        if (Features.CROSS.enabled) crosshair(ctx, sw, sh);

        // ---- HUD boxes
        if (Features.POTIONS.enabled) potions(ctx, mc);
        if (Features.SPEED.enabled) {
            double sp = Math.hypot(mc.player.getX() - mc.player.prevX, mc.player.getZ() - mc.player.prevZ) * 20.0;
            Hud.textBox(ctx, mc, Features.SPEED_STYLE, String.format(Locale.ROOT, "Speed: %.1f b/s", sp));
        }
        if (Features.BIOME.enabled) {
            String b = mc.world.getBiome(mc.player.getBlockPos()).getKey()
                    .map(k -> pretty(k.getValue().getPath())).orElse("Unknown");
            Hud.textBox(ctx, mc, Features.BIOME_STYLE, "Biome: " + b);
        }
        if (Features.WTIME.enabled) {
            long t = mc.world.getTimeOfDay();
            long day = t / 24000L + 1;
            long tod = t % 24000L;
            int hour = (int) ((tod / 1000L + 6L) % 24L);
            int min = (int) ((tod % 1000L) * 60L / 1000L);
            Hud.textBox(ctx, mc, Features.WTIME_STYLE, String.format(Locale.ROOT, "Day %d  %02d:%02d", day, hour, min));
        }
        if (Features.MEMORY.enabled) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
            long max = rt.maxMemory() / 1048576L;
            Hud.textBox(ctx, mc, Features.MEM_STYLE, "Mem: " + used + " / " + max + " MB");
        }
        if (Features.SERVER.enabled) {
            String addr = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address
                    : (mc.isInSingleplayer() ? "Singleplayer" : "Unknown");
            String t = addr;
            if (mc.getNetworkHandler() != null) t += "  (" + mc.getNetworkHandler().getPlayerList().size() + " online)";
            Hud.textBox(ctx, mc, Features.SERVER_STYLE, t);
        }
        if (Features.ITEMS.enabled) itemCount(ctx, mc);
        if (Features.INVHUD.enabled) inventory(ctx, mc);
        if (Features.COMPASS.enabled) compass(ctx, mc);
        if (Features.RADAR.enabled) radar(ctx, mc);
        if (Features.SESSION.enabled) {
            long s = (System.currentTimeMillis() - FeatureTick.sessionStart) / 1000L;
            Hud.textBox(ctx, mc, Features.SESSION_STYLE,
                    String.format(Locale.ROOT, "Session: %02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        }
    }

    // ------------------------------------------------------------------ helpers
    private static String pretty(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String mmss(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static void line2d(DrawContext ctx, float x1, float y1, float x2, float y2, int color, int thick) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f || len > 8000f) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x1, y1, 0f);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        ctx.fill(0, 0, Math.max(1, Math.round(len)), thick, color);
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------------ overlays
    private static void vignette(DrawContext ctx, MinecraftClient mc, int sw, int sh) {
        float hp = mc.player.getHealth();
        float thr = (float) Features.VG_HP.value;
        if (hp > thr || hp <= 0f) return;
        float k = (thr - hp) / thr;
        double pulse = 0.7 + 0.3 * Math.sin(System.currentTimeMillis() / 180.0);
        int a = (int) Math.max(0, Math.min(255, 255.0 * Features.VG_INT.value * (0.35 + 0.65 * k) * pulse));
        int solid = (a << 24) | 0xCC0000;
        int clear = 0x00CC0000;
        int bandY = sh / 5;
        ctx.fillGradient(0, 0, sw, bandY, solid, clear);
        ctx.fillGradient(0, sh - bandY, sw, sh, clear, solid);
        int steps = 10;
        int stepW = Math.max(1, sw / 8 / steps);
        for (int i = 0; i < steps; i++) {
            int al = a * (steps - i) / steps;
            int col = (al << 24) | 0xCC0000;
            ctx.fill(i * stepW, 0, (i + 1) * stepW, sh, col);
            ctx.fill(sw - (i + 1) * stepW, 0, sw - i * stepW, sh, col);
        }
    }

    private static void crosshair(DrawContext ctx, int sw, int sh) {
        int cx = sw / 2, cy = sh / 2;
        int size = (int) Features.CH_SIZE.value;
        int t = (int) Features.CH_THICK.value;
        int gap = (int) Features.CH_GAP.value;
        int h = t / 2;
        int col = 0xFF000000 | Features.CH_COLOR.rgb(0.0);
        switch (Features.CH_STYLE.index) {
            case 0 -> {
                ctx.fill(cx - size, cy - h, cx + size + 1, cy - h + t, col);
                ctx.fill(cx - h, cy - size, cx - h + t, cy + size + 1, col);
            }
            case 1 -> {
                ctx.fill(cx - size - gap, cy - h, cx - gap, cy - h + t, col);
                ctx.fill(cx + gap + 1, cy - h, cx + size + gap + 1, cy - h + t, col);
                ctx.fill(cx - h, cy - size - gap, cx - h + t, cy - gap, col);
                ctx.fill(cx - h, cy + gap + 1, cx - h + t, cy + size + gap + 1, col);
            }
            case 2 -> {
                int r = Math.max(1, size / 3);
                ctx.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, col);
            }
            default -> {
                for (int i = 0; i < 40; i++) {
                    double ang = i * Math.PI * 2.0 / 40.0;
                    int x = cx + (int) Math.round(Math.cos(ang) * size);
                    int y = cy + (int) Math.round(Math.sin(ang) * size);
                    ctx.fill(x, y, x + t, y + t, col);
                }
            }
        }
    }

    private static void chunkBorders(DrawContext ctx, MinecraftClient mc, int sw, int sh) {
        Vec3d p = mc.player.getPos();
        int r = (int) Features.CB_RANGE.value;
        double hgt = Features.CB_HEIGHT.value;
        int col = 0xFF000000 | Features.CB_COLOR.rgb(0.0);
        int cx = (int) Math.floor(p.x / 16.0);
        int cz = (int) Math.floor(p.z / 16.0);
        for (int i = cx - r; i <= cx + r + 1; i++) {
            for (int j = cz - r; j <= cz + r + 1; j++) {
                SusChunks.edge(ctx, sw, sh, i * 16, p.y - hgt, j * 16, i * 16, p.y + hgt, j * 16, col, 1);
            }
        }
        double y = Math.floor(p.y);
        double x1 = cx * 16, x2 = x1 + 16, z1 = cz * 16, z2 = z1 + 16;
        SusChunks.edge(ctx, sw, sh, x1, y, z1, x2, y, z1, col, 1);
        SusChunks.edge(ctx, sw, sh, x2, y, z1, x2, y, z2, col, 1);
        SusChunks.edge(ctx, sw, sh, x2, y, z2, x1, y, z2, col, 1);
        SusChunks.edge(ctx, sw, sh, x1, y, z2, x1, y, z1, col, 1);
    }

    private static void tracers(DrawContext ctx, MinecraftClient mc, int sw, int sh, float td) {
        double range = Features.TR_RANGE.value;
        int thick = (int) Features.TR_THICK.value;
        int col = 0xFF000000 | Features.TR_COLOR.rgb(0.0);
        int mode = Features.TR_TARGET.index;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity) || e == mc.player || e instanceof ArmorStandEntity) continue;
            boolean isPlayer = e instanceof PlayerEntity;
            if (mode == 0 && !isPlayer) continue;
            if (mode == 1 && isPlayer) continue;
            if (mc.player.distanceTo(e) > range) continue;
            Vec3d pos = e.getLerpedPos(td).add(0.0, e.getHeight() / 2.0, 0.0);
            float[] pt = Nametags.project(pos, sw, sh);
            if (pt == null) continue;
            line2d(ctx, sw / 2f, sh, pt[0], pt[1], col, thick);
        }
    }

    private static void waypoints(DrawContext ctx, MinecraftClient mc, int sw, int sh) {
        TextRenderer tr = mc.textRenderer;
        String dim = mc.world.getRegistryKey().getValue().toString();
        int col = 0xFF000000 | Features.WP_COLOR.rgb(0.0);
        Vec3d pp = mc.player.getPos();
        for (FeatureTick.Waypoint w : FeatureTick.WAYPOINTS) {
            if (!w.dim().equals(dim)) continue;
            Vec3d at = new Vec3d(w.x(), w.y(), w.z());
            double d = pp.distanceTo(at);
            if (d < 2.0) continue;
            float[] p = Nametags.project(at.add(0.0, 1.0, 0.0), sw, sh);
            if (p == null) continue;
            String t = w.name() + "  " + Math.round(d) + "m";
            int tw = tr.getWidth(t);
            int x = (int) p[0], y = (int) p[1];
            ctx.fill(x - tw / 2 - 3, y - 6, x + tw / 2 + 3, y + 6, 0x90000000);
            ctx.drawText(tr, t, x - tw / 2, y - 4, col, true);
            ctx.fill(x - 1, y + 7, x + 2, y + 10, col);
        }
    }

    // ------------------------------------------------------------------ HUD boxes
    private static void potions(DrawContext ctx, MinecraftClient mc) {
        List<Line> ls = new ArrayList<>();
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String name = e.getEffectType().value().getName().getString();
            String amp = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
            String time = e.isInfinite() ? "**:**" : mmss(e.getDuration() / 20);
            ls.add(new Line(name + amp + "  " + time, e.getEffectType().value().getColor()));
        }
        if (ls.isEmpty()) return;
        HudStyle st = Features.POT_STYLE;
        TextRenderer tr = mc.textRenderer;
        int w = 0;
        for (Line l : ls) w = Math.max(w, tr.getWidth(l.text()));
        w += 8;
        int h = ls.size() * 10 + 4;
        Hud.begin(ctx, st, w, h);
        if (st.bg.value) ctx.fill(0, 0, w, h, 0x90000000);
        int y = 3;
        for (Line l : ls) {
            ctx.drawText(tr, l.text(), 4, y, 0xFF000000 | l.rgb(), true);
            y += 10;
        }
        Hud.end(ctx);
    }

    private static void itemCount(DrawContext ctx, MinecraftClient mc) {
        Item[] items = {Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.GOLDEN_APPLE,
                Items.ENDER_PEARL, Items.ARROW, Items.EXPERIENCE_BOTTLE};
        Item it = items[Math.floorMod(Features.ITEM_KIND.index, items.length)];
        int count = mc.player.getInventory().count(it);
        String t = "x" + count;
        HudStyle st = Features.ITEMS_STYLE;
        int w = 24 + mc.textRenderer.getWidth(t) + 4;
        int h = 20;
        Hud.begin(ctx, st, w, h);
        if (st.bg.value) ctx.fill(0, 0, w, h, 0x90000000);
        ctx.drawItem(new ItemStack(it), 3, 2);
        ctx.drawText(mc.textRenderer, t, 24, 6, 0xFF000000 | st.color.rgb(0.0), true);
        Hud.end(ctx);
    }

    private static void inventory(DrawContext ctx, MinecraftClient mc) {
        HudStyle st = Features.INV_STYLE;
        int w = 9 * 18 + 4;
        int h = 3 * 18 + 4;
        Hud.begin(ctx, st, w, h);
        if (st.bg.value) ctx.fill(0, 0, w, h, 0x90000000);
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            int x = 2 + ((i - 9) % 9) * 18;
            int y = 2 + ((i - 9) / 9) * 18;
            ctx.drawItem(s, x, y);
            ctx.drawStackOverlay(mc.textRenderer, s, x, y);
        }
        Hud.end(ctx);
    }

    private static void compass(DrawContext ctx, MinecraftClient mc) {
        HudStyle st = Features.COMPASS_STYLE;
        TextRenderer tr = mc.textRenderer;
        int w = (int) Features.COMPASS_W.value;
        int h = 16;
        float heading = MathHelper.wrapDegrees(mc.player.getYaw() + 180f);
        Hud.begin(ctx, st, w, h);
        if (st.bg.value) ctx.fill(0, 0, w, h, 0x90000000);
        int rgb = 0xFF000000 | st.color.rgb(0.0);
        float pxPerDeg = w / 180f;
        for (int i = 0; i < COMPASS_NAMES.length; i++) {
            float delta = MathHelper.wrapDegrees(i * 45f - heading);
            float x = w / 2f + delta * pxPerDeg;
            if (Math.abs(x - w / 2f) > w / 2f - 8f) continue;
            String n = COMPASS_NAMES[i];
            int c = i == 0 ? 0xFFFF5555 : rgb;
            ctx.drawText(tr, n, (int) x - tr.getWidth(n) / 2, 4, c, true);
        }
        ctx.fill(w / 2, 0, w / 2 + 1, 3, 0xFFFFFFFF);
        Hud.end(ctx);
    }

    private static void radar(DrawContext ctx, MinecraftClient mc) {
        HudStyle st = Features.RADAR_STYLE;
        int size = (int) Features.RADAR_SIZE.value;
        int half = size / 2;
        double range = Features.RADAR_RANGE.value;
        double scale = (half - 3.0) / range;
        double yaw = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        boolean mobs = Features.RADAR_MOBS.value;
        Hud.begin(ctx, st, size, size);
        ctx.fill(0, 0, size, size, st.bg.value ? 0x90000000 : 0x40000000);
        int rgb = 0xFF000000 | st.color.rgb(0.0);
        ctx.fill(half, 0, half + 1, size, 0x30FFFFFF);
        ctx.fill(0, half, size, half + 1, 0x30FFFFFF);
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            boolean isPlayer = e instanceof PlayerEntity;
            boolean isMob = e instanceof LivingEntity && !isPlayer && !(e instanceof ArmorStandEntity);
            if (!isPlayer && !(mobs && isMob)) continue;
            double dx = e.getX() - mc.player.getX();
            double dz = e.getZ() - mc.player.getZ();
            double fwd = dx * -sin + dz * cos;
            double right = dx * -cos + dz * -sin;
            int x = half + (int) Math.round(right * scale);
            int y = half + (int) Math.round(-fwd * scale);
            x = Math.max(2, Math.min(size - 3, x));
            y = Math.max(2, Math.min(size - 3, y));
            int c = isPlayer ? 0xFFFF4040 : 0xFFFFD040;
            ctx.fill(x - 1, y - 1, x + 2, y + 2, c);
        }
        ctx.fill(half - 1, half - 1, half + 2, half + 2, rgb);
        Hud.end(ctx);
    }
}
