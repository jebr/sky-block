package nl.jebr.skyblock.level;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.util.RegionScanner;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Berekent eilandlevels op basis van de blokken binnen de eilandgrens.
 * Waarden en limieten staan in {@code levels.yml}.
 *
 * <p>Alle methoden horen op de hoofdthread te worden aangeroepen; het tellen
 * zelf gebeurt asynchroon via {@link RegionScanner}.</p>
 */
public class LevelManager {

    private static final String FILE_NAME = "levels.yml";

    private final SkyBlockPlugin plugin;
    private final Map<Material, Integer> blockValues = new EnumMap<>(Material.class);
    private final Map<Material, Integer> limits = new EnumMap<>(Material.class);
    /** Id's van eilanden waarvoor nu een berekening loopt. */
    private final Set<UUID> calculating = new HashSet<>();

    private long pointsPerLevel = 100;
    private int defaultValue = 0;

    public LevelManager(SkyBlockPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    // ================================================================== configuratie

    /** Laadt levels.yml opnieuw (en maakt het bestand aan als het nog niet bestaat). */
    public void reload() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        InputStream resource = plugin.getResource(FILE_NAME);
        if (resource != null) {
            // Nieuwe standaardwaarden uit de jar als terugval gebruiken
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }

        pointsPerLevel = Math.max(1L, config.getLong("points-per-level", 100L));
        defaultValue = Math.max(0, config.getInt("default-value", 0));

        blockValues.clear();
        limits.clear();
        loadSection(config.getConfigurationSection("block-values"), blockValues, "block-values");
        loadSection(config.getConfigurationSection("limits"), limits, "limits");

        plugin.getLogger().info("Eilandlevels geladen: " + blockValues.size() + " blokwaarden, "
                + limits.size() + " limieten, " + pointsPerLevel + " punten per level.");
    }

    /** Leest materiaal → getal uit een sectie; onbekende materialen worden overgeslagen. */
    private void loadSection(ConfigurationSection section, Map<Material, Integer> target, String path) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String name = key.toUpperCase(Locale.ROOT);
            Material material = name.startsWith("LEGACY_") ? null : Material.getMaterial(name);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Onbekend blok '" + key + "' in " + FILE_NAME + " (" + path
                        + "), wordt overgeslagen.");
                continue;
            }
            int value = section.getInt(key, 0);
            if (value < 0) {
                plugin.getLogger().warning("Negatieve waarde voor '" + key + "' in " + FILE_NAME + " (" + path
                        + "), wordt overgeslagen.");
                continue;
            }
            target.put(material, value);
        }
    }

    // ================================================================== berekenen

    /**
     * Berekent het level van het eiland en slaat level en punten op in het eiland.
     * De callback krijgt het nieuwe level en wordt op de hoofdthread aangeroepen.
     * Loopt er al een berekening voor dit eiland, dan gebeurt er niets (ook geen callback).
     */
    public void calculate(Island island, Consumer<Long> callback) {
        if (island == null) {
            return;
        }
        UUID id = island.getId();
        if (!calculating.add(id)) {
            return;
        }
        World world = island.getWorld();
        if (world == null) {
            calculating.remove(id);
            plugin.getLogger().warning("Kan level van eiland " + id + " niet berekenen: wereld '"
                    + island.getWorldName() + "' is niet geladen.");
            return;
        }
        int radius = plugin.getIslandManager().getRadius(island);
        int minX = island.getCenterX() - radius;
        int maxX = island.getCenterX() + radius;
        int minZ = island.getCenterZ() - radius;
        int maxZ = island.getCenterZ() + radius;
        try {
            RegionScanner.scan(plugin, world, minX, minZ, maxX, maxZ, false, result -> {
                try {
                    long points = calculatePoints(result.getCounts());
                    long level = points / pointsPerLevel;
                    island.setLevelPoints(points);
                    island.setLevel(level);
                    if (callback != null) {
                        callback.accept(level);
                    }
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.SEVERE, "Fout bij het verwerken van de levelberekening van eiland "
                            + id, exception);
                } finally {
                    calculating.remove(id);
                }
            });
        } catch (Exception exception) {
            calculating.remove(id);
            plugin.getLogger().log(Level.SEVERE, "Kon de levelberekening van eiland " + id + " niet starten",
                    exception);
        }
    }

    /** Telt de punten van een blokkentelling, rekening houdend met de limieten. */
    private long calculatePoints(Map<Material, Integer> counts) {
        long points = 0;
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            Material material = entry.getKey();
            int value = getBlockValue(material);
            if (value <= 0) {
                continue;
            }
            long amount = entry.getValue();
            Integer limit = limits.get(material);
            if (limit != null) {
                amount = Math.min(amount, limit);
            }
            points += amount * value;
        }
        return points;
    }

    public boolean isCalculating(Island island) {
        return island != null && calculating.contains(island.getId());
    }

    // ================================================================== opvragen

    /** De waarde van één blok; niet-genoemde vaste blokken krijgen de standaardwaarde. */
    public int getBlockValue(Material material) {
        if (material == null) {
            return 0;
        }
        Integer value = blockValues.get(material);
        if (value != null) {
            return value;
        }
        return material.isBlock() && material.isSolid() ? defaultValue : 0;
    }

    /** Maximaal aantal blokken dat meetelt, of -1 als er geen limiet is. */
    public int getLimit(Material material) {
        Integer limit = material == null ? null : limits.get(material);
        return limit == null ? -1 : limit;
    }

    /** Alle ingestelde blokwaarden (alleen-lezen). */
    public Map<Material, Integer> getBlockValues() {
        return Collections.unmodifiableMap(blockValues);
    }

    public long getPointsPerLevel() {
        return pointsPerLevel;
    }

    /** Punten die nog nodig zijn voor het volgende level (op basis van de laatst berekende waarden). */
    public long getPointsToNextLevel(Island island) {
        if (island == null) {
            return pointsPerLevel;
        }
        long needed = (island.getLevel() + 1) * pointsPerLevel - island.getLevelPoints();
        return Math.max(0L, needed);
    }

    /** Alle eilanden gesorteerd op level (aflopend) en daarna op punten (aflopend). */
    public List<Island> getTopIslands(int limit) {
        List<Island> sorted = new ArrayList<>(plugin.getIslandManager().getIslands());
        sorted.sort((a, b) -> {
            int compare = Long.compare(b.getLevel(), a.getLevel());
            if (compare != 0) {
                return compare;
            }
            return Long.compare(b.getLevelPoints(), a.getLevelPoints());
        });
        if (limit >= 0 && sorted.size() > limit) {
            return new ArrayList<>(sorted.subList(0, limit));
        }
        return sorted;
    }

    /** Rangnummer (1 = hoogste) van het eiland in de ranglijst, of -1 als onbekend. */
    public int getRank(Island island) {
        if (island == null) {
            return -1;
        }
        List<Island> sorted = getTopIslands(-1);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getId().equals(island.getId())) {
                return i + 1;
            }
        }
        return -1;
    }
}
