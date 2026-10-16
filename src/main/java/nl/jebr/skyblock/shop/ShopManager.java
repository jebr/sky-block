package nl.jebr.skyblock.shop;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * De servershop: laadt categorieen en prijzen uit shop.yml en regelt kopen en
 * verkopen. Alleen gewone items (zonder eigen naam, betoveringen of andere
 * metadata) kunnen worden verkocht.
 */
public class ShopManager {

    /** Uitkomst van een koop- of verkooppoging. */
    public enum Result { SUCCESS, NOT_FOR_SALE, NOT_ENOUGH_MONEY, INVENTORY_FULL, NOTHING_TO_SELL }

    private final SkyBlockPlugin plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    /** Verkoopbare items per materiaal (voor sellHand/sellAll). Eerste vermelding in shop.yml wint. */
    private final Map<Material, ShopItem> sellable = new EnumMap<>(Material.class);

    public ShopManager(SkyBlockPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    // ================================================================== laden

    /** (Her)laadt shop.yml uit de pluginmap. */
    public void reload() {
        categories.clear();
        sellable.clear();
        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("categories");
        if (root == null) {
            plugin.getLogger().warning("shop.yml bevat geen categorieen.");
            return;
        }
        int itemCount = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemSection = section.getConfigurationSection("items");
            if (itemSection != null) {
                for (String itemKey : itemSection.getKeys(false)) {
                    ConfigurationSection entry = itemSection.getConfigurationSection(itemKey);
                    if (entry == null) {
                        continue;
                    }
                    Material material = Material.matchMaterial(entry.getString("material", itemKey));
                    if (material == null || material.isAir() || !material.isItem()) {
                        plugin.getLogger().warning("Onbekend materiaal in shop.yml: " + itemKey);
                        continue;
                    }
                    int amount = Math.max(1, Math.min(64, entry.getInt("amount", 1)));
                    ShopItem item = new ShopItem(itemKey.toLowerCase(Locale.ROOT), material, amount,
                            entry.getDouble("buy", -1), entry.getDouble("sell", -1));
                    items.add(item);
                    if (item.canSell()) {
                        sellable.putIfAbsent(material, item);
                    }
                    itemCount++;
                }
            }
            Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
            String id = key.toLowerCase(Locale.ROOT);
            categories.put(id, new ShopCategory(id,
                    section.getString("name", key),
                    icon == null || icon.isAir() ? Material.CHEST : icon,
                    section.getInt("slot", -1),
                    section.getStringList("description"),
                    items));
        }
        plugin.getLogger().info(itemCount + " shopitems geladen in " + categories.size() + " categorieen.");
    }

    // ================================================================== opvragen

    public List<ShopCategory> getCategories() {
        return Collections.unmodifiableList(new ArrayList<>(categories.values()));
    }

    public ShopCategory getCategory(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    /** Het verkoopbare shopitem voor dit materiaal, of null. */
    public ShopItem getSellable(Material material) {
        return sellable.get(material);
    }

    /** Of dit item verkocht kan worden (juist materiaal en geen metadata). */
    public boolean isSellable(ItemStack item) {
        return item != null && !item.getType().isAir() && !item.hasItemMeta() && sellable.containsKey(item.getType());
    }

    /** Aantal gewone (verkoopbare) items van dit materiaal in de inventory. */
    public int countPlain(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material && !item.hasItemMeta()) {
                count += item.getAmount();
            }
        }
        return count;
    }

    // ================================================================== kopen

    /**
     * Koopt een aantal eenheden van een shopitem. Controleert saldo en vrije
     * ruimte; weigert als niet alles past. Stuurt zelf berichten.
     */
    public Result buy(Player player, ShopItem item, int units) {
        Messages messages = plugin.getMessages();
        if (!item.canBuy() || units <= 0) {
            messages.send(player, "shop.not-buyable", "{item}", item.getDisplayName());
            return Result.NOT_FOR_SALE;
        }
        int totalAmount = item.getAmount() * units;
        double price = item.getBuyPrice() * units;
        if (!plugin.getEconomy().has(player.getUniqueId(), price)) {
            messages.send(player, "general.not-enough-money", "{amount}", plugin.getEconomy().format(price));
            return Result.NOT_ENOUGH_MONEY;
        }
        if (getFreeSpace(player.getInventory(), item.getMaterial()) < totalAmount) {
            messages.send(player, "shop.inventory-full");
            return Result.INVENTORY_FULL;
        }
        if (!plugin.getEconomy().withdraw(player.getUniqueId(), price)) {
            messages.send(player, "general.not-enough-money", "{amount}", plugin.getEconomy().format(price));
            return Result.NOT_ENOUGH_MONEY;
        }
        int remaining = totalAmount;
        int maxStack = Math.max(1, item.getMaterial().getMaxStackSize());
        while (remaining > 0) {
            int stack = Math.min(remaining, maxStack);
            remaining -= stack;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(item.getMaterial(), stack));
            // Zou niet moeten gebeuren na de ruimtecontrole, maar voor de zekerheid laten vallen
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        messages.send(player, "shop.bought", "{amount}", totalAmount, "{item}", item.getDisplayName(),
                "{price}", plugin.getEconomy().format(price));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        return Result.SUCCESS;
    }

    /** Hoeveel items van dit materiaal er nog in de opslag van de inventory passen. */
    private int getFreeSpace(PlayerInventory inventory, Material material) {
        int maxStack = Math.max(1, material.getMaxStackSize());
        int free = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free += maxStack;
            } else if (item.getType() == material && !item.hasItemMeta()) {
                free += Math.max(0, maxStack - item.getAmount());
            }
        }
        return free;
    }

    // ================================================================== verkopen

    /**
     * Verkoopt een aantal eenheden van een shopitem uit de inventory. Stuurt
     * zelf berichten.
     *
     * @param units aantal eenheden; bij -1 alles wat de speler heeft (ook losse stuks)
     * @return het verdiende bedrag (0 als niets verkocht)
     */
    public double sell(Player player, ShopItem item, int units) {
        Messages messages = plugin.getMessages();
        if (!item.canSell()) {
            messages.send(player, "shop.not-sellable", "{item}", item.getDisplayName());
            return 0;
        }
        int have = countPlain(player, item.getMaterial());
        int toSell = units < 0 ? have : item.getAmount() * units;
        if (have <= 0 || have < toSell) {
            messages.send(player, "shop.not-enough-items", "{amount}", toSell, "{item}", item.getDisplayName());
            return 0;
        }
        removePlain(player, item.getMaterial(), toSell);
        double earned = round(item.getSellPricePerItem() * toSell);
        plugin.getEconomy().deposit(player.getUniqueId(), earned);
        messages.send(player, "shop.sold", "{amount}", toSell, "{item}", item.getDisplayName(),
                "{price}", plugin.getEconomy().format(earned));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
        return earned;
    }

    /** Verkoopt het item in de hoofdhand. Stuurt zelf berichten. */
    public double sellHand(Player player) {
        Messages messages = plugin.getMessages();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            messages.send(player, "shop.hand-empty");
            return 0;
        }
        if (!isSellable(hand)) {
            messages.send(player, "shop.hand-not-sellable");
            return 0;
        }
        ShopItem item = sellable.get(hand.getType());
        int amount = hand.getAmount();
        double earned = round(item.getSellPricePerItem() * amount);
        player.getInventory().setItemInMainHand(null);
        plugin.getEconomy().deposit(player.getUniqueId(), earned);
        messages.send(player, "shop.sold", "{amount}", amount, "{item}", item.getDisplayName(),
                "{price}", plugin.getEconomy().format(earned));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
        return earned;
    }

    /**
     * Verkoopt alle verkoopbare items uit de inventory (niet de pantser- en
     * tweede-handvakjes). Stuurt zelf berichten.
     */
    public double sellAll(Player player) {
        Messages messages = plugin.getMessages();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        Map<Material, Integer> sold = new EnumMap<>(Material.class);
        double earned = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isSellable(item)) {
                continue;
            }
            ShopItem shopItem = sellable.get(item.getType());
            earned += shopItem.getSellPricePerItem() * item.getAmount();
            sold.merge(item.getType(), item.getAmount(), Integer::sum);
            contents[slot] = null;
        }
        if (sold.isEmpty()) {
            messages.send(player, "shop.nothing-to-sell");
            return 0;
        }
        earned = round(earned);
        inventory.setStorageContents(contents);
        player.updateInventory();
        plugin.getEconomy().deposit(player.getUniqueId(), earned);
        int total = 0;
        for (int amount : sold.values()) {
            total += amount;
        }
        messages.send(player, "shop.sold-all", "{amount}", total, "{types}", sold.size(),
                "{price}", plugin.getEconomy().format(earned));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
        return earned;
    }

    private void removePlain(Player player, Material material, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != material || item.hasItemMeta()) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            if (take >= item.getAmount()) {
                contents[slot] = null;
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
        inventory.setStorageContents(contents);
        player.updateInventory();
    }

    /** Opbrengst naar beneden afronden op centen, zodat losse verkopen niet meer opleveren dan per stapel. */
    private static double round(double value) {
        return Math.floor(value * 100.0 + 1e-6) / 100.0;
    }
}
