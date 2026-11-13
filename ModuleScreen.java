package com.example.knifeclient;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Animated menu. Left click a card = toggle, right click = settings. */
public class ModuleScreen extends Screen {
    private record Card(ClientModule m, int x, int y, int w, int h) { }
    private record Hit(Setting s, int x, int y, int w, int h) { }

    private final long openTime = System.currentTimeMillis();
    private long lastFrame = openTime;
    private float dt = 0.016f;
    private final Map<Object, Float> anims = new HashMap<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private ClientModule selected;
    private long selectedTime;
    private int scroll;
    private int maxScroll;
    private Hit dragging;
    private int px, py, pw, ph;
    private int closeX, resetX, resetW;

    public ModuleScreen() {
        super(Text.literal("Knife Client"));
    }

    // ------------------------------------------------------------ helpers
    private static int argb(int a, int rgb) {
        return (Math.max(8, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t);
        int g = (int) (((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t);
        int b = (int) ((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float anim(Object key, float target, float init) {
        float cur = anims.getOrDefault(key, init);
        if (!Modules.UI_ANIM.value) {
            cur = target;
        } else {
            cur += (target - cur) * Math.min(1f, dt * 16f * (float) Modules.UI_SPEED.value);
        }
        anims.put(key, cur);
        return cur;
    }

    private float intro(long now, int index) {
        if (!Modules.UI_ANIM.value) return 1f;
        return ease((now - openTime - index * 28L) / (340f / (float) Modules.UI_SPEED.value));
    }

    private static int rowH(Setting s) {
        return (s instanceof Setting.Num) ? 28 : 18;
    }

    // ------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        TextRenderer tr = this.textRenderer;
        int accent = Modules.UI_ACCENT.rgb(0.0);
        float intro0 = intro(now, 0);

        ctx.fill(0, 0, width, height, argb((int) (Modules.UI_DIM.value * 255 * intro0), 0x000000));

        int n = Modules.ALL.size();
        int cols = Math.max(2, Math.min(4, (width - 24) / 116));
        int cw = 110, ch = 30, gap = 6;
        int rows = (n + cols - 1) / cols;
        int gridW = cols * cw + (cols - 1) * gap;
        int gridH = rows * ch + (rows - 1) * gap;
        int left = (width - gridW) / 2;
        int top = Math.max(46, (height - gridH) / 2 + 10);

        // title
        String title = "KNIFE CLIENT";
        int tw = tr.getWidth(title);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, top - 38f - (1f - intro0) * 10f, 0f);
        ctx.getMatrices().scale(2f, 2f, 1f);
        ctx.drawText(tr, title, -tw / 2, 0, argb((int) (255 * intro0), accent), true);
        ctx.getMatrices().pop();
        int lineW = (int) (tw * 2 * intro0);
        ctx.fill(width / 2 - lineW / 2, top - 18, width / 2 + lineW / 2, top - 17, argb((int) (255 * intro0), accent));

        // cards
        cards.clear();
        for (int i = 0; i < n; i++) {
            ClientModule m = Modules.ALL.get(i);
            int cx = left + (i % cols) * (cw + gap);
            int cy = top + (i / cols) * (ch + gap);
            float in = intro(now, i + 1);
            int yy = cy + (int) ((1f - in) * 16f);
            int a = (int) (255 * in);
            cards.add(new Card(m, cx, cy, cw, ch));

            boolean hovered = selected == null && inside(mx, my, cx, cy, cw, ch);
            float hov = anim(m.id + ":h", hovered ? 1f : 0f, 0f);
            float on = anim(m.id + ":t", m.enabled ? 1f : 0f, m.enabled ? 1f : 0f);

            ctx.fill(cx, yy, cx + cw, yy + ch, argb((int) (a * 0.88f), mix(0x12141B, 0x20253A, hov)));
            ctx.fill(cx, yy, cx + 2, yy + ch, argb(a, mix(0x444A58, accent, on)));
            if (hov > 0.02f) ctx.drawBorder(cx, yy, cw, ch, argb((int) (a * hov * 0.8f), accent));

            ctx.drawText(tr, m.name, cx + 8, yy + 5, argb(a, 0xFFFFFF), false);
            String status = m.toggleable ? (m.enabled ? "ON" : "OFF") : "CONFIG";
            int sc = m.toggleable ? (m.enabled ? 0x55FF77 : 0xFF6666) : accent;
            ctx.drawText(tr, status, cx + 8, yy + 17, argb(a, sc), false);
            if (m.toggleable) {
                int sx = cx + cw - 26, sy = yy + 10;
                ctx.fill(sx, sy, sx + 20, sy + 10, argb(a, mix(0x3A3F4C, accent, on)));
                int kx = sx + 1 + (int) (on * 10);
                ctx.fill(kx, sy + 1, kx + 8, sy + 9, argb(a, 0xFFFFFF));
            }
        }

        ctx.drawCenteredTextWithShadow(tr, "Left click: toggle   |   Right click: settings   |   Esc: close",
                width / 2, height - 16, argb((int) (170 * intro0), 0xFFFFFF));

        renderPanel(ctx, mx, my, now, accent);
    }

    private void renderPanel(DrawContext ctx, int mx, int my, long now, int accent) {
        hits.clear();
        if (selected == null) return;
        TextRenderer tr = this.textRenderer;
        float p = Modules.UI_ANIM.value ? ease((now - selectedTime) / (240f / (float) Modules.UI_SPEED.value)) : 1f;
        int a = (int) (255 * p);

        ctx.fill(0, 0, width, height, argb((int) (120 * p), 0x000000));

        pw = Math.min(260, width - 16);
        int total = 0;
        for (Setting s : selected.settings) total += rowH(s);
        ph = Math.min(height - 24, total + 36);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + (int) ((1f - p) * 24f);

        ctx.fill(px, py, px + pw, py + ph, argb((int) (a * 0.97f), 0x0E1016));
        ctx.drawBorder(px, py, pw, ph, argb(a, accent));
        ctx.fill(px + 1, py + 1, px + pw - 1, py + 22, argb(a, 0x161A24));
        ctx.drawText(tr, selected.name, px + 8, py + 7, argb(a, accent), true);
        ctx.drawText(tr, selected.desc, px + 8 + tr.getWidth(selected.name) + 8, py + 7, argb((int) (a * 0.55f), 0xFFFFFF), false);

        String reset = "Reset";
        resetW = tr.getWidth(reset) + 8;
        closeX = px + pw - 18;
        resetX = closeX - resetW - 4;
        boolean overReset = inside(mx, my, resetX, py + 4, resetW, 15);
        boolean overClose = inside(mx, my, closeX, py + 4, 14, 15);
        ctx.fill(resetX, py + 4, resetX + resetW, py + 19, argb(a, overReset ? 0x3A3F55 : 0x232838));
        ctx.drawText(tr, reset, resetX + 4, py + 8, argb(a, 0xFFFFFF), false);
        ctx.fill(closeX, py + 4, closeX + 14, py + 19, argb(a, overClose ? 0x7A2A2A : 0x232838));
        ctx.drawText(tr, "X", closeX + 4, py + 8, argb(a, 0xFFFFFF), false);

        int viewTop = py + 24;
        int viewBot = py + ph - 2;
        maxScroll = Math.max(0, total + 8 - (ph - 30));
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.enableScissor(px + 1, viewTop, px + pw - 1, viewBot);
        int y = py + 28 - scroll;
        for (Setting s : selected.settings) {
            int rh = rowH(s);
            int rx = px + 8, rw = pw - 16;
            boolean over = inside(mx, my, rx, y, rw, rh) && my >= viewTop && my < viewBot;
            if (over) ctx.fill(rx, y, rx + rw, y + rh - 1, argb((int) (a * 0.35f), 0x2A2F45));

            if (s instanceof Setting.Bool b) {
                float on = anim(b, b.value ? 1f : 0f, b.value ? 1f : 0f);
                ctx.drawText(tr, b.label, px + 12, y + 5, argb(a, 0xE8E8E8), false);
                int sx = px + pw - 36, sy = y + 4;
                ctx.fill(sx, sy, sx + 22, sy + 10, argb(a, mix(0x3A3F4C, accent, on)));
                int kx = sx + 1 + (int) (on * 12);
                ctx.fill(kx, sy + 1, kx + 8, sy + 9, argb(a, 0xFFFFFF));
            } else if (s instanceof Setting.Choice c) {
                ctx.drawText(tr, c.label, px + 12, y + 5, argb(a, 0xE8E8E8), false);
                String t = "< " + c.text() + " >";
                ctx.drawText(tr, t, px + pw - 12 - tr.getWidth(t), y + 5, argb(a, accent), false);
            } else if (s instanceof Setting.Num n) {
                ctx.drawText(tr, n.label, px + 12, y + 3, argb(a, 0xE8E8E8), false);
                String t = n.text();
                ctx.drawText(tr, t, px + pw - 12 - tr.getWidth(t), y + 3, argb(a, accent), false);
                int tx = px + 10, ty = y + 16, tw2 = pw - 20;
                track(ctx, n, tx, ty, tw2, 6, accent, a);
                double frac = (n.value - n.min) / (n.max - n.min);
                int kx = tx + (int) (frac * (tw2 - 4));
                ctx.fill(kx, ty - 2, kx + 4, ty + 8, argb(a, 0xFFFFFF));
            }
            hits.add(new Hit(s, px + 8, y, pw - 16, rh));
            y += rh;
        }
        ctx.disableScissor();
    }

    private void track(DrawContext ctx, Setting.Num n, int x, int y, int w, int h, int accent, int a) {
        if (n.kind == Setting.Num.NORMAL || n.group == null) {
            ctx.fill(x, y, x + w, y + h, argb(a, 0x2A2F3D));
            int fw = (int) (w * (n.value - n.min) / (n.max - n.min));
            ctx.fill(x, y, x + fw, y + h, argb(a, accent));
            return;
        }
        float hue = (float) (n.group.hue.value / 360.0);
        float sat = (float) n.group.sat.value;
        for (int i = 0; i < w; i++) {
            float t = i / (float) Math.max(1, w - 1);
            int c;
            if (n.kind == Setting.Num.HUE) c = ColorGroup.hsb(t, 1f, 1f);
            else if (n.kind == Setting.Num.SAT) c = ColorGroup.hsb(hue, t, 1f);
            else c = ColorGroup.hsb(hue, sat, t);
            ctx.fill(x + i, y, x + i + 1, y + h, argb(a, c));
        }
    }

    // ------------------------------------------------------------ input
    private void setFromMouse(Hit h, double mx) {
        Setting.Num n = (Setting.Num) h.s();
        double t = (mx - (h.x() + 2)) / (double) (h.w() - 4);
        t = Math.max(0.0, Math.min(1.0, t));
        n.set(n.min + t * (n.max - n.min));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (selected != null) {
            if (inside(mx, my, px, py, pw, ph)) {
                if (my < py + 22) {
                    if (inside(mx, my, closeX, py + 4, 14, 15)) {
                        selected = null;
                        Modules.save();
                    } else if (inside(mx, my, resetX, py + 4, resetW, 15)) {
                        selected.resetSettings();
                        Modules.save();
                    }
                    return true;
                }
                if (my >= py + 24 && my < py + ph - 2) {
                    for (Hit h : hits) {
                        if (inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                            Setting s = h.s();
                            if (s instanceof Setting.Bool b) {
                                b.value = !b.value;
                                Modules.save();
                            } else if (s instanceof Setting.Choice c) {
                                if (button == 1) c.prev(); else c.next();
                                Modules.save();
                            } else if (s instanceof Setting.Num) {
                                dragging = h;
                                setFromMouse(h, mx);
                            }
                            break;
                        }
                    }
                }
                return true;
            }
            selected = null;
            Modules.save();
            return true;
        }
        for (Card c : cards) {
            if (inside(mx, my, c.x(), c.y(), c.w(), c.h())) {
                if (button == 0 && c.m().toggleable) {
                    c.m().enabled = !c.m().enabled;
                    Modules.save();
                } else {
                    selected = c.m();
                    selectedTime = System.currentTimeMillis();
                    scroll = 0;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            setFromMouse(dragging, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            Modules.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (selected != null) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vAmount * 16)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            selected = null;
            Modules.save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        Modules.save();
    }
}
