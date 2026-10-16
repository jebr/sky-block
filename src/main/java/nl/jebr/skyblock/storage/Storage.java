package nl.jebr.skyblock.storage;

import nl.jebr.skyblock.SkyBlockPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Bestandsopslag. Elk eiland staat in data/islands/&lt;id&gt;.yml en elke speler in
 * data/players/&lt;uuid&gt;.yml.
 */
public class Storage {

    private final SkyBlockPlugin plugin;
    private final File islandFolder;
    private final File playerFolder;

    public Storage(SkyBlockPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        this.islandFolder = new File(dataFolder, "islands");
        this.playerFolder = new File(dataFolder, "players");
        if (!islandFolder.exists() && !islandFolder.mkdirs()) {
            plugin.getLogger().warning("Kon map " + islandFolder + " niet aanmaken");
        }
        if (!playerFolder.exists() && !playerFolder.mkdirs()) {
            plugin.getLogger().warning("Kon map " + playerFolder + " niet aanmaken");
        }
    }

    public File getIslandFolder() {
        return islandFolder;
    }

    public File getPlayerFolder() {
        return playerFolder;
    }

    public File islandFile(String id) {
        return new File(islandFolder, id + ".yml");
    }

    public File playerFile(String uuid) {
        return new File(playerFolder, uuid + ".yml");
    }

    public void save(YamlConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Kon " + file.getName() + " niet opslaan", e);
        }
    }

    public void delete(File file) {
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Kon " + file.getName() + " niet verwijderen");
        }
    }
}
