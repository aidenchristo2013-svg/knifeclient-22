package com.example.knifeclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sus Chunks: flags loaded chunks that contain a lot of player-made blocks (storage, furnaces,
 * beds, planks, glass, lanterns and so on) and outlines them through walls.
 *
 * The outline is drawn as 2D lines on the HUD layer from projected 3D corners. That means it
 * always shows through walls and does not depend on any GL render state.
 *
 * It is a heuristic. Villages, mineshafts, strongholds and other generated structures also
 * contain these blocks, so raise "Min Score" if you get too many false positives.
 */
public final class SusChunks {
    private SusChunks() { }

    private record Result(int score, int minY, int maxY) { }
    private record Hit(long key, Result r, double dist) { }

    private static final Map<Long, Result> RESULTS = new HashMap<>();
    private static final ArrayDeque<WorldChunk> QUEUE = new ArrayDeque<>();
    private static final Map<Block, Integer> WEIGHTS = new HashMap<>();
    private static final int MAX_BOXES = 40;

    // ---------------------------------------------------------------- block weights
    private static final String[] HEAVY = {
            "chest", "barrel", "shulker_box", "furnace", "smoker", "hopper", "dropper", "dispenser",
            "crafting_table", "_bed", "enchanting_table", "anvil", "brewing_stand", "beacon", "cauldron"};
    private static final String[] LIGHT = {
            "planks", "bricks", "concrete", "glass", "torch", "lantern", "_door", "sign", "trapdoor",
            "ladder", "rail", "wool", "carpet", "_stairs", "_slab", "fence", "bookshelf", "campfire"};

    private static int weightOf(Block block) {
        return WEIGHTS.computeIfAbsent(block, b -> {
            String path = Registries.BLOCK.getId(b).getPath();
            for (String k : HEAVY) if (path.contains(k)) return 4;
            for (String k : LIGHT) if (path.contains(k)) return 1;
            return 0;
        });
    }

    private static boolean interesting(BlockState s) {
        return weightOf(s.getBlock()) > 0;
    }

    // ---------------------------------------------------------------- scanning
    private static Result scan(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomY();
        int score = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty() || !sec.hasAny(SusChunks::interesting)) continue;
            int baseY = bottom + si * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int w = weightOf(sec.getBlockState(x, y, z).getBlock());
                        if (w > 0) {
                            score += w;
                            int wy = baseY + y;
                            if (wy < minY) minY = wy;
                            if (wy > maxY) maxY = wy;
                        }
                    }
                }
            }
        }
        return new Result(score, minY, maxY);
    }

    private static long key(ChunkPos p) {
        return ChunkPos.toLong(p.x, p.z);
    }

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (Modules.SUSCHUNKS.enabled) QUEUE.add(chunk);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> RESULTS.remove(key(chunk.getPos())));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(SusChunks::tick);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> hud(ctx));
    }

    private static void clear() {
        RESULTS.clear();
        QUEUE.clear();
    }

    private static void tick(MinecraftClient mc) {
        if (!Modules.SUSCHUNKS.enabled || mc.world == null) {
            QUEUE.clear();
            return;
        }
        // a couple of chunks per tick keeps the scan from causing lag spikes
        for (int i = 0; i < 4 && !QUEUE.isEmpty(); i++) {
            WorldChunk c = QUEUE.poll();
            RESULTS.put(key(c.getPos()), scan(c));
        }
    }

    /** Queue every chunk that is currently loaded around the player. */
    public static void rescanAll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        int r = mc.options.getClampedViewDistance();
        ChunkPos c = mc.player.getChunkPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                WorldChunk wc = mc.world.getChunkManager().getWorldChunk(c.x + dx, c.z + dz);
                if (wc != null) QUEUE.add(wc);
            }
        }
    }

    // ---------------------------------------------------------------- drawing
    private static boolean wasOn = false;

    private static void hud(DrawContext ctx) {
        boolean on = Modules.SUSCHUNKS.enabled;
        if (on && !wasOn) rescanAll();
        wasOn = on;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!on || mc.player == null || mc.world == null || mc.options.hudHidden) return;

        int flagged = 0, drawn = 0;
        if (Nametags.ready()) {
            int sw = ctx.getScaledWindowWidth();
            int sh = ctx.getScaledWindowHeight();
            Vec3d pp = mc.player.getPos();
            int range = (int) Modules.SC_RANGE.value;
            int minScore = (int) Modules.SC_MIN.value;
            int color = 0xFF000000 | Modules.SC_COLOR.rgb(0.0);
            int thick = Math.max(1, (int) Math.round(Modules.SC_LINE.value / 2.0));

            List<Hit> hits = new ArrayList<>();
            for (Map.Entry<Long, Result> e : RESULTS.entrySet()) {
                Result r = e.getValue();
                if (r.score() < minScore || r.minY() > r.maxY()) continue;
                int cx = ChunkPos.getPackedX(e.getKey());
                int cz = ChunkPos.getPackedZ(e.getKey());
                double dx = cx * 16 + 8 - pp.x, dz = cz * 16 + 8 - pp.z;
                if (Math.abs(cx - (int) Math.floor(pp.x / 16.0)) > range
                        || Math.abs(cz - (int) Math.floor(pp.z / 16.0)) > range) continue;
                hits.add(new Hit(e.getKey(), r, dx * dx + dz * dz));
            }
            flagged = hits.size();
            hits.sort((a, b) -> Double.compare(a.dist(), b.dist()));

            for (Hit h : hits) {
                if (drawn >= MAX_BOXES) break;
                int cx = ChunkPos.getPackedX(h.key());
                int cz = ChunkPos.getPackedZ(h.key());
                double x1 = cx * 16, x2 = cx * 16 + 16, z1 = cz * 16, z2 = cz * 16 + 16;
                double y1 = h.r().minY(), y2 = h.r().maxY() + 1;
                drawBox(ctx, sw, sh, x1, y1, z1, x2, y2, z2, color, thick);
                drawn++;
                // score label above the box
                float[] p = Nametags.project(new Vec3d((x1 + x2) / 2, y2 + 1.0, (z1 + z2) / 2), sw, sh);
                if (p != null) {
                    String t = "Sus " + h.r().score();
                    ctx.drawText(mc.textRenderer, t, (int) p[0] - mc.textRenderer.getWidth(t) / 2, (int) p[1] - 4, color, true);
                }
            }
        }
        if (Modules.SC_DEBUG.value) {
            ctx.drawText(mc.textRenderer, "[Sus Chunks] matrices=" + Nametags.ready() + " scanned=" + RESULTS.size()
                    + " flagged=" + flagged + " drawn=" + drawn + " queue=" + QUEUE.size(), 4, 16, 0xFFFFFF55, true);
        }
    }

    static void edge(DrawContext ctx, int sw, int sh, double ax, double ay, double az,
                             double bx, double by, double bz, int color, int thick) {
        float[] s = Nametags.segment(new Vec3d(ax, ay, az), new Vec3d(bx, by, bz), sw, sh);
        if (s == null) return;
        float dx = s[2] - s[0], dy = s[3] - s[1];
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f || len > 8000f) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(s[0], s[1], 0f);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        ctx.fill(0, 0, Math.max(1, Math.round(len)), thick, color);
        ctx.getMatrices().pop();
    }

    private static void drawBox(DrawContext ctx, int sw, int sh, double x1, double y1, double z1,
                                double x2, double y2, double z2, int c, int t) {
        edge(ctx, sw, sh, x1, y1, z1, x2, y1, z1, c, t); edge(ctx, sw, sh, x2, y1, z1, x2, y1, z2, c, t);
        edge(ctx, sw, sh, x2, y1, z2, x1, y1, z2, c, t); edge(ctx, sw, sh, x1, y1, z2, x1, y1, z1, c, t);
        edge(ctx, sw, sh, x1, y2, z1, x2, y2, z1, c, t); edge(ctx, sw, sh, x2, y2, z1, x2, y2, z2, c, t);
        edge(ctx, sw, sh, x2, y2, z2, x1, y2, z2, c, t); edge(ctx, sw, sh, x1, y2, z2, x1, y2, z1, c, t);
        edge(ctx, sw, sh, x1, y1, z1, x1, y2, z1, c, t); edge(ctx, sw, sh, x2, y1, z1, x2, y2, z1, c, t);
        edge(ctx, sw, sh, x2, y1, z2, x2, y2, z2, c, t); edge(ctx, sw, sh, x1, y1, z2, x1, y2, z2, c, t);
    }
}
