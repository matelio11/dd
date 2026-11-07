package gg.kiteblocks.enchants;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Minecraft-specific custom enchantments for Paper. No vanilla XP, Vault or currencies are changed. */
public final class CustomEnchantsPlugin extends JavaPlugin implements Listener {
    private static final String MENU = ChatColor.DARK_PURPLE + "KiteBlocks | Custom Enchants";
    private final Random random = new Random();
    private final Map<UUID, Long> tricksterCooldown = new HashMap<>();
    private final Map<UUID, Long> scorchCooldown = new HashMap<>();

    private enum Enchant {
        DECAPITATION("Decapitation", 10, ChatColor.GREEN, "Swords & Axes", Material.PLAYER_HEAD,
                "Chance to obtain a defeated player's head."),
        WELLFED("WellFed", 5, ChatColor.GREEN, "Swords & Axes", Material.COOKED_BEEF,
                "Chance to refill hunger when attacking players."),
        TRICKSTER("Trickster", 5, ChatColor.YELLOW, "Swords", Material.SLIME_BALL,
                "Chance to shuffle the enemy's hotbar items."),
        SUMMONER("Summoner", 3, ChatColor.YELLOW, "All Weapons", Material.ENDER_EYE,
                "Chance to get 2 custom Summoning Eyes from Endermen."),
        SCORCH("Scorch", 5, ChatColor.YELLOW, "Swords", Material.BLAZE_POWDER,
                "Chance to ignite and slow an enemy player.");

        final String display;
        final int maxLevel;
        final ChatColor color;
        final String allowed;
        final Material icon;
        final String description;

        Enchant(String display, int maxLevel, ChatColor color, String allowed, Material icon, String description) {
            this.display = display;
            this.maxLevel = maxLevel;
            this.color = color;
            this.allowed = allowed;
            this.icon = icon;
            this.description = description;
        }

        static Enchant find(String name) {
            for (Enchant e : values()) {
                if (e.name().equalsIgnoreCase(name)) return e;
            }
            return null;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Enabled 5 custom enchants for KiteBlocks. Use /enchants and /kbenchant apply.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("enchants")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players can use /enchants in-game.");
                return true;
            }
            showMenu(p);
            return true;
        }
        if (!command.getName().equalsIgnoreCase("kbenchant")) return false;
        if (!sender.hasPermission("kiteblocks.enchants.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Use this command as a player holding the item.");
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("apply")) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /kbenchant apply <name> <level>");
            return true;
        }
        Enchant e = Enchant.find(args[1]);
        if (e == null) {
            p.sendMessage(ChatColor.RED + "Unknown enchant. Use /enchants to see the five names.");
            return true;
        }
        int level;
        try { level = Integer.parseInt(args[2]); }
        catch (NumberFormatException err) { p.sendMessage(ChatColor.RED + "Level must be a whole number."); return true; }
        if (level < 1 || level > e.maxLevel) {
            p.sendMessage(ChatColor.RED + "Valid levels: 1 to " + e.maxLevel);
            return true;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!validWeapon(e, held)) {
            p.sendMessage(ChatColor.RED + e.display + " is for " + e.allowed + ". Hold a valid weapon.");
            return true;
        }
        applyEnchant(held, e, level);
        p.sendMessage(ChatColor.GREEN + "Applied " + e.display + " " + roman(level) + " to your weapon.");
        return true;
    }

    private boolean validWeapon(Enchant e, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        String type = stack.getType().name();
        boolean sword = type.endsWith("_SWORD");
        boolean axe = type.endsWith("_AXE") && !type.endsWith("_PICKAXE");
        if (e == Enchant.SUMMONER) {
            return sword || axe || stack.getType() == Material.BOW || stack.getType() == Material.CROSSBOW
                    || stack.getType() == Material.TRIDENT || stack.getType() == Material.MACE;
        }
        if (e == Enchant.TRICKSTER || e == Enchant.SCORCH) return sword;
        return sword || axe;
    }

    private NamespacedKey key(Enchant e) {
        return new NamespacedKey(this, "enchant_" + e.name().toLowerCase(Locale.ROOT));
    }

    private int level(ItemStack stack, Enchant enchant) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;
        Integer num = meta.getPersistentDataContainer().get(key(enchant), PersistentDataType.INTEGER);
        return num == null ? 0 : Math.max(0, Math.min(enchant.maxLevel, num));
    }

    private void applyEnchant(ItemStack stack, Enchant enchant, int level) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) throw new IllegalStateException("Item does not support metadata");
        meta.getPersistentDataContainer().set(key(enchant), PersistentDataType.INTEGER, level);
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> ChatColor.stripColor(line).startsWith(enchant.display + " "));
        lore.add(enchant.color + enchant.display + " " + roman(level));
        meta.setLore(lore);
        stack.setItemMeta(meta); // Bukkit method returns boolean in Paper 26.2.
    }

    private static String roman(int number) {
        String[] values = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number >= 1 && number < values.length ? values[number] : Integer.toString(number);
    }

    private void showMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU);
        for (int i = 0; i < Enchant.values().length; i++) {
            Enchant enchant = Enchant.values()[i];
            ItemStack icon = new ItemStack(enchant.icon);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(enchant.color + "" + ChatColor.BOLD + enchant.display);
                meta.setLore(Arrays.asList(
                        ChatColor.DARK_GREEN + "Enchant Information",
                        ChatColor.GRAY + "Max Level: " + ChatColor.GREEN + enchant.maxLevel,
                        ChatColor.GRAY + "Enchant Type: " + ChatColor.GREEN + enchant.allowed,
                        "",
                        ChatColor.DARK_GREEN + "Description",
                        ChatColor.GRAY + enchant.description,
                        "",
                        ChatColor.GREEN + "Click for details!"));
                icon.setItemMeta(meta);
            }
            inv.setItem(11 + i, icon);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!MENU.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();
        int ordinal = slot - 11;
        if (ordinal < 0 || ordinal >= Enchant.values().length) return;
        Enchant e = Enchant.values()[ordinal];
        p.sendMessage(e.color + "" + ChatColor.BOLD + e.display + ChatColor.RESET +
                ChatColor.GRAY + " (max " + e.maxLevel + ", " + e.allowed + ") — " + e.description);
    }

    private boolean chance(String configKey, int lvl) {
        double perLevel = getConfig().getDouble("chances." + configKey, 0.0);
        double percent = Math.max(0, Math.min(100, perLevel * lvl));
        return random.nextDouble() * 100.0 < percent;
    }

    private boolean ready(Map<UUID, Long> cooldowns, UUID id, String setting) {
        long now = System.currentTimeMillis();
        long old = cooldowns.getOrDefault(id, 0L);
        long seconds = getConfig().getLong("cooldowns." + setting, 5L);
        if (now < old + seconds * 1000L) return false;
        cooldowns.put(id, now);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player player) attacker = player;
        else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) attacker = player;
        }
        if (attacker == null || attacker.equals(victim)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        int wellFed = level(weapon, Enchant.WELLFED);
        if (wellFed > 0 && chance("wellfed-per-level", wellFed)) {
            attacker.setFoodLevel(Math.min(20, attacker.getFoodLevel() + 1 + wellFed));
            attacker.setSaturation(Math.min(20f, attacker.getSaturation() + 0.5f + wellFed * 0.5f));
        }
        int trickster = level(weapon, Enchant.TRICKSTER);
        if (trickster > 0 && chance("trickster-per-level", trickster)
                && ready(tricksterCooldown, victim.getUniqueId(), "trickster-seconds")) {
            PlayerInventory inventory = victim.getInventory();
            List<ItemStack> hotbar = new ArrayList<>();
            for (int i = 0; i < 9; i++) hotbar.add(inventory.getItem(i));
            Collections.shuffle(hotbar, random);
            for (int i = 0; i < 9; i++) inventory.setItem(i, hotbar.get(i));
            victim.sendMessage(ChatColor.GOLD + "Trickster shuffled your hotbar!");
        }
        int scorch = level(weapon, Enchant.SCORCH);
        if (scorch > 0 && chance("scorch-per-level", scorch)
                && ready(scorchCooldown, victim.getUniqueId(), "scorch-seconds")) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), 20 + scorch * 15));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 + scorch * 10, 0));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        int decapitation = level(killer.getInventory().getItemInMainHand(), Enchant.DECAPITATION);
        if (decapitation <= 0 || !chance("decapitation-per-level", decapitation)) return;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(victim);
            skull.setDisplayName(ChatColor.GREEN + victim.getName() + "'s Head");
            head.setItemMeta(skull);
        }
        event.getDrops().add(head);
        killer.sendMessage(ChatColor.GREEN + "Decapitation: you received " + victim.getName() + "'s head!");
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enderman)) return;
        Player killer = entity.getKiller();
        if (killer == null) return;
        int summoner = level(killer.getInventory().getItemInMainHand(), Enchant.SUMMONER);
        if (summoner <= 0 || !chance("summoner-per-level", summoner)) return;
        ItemStack eyes = new ItemStack(Material.ENDER_EYE, 2);
        ItemMeta meta = eyes.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Summoning Eye");
            meta.setLore(List.of(ChatColor.GRAY + "KiteBlocks custom Summoner reward"));
            eyes.setItemMeta(meta);
        }
        event.getDrops().add(eyes);
        killer.sendMessage(ChatColor.LIGHT_PURPLE + "Summoner: you received 2 Summoning Eyes!");
    }
}
