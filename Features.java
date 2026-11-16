package com.example.knifeclient;

/**
 * 30 extra modules. Registered from the end of Modules' static block, so they show up in the menu,
 * the search box and the config file automatically.
 *
 * The fields are not final only because they are assigned inside init(); treat them as constants.
 */
public final class Features {
    private Features() { }

    // ---- Render / world (15)
    public static ClientModule SKY, TIME, WEATHER, FULLBRIGHT, FOV, NOHURT, PARTICLES, NOBOB, HITBOX,
            CHUNKB, ESP, TRACERS, WAYPOINTS, CROSS, VIGNETTE;
    // ---- HUD (11)
    public static ClientModule POTIONS, SPEED, BIOME, WTIME, MEMORY, SERVER, ITEMS, INVHUD, COMPASS, RADAR, SESSION;
    // ---- Utility (4)
    public static ClientModule SNEAK, WALK, AFK, DEATH;

    public static ColorGroup SKY_COLOR, CB_COLOR, ESP_COLOR, TR_COLOR, WP_COLOR, CH_COLOR;
    public static Setting.Choice TIME_MODE, WEATHER_MODE, ESP_TARGET, TR_TARGET, CH_STYLE, ITEM_KIND;
    public static Setting.Num TIME_CUSTOM, FOV_VALUE, CB_RANGE, CB_HEIGHT, ESP_RANGE, TR_RANGE, TR_THICK,
            CH_SIZE, CH_THICK, CH_GAP, VG_HP, VG_INT, COMPASS_W, RADAR_SIZE, RADAR_RANGE, AFK_SECS;
    public static Setting.Bool CH_HIDE, RADAR_MOBS;
    public static HudStyle POT_STYLE, SPEED_STYLE, BIOME_STYLE, WTIME_STYLE, MEM_STYLE, SERVER_STYLE,
            ITEMS_STYLE, INV_STYLE, COMPASS_STYLE, RADAR_STYLE, SESSION_STYLE;

    private static ClientModule mod(String id, String name, String desc, String cat) {
        return Modules.reg(new ClientModule(id, name, desc, true, false).cat(cat));
    }

    static void init() {
        // ======================================================== world / render
        SKY = mod("sky", "Custom Sky", "Tint the sky any colour (keeps day/night)", "Render");
        SKY_COLOR = new ColorGroup(SKY, 280, 0.6, 1.0, false);

        TIME = mod("time", "Time Changer", "Force the time of day (client only)", "Render");
        TIME_MODE = TIME.add(new Setting.Choice("mode", "Time", 1, "Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise", "Custom"));
        TIME_CUSTOM = TIME.add(new Setting.Num("custom", "Custom Time", 6000, 0, 24000, 100, ""));

        WEATHER = mod("weather", "Weather Changer", "Force clear, rain or thunder (client only)", "Render");
        WEATHER_MODE = WEATHER.add(new Setting.Choice("mode", "Weather", 0, "Clear", "Rain", "Thunder"));

        FULLBRIGHT = mod("fullbright", "Fullbright", "See in the dark (client night vision)", "Render");

        FOV = mod("fov", "Custom FOV", "Set your field of view past the slider limit", "Render");
        FOV_VALUE = FOV.add(new Setting.Num("fov", "FOV", 110, 30, 140, 1, ""));

        NOHURT = mod("nohurt", "No Hurt Cam", "Remove the screen tilt when you take damage", "Render");
        PARTICLES = mod("particles", "Minimal Particles", "Cut particles down for cleaner visuals and FPS", "Render");
        NOBOB = mod("nobob", "No View Bobbing", "Turn off camera bobbing while walking", "Render");
        HITBOX = mod("hitbox", "Hitboxes", "Always show entity hitboxes", "Render");

        CHUNKB = mod("chunkb", "Chunk Borders", "Draw chunk borders around you", "Render");
        CB_RANGE = CHUNKB.add(new Setting.Num("range", "Range", 1, 0, 3, 1, " chunks"));
        CB_HEIGHT = CHUNKB.add(new Setting.Num("height", "Height", 24, 8, 64, 4, ""));
        CB_COLOR = new ColorGroup(CHUNKB, 120, 0.8, 1.0, false);

        ESP = mod("esp", "Entity ESP", "Glowing outline through walls", "Render");
        ESP_TARGET = ESP.add(new Setting.Choice("target", "Target", 0, "Players", "Mobs", "Items", "Everything"));
        ESP_RANGE = ESP.add(new Setting.Num("range", "Range", 64, 8, 256, 8, " m"));
        ESP_COLOR = new ColorGroup(ESP, 0, 0.85, 1.0, false);

        TRACERS = mod("tracers", "Tracers", "Lines from the bottom of the screen to entities", "Render");
        TR_TARGET = TRACERS.add(new Setting.Choice("target", "Target", 0, "Players", "Mobs", "Both"));
        TR_RANGE = TRACERS.add(new Setting.Num("range", "Range", 128, 16, 256, 8, " m"));
        TR_THICK = TRACERS.add(new Setting.Num("thick", "Thickness", 1, 1, 3, 1, ""));
        TR_COLOR = new ColorGroup(TRACERS, 200, 0.8, 1.0, false);

        WAYPOINTS = mod("waypoints", "Waypoints", "B adds a waypoint, shown through walls with distance", "Render");
        WP_COLOR = new ColorGroup(WAYPOINTS, 50, 0.9, 1.0, false);

        CROSS = mod("crosshair", "Custom Crosshair", "Your own crosshair style, colour and size", "Render");
        CH_STYLE = CROSS.add(new Setting.Choice("style", "Style", 0, "Cross", "Plus Gap", "Dot", "Circle"));
        CH_SIZE = CROSS.add(new Setting.Num("size", "Size", 5, 1, 20, 1, "px"));
        CH_THICK = CROSS.add(new Setting.Num("thick", "Thickness", 1, 1, 4, 1, "px"));
        CH_GAP = CROSS.add(new Setting.Num("gap", "Gap", 2, 0, 10, 1, "px"));
        CH_HIDE = CROSS.add(new Setting.Bool("hide", "Hide Vanilla Crosshair", true));
        CH_COLOR = new ColorGroup(CROSS, 0, 0.0, 1.0, false);

        VIGNETTE = mod("vignette", "Low Health Warning", "Red pulsing edges when your health is low", "Render");
        VG_HP = VIGNETTE.add(new Setting.Num("hp", "Below Health", 8, 2, 19, 1, ""));
        VG_INT = VIGNETTE.add(new Setting.Num("int", "Intensity", 0.6, 0.1, 1.0, 0.05, ""));

        // ======================================================== HUD
        POTIONS = mod("potions", "Potion Effects", "Active effects with timers", "HUD");
        POT_STYLE = new HudStyle(POTIONS, 99, 12);

        SPEED = mod("speed", "Speed", "Your speed in blocks per second", "HUD");
        SPEED_STYLE = new HudStyle(SPEED, 1, 31);

        BIOME = mod("biome", "Biome", "Name of the biome you are in", "HUD");
        BIOME_STYLE = new HudStyle(BIOME, 1, 35);

        WTIME = mod("wtime", "World Time", "In-game day and clock", "HUD");
        WTIME_STYLE = new HudStyle(WTIME, 1, 29);

        MEMORY = mod("memory", "Memory", "Game memory usage", "HUD");
        MEM_STYLE = new HudStyle(MEMORY, 99, 2);

        SERVER = mod("server", "Server Info", "Server address and players online", "HUD");
        SERVER_STYLE = new HudStyle(SERVER, 50, 1);

        ITEMS = mod("itemcount", "Item Counter", "Count of totems, crystals, apples and more", "HUD");
        ITEM_KIND = ITEMS.add(new Setting.Choice("kind", "Item", 0, "Totems", "Crystals", "Golden Apples", "Ender Pearls", "Arrows", "XP Bottles"));
        ITEMS_STYLE = new HudStyle(ITEMS, 50, 90);

        INVHUD = mod("invhud", "Inventory HUD", "Your inventory on screen", "HUD");
        INV_STYLE = new HudStyle(INVHUD, 1, 70);

        COMPASS = mod("compass", "Compass", "Heading strip with N E S W", "HUD");
        COMPASS_W = COMPASS.add(new Setting.Num("width", "Width", 160, 80, 300, 10, "px"));
        COMPASS_STYLE = new HudStyle(COMPASS, 50, 6);

        RADAR = mod("radar", "Radar", "Top-down radar of nearby players", "HUD");
        RADAR_SIZE = RADAR.add(new Setting.Num("size", "Radar Size", 80, 50, 140, 5, "px"));
        RADAR_RANGE = RADAR.add(new Setting.Num("range", "Radar Range", 48, 16, 128, 4, " m"));
        RADAR_MOBS = RADAR.add(new Setting.Bool("mobs", "Show Mobs", false));
        RADAR_STYLE = new HudStyle(RADAR, 99, 75);

        SESSION = mod("session", "Session Timer", "How long you have been playing", "HUD");
        SESSION_STYLE = new HudStyle(SESSION, 1, 95);

        // ======================================================== utility
        SNEAK = mod("sneak", "Always Sneak", "Hold sneak automatically", "Utility");
        WALK = mod("walk", "Auto Walk", "Hold forward automatically", "Utility");
        AFK = mod("antiafk", "Anti AFK", "Small look and jump nudges so you are not kicked", "Utility");
        AFK_SECS = AFK.add(new Setting.Num("secs", "Every", 30, 10, 120, 5, " s"));
        DEATH = mod("deathcoords", "Death Coordinates", "Prints where you died in chat", "Utility");
    }
}
