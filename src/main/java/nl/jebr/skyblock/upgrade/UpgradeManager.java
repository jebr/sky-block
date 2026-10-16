package nl.jebr.skyblock.upgrade;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.island.Island;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Leest de upgradeniveaus uit config.yml en voert upgrades uit.
 */
public class UpgradeManager {

    /** Uitkomst van een upgradepoging. */
    public enum Result { SUCCESS, MAX_LEVEL, NOT_ENOUGH_MONEY }

    private final SkyBlockPlugin plugin;
    private final Map<UpgradeType, TreeMap<Integer, ConfigurationSection>> levels = new EnumMap<>(UpgradeType.class);
    private final TreeMap<Integer, Map<Material, Integer>> generatorBlocks = new TreeMap<>();

    public UpgradeManager(SkyBlockPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        levels.clear();
        generatorBlocks.clear();
        for (UpgradeType type : UpgradeType.values()) {
            TreeMap<Integer, ConfigurationSection> map = new TreeMap<>();
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("upgrades." + type.getKey());
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(key);
                        ConfigurationSection levelSection = section.getConfigurationSection(key);
                        if (levelSection != null) {
                            map.put(level, levelSection);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Ongeldig upgradeniveau '" + key + "' bij " + type.getKey());
                    }
                }
            }
            if (map.isEmpty()) {
                plugin.getLogger().warning("Geen niveaus gevonden voor upgrade " + type.getKey());
            }
            levels.put(type, map);
        }

        for (Map.Entry<Integer, ConfigurationSection> entry : levels.get(UpgradeType.GENERATOR).entrySet()) {
            Map<Material, Integer> blocks = new LinkedHashMap<>();
            ConfigurationSection blockSection = entry.getValue().getConfigurationSection("blocks");
            if (blockSection != null) {
                for (String materialName : blockSection.getKeys(false)) {
                    Material material = Material.matchMaterial(materialName);
                    if (material == null || !material.isBlock()) {
                        plugin.getLogger().warning("Onbekend generatorblok: " + materialName);
                        continue;
                    }
                    blocks.put(material, Math.max(0, blockSection.getInt(materialName)));
                }
            }
            if (blocks.isEmpty()) {
                blocks.put(Material.COBBLESTONE, 100);
            }
            generatorBlocks.put(entry.getKey(), blocks);
        }

        int maxRadius = getRadiusForLevel(getMaxLevel(UpgradeType.SIZE));
        int spacing = plugin.getConfig().getInt("world.island-spacing", 400);
        if (maxRadius * 2 >= spacing) {
            plugin.getLogger().warning("Let op: island-spacing (" + spacing + ") is kleiner dan 2x de maximale straal ("
                    + maxRadius + "). Eilanden kunnen overlappen!");
        }
    }

    public int getMaxLevel(UpgradeType type) {
        TreeMap<Integer, ConfigurationSection> map = levels.get(type);
        return map == null || map.isEmpty() ? 1 : map.lastKey();
    }

    public int getCurrentLevel(Island island, UpgradeType type) {
        return switch (type) {
            case SIZE -> island.getSizeLevel();
            case MEMBERS -> island.getMemberLevel();
            case GENERATOR -> island.getGeneratorLevel();
        };
    }

    /** Kosten om naar het opgegeven niveau te gaan (0 als onbekend). */
    public double getCost(UpgradeType type, int level) {
        ConfigurationSection section = section(type, level);
        return section == null ? 0 : section.getDouble("cost");
    }

    public int getRadiusForLevel(int level) {
        ConfigurationSection section = section(UpgradeType.SIZE, level);
        return section == null ? 25 : section.getInt("radius", 25);
    }

    public int getMemberLimitForLevel(int level) {
        ConfigurationSection section = section(UpgradeType.MEMBERS, level);
        return section == null ? 2 : section.getInt("limit", 2);
    }

    /** Beschermde straal van het eiland in blokken. */
    public int getRadius(Island island) {
        return getRadiusForLevel(island.getSizeLevel());
    }

    /** Maximaal aantal leden inclusief de eigenaar. */
    public int getMemberLimit(Island island) {
        return getMemberLimitForLevel(island.getMemberLevel());
    }

    /** Kansverdeling van de generator op het opgegeven niveau (materiaal -> gewicht). */
    public Map<Material, Integer> getGeneratorBlocks(int level) {
        Map.Entry<Integer, Map<Material, Integer>> entry = generatorBlocks.floorEntry(Math.max(level, 1));
        if (entry == null) {
            entry = generatorBlocks.firstEntry();
        }
        return entry == null ? Map.of(Material.COBBLESTONE, 100) : entry.getValue();
    }

    /** Kiest willekeurig een blok voor de cobblestone-generator van dit eiland. */
    public Material rollGeneratorBlock(Island island) {
        Map<Material, Integer> blocks = getGeneratorBlocks(island.getGeneratorLevel());
        int total = 0;
        for (int weight : blocks.values()) {
            total += weight;
        }
        if (total <= 0) {
            return Material.COBBLESTONE;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Map.Entry<Material, Integer> entry : blocks.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return Material.COBBLESTONE;
    }

    /**
     * Probeert het eiland een niveau te upgraden. Het geld wordt van de betaler
     * afgeschreven. Werkt bij succes de wereldrand van spelers op het eiland bij.
     */
    public Result upgrade(Player payer, Island island, UpgradeType type) {
        int current = getCurrentLevel(island, type);
        if (current >= getMaxLevel(type)) {
            return Result.MAX_LEVEL;
        }
        int next = current + 1;
        double cost = getCost(type, next);
        if (!plugin.getEconomy().withdraw(payer.getUniqueId(), cost)) {
            return Result.NOT_ENOUGH_MONEY;
        }
        switch (type) {
            case SIZE -> island.setSizeLevel(next);
            case MEMBERS -> island.setMemberLevel(next);
            case GENERATOR -> island.setGeneratorLevel(next);
        }
        if (type == UpgradeType.SIZE) {
            plugin.getIslandManager().refreshBorders(island);
        }
        return Result.SUCCESS;
    }

    private ConfigurationSection section(UpgradeType type, int level) {
        TreeMap<Integer, ConfigurationSection> map = levels.get(type);
        return map == null ? null : map.get(level);
    }
}
