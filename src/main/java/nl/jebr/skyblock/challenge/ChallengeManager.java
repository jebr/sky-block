package nl.jebr.skyblock.challenge;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.player.PlayerData;
import nl.jebr.skyblock.shop.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Laadt de challenges uit challenges.yml, controleert of een speler een
 * challenge heeft gehaald en deelt de beloningen uit.
 */
public class ChallengeManager {

    /** Standaard zoekstraal voor ISLAND_BLOCKS-challenges. */
    public static final int DEFAULT_BLOCK_RADIUS = 10;

    /** Status van een challenge voor een bepaalde speler (voor menu's). */
    public enum Status {
        /** De categorie is nog niet ontgrendeld. */
        LOCKED,
        /** Nog nooit voltooid. */
        AVAILABLE,
        /** Al voltooid maar opnieuw te doen. */
        REPEATABLE,
        /** Voltooid en niet (meer) te herhalen. */
        COMPLETED
    }

    private final SkyBlockPlugin plugin;
    private final Map<String, ChallengeCategory> categories = new LinkedHashMap<>();
    private final Map<String, Challenge> challenges = new LinkedHashMap<>();

    public ChallengeManager(SkyBlockPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    // ================================================================== laden

    /** (Her)laadt challenges.yml uit de pluginmap. */
    public void reload() {
        categories.clear();
        challenges.clear();
        File file = new File(plugin.getDataFolder(), "challenges.yml");
        if (!file.exists()) {
            plugin.saveResource("challenges.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection categorySection = config.getConfigurationSection("categories");
        if (categorySection != null) {
            for (String key : categorySection.getKeys(false)) {
                ConfigurationSection section = categorySection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                String id = key.toLowerCase(Locale.ROOT);
                categories.put(id, new ChallengeCategory(id,
                        section.getString("name", key),
                        material(section.getString("icon"), Material.BOOK),
                        section.getLong("required-level", 0),
                        section.getStringList("description")));
            }
        }

        ConfigurationSection challengeSection = config.getConfigurationSection("challenges");
        if (challengeSection != null) {
            for (String key : challengeSection.getKeys(false)) {
                ConfigurationSection section = challengeSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                try {
                    Challenge challenge = parseChallenge(key, section);
                    if (challenge != null) {
                        challenges.put(challenge.getId(), challenge);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Kon challenge '" + key + "' niet laden: " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info(challenges.size() + " challenges geladen in " + categories.size() + " categorieen.");
    }

    private Challenge parseChallenge(String key, ConfigurationSection section) {
        String categoryId = section.getString("category", "").toLowerCase(Locale.ROOT);
        if (!categories.containsKey(categoryId)) {
            plugin.getLogger().warning("Challenge '" + key + "' heeft een onbekende categorie: " + categoryId);
            return null;
        }
        ChallengeType type;
        try {
            type = ChallengeType.valueOf(section.getString("type", "INVENTORY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Challenge '" + key + "' heeft een onbekend type.");
            return null;
        }
        Map<Material, Integer> required = materialMap(section.getConfigurationSection("required"), type == ChallengeType.ISLAND_BLOCKS);
        if (type != ChallengeType.ISLAND_LEVEL && required.isEmpty()) {
            plugin.getLogger().warning("Challenge '" + key + "' heeft geen geldige vereisten.");
            return null;
        }
        Challenge.Reward reward = parseReward(section.getConfigurationSection("reward"));
        boolean repeatable = section.getBoolean("repeatable", false);
        Challenge.Reward repeatReward = section.isConfigurationSection("repeat-reward")
                ? parseReward(section.getConfigurationSection("repeat-reward"))
                : reward;
        return new Challenge(key, categoryId,
                section.getString("name", key),
                material(section.getString("icon"), Material.PAPER),
                section.getStringList("description"),
                type,
                required,
                section.getBoolean("take", true),
                section.getLong("level", 0),
                Math.max(1, Math.min(32, section.getInt("radius", DEFAULT_BLOCK_RADIUS))),
                reward, repeatReward, repeatable,
                section.getInt("max-completions", 0));
    }

    private Challenge.Reward parseReward(ConfigurationSection section) {
        if (section == null) {
            return Challenge.Reward.empty();
        }
        return new Challenge.Reward(
                Math.max(0, section.getDouble("money", 0)),
                materialMap(section.getConfigurationSection("items"), false),
                section.getStringList("commands"),
                Math.max(0, section.getInt("xp", 0)));
    }

    private Map<Material, Integer> materialMap(ConfigurationSection section, boolean blocksOnly) {
        Map<Material, Integer> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            int amount = section.getInt(key);
            // Blokken-challenges tellen blokken (ook water), de rest moet een echt item zijn
            boolean valid = blocksOnly ? material != null && material.isBlock() : material != null && material.isItem();
            if (!valid || material.isAir() || amount <= 0) {
                plugin.getLogger().warning("Ongeldig materiaal of aantal in challenges.yml: " + key);
                continue;
            }
            result.put(material, amount);
        }
        return result;
    }

    private Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material == null || material.isAir() ? fallback : material;
    }

    // ================================================================== opvragen

    public Collection<Challenge> getChallenges() {
        return Collections.unmodifiableCollection(challenges.values());
    }

    /** De challenge met deze id (hoofdletterongevoelig), of null. */
    public Challenge getChallenge(String id) {
        return id == null ? null : challenges.get(id.toLowerCase(Locale.ROOT));
    }

    /** Alle categorieen in de volgorde van challenges.yml. */
    public List<ChallengeCategory> getCategories() {
        return new ArrayList<>(categories.values());
    }

    public ChallengeCategory getCategory(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    /** Alle challenges in een categorie, in de volgorde van challenges.yml. */
    public List<Challenge> getChallenges(String categoryId) {
        List<Challenge> result = new ArrayList<>();
        for (Challenge challenge : challenges.values()) {
            if (challenge.getCategoryId().equalsIgnoreCase(categoryId)) {
                result.add(challenge);
            }
        }
        return result;
    }

    /** Of de categorie ontgrendeld is voor dit eiland (null = geen eiland = alleen level-0-categorieen). */
    public boolean isUnlocked(ChallengeCategory category, Island island) {
        long level = island == null ? 0 : island.getLevel();
        return level >= category.getRequiredLevel();
    }

    /** Hoe vaak de speler deze challenge heeft voltooid. */
    public int getCompletions(Player player, Challenge challenge) {
        return plugin.getPlayerDataManager().get(player).getChallengeCompletions(challenge.getId());
    }

    /** De status van een challenge voor deze speler. */
    public Status getStatus(Player player, Challenge challenge) {
        ChallengeCategory category = getCategory(challenge.getCategoryId());
        Island island = plugin.getIslandManager().getIslandOf(player);
        if (category != null && !isUnlocked(category, island)) {
            return Status.LOCKED;
        }
        int completions = getCompletions(player, challenge);
        if (completions <= 0) {
            return Status.AVAILABLE;
        }
        return challenge.canComplete(completions) ? Status.REPEATABLE : Status.COMPLETED;
    }

    // ================================================================== voltooien

    /**
     * Probeert de challenge te voltooien: controleert alle voorwaarden, neemt
     * eventueel items in, geeft de beloning en stuurt berichten.
     *
     * @return true als de challenge is voltooid
     */
    public boolean complete(Player player, String id) {
        Messages messages = plugin.getMessages();
        Challenge challenge = getChallenge(id);
        if (challenge == null) {
            messages.send(player, "challenges.unknown", "{id}", id);
            return false;
        }
        Island island = plugin.getIslandManager().getIslandOf(player);
        if (island == null) {
            messages.send(player, "general.no-island");
            return false;
        }
        ChallengeCategory category = getCategory(challenge.getCategoryId());
        if (category != null && !isUnlocked(category, island)) {
            messages.send(player, "challenges.category-locked",
                    "{category}", category.getName(), "{level}", category.getRequiredLevel());
            return false;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        int completions = data.getChallengeCompletions(challenge.getId());
        if (!challenge.canComplete(completions)) {
            messages.send(player, completions > 0 && challenge.isRepeatable()
                    ? "challenges.max-completions" : "challenges.already-completed",
                    "{challenge}", challenge.getName());
            return false;
        }

        switch (challenge.getType()) {
            case INVENTORY -> {
                Map<Material, Integer> missing = getMissingItems(player, challenge.getRequiredItems());
                if (!missing.isEmpty()) {
                    messages.send(player, "challenges.missing-items", "{items}", formatItems(missing));
                    return false;
                }
                if (challenge.isTakeItems()) {
                    takeItems(player, challenge.getRequiredItems());
                }
            }
            case ISLAND_LEVEL -> {
                if (island.getLevel() < challenge.getRequiredLevel()) {
                    messages.send(player, "challenges.level-too-low",
                            "{level}", challenge.getRequiredLevel(), "{current}", island.getLevel());
                    return false;
                }
            }
            case ISLAND_BLOCKS -> {
                Island here = plugin.getIslandManager().getIslandAt(player.getLocation());
                if (here == null || !here.getId().equals(island.getId())) {
                    messages.send(player, "challenges.not-on-island");
                    return false;
                }
                Map<Material, Integer> missing = getMissingBlocks(player, island, challenge);
                if (!missing.isEmpty()) {
                    messages.send(player, "challenges.missing-blocks",
                            "{radius}", challenge.getRadius(), "{items}", formatItems(missing));
                    return false;
                }
            }
        }

        Challenge.Reward reward = challenge.getRewardFor(completions);
        data.addChallengeCompletion(challenge.getId());
        giveReward(player, reward);

        String rewardText = describeReward(reward);
        if (completions <= 0) {
            messages.send(player, "challenges.completed", "{challenge}", challenge.getName(), "{reward}", rewardText);
            String broadcast = messages.get("challenges.broadcast",
                    "{player}", player.getName(), "{challenge}", challenge.getName());
            if (!org.bukkit.ChatColor.stripColor(broadcast).isEmpty()) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        online.sendMessage(messages.prefix() + broadcast);
                    }
                }
            }
        } else {
            messages.send(player, "challenges.repeated", "{challenge}", challenge.getName(),
                    "{reward}", rewardText, "{count}", completions + 1);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        return true;
    }

    /** Welke items (en hoeveel) de speler nog mist. Leeg als alles aanwezig is. */
    public Map<Material, Integer> getMissingItems(Player player, Map<Material, Integer> required) {
        Map<Material, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int have = countItems(player.getInventory(), entry.getKey());
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }
        return missing;
    }

    /** Telt gewone items (zonder eigen naam of andere metadata) van dit materiaal. */
    private int countItems(PlayerInventory inventory, Material material) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isPlain(item, material)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void takeItems(Player player, Map<Material, Integer> required) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                ItemStack item = contents[slot];
                if (!isPlain(item, entry.getKey())) {
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
        }
        inventory.setStorageContents(contents);
        player.updateInventory();
    }

    private static boolean isPlain(ItemStack item, Material material) {
        return item != null && item.getType() == material && !item.hasItemMeta();
    }

    /**
     * Telt de vereiste blokken binnen de straal van de challenge rond de
     * speler, alleen binnen de grenzen van het eiland.
     */
    public Map<Material, Integer> getMissingBlocks(Player player, Island island, Challenge challenge) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return new LinkedHashMap<>(challenge.getRequiredItems());
        }
        int radius = challenge.getRadius();
        int islandRadius = plugin.getIslandManager().getRadius(island);
        int minY = Math.max(world.getMinHeight(), location.getBlockY() - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, location.getBlockY() + radius);
        int baseX = location.getBlockX();
        int baseZ = location.getBlockZ();
        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                // Alleen kolommen binnen de eilandgrens tellen
                if (Math.abs(x - island.getCenterX()) > islandRadius || Math.abs(z - island.getCenterZ()) > islandRadius) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (challenge.getRequiredItems().containsKey(type)) {
                        counts.merge(type, 1, Integer::sum);
                    }
                }
            }
        }
        Map<Material, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Material, Integer> entry : challenge.getRequiredItems().entrySet()) {
            int have = counts.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }
        return missing;
    }

    private void giveReward(Player player, Challenge.Reward reward) {
        if (reward.money() > 0) {
            plugin.getEconomy().deposit(player.getUniqueId(), reward.money());
        }
        if (reward.xp() > 0) {
            player.giveExp(reward.xp());
        }
        for (Map.Entry<Material, Integer> entry : reward.items().entrySet()) {
            int amount = entry.getValue();
            int maxStack = Math.max(1, entry.getKey().getMaxStackSize());
            while (amount > 0) {
                int stackSize = Math.min(amount, maxStack);
                amount -= stackSize;
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(entry.getKey(), stackSize));
                for (ItemStack rest : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
        for (String command : reward.commands()) {
            String parsed = command.replace("{player}", player.getName());
            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    // ================================================================== teksten

    /** Leesbare lijst als "64x Cobblestone, 16x Oak Log". */
    public String formatItems(Map<Material, Integer> items) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            parts.add(plugin.getMessages().get("challenges.item-format",
                    "{amount}", entry.getValue(), "{item}", ShopItem.formatMaterial(entry.getKey())));
        }
        return String.join(plugin.getMessages().get("challenges.item-separator"), parts);
    }

    /** Leesbare samenvatting van een beloning voor in chat. */
    public String describeReward(Challenge.Reward reward) {
        List<String> parts = new ArrayList<>();
        if (reward.money() > 0) {
            parts.add(plugin.getEconomy().format(reward.money()));
        }
        if (!reward.items().isEmpty()) {
            parts.add(formatItems(reward.items()));
        }
        if (reward.xp() > 0) {
            parts.add(plugin.getMessages().get("challenges.xp-format", "{xp}", reward.xp()));
        }
        if (parts.isEmpty()) {
            return plugin.getMessages().get("challenges.no-reward");
        }
        return String.join(plugin.getMessages().get("challenges.item-separator"), parts);
    }

    /** Losse regels voor in een lore (zonder kleur van de beschrijving). */
    public List<String> rewardLines(Challenge.Reward reward) {
        List<String> lines = new ArrayList<>();
        if (reward.money() > 0) {
            lines.add(plugin.getMessages().get("challenges.lore.reward-money", "{amount}", plugin.getEconomy().format(reward.money())));
        }
        for (Map.Entry<Material, Integer> entry : reward.items().entrySet()) {
            lines.add(plugin.getMessages().get("challenges.lore.reward-item",
                    "{amount}", entry.getValue(), "{item}", ShopItem.formatMaterial(entry.getKey())));
        }
        if (reward.xp() > 0) {
            lines.add(plugin.getMessages().get("challenges.lore.reward-xp", "{xp}", reward.xp()));
        }
        if (!reward.commands().isEmpty()) {
            lines.add(plugin.getMessages().get("challenges.lore.reward-extra"));
        }
        if (lines.isEmpty()) {
            lines.add(plugin.getMessages().get("challenges.lore.reward-none"));
        }
        return lines;
    }
}
