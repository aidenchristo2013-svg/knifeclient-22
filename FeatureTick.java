package com.example.knifeclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Per-tick logic for the extra modules (time, weather, fullbright, sneak, anti-afk, waypoints...). */
public final class FeatureTick {
    private FeatureTick() { }

    public record Waypoint(String name, double x, double y, double z, String dim) { }

    public static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    public static long sessionStart = System.currentTimeMillis();

    private static KeyBinding wpAdd, wpClear;
    private static boolean nvAdded, bobSaved, prevBob, partSaved, hbOn, sneakForced, walkForced, deathNotified;
    private static Object prevParticles;
    private static int afkTicks, afkJump;
    private static final Random RNG = new Random();
    private static final long[] TIMES = {1000L, 6000L, 12000L, 14000L, 18000L, 23000L};

    public static void init() {
        wpAdd = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.knifeclient.waypoint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.knifeclient"));
        wpClear = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.knifeclient.waypoint_clear", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.knifeclient"));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sessionStart = System.currentTimeMillis());
        ClientTickEvents.END_CLIENT_TICK.register(FeatureTick::tick);
    }

    /** Used by EntityMixin: should this entity get the ESP glow? */
    public static boolean espMatches(Entity e) {
        if (Features.ESP == null || !Features.ESP.enabled) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || e == mc.player || !e.getWorld().isClient) return false;
        if (e.distanceTo(mc.player) > Features.ESP_RANGE.value) return false;
        boolean player = e instanceof PlayerEntity;
        boolean mob = e instanceof LivingEntity && !player;
        boolean item = e instanceof ItemEntity;
        return switch (Features.ESP_TARGET.index) {
            case 0 -> player;
            case 1 -> mob;
            case 2 -> item;
            default -> player || mob || item;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) return;

        // ---- keybinds
        while (wpAdd.wasPressed()) {
            String dim = w.getRegistryKey().getValue().toString();
            String name = "WP " + (WAYPOINTS.size() + 1);
            WAYPOINTS.add(new Waypoint(name, p.getX(), p.getY(), p.getZ(), dim));
            p.sendMessage(Text.literal(String.format(Locale.ROOT, "[Knife Client] %s saved at %d %d %d",
                    name, (int) p.getX(), (int) p.getY(), (int) p.getZ())), false);
        }
        while (wpClear.wasPressed()) {
            WAYPOINTS.clear();
            p.sendMessage(Text.literal("[Knife Client] Waypoints cleared"), false);
        }

        // ---- world changers
        if (Features.TIME.enabled) {
            int m = Features.TIME_MODE.index;
            long t = m < TIMES.length ? TIMES[m] : (long) Features.TIME_CUSTOM.value;
            w.setTime(w.getTime(), t, false);
        }
        if (Features.WEATHER.enabled) {
            int m = Features.WEATHER_MODE.index;
            w.setRainGradient(m >= 1 ? 1f : 0f);
            w.setThunderGradient(m >= 2 ? 1f : 0f);
        }

        // ---- fullbright (client-side night vision)
        StatusEffectInstance nv = p.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (Features.FULLBRIGHT.enabled) {
            if (nv == null || (nvAdded && nv.getDuration() < 300)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
                nvAdded = true;
            }
        } else if (nvAdded) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            nvAdded = false;
        }

        // ---- options we override while a module is on, restored when it is turned off
        SimpleOption<Boolean> bob = mc.options.getBobView();
        if (Features.NOBOB.enabled) {
            if (!bobSaved) { prevBob = bob.getValue(); bobSaved = true; }
            if (bob.getValue()) bob.setValue(false);
        } else if (bobSaved) {
            bob.setValue(prevBob);
            bobSaved = false;
        }

        // Minimal Particles: the enum type is looked up at runtime (ALL, DECREASED, MINIMAL: last = minimal)
        SimpleOption part = mc.options.getParticles();
        if (Features.PARTICLES.enabled) {
            Object cur = part.getValue();
            if (!partSaved) { prevParticles = cur; partSaved = true; }
            Object[] all = ((Enum<?>) cur).getDeclaringClass().getEnumConstants();
            Object minimal = all[all.length - 1];
            if (cur != minimal) part.setValue(minimal);
        } else if (partSaved) {
            if (prevParticles != null) part.setValue(prevParticles);
            partSaved = false;
        }

        if (Features.HITBOX.enabled && !hbOn) {
            mc.getEntityRenderDispatcher().setRenderHitboxes(true);
            hbOn = true;
        } else if (!Features.HITBOX.enabled && hbOn) {
            mc.getEntityRenderDispatcher().setRenderHitboxes(false);
            hbOn = false;
        }

        // ---- movement helpers
        boolean free = mc.currentScreen == null;
        if (Features.SNEAK.enabled && free) {
            mc.options.sneakKey.setPressed(true);
            sneakForced = true;
        } else if (sneakForced) {
            mc.options.sneakKey.setPressed(false);
            sneakForced = false;
        }
        if (Features.WALK.enabled && free) {
            mc.options.forwardKey.setPressed(true);
            walkForced = true;
        } else if (walkForced) {
            mc.options.forwardKey.setPressed(false);
            walkForced = false;
        }

        // ---- anti afk: tiny look nudge + a hop every N seconds
        if (Features.AFK.enabled && free) {
            afkTicks++;
            if (afkTicks >= (int) (Features.AFK_SECS.value * 20)) {
                afkTicks = 0;
                p.setYaw(p.getYaw() + (RNG.nextBoolean() ? 7f : -7f));
                afkJump = 3;
            }
        } else {
            afkTicks = 0;
        }
        if (afkJump > 0) {
            mc.options.jumpKey.setPressed(true);
            afkJump--;
            if (afkJump == 0) mc.options.jumpKey.setPressed(false);
        }

        // ---- death coordinates
        if (mc.currentScreen instanceof DeathScreen) {
            if (!deathNotified && Features.DEATH.enabled) {
                p.sendMessage(Text.literal(String.format(Locale.ROOT, "[Knife Client] You died at %d %d %d (%s)",
                        (int) p.getX(), (int) p.getY(), (int) p.getZ(), w.getRegistryKey().getValue())), false);
            }
            deathNotified = true;
        } else {
            deathNotified = false;
        }
    }
}
