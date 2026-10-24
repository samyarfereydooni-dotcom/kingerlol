

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Kinger Lol v4 - all-in-one Fabric client for Minecraft Java 1.21.11.
 * Categories: SMP | PvP (Sword/Bow/Crystal/Anchor/Mace/General) | Movement | Render | HUD | Waypoints
 */
public class KingerLol implements ClientModInitializer {

    public static final String NAME = "Kinger Lol";
    public static final String VERSION = "v4.0";

    // =========================================================
    //                    MODULE REGISTRY
    // =========================================================
    enum Cat {
        SMP("SMP"),
        PVP_SWORD("Sword"),
        PVP_BOW("Bow"),
        PVP_CRYSTAL("Crystal"),
        PVP_ANCHOR("Anchor"),
        PVP_MACE("Mace"),
        PVP_GENERAL("General"),
        MOVEMENT("Movement"),
        RENDER("Render"),
        HUD("HUD"),
        WAYPOINTS("Waypoints");
        final String label;
        Cat(String label) { this.label = label; }
    }

    static final List<Mod> MODS = new ArrayList<>();
    static final List<Num> NUMS = new ArrayList<>();

    static final class Mod {
        final String name, desc;
        final Cat cat;
        final boolean action;
        boolean on;
        Mod(String name, String desc, Cat cat, boolean on, boolean action) {
            this.name = name; this.desc = desc; this.cat = cat;
            this.on = on; this.action = action; MODS.add(this);
        }
    }

    static final class Num {
        final String name, desc;
        final Cat cat;
        final int min, max, step;
        int value;
        Num(String name, String desc, Cat cat, int value, int min, int max, int step) {
            this.name = name; this.desc = desc; this.cat = cat;
            this.value = value; this.min = min; this.max = max; this.step = step;
            NUMS.add(this);
        }
        void change(int dir) {
            value = Math.max(min, Math.min(max, value + dir * step));
        }
    }

    record Waypoint(String name, int x, int y, int z, String dim) { }
    static final List<Waypoint> WAYPOINTS = new ArrayList<>();

    // ================== SMP ==================
    static final Mod PLAYER_ESP = new Mod("Player ESP", "Shows players through walls", Cat.SMP, false, false);
    static final Mod ITEM_ESP = new Mod("Item ESP", "Shows dropped items", Cat.SMP, false, false);
    static final Mod CHEST_ESP = new Mod("Chest ESP", "Shows containers", Cat.SMP, false, false);
    static final Mod STORAGE = new Mod("Storage Finder", "Lists nearby containers (G)", Cat.SMP, false, true);
    static final Mod BASE_FINDER = new Mod("Base Finder", "Finds clusters of blocks (furnace, bed, etc.)", Cat.SMP, false, true);
    static final Mod SPAWNER_FINDER = new Mod("Spawner Finder", "Lists nearby spawners", Cat.SMP, false, true);
    static final Mod TRACERS = new Mod("Tracers", "Lines to players (HUD)", Cat.SMP, false, false);
    static final Num ESP_RANGE = new Num("ESP Range", "Max distance for ESP", Cat.SMP, 64, 16, 256, 8);

    // ================== PVP SWORD ==================
    static final Mod KILLAURA = new Mod("Kill Aura", "Auto attacks nearby players", Cat.PVP_SWORD, false, false);
    static final Mod AIMASSIST = new Mod("Aim Assist", "Smoothly aims at nearby players", Cat.PVP_SWORD, false, false);
    static final Mod TRIGGERBOT = new Mod("Trigger Bot", "Attacks when crosshair on player", Cat.PVP_SWORD, false, false);
    static final Mod CRITICALS = new Mod("Criticals", "Jumps before attacking", Cat.PVP_SWORD, false, false);
    static final Mod VELOCITY = new Mod("Velocity", "Reduces knockback taken", Cat.PVP_SWORD, false, false);
    static final Mod AUTO_SWORD = new Mod("Auto Sword", "Switches to best weapon", Cat.PVP_SWORD, false, false);
    static final Num AURA_RANGE = new Num("Aura Range", "Max distance to attack", Cat.PVP_SWORD, 3, 1, 6, 1);
    static final Num AURA_CPS = new Num("Aura CPS", "Max attacks per second", Cat.PVP_SWORD, 10, 1, 20, 1);
    static final Num AIM_SPEED = new Num("Aim Speed", "Aim Assist rotation 1-100", Cat.PVP_SWORD, 30, 1, 100, 5);

    // ================== PVP BOW ==================
    static final Mod BOW_AIMBOT = new Mod("Bow Aimbot", "Aims bow at nearest player", Cat.PVP_BOW, false, false);
    static final Mod AUTO_BOW = new Mod("Auto Bow", "Releases bow when fully charged", Cat.PVP_BOW, false, false);
    static final Mod FAST_BOW = new Mod("Fast Bow", "Releases bow early", Cat.PVP_BOW, false, false);

    // ================== PVP CRYSTAL ==================
    static final Mod AUTO_CRYSTAL = new Mod("Auto Crystal", "Places and breaks crystals", Cat.PVP_CRYSTAL, false, false);
    static final Mod CRYSTAL_AURA = new Mod("Crystal Aura", "Attacks nearby crystals", Cat.PVP_CRYSTAL, false, false);
    static final Num CRYSTAL_RANGE = new Num("Crystal Range", "Max distance", Cat.PVP_CRYSTAL, 5, 2, 8, 1);

    // ================== PVP ANCHOR ==================
    static final Mod AUTO_ANCHOR = new Mod("Auto Anchor", "Charges and detonates anchors", Cat.PVP_ANCHOR, false, false);
    static final Mod ANCHOR_AURA = new Mod("Anchor Aura", "Detonates nearby charged anchors", Cat.PVP_ANCHOR, false, false);
    static final Num ANCHOR_RANGE = new Num("Anchor Range", "Max distance", Cat.PVP_ANCHOR, 5, 2, 8, 1);

    // ================== PVP MACE ==================
    static final Mod AUTO_MACE = new Mod("Auto Mace", "Full wind+elytra+pearl+mace combo (R)", Cat.PVP_MACE, false, false);
    static final Mod REPEAT_COMBO = new Mod("Repeat Combo", "Keep repeating combo", Cat.PVP_MACE, false, false);
    static final Num MACE_RANGE = new Num("Mace Range", "Combo trigger range", Cat.PVP_MACE, 10, 4, 20, 1);

    // ================== PVP GENERAL ==================
    static final Mod AUTO_TOTEM = new Mod("Auto Totem", "Keeps a totem in offhand", Cat.PVP_GENERAL, true, false);
    static final Mod AUTO_ARMOR = new Mod("Auto Armor", "Equips best armor", Cat.PVP_GENERAL, true, false);
    static final Mod AUTO_EAT = new Mod("Auto Eat", "Eats food when hungry", Cat.PVP_GENERAL, false, false);
    static final Mod AUTO_TOOL = new Mod("Auto Tool", "Switches to fastest tool", Cat.PVP_GENERAL, false, false);
    static final Num EAT_AT = new Num("Eat At Hunger", "Start eating below this", Cat.PVP_GENERAL, 14, 4, 19, 1);

    // ================== MOVEMENT ==================
    static final Mod AUTO_SPRINT = new Mod("Auto Sprint", "Sprints automatically", Cat.MOVEMENT, false, false);
    static final Mod SPEED = new Mod("Speed", "Multiplies movement speed", Cat.MOVEMENT, false, false);
    static final Mod NOFALL = new Mod("No Fall", "Prevents fall damage", Cat.MOVEMENT, false, false);
    static final Mod HIGH_JUMP = new Mod("High Jump", "Jump higher", Cat.MOVEMENT, false, false);
    static final Num SPEED_MULT = new Num("Speed Mult %", "Extra speed as %", Cat.MOVEMENT, 30, 5, 100, 5);
    static final Num JUMP_BOOST = new Num("Jump Boost", "Extra jump velocity %", Cat.MOVEMENT, 40, 5, 150, 5);

    // ================== RENDER ==================
    static final Mod FULLBRIGHT = new Mod("Fullbright", "See in the dark", Cat.RENDER, false, false);
    static final Mod ZOOM = new Mod("Zoom", "Hold C to zoom", Cat.RENDER, true, false);
    static final Mod NO_HURT_CAM = new Mod("No Hurt Cam", "No camera shake on damage", Cat.RENDER, false, false);
    static final Num ZOOM_FOV = new Num("Zoom FOV", "Lower = more zoom", Cat.RENDER, 30, 10, 60, 5);

    // ================== HUD ==================
    static final Mod WATERMARK = new Mod("Watermark", "Shows client name", Cat.HUD, true, false);
    static final Mod ARRAYLIST = new Mod("Module List", "Shows enabled modules", Cat.HUD, true, false);
    static final Mod FPS_HUD = new Mod("FPS", "Shows FPS", Cat.HUD, true, false);
    static final Mod PING_HUD = new Mod("Ping", "Shows ping", Cat.HUD, true, false);
    static final Mod COORDS_HUD = new Mod("Coordinates", "Shows XYZ", Cat.HUD, true, false);
    static final Mod SPEED_HUD = new Mod("Speed HUD", "Shows b/s", Cat.HUD, false, false);
    static final Mod CLOCK_HUD = new Mod("Clock", "Real time", Cat.HUD, false, false);
    static final Mod KEYSTROKES = new Mod("Keystrokes", "WASD keys", Cat.HUD, true, false);
    static final Mod ARMOR_HUD = new Mod("Armor HUD", "Armor and durability", Cat.HUD, true, false);
    static final Mod WAYPOINT_HUD = new Mod("Waypoint HUD", "Waypoint directions", Cat.HUD, true, false);
    static final Mod PLAYERS_HUD = new Mod("Nearby Players", "Players with distance/HP", Cat.HUD, false, false);

    // =========================================================
    //                          KEYS
    // =========================================================
    private static KeyBinding guiKey, maceKey, storageKey, zoomKey, waypointKey, kauraKey;

    // =========================================================
    //                    AUTO MACE CONSTANTS
    // =========================================================
    static final int SLOT_MACE = 0, SLOT_WIND = 1, SLOT_PEARL = 2, SLOT_ELYTRA = 3;
    static final double ATTACK_REACH = 3.0;
    static final int CHEST_SLOT = 6;
    static final int MAX_STATE_TICKS = 120;
    enum State { IDLE, WIND1, RISE1, ELYTRA, PEARL, GLIDE, LAND, WIND2, RISE2, SMASH }
    private static State state = State.IDLE;
    private static int timer = 0, stateTicks = 0;
    private static PlayerEntity maceTarget;
    private static boolean elytraEquipped = false;
    private static Vec3d lastPos = Vec3d.ZERO;

    // =========================================================
    //                    OTHER FEATURE STATE
    // =========================================================
    private static final int OFFHAND_BUTTON = 40;
    private static final int MAX_RESULTS = 25;
    private static int totemCooldown = 0, armorCooldown = 0;
    private static boolean weHoldAttack = false, weHoldSprint = false;
    private static boolean fullbrightApplied = false, zooming = false;
    private static int oldFov = 70, auraCooldown = 0, triggerCooldown = 0;
    private static boolean eating = false;
    private static int slotBeforeEating = -1;
    private static boolean deathRecorded = false;
    private static double lastX, lastZ, speed = 0;
    private static float lastHealth = 20f;
    static Cat lastTab = Cat.SMP;

    private static final Item[][] ARMOR = {
            {Items.LEATHER_HELMET, Items.GOLDEN_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET,
                    Items.DIAMOND_HELMET, Items.NETHERITE_HELMET},
            {Items.LEATHER_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE,
                    Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE},
            {Items.LEATHER_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS,
                    Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS},
            {Items.LEATHER_BOOTS, Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS,
                    Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS}
    };
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // =========================================================
    //                          INIT
    // =========================================================
    @Override
    public void onInitializeClient() {
        load();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("kingerlol", "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Kinger Lol - Menu", GLFW.GLFW_KEY_KP_5, category));
        maceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Kinger Lol - Auto Mace", GLFW.GLFW_KEY_R, category));
        storageKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Kinger Lol - Storage Finder", GLFW.GLFW_KEY_G, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Kinger Lol - Zoom", GLFW.GLFW_KEY_C, category));
        waypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Kinger Lol - Add Waypoint", GLFW.GLFW_KEY_B, category));
        kauraKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Kinger Lol - Kill Aura", GLFW.GLFW_KEY_V, category));

        ClientTickEvents.END_CLIENT_TICK.register(KingerLol::tick);
        HudRenderCallback.EVENT.register(KingerLol::renderHud);
    }

    // =========================================================
    //                    CONFIG
    // =========================================================
    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("kingerlol.properties");
    }
    private static String key(String s) { return s.toLowerCase().replace(' ', '_'); }
    private static boolean persists(Mod m) { return !m.action && m != AUTO_MACE; }

    static void save() {
        try {
            Properties pr = new Properties();
            for (Mod m : MODS) if (persists(m)) pr.setProperty("mod." + key(m.name), String.valueOf(m.on));
            for (Num n : NUMS) pr.setProperty("num." + key(n.name), String.valueOf(n.value));
            pr.setProperty("tab", lastTab.name());
            pr.setProperty("wp.count", String.valueOf(WAYPOINTS.size()));
            int i = 0;
            for (Waypoint w : WAYPOINTS) {
                pr.setProperty("wp." + i, w.name().replace("|", "_") + "|" + w.x() + "|" + w.y() + "|" + w.z() + "|" + w.dim());
                i++;
            }
            try (OutputStream os = Files.newOutputStream(configPath())) { pr.store(os, NAME); }
        } catch (Exception ignored) { }
    }

    private static void load() {
        try {
            Path path = configPath();
            if (!Files.exists(path)) return;
            Properties pr = new Properties();
            try (InputStream is = Files.newInputStream(path)) { pr.load(is); }
            for (Mod m : MODS) {
                String v = pr.getProperty("mod." + key(m.name));
                if (v != null && persists(m)) m.on = Boolean.parseBoolean(v);
            }
            for (Num n : NUMS) {
                String v = pr.getProperty("num." + key(n.name));
                if (v != null) n.value = Math.max(n.min, Math.min(n.max, Integer.parseInt(v.trim())));
            }
            String tab = pr.getProperty("tab");
            if (tab != null) lastTab = Cat.valueOf(tab);
            WAYPOINTS.clear();
            int count = Integer.parseInt(pr.getProperty("wp.count", "0"));
            for (int i = 0; i < count; i++) {
                String raw = pr.getProperty("wp." + i);
                if (raw == null) continue;
                String[] parts = raw.split("\\|");
                if (parts.length < 5) continue;
                WAYPOINTS.add(new Waypoint(parts[0], Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), parts[4]));
            }
        } catch (Exception ignored) { }
    }

    // =========================================================
    //                          TICK
    // =========================================================
    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            state = State.IDLE; elytraEquipped = false;
            fullbrightApplied = false; zooming = false; eating = false;
            return;
        }

        while (guiKey.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new Menu());
        while (maceKey.wasPressed()) {
            AUTO_MACE.on = !AUTO_MACE.on;
            p.sendMessage(Text.literal(NAME + " | Auto Mace: " + (AUTO_MACE.on ? "ON" : "OFF")), true);
        }
        while (kauraKey.wasPressed()) {
            KILLAURA.on = !KILLAURA.on;
            p.sendMessage(Text.literal(NAME + " | Kill Aura: " + (KILLAURA.on ? "ON" : "OFF")), true);
        }
        while (storageKey.wasPressed()) findStorage(mc);
        while (waypointKey.wasPressed()) addWaypoint(mc, null);

        if (p.getHealth() <= 0f) {
            if (!deathRecorded) {
                WAYPOINTS.removeIf(w -> w.name().equals("Death"));
                addWaypoint(mc, "Death");
                deathRecorded = true;
            }
        } else deathRecorded = false;

        double dx = p.getX() - lastX, dz = p.getZ() - lastZ;
        speed = speed * 0.7 + (Math.sqrt(dx * dx + dz * dz) * 20.0) * 0.3;
        lastX = p.getX(); lastZ = p.getZ();

        if (AUTO_TOTEM.on) autoTotem(mc, p);
        if (AUTO_ARMOR.on) autoArmor(mc, p);
        autoEat(mc, p);
        autoTool(mc, p);
        autoSprint(mc, p);
        movementSpeed(mc, p);
        noFall(mc, p);
        fullbright(p);
        zoom(mc);
        autoMace(mc, p);
        killAura(mc, p);
        aimAssist(mc, p);
        triggerBot(mc, p);
        autoSword(mc, p);
        velocity(mc, p);
        bowAimbot(mc, p);
        autoCrystal(mc, p);
        crystalAura(mc, p);
        autoAnchor(mc, p);
        anchorAura(mc, p);
    }

    // =========================================================
    //                    EXISTING FEATURES
    // =========================================================
    private static void autoTotem(MinecraftClient mc, ClientPlayerEntity p) {
        if (totemCooldown > 0) { totemCooldown--; return; }
        if (p.currentScreenHandler != p.playerScreenHandler) return;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        for (int slot = 9; slot <= 44; slot++) {
            ItemStack s = p.playerScreenHandler.getSlot(slot).getStack();
            if (s.isOf(Items.TOTEM_OF_UNDYING)) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, OFFHAND_BUTTON, SlotActionType.SWAP, p);
                totemCooldown = 5; return;
            }
        }
    }

    private static int armorRank(int slotIndex, Item item) {
        Item[] list = ARMOR[slotIndex];
        for (int i = 0; i < list.length; i++) if (list[i] == item) return i + 1;
        return 0;
    }

    private static void autoArmor(MinecraftClient mc, ClientPlayerEntity p) {
        if (armorCooldown > 0) { armorCooldown--; return; }
        if (mc.currentScreen != null) return;
        if (p.currentScreenHandler != p.playerScreenHandler) return;
        if (state != State.IDLE || elytraEquipped) return;
        for (int s = 0; s < 4; s++) {
            ItemStack worn = p.getEquippedStack(ARMOR_SLOTS[s]);
            if (s == 1 && worn.isOf(Items.ELYTRA)) continue;
            int wornRank = armorRank(s, worn.getItem());
            int bestSlot = -1, bestRank = wornRank;
            for (int slot = 9; slot <= 44; slot++) {
                ItemStack st = p.playerScreenHandler.getSlot(slot).getStack();
                int r = armorRank(s, st.getItem());
                if (r > bestRank) { bestRank = r; bestSlot = slot; }
            }
            if (bestSlot < 0) continue;
            int sync = p.playerScreenHandler.syncId;
            int armorSlot = 5 + s;
            mc.interactionManager.clickSlot(sync, bestSlot, 0, SlotActionType.PICKUP, p);
            mc.interactionManager.clickSlot(sync, armorSlot, 0, SlotActionType.PICKUP, p);
            mc.interactionManager.clickSlot(sync, bestSlot, 0, SlotActionType.PICKUP, p);
            armorCooldown = 10; return;
        }
    }

    private static boolean isGoodFood(ItemStack st) {
        if (st.isEmpty() || st.get(DataComponentTypes.FOOD) == null) return false;
        return !(st.isOf(Items.ROTTEN_FLESH) || st.isOf(Items.SPIDER_EYE)
                || st.isOf(Items.POISONOUS_POTATO) || st.isOf(Items.PUFFERFISH)
                || st.isOf(Items.CHORUS_FRUIT) || st.isOf(Items.GOLDEN_APPLE)
                || st.isOf(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private static void stopEating(MinecraftClient mc, ClientPlayerEntity p) {
        if (!eating) return;
        mc.options.useKey.setPressed(false);
        if (slotBeforeEating >= 0) select(p, slotBeforeEating);
        slotBeforeEating = -1; eating = false;
    }

    private static void autoEat(MinecraftClient mc, ClientPlayerEntity p) {
        if (!AUTO_EAT.on || AUTO_MACE.on || mc.currentScreen != null) { stopEating(mc, p); return; }
        int food = p.getHungerManager().getFoodLevel();
        if (!eating) {
            if (food > EAT_AT.value) return;
            for (int slot = 0; slot < 9; slot++) {
                if (isGoodFood(p.getInventory().getStack(slot))) {
                    slotBeforeEating = p.getInventory().getSelectedSlot();
                    select(p, slot);
                    mc.options.useKey.setPressed(true);
                    eating = true; return;
                }
            }
        } else if (food >= 20 || !isGoodFood(p.getMainHandStack())) stopEating(mc, p);
    }

    private static void autoTool(MinecraftClient mc, ClientPlayerEntity p) {
        if (!AUTO_TOOL.on || AUTO_MACE.on || eating || mc.currentScreen != null) return;
        if (!mc.options.attackKey.isPressed()) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState bs = mc.world.getBlockState(hit.getBlockPos());
        int cur = p.getInventory().getSelectedSlot();
        int best = cur;
        float bestSpeed = p.getInventory().getStack(cur).getMiningSpeedMultiplier(bs);
        for (int slot = 0; slot < 9; slot++) {
            float sp = p.getInventory().getStack(slot).getMiningSpeedMultiplier(bs);
            if (sp > bestSpeed + 0.01f) { bestSpeed = sp; best = slot; }
        }
        if (best != cur) select(p, best);
    }

    private static void autoSprint(MinecraftClient mc, ClientPlayerEntity p) {
        if (AUTO_SPRINT.on && mc.currentScreen == null) {
            if (mc.options.forwardKey.isPressed() && !p.isSneaking() && !p.horizontalCollision) {
                mc.options.sprintKey.setPressed(true); weHoldSprint = true;
            }
        } else if (weHoldSprint) {
            mc.options.sprintKey.setPressed(false); weHoldSprint = false;
        }
    }

    private static void fullbright(ClientPlayerEntity p) {
        if (FULLBRIGHT.on) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 220) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false, false));
            }
            fullbrightApplied = true;
        } else if (fullbrightApplied) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION); fullbrightApplied = false;
        }
    }

    private static void zoom(MinecraftClient mc) {
        boolean want = ZOOM.on && zoomKey.isPressed() && mc.currentScreen == null;
        if (want) {
            if (!zooming) { oldFov = mc.options.getFov().getValue(); zooming = true; }
            mc.options.getFov().setValue(ZOOM_FOV.value);
        } else if (zooming) { mc.options.getFov().setValue(oldFov); zooming = false; }
    }

    // =========================================================
    //                     STORAGE FINDER
    // =========================================================
    static void findStorage(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        List<BlockPos> found = new ArrayList<>();
        ChunkPos center = p.getChunkPos();
        int radius = 8;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = mc.world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntityPositions()) {
                    BlockEntity be = mc.world.getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                            || be instanceof ShulkerBoxBlockEntity || be instanceof EnderChestBlockEntity
                            || be instanceof HopperBlockEntity || be instanceof AbstractFurnaceBlockEntity) {
                        found.add(pos.toImmutable());
                    }
                }
            }
        }
        BlockPos me = p.getBlockPos();
        found.sort(Comparator.comparingDouble(b -> b.getSquaredDistance(me)));
        p.sendMessage(Text.literal(NAME + " | Storage found: " + found.size()), false);
        int n = Math.min(MAX_RESULTS, found.size());
        for (int i = 0; i < n; i++) {
            BlockPos b = found.get(i);
            int dist = (int) Math.sqrt(b.getSquaredDistance(me));
            p.sendMessage(Text.literal((i + 1) + ") " + b.getX() + " " + b.getY() + " " + b.getZ() + "  (" + dist + "m)"), false);
        }
    }

    // =========================================================
    //                        WAYPOINTS
    // =========================================================
    static void addWaypoint(MinecraftClient mc, String name) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        String n = name != null ? name : "WP" + (WAYPOINTS.size() + 1);
        String dim = mc.world.getRegistryKey().getValue().toString();
        WAYPOINTS.add(new Waypoint(n, (int) Math.floor(p.getX()), (int) Math.floor(p.getY()),
                (int) Math.floor(p.getZ()), dim));
        while (WAYPOINTS.size() > 20) WAYPOINTS.remove(0);
        save();
        p.sendMessage(Text.literal(NAME + " | Waypoint saved: " + n), true);
    }

    private static String cardinal(double dx, double dz) {
        double ang = (Math.toDegrees(Math.atan2(dz, dx)) + 360.0) % 360.0;
        String[] names = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        return names[(int) Math.round(ang / 45.0) % 8];
    }

    // =========================================================
    //                        AUTO MACE
    // =========================================================
    private static void setState(State s, int delay) { state = s; timer = delay; stateTicks = 0; }

    private static void autoMace(MinecraftClient mc, ClientPlayerEntity p) {
        if (!AUTO_MACE.on) {
            if (state != State.IDLE || elytraEquipped) abort(mc, p, null);
            return;
        }
        if (mc.currentScreen != null) return;
        if (timer > 0) { timer--; return; }
        stateTicks++;
        if (state != State.IDLE) {
            if (stateTicks > MAX_STATE_TICKS) { abort(mc, p, "timeout"); return; }
            if (maceTarget == null || !maceTarget.isAlive() || p.distanceTo(maceTarget) > 40) {
                abort(mc, p, "target lost"); return;
            }
        }
        switch (state) {
            case IDLE -> {
                String missing = checkMaceItems(p);
                if (missing != null) { AUTO_MACE.on = false; p.sendMessage(Text.literal(NAME + " | " + missing), true); return; }
                maceTarget = nearest(mc, p, MACE_RANGE.value);
                if (maceTarget == null) return;
                if (!p.isOnGround()) return;
                p.jump();
                setState(State.WIND1, 3);
            }
            case WIND1 -> {
                p.setPitch(90f);
                select(p, SLOT_WIND);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                setState(State.RISE1, 3);
            }
            case RISE1 -> {
                if (!p.isOnGround() && p.getVelocity().y > 0.1) setState(State.ELYTRA, 0);
                else if (p.isOnGround() && stateTicks > 8) abort(mc, p, "wind charge failed");
            }
            case ELYTRA -> {
                if (!elytraEquipped) { swapChest(mc, p); elytraEquipped = true; }
                p.startGliding();
                p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                setState(State.PEARL, 2);
            }
            case PEARL -> {
                lookAt(p, maceTarget.getEyePos());
                select(p, SLOT_PEARL);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                lastPos = new Vec3d(p.getX(), p.getY(), p.getZ());
                setState(State.GLIDE, 0);
            }
            case GLIDE -> {
                lookAt(p, maceTarget.getEyePos());
                Vec3d now = new Vec3d(p.getX(), p.getY(), p.getZ());
                boolean teleported = now.distanceTo(lastPos) > 4.0;
                lastPos = now;
                if (teleported || (p.isOnGround() && stateTicks > 10)) setState(State.LAND, 1);
            }
            case LAND -> {
                if (elytraEquipped) { swapChest(mc, p); elytraEquipped = false; }
                if (p.distanceTo(maceTarget) > 12) { abort(mc, p, "pearl landed too far"); return; }
                setState(State.WIND2, 2);
            }
            case WIND2 -> {
                p.setPitch(90f);
                select(p, SLOT_WIND);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                setState(State.RISE2, 3);
            }
            case RISE2 -> {
                if (!p.isOnGround() && p.getVelocity().y < -0.2 && p.getY() - maceTarget.getY() > 3.0) {
                    select(p, SLOT_MACE); setState(State.SMASH, 0);
                } else if (p.isOnGround() && stateTicks > 8) abort(mc, p, "second wind charge failed");
            }
            case SMASH -> {
                lookAt(p, maceTarget.getEyePos());
                select(p, SLOT_MACE);
                if (p.distanceTo(maceTarget) <= ATTACK_REACH) {
                    mc.interactionManager.attackEntity(p, maceTarget);
                    p.swingHand(Hand.MAIN_HAND);
                    finishMace(p);
                } else if (p.isOnGround()) abort(mc, p, "missed");
            }
            default -> { }
        }
    }

    private static void finishMace(ClientPlayerEntity p) {
        if (REPEAT_COMBO.on) setState(State.IDLE, 40);
        else { AUTO_MACE.on = false; setState(State.IDLE, 0); p.sendMessage(Text.literal(NAME + " | Auto Mace: done"), true); }
    }

    private static void abort(MinecraftClient mc, ClientPlayerEntity p, String msg) {
        if (elytraEquipped) { swapChest(mc, p); elytraEquipped = false; }
        setState(State.IDLE, 0);
        if (msg != null) { AUTO_MACE.on = false; p.sendMessage(Text.literal(NAME + " | Auto Mace: " + msg), true); }
    }

    private static String checkMaceItems(ClientPlayerEntity p) {
        var inv = p.getInventory();
        if (!inv.getStack(SLOT_MACE).isOf(Items.MACE)) return "put Mace in slot " + (SLOT_MACE + 1);
        if (!inv.getStack(SLOT_WIND).isOf(Items.WIND_CHARGE) || inv.getStack(SLOT_WIND).getCount() < 2)
            return "need 2+ Wind Charges in slot " + (SLOT_WIND + 1);
        if (!inv.getStack(SLOT_PEARL).isOf(Items.ENDER_PEARL)) return "put Ender Pearl in slot " + (SLOT_PEARL + 1);
        if (!inv.getStack(SLOT_ELYTRA).isOf(Items.ELYTRA)) return "put Elytra in slot " + (SLOT_ELYTRA + 1);
        var chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isEmpty() || chest.isOf(Items.ELYTRA)) return "wear a chestplate first";
        return null;
    }

    private static void swapChest(MinecraftClient mc, ClientPlayerEntity p) {
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, CHEST_SLOT, SLOT_ELYTRA, SlotActionType.SWAP, p);
    }

    // =========================================================
    //                    COMMON HELPERS
    // =========================================================
    private static PlayerEntity nearest(MinecraftClient mc, ClientPlayerEntity self, double range) {
        PlayerEntity best = null;
        double bestD = range * range;
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == self || !other.isAlive() || other.isSpectator()) continue;
            double d = self.squaredDistanceTo(other);
            if (d < bestD) { bestD = d; best = other; }
        }
        return best;
    }

    private static void select(ClientPlayerEntity p, int slot) { p.getInventory().setSelectedSlot(slot); }

    private static void lookAt(ClientPlayerEntity p, Vec3d pos) {
        Vec3d eye = p.getEyePos();
        double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        p.setYaw(yaw); p.setPitch(pitch);
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    private static int findItemInHotbar(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    // =========================================================
    //                    PVP - SWORD
    // =========================================================
    private static void killAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (!KILLAURA.on || AUTO_MACE.on || mc.currentScreen != null) { auraCooldown = 0; return; }
        if (auraCooldown > 0) { auraCooldown--; return; }
        PlayerEntity target = nearest(mc, p, AURA_RANGE.value);
        if (target == null) return;
        if (p.getAttackCooldownProgress(0f) < 0.9f) return;
        if (!p.canSee(target)) return;
        if (CRITICALS.on && p.isOnGround() && p.getVelocity().y == 0 && !p.isSprinting()
                && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.BLINDNESS)) {
            p.jump(); auraCooldown = 1; return;
        }
        lookAt(p, target.getEyePos());
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        auraCooldown = Math.max(1, 20 / Math.max(1, AURA_CPS.value));
    }

    private static void aimAssist(MinecraftClient mc, ClientPlayerEntity p) {
        if (!AIMASSIST.on || AUTO_MACE.on || KILLAURA.on || mc.currentScreen != null) return;
        Vec3d eye = p.getEyePos();
        PlayerEntity target = null;
        double bestScore = Double.MAX_VALUE;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator()) continue;
            double d = p.distanceTo(o);
            if (d > 8.0) continue;
            Vec3d tEye = o.getEyePos();
            double dx = tEye.x - eye.x, dy = tEye.y - eye.y, dz = tEye.z - eye.z;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
            float angle = Math.max(Math.abs(wrapDegrees(wantYaw - p.getYaw())), Math.abs(wantPitch - p.getPitch()));
            if (angle > 30f) continue;
            double score = angle + d * 2.0;
            if (score < bestScore) { bestScore = score; target = o; }
        }
        if (target == null) return;
        Vec3d tEye = target.getEyePos();
        double dx = tEye.x - eye.x, dy = tEye.y - eye.y, dz = tEye.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        float speedF = Math.min(1f, AIM_SPEED.value / 100f);
        p.setYaw(p.getYaw() + wrapDegrees(wantYaw - p.getYaw()) * speedF);
        p.setPitch(p.getPitch() + (wantPitch - p.getPitch()) * speedF);
    }

    private static void triggerBot(MinecraftClient mc, ClientPlayerEntity p) {
        if (!TRIGGERBOT.on || AUTO_MACE.on || KILLAURA.on || mc.currentScreen != null) { triggerCooldown = 0; return; }
        if (triggerCooldown > 0) { triggerCooldown--; return; }
        if (!(mc.crosshairTarget instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof PlayerEntity target)) return;
        if (target == p || !target.isAlive()) return;
        if (p.distanceTo(target) > 4.5) return;
        if (p.getAttackCooldownProgress(0f) < 0.9f) return;
        if (!p.canSee(target)) return;
        if (CRITICALS.on && p.isOnGround() && p.getVelocity().y == 0 && !p.isSprinting()
                && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.BLINDNESS)) {
            p.jump(); triggerCooldown = 1; return;
        }
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        triggerCooldown = 2;
    }

    private static void autoSword(MinecraftClient mc, ClientPlayerEntity p) {
        if (!AUTO_SWORD.on || mc.currentScreen != null) return;
        if (mc.options.attackKey.isPressed()) return;
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            int score = 0;
            if (st.isOf(Items.NETHERITE_SWORD)) score = 100;
            else if (st.isOf(Items.DIAMOND_SWORD)) score = 90;
            else if (st.isOf(Items.IRON_SWORD)) score = 80;
            else if (st.isOf(Items.STONE_SWORD)) score = 70;
            else if (st.isOf(Items.GOLDEN_SWORD)) score = 60;
            else if (st.isOf(Items.WOODEN_SWORD)) score = 50;
            else if (st.isOf(Items.NETHERITE_AXE)) score = 45;
            else if (st.isOf(Items.DIAMOND_AXE)) score = 40;
            else if (st.isOf(Items.MACE)) score = 30;
            else if (st.isOf(Items.TRIDENT)) score = 35;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (best >= 0 && best != p.getInventory().getSelectedSlot()) select(p, best);
    }

    private static void velocity(MinecraftClient mc, ClientPlayerEntity p) {
        if (!VELOCITY.on) { lastHealth = p.getHealth(); return; }
        float hp = p.getHealth();
        if (hp < lastHealth - 0.1f && hp > 0) {
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x * 0.2, v.y, v.z * 0.2);
        }
        lastHealth = hp;
    }

    // =========================================================
    //                    PVP - BOW
    // =========================================================
    private static void bowAimbot(MinecraftClient mc, ClientPlayerEntity p) {
        if (!BOW_AIMBOT.on || mc.currentScreen != null) return;
        if (!p.isUsingItem()) return;
        if (!(p.getActiveItem().isOf(Items.BOW) || p.getActiveItem().isOf(Items.CROSSBOW))) return;
        PlayerEntity target = nearest(mc, p, 40);
        if (target == null) return;
        Vec3d aim = predictArrowPos(p, target);
        lookAt(p, aim);
    }

    private static Vec3d predictArrowPos(ClientPlayerEntity p, PlayerEntity target) {
        Vec3d eye = p.getEyePos();
        Vec3d tPos = target.getEntityPos().add(0, target.getHeight() * 0.6, 0);
        double dist = eye.distanceTo(tPos);
        Vec3d tv = target.getVelocity();
        double arrowSpeed = 3.0;
        double time = dist / arrowSpeed;
        Vec3d predicted = tPos.add(tv.multiply(time, time, time));
        double drop = 0.05 * time * time * 9.8;
        return predicted.add(0, drop, 0);
    }

    // =========================================================
    //                    PVP - CRYSTAL
    // =========================================================
    private static void crystalAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (!CRYSTAL_AURA.on || mc.currentScreen != null) return;
        if (p.getAttackCooldownProgress(0f) < 0.9f) return;
        EndCrystalEntity best = null;
        double bestD = CRYSTAL_RANGE.value * CRYSTAL_RANGE.value;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity c) {
                double d = p.squaredDistanceTo(c);
                if (d < bestD) { bestD = d; best = c; }
            }
        }
        if (best == null) return;
        lookAt(p, best.getEntityPos());
        mc.interactionManager.attackEntity(p, best);
        p.swingHand(Hand.MAIN_HAND);
    }

    private static void autoCrystal(MinecraftClient mc, ClientPlayerEntity p) {
        if (!AUTO_CRYSTAL.on || mc.currentScreen != null) return;
        if (mc.currentScreen != null || mc.player == null) return;
        if (p.getAttackCooldownProgress(0f) < 0.9f) return;
        // Attack nearby crystal first
        EndCrystalEntity nearestCrystal = null;
        double cd = CRYSTAL_RANGE.value * CRYSTAL_RANGE.value;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity c) {
                double d = p.squaredDistanceTo(c);
                if (d < cd) { cd = d; nearestCrystal = c; }
            }
        }
        if (nearestCrystal != null) {
            int swordSlot = findItemInHotbar(p, Items.NETHERITE_SWORD);
            if (swordSlot < 0) swordSlot = findItemInHotbar(p, Items.DIAMOND_SWORD);
            if (swordSlot >= 0) {
                select(p, swordSlot);
                lookAt(p, nearestCrystal.getEntityPos());
                mc.interactionManager.attackEntity(p, nearestCrystal);
                p.swingHand(Hand.MAIN_HAND);
            }
            return;
        }
        // Place new crystal
        int crystalSlot = findItemInHotbar(p, Items.END_CRYSTAL);
        if (crystalSlot < 0) return;
        BlockPos placePos = findCrystalSpot(mc, p);
        if (placePos == null) return;
        select(p, crystalSlot);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(placePos), Direction.UP, placePos, false);
        lookAt(p, Vec3d.ofCenter(placePos));
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
    }

    private static BlockPos findCrystalSpot(MinecraftClient mc, ClientPlayerEntity p) {
        BlockPos base = p.getBlockPos();
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    BlockState st = mc.world.getBlockState(pos);
                    if ((st.isOf(net.minecraft.block.Blocks.OBSIDIAN) || st.isOf(net.minecraft.block.Blocks.BEDROCK))
                            && mc.world.isAir(pos.up())) {
                        if (p.squaredDistanceTo(pos.toCenterPos()) <= CRYSTAL_RANGE.value * CRYSTAL_RANGE.value)
                            return pos;
                    }
                }
            }
        }
        return null;
    }

    // =========================================================
    //                    PVP - ANCHOR
    // =========================================================
    private static void anchorAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (!ANCHOR_AURA.on || mc.currentScreen == null) return;
        BlockPos anchor = findAnchor(mc, p);
        if (anchor == null) return;
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
        lookAt(p, Vec3d.ofCenter(anchor));
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
    }

    private static void autoAnchor(MinecraftClient mc, ClientPlayerEntity p) {
        if (!AUTO_ANCHOR.on || mc.currentScreen != null) return;
        BlockPos anchor = findAnchor(mc, p);
        if (anchor == null) return;
        BlockState st = mc.world.getBlockState(anchor);
        // If not charged, use glowstone
        int glowSlot = findItemInHotbar(p, Items.GLOWSTONE);
        if (glowSlot >= 0) {
            select(p, glowSlot);
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
            lookAt(p, Vec3d.ofCenter(anchor));
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    private static BlockPos findAnchor(MinecraftClient mc, ClientPlayerEntity p) {
        BlockPos base = p.getBlockPos();
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    BlockState st = mc.world.getBlockState(pos);
                    if (st.isOf(net.minecraft.block.Blocks.RESPAWN_ANCHOR)) {
                        if (p.squaredDistanceTo(pos.toCenterPos()) <= ANCHOR_RANGE.value * ANCHOR_RANGE.value)
                            return pos;
                    }
                }
            }
        }
        return null;
    }

    // =========================================================
    //                    MOVEMENT
    // =========================================================
    private static void movementSpeed(MinecraftClient mc, ClientPlayerEntity p) {
        if (!SPEED.on || mc.currentScreen != null) return;
        if (!p.isOnGround()) return;
        if (!(mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed())) return;
        Vec3d v = p.getVelocity();
        double mult = SPEED_MULT.value / 100.0;
        p.setVelocity(v.x * (1 + mult), v.y, v.z * (1 + mult));
    }

    private static void noFall(MinecraftClient mc, ClientPlayerEntity p) {
        if (!NOFALL.on) return;
        if (p.fallDistance > 2.0f) {
            p.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
            p.fallDistance = 0f;
        }
    }

    // =========================================================
    //                    SMP - PROJECTION
    // =========================================================
    private static int[] projectToScreen(Vec3d worldPos, Vec3d camPos, float yaw, float pitch, int sw, int sh, double focal) {
        Vec3d delta = worldPos.subtract(camPos);
        double cosY = Math.cos(Math.toRadians(-yaw));
        double sinY = Math.sin(Math.toRadians(-yaw));
        Vec3d r1 = new Vec3d(delta.x * cosY + delta.z * sinY, delta.y, -delta.x * sinY + delta.z * cosY);
        double cosP = Math.cos(Math.toRadians(-pitch));
        double sinP = Math.sin(Math.toRadians(-pitch));
        Vec3d r2 = new Vec3d(r1.x, r1.y * cosP - r1.z * sinP, r1.y * sinP + r1.z * cosP);
        if (r2.z > -0.1) return null;
        int sx = (int) (sw / 2.0 + (r2.x / -r2.z) * focal);
        int sy = (int) (sh / 2.0 - (r2.y / -r2.z) * focal);
        if (sx < -100 || sx > sw + 100 || sy < -100 || sy > sh + 100) return null;
        return new int[]{sx, sy};
    }

    // =========================================================
    //                      COLOR HELPERS
    // =========================================================
    static int accent(int offset) {
        float h = (((System.currentTimeMillis() / 15) + offset * 12L) % 360L) / 360f;
        return hsv(h, 0.55f, 1f);
    }

    static int hsv(float h, float s, float v) {
        float hh = h * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (((i % 6) + 6) % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    static int scale(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int withAlpha(int argb, int alpha) { return (alpha << 24) | (argb & 0xFFFFFF); }

    static void rainbowText(DrawContext ctx, TextRenderer tr, String text, int x, int y) {
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            ctx.drawTextWithShadow(tr, ch, cx, y, accent(i * 3));
            cx += tr.getWidth(ch);
        }
    }

    // =========================================================
    //                          HUD
    // =========================================================
    private static int hudLine(DrawContext ctx, TextRenderer tr, String s, int y) {
        int w = tr.getWidth(s) + 10;
        ctx.fill(4, y, 4 + w, y + 13, 0xA0101018);
        ctx.fill(4, y, 6, y + 13, accent(y));
        ctx.drawTextWithShadow(tr, s, 10, y + 3, 0xFFFFFFFF);
        return y + 14;
    }

    private static void renderESP(DrawContext ctx, TextRenderer tr, MinecraftClient mc, ClientPlayerEntity p) {
        if (!PLAYER_ESP.on && !ITEM_ESP.on && !CHEST_ESP.on && !TRACERS.on) return;
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        double fov = mc.options.getFov().getValue();
        double focal = (sh / 2.0) / Math.tan(Math.toRadians(fov / 2.0));
        double maxD = ESP_RANGE.value;

        if (PLAYER_ESP.on || TRACERS.on) {
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == p || !o.isAlive()) continue;
                double d = p.distanceTo(o);
                if (d > maxD) continue;
                int[] sc = projectToScreen(o.getEntityPos().add(0, o.getHeight() + 0.3, 0), camPos, camYaw, camPitch, sw, sh, focal);
                if (sc == null) continue;
                if (PLAYER_ESP.on) {
                    String label = o.getName().getString() + " " + (int) d + "m " + (int) Math.ceil(o.getHealth()) + "hp";
                    int w = tr.getWidth(label);
                    ctx.fill(sc[0] - w / 2 - 2, sc[1] - 20, sc[0] + w / 2 + 2, sc[1] - 4, 0xA0000000);
                    ctx.drawTextWithShadow(tr, label, sc[0] - w / 2, sc[1] - 18, accent(sc[0]));
                }
                if (TRACERS.on) {
                    ctx.fill(sw / 2, sh - 2, sc[0], sh, accent(sc[0] + sc[1]));
                }
            }
        }

        if (CHEST_ESP.on || ITEM_ESP.on) {
            for (Entity e : mc.world.getEntities()) {
                if (e == p) continue;
                double d = p.distanceTo(e);
                if (d > maxD) continue;
                boolean isItem = e instanceof net.minecraft.entity.ItemEntity;
                boolean isChest = false;
                if (CHEST_ESP.on && e instanceof net.minecraft.entity.vehicle.ChestBoatEntity) isChest = true;
                if (!isItem && !isChest) continue;
                if (isItem && !ITEM_ESP.on) continue;
                if (isChest && !CHEST_ESP.on) continue;
                int[] sc = projectToScreen(e.getEntityPos().add(0, 0.5, 0), camPos, camYaw, camPitch, sw, sh, focal);
                if (sc == null) continue;
                String label = isItem ? "Item " + (int) d + "m" : "Chest " + (int) d + "m";
                int w = tr.getWidth(label);
                ctx.fill(sc[0] - w / 2 - 1, sc[1] - 12, sc[0] + w / 2 + 1, sc[1], 0xA0000000);
                ctx.drawTextWithShadow(tr, label, sc[0] - w / 2, sc[1] - 10, accent(sc[0]));
            }
        }
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden || mc.currentScreen instanceof Menu) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        renderESP(ctx, tr, mc, p);

        int y = 4;
        if (WATERMARK.on) {
            int w = tr.getWidth(NAME) + 14;
            ctx.fill(4, y, 4 + w, y + 16, 0xB0101018);
            ctx.fill(4, y, 6, y + 16, accent(0));
            rainbowText(ctx, tr, NAME, 11, y + 4);
            y += 20;
        }
        if (FPS_HUD.on) y = hudLine(ctx, tr, "FPS: " + mc.getCurrentFps(), y);
        if (PING_HUD.on) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            y = hudLine(ctx, tr, "Ping: " + (e == null ? 0 : e.getLatency()) + "ms", y);
        }
        if (COORDS_HUD.on) y = hudLine(ctx, tr, "XYZ: " + (int) Math.floor(p.getX()) + " " + (int) Math.floor(p.getY()) + " " + (int) Math.floor(p.getZ()), y);
        if (SPEED_HUD.on) y = hudLine(ctx, tr, String.format("Speed: %.1f b/s", speed), y);
        if (CLOCK_HUD.on) {
            LocalTime now = LocalTime.now();
            y = hudLine(ctx, tr, String.format("Time: %02d:%02d", now.getHour(), now.getMinute()), y);
        }
        if (WAYPOINT_HUD.on && !WAYPOINTS.isEmpty()) {
            String dim = mc.world.getRegistryKey().getValue().toString();
            List<Waypoint> here = new ArrayList<>();
            for (Waypoint w : WAYPOINTS) if (w.dim().equals(dim)) here.add(w);
            double px = p.getX(), pz = p.getZ();
            here.sort(Comparator.comparingDouble((Waypoint w) -> (w.x() - px) * (w.x() - px) + (w.z() - pz) * (w.z() - pz)));
            for (int i = 0; i < Math.min(4, here.size()); i++) {
                Waypoint w = here.get(i);
                double ddx = w.x() + 0.5 - px, ddz = w.z() + 0.5 - pz;
                int dist = (int) Math.sqrt(ddx * ddx + ddz * ddz);
                y = hudLine(ctx, tr, w.name() + "  " + dist + "m " + cardinal(ddx, ddz), y);
            }
        }
        if (PLAYERS_HUD.on) {
            List<PlayerEntity> near = new ArrayList<>();
            for (PlayerEntity o : mc.world.getPlayers()) if (o != p && p.distanceTo(o) <= 64) near.add(o);
            near.sort(Comparator.comparingDouble(o -> p.squaredDistanceTo(o)));
            for (int i = 0; i < Math.min(5, near.size()); i++) {
                PlayerEntity o = near.get(i);
                y = hudLine(ctx, tr, o.getName().getString() + "  " + (int) p.distanceTo(o) + "m  " + (int) Math.ceil(o.getHealth()) + "hp", y);
            }
        }
        if (ARRAYLIST.on) {
            List<Mod> active = new ArrayList<>();
            for (Mod m : MODS) if (m.on && !m.action && m.cat != Cat.HUD) active.add(m);
            active.sort(Comparator.comparingInt((Mod m) -> -tr.getWidth(m.name)));
            int ay = 4;
            for (int i = 0; i < active.size(); i++) {
                Mod m = active.get(i);
                int w = tr.getWidth(m.name) + 10;
                int x = sw - w - 4;
                ctx.fill(x, ay, x + w, ay + 13, 0xA0101018);
                ctx.fill(sw - 6, ay, sw - 4, ay + 13, accent(i * 8));
                ctx.drawTextWithShadow(tr, m.name, x + 4, ay + 3, accent(i * 8));
                ay += 14;
            }
        }
        if (KEYSTROKES.on) {
            int kx = 6, ky = sh - 70;
            drawKey(ctx, tr, "W", kx + 22, ky, mc.options.forwardKey.isPressed());
            drawKey(ctx, tr, "A", kx, ky + 22, mc.options.leftKey.isPressed());
            drawKey(ctx, tr, "S", kx + 22, ky + 22, mc.options.backKey.isPressed());
            drawKey(ctx, tr, "D", kx + 44, ky + 22, mc.options.rightKey.isPressed());
        }
        if (ARMOR_HUD.on) {
            int ax = 6, ay = sh / 2 - 36;
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                ItemStack st = p.getEquippedStack(ARMOR_SLOTS[i]);
                if (st.isEmpty()) continue;
                int iy = ay + i * 18;
                ctx.drawItem(st, ax, iy);
                if (st.getMaxDamage() > 0) {
                    int pct = 100 - (st.getDamage() * 100 / st.getMaxDamage());
                    int col = pct > 60 ? 0xFF55FF55 : pct > 30 ? 0xFFFFFF55 : 0xFFFF5555;
                    ctx.drawTextWithShadow(tr, pct + "%", ax + 20, iy + 4, col);
                }
            }
        }
    }

    private static void drawKey(DrawContext ctx, TextRenderer tr, String key, int x, int y, boolean pressed) {
        int bg = pressed ? withAlpha(accent(x), 0xC0) : 0x90101018;
        ctx.fill(x, y, x + 20, y + 20, bg);
        int tw = tr.getWidth(key);
        ctx.drawTextWithShadow(tr, key, x + (20 - tw) / 2, y + 6, 0xFFFFFFFF);
    }

    // =========================================================
    //                        MENU
    // =========================================================
    enum Kind { TAB, TOGGLE, ACTION, STEP, VALUE }

    record Widget(int x, int y, int w, int h, String label, Kind kind,
                  BooleanSupplier active, String desc, Supplier<String> tag) { }

    static class Menu extends Screen {
        static final int PW = 480, PH = 420;
        private final long openTime = System.currentTimeMillis();
        private final List<Widget> widgets = new ArrayList<>();

        Menu() { super(Text.literal(NAME)); }

        private int px() { return (this.width - PW) / 2; }
        private int py() { return (this.height - PH) / 2; }

        private void hit(int x, int y, int w, int h, Runnable action) {
            addDrawableChild(ButtonWidget.builder(Text.empty(), b -> action.run()).dimensions(x, y, w, h).build());
        }

        @Override
        protected void init() {
            widgets.clear();
            int px = px(), py = py();
            int sx = px + 8, sy = py + 42, i = 0;
            for (Cat c : Cat.values()) {
                final Cat cc = c;
                int ty = sy + i * 28;
                hit(sx, ty, 108, 24, () -> { lastTab = cc; save(); clearAndInit(); });
                widgets.add(new Widget(sx, ty, 108, 24, c.label, Kind.TAB, () -> lastTab == cc, c.label + " modules", null));
                i++;
            }
            int cx = px + 124, cy = py + 42;
            if (lastTab == Cat.WAYPOINTS) { buildWaypoints(cx, cy); return; }

            int col = 0, row = 0;
            for (Mod m : MODS) {
                if (m.cat != lastTab) continue;
                int mx = cx + col * 172, my = cy + row * 30;
                hit(mx, my, 168, 26, () -> {
                    if (m.action) {
                        if (m == STORAGE) { close(); findStorage(this.client); }
                        else if (m == BASE_FINDER) { close(); findBase(this.client); }
                        else if (m == SPAWNER_FINDER) { close(); findSpawners(this.client); }
                    } else { m.on = !m.on; save(); }
                });
                widgets.add(new Widget(mx, my, 168, 26, m.name, m.action ? Kind.ACTION : Kind.TOGGLE,
                        () -> m.on, m.desc, m.action ? () -> "RUN" : null));
                col++;
                if (col == 2) { col = 0; row++; }
            }
            if (col == 1) row++;

            for (Num n : NUMS) {
                if (n.cat != lastTab) continue;
                int ny = cy + row * 30;
                widgets.add(new Widget(cx, ny, 250, 26, n.name, Kind.VALUE, () -> false, n.desc, () -> String.valueOf(n.value)));
                hit(cx + 254, ny, 22, 26, () -> { n.change(-1); save(); });
                widgets.add(new Widget(cx + 254, ny, 22, 26, "-", Kind.STEP, () -> false, n.desc, null));
                hit(cx + 278, ny, 22, 26, () -> { n.change(1); save(); });
                widgets.add(new Widget(cx + 278, ny, 22, 26, "+", Kind.STEP, () -> false, n.desc, null));
                row++;
            }
        }

        private void buildWaypoints(int cx, int cy) {
            hit(cx, cy, 168, 26, () -> { addWaypoint(this.client, null); clearAndInit(); });
            widgets.add(new Widget(cx, cy, 168, 26, "Add Here", Kind.ACTION, () -> false, "Save waypoint (B)", () -> "ADD"));
            hit(cx + 172, cy, 168, 26, () -> { WAYPOINTS.clear(); save(); clearAndInit(); });
            widgets.add(new Widget(cx + 172, cy, 168, 26, "Clear All", Kind.ACTION, () -> false, "Delete all", () -> "CLR"));
            int row = 1;
            for (int i = 0; i < Math.min(9, WAYPOINTS.size()); i++) {
                final int idx = i;
                Waypoint w = WAYPOINTS.get(i);
                int wy = cy + row * 30;
                hit(cx, wy, 340, 26, () -> {
                    if (idx < WAYPOINTS.size()) WAYPOINTS.remove(idx);
                    save(); clearAndInit();
                });
                widgets.add(new Widget(cx, wy, 340, 26, w.name() + "  " + w.x() + " " + w.y() + " " + w.z(),
                        Kind.ACTION, () -> false, "Click to delete", () -> "DEL"));
                row++;
            }
        }

        @Override public void removed() { save(); }

        @Override
        public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
            float t = Math.min(1f, (System.currentTimeMillis() - openTime) / 200f);
            ctx.fill(0, 0, this.width, this.height, ((int) (0xA0 * t)) << 24);
            int px = px(), py = py();
            ctx.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, withAlpha(accent(0), 0xC0));
            ctx.fill(px, py, px + PW, py + PH, 0xFF101018);
            ctx.fill(px, py, px + PW, py + 32, 0xFF1A1A26);
            ctx.fill(px, py + 32, px + PW, py + 34, accent(10));
            ctx.fill(px + 4, py + 38, px + 120, py + PH - 30, 0xFF181820);
            ctx.fill(px + 124, py + 38, px + PW - 4, py + PH - 30, 0xFF181820);
            ctx.fill(px + 4, py + PH - 26, px + PW - 4, py + PH - 4, 0xFF181820);
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            TextRenderer tr = this.textRenderer;
            int px = px(), py = py();
            rainbowText(ctx, tr, "KINGER LOL", px + 12, py + 12);
            String ver = VERSION + "  |  1.21.11";
            ctx.drawTextWithShadow(tr, ver, px + PW - tr.getWidth(ver) - 12, py + 12, 0xFF777788);
            String hoverDesc = null;
            for (Widget w : widgets) {
                boolean hov = mouseX >= w.x() && mouseX < w.x() + w.w() && mouseY >= w.y() && mouseY < w.y() + w.h();
                boolean on = w.active().getAsBoolean();
                int accent = accent(w.x() / 10 + w.y() / 10);
                int bg;
                if (w.kind() == Kind.VALUE) bg = 0xFF1D1D28;
                else if (on && (w.kind() == Kind.TOGGLE || w.kind() == Kind.TAB)) bg = scale(accent, hov ? 0.55f : 0.45f);
                else bg = hov ? 0xFF2E2E3E : 0xFF23232E;
                ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), bg);
                if (on && (w.kind() == Kind.TOGGLE || w.kind() == Kind.TAB))
                    ctx.fill(w.x(), w.y(), w.x() + 3, w.y() + w.h(), accent);
                if (hov && w.kind() != Kind.VALUE)
                    ctx.fill(w.x(), w.y() + w.h() - 1, w.x() + w.w(), w.y() + w.h(), accent);
                int ty = w.y() + (w.h() - 8) / 2;
                if (w.kind() == Kind.STEP) {
                    int tw = tr.getWidth(w.label());
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + (w.w() - tw) / 2, ty, 0xFFFFFFFF);
                } else {
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + 10, ty, on ? 0xFFFFFFFF : 0xFFBBBBCC);
                    String tag = null;
                    int col = 0xFF88BBFF;
                    if (w.tag() != null) { tag = w.tag().get(); if (w.kind() == Kind.VALUE) col = 0xFFFFFFFF; }
                    else if (w.kind() == Kind.TOGGLE) { tag = on ? "ON" : "OFF"; col = on ? 0xFF55FF77 : 0xFFFF5566; }
                    if (tag != null) ctx.drawTextWithShadow(tr, tag, w.x() + w.w() - tr.getWidth(tag) - 8, ty, col);
                }
                if (hov && w.desc() != null) hoverDesc = w.desc();
            }
            String footer = hoverDesc != null ? hoverDesc : "ESC = close   |   Numpad 5 = open menu   |   V = Kill Aura   |   R = Auto Mace";
            ctx.drawTextWithShadow(tr, footer, px + 12, py + PH - 19, 0xFFAAAABB);
        }

        @Override public boolean shouldPause() { return false; }
    }

    // =========================================================
    //                    BASE / SPAWNER FINDER
    // =========================================================
    static void findBase(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        List<BlockPos> clusters = new ArrayList<>();
        BlockPos base = p.getBlockPos();
        int hits = 0;
        for (int dx = -32; dx <= 32; dx += 2) {
            for (int dy = -16; dy <= 16; dy += 2) {
                for (int dz = -32; dz <= 32; dz += 2) {
                    BlockPos pos = base.add(dx, dy, dz);
                    BlockState st = mc.world.getBlockState(pos);
                    if (st.isOf(net.minecraft.block.Blocks.FURNACE) || st.isOf(net.minecraft.block.Blocks.CRAFTING_TABLE)
                            || st.isOf(net.minecraft.block.Blocks.ANVIL) || st.isOf(net.minecraft.block.Blocks.ENCHANTING_TABLE)
                            || st.isOf(net.minecraft.block.Blocks.RED_BED)) {
                        hits++;
                        if (hits <= 10) clusters.add(pos.toImmutable());
                    }
                }
            }
        }
        p.sendMessage(Text.literal(NAME + " | Base Finder: " + hits + " suspicious blocks"), false);
        for (BlockPos b : clusters) {
            p.sendMessage(Text.literal("  " + b.getX() + " " + b.getY() + " " + b.getZ()), false);
        }
    }

    static void findSpawners(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        List<BlockPos> found = new ArrayList<>();
        ChunkPos center = p.getChunkPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                Chunk chunk = mc.world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntityPositions()) {
                    BlockState st = mc.world.getBlockState(pos);
                    if (st.isOf(net.minecraft.block.Blocks.SPAWNER)) found.add(pos.toImmutable());
                }
            }
        }
        p.sendMessage(Text.literal(NAME + " | Spawners found: " + found.size()), false);
        int n = Math.min(MAX_RESULTS, found.size());
        for (int i = 0; i < n; i++) {
            BlockPos b = found.get(i);
            int dist = (int) Math.sqrt(b.getSquaredDistance(p.getBlockPos()));
            p.sendMessage(Text.literal((i + 1) + ") " + b.getX() + " " + b.getY() + " " + b.getZ() + "  (" + dist + "m)"), false);
