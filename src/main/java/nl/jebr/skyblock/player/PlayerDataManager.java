package nl.jebr.skyblock.player;

import nl.jebr.skyblock.SkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Laadt en bewaart spelersgegevens. Gegevens van online spelers blijven in het
 * geheugen; gegevens van offline spelers worden op aanvraag geladen en ook
 * gecachet tot de volgende save.
 */
public class PlayerDataManager {

    private final SkyBlockPlugin plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /** Haalt de gegevens op (laadt ze zo nodig van schijf of maakt nieuwe aan). */
    public PlayerData get(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            return data;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return load(uuid, offline.getName());
    }

    public PlayerData get(Player player) {
        PlayerData data = get(player.getUniqueId());
        data.setName(player.getName());
        return data;
    }

    /** Laadt de gegevens van schijf, of maakt nieuwe aan met het startsaldo. */
    public PlayerData load(UUID uuid, String name) {
        PlayerData existing = cache.get(uuid);
        if (existing != null) {
            existing.setName(name);
            return existing;
        }
        File file = plugin.getStorage().playerFile(uuid.toString());
        PlayerData data = new PlayerData(uuid, name);
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            data.setName(config.getString("name", name));
            data.setBalance(config.getDouble("balance"));
            String island = config.getString("island");
            if (island != null && !island.isEmpty()) {
                try {
                    data.setIslandId(UUID.fromString(island));
                } catch (IllegalArgumentException ignored) {
                    // ongeldig
                }
            }
            data.setResetCount(config.getInt("resets.count"));
            data.setLastReset(config.getLong("resets.last"));
            ConfigurationSection challenges = config.getConfigurationSection("challenges");
            if (challenges != null) {
                for (String key : challenges.getKeys(false)) {
                    data.loadChallenge(key, challenges.getInt(key));
                }
            }
            if (name != null) {
                data.setName(name);
            }
            data.clearDirty();
        } else {
            data.setBalance(plugin.getConfig().getDouble("economy.starting-balance", 100.0));
        }
        cache.put(uuid, data);
        return data;
    }

    public void save(PlayerData data) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", data.getName());
        config.set("balance", data.getBalance());
        config.set("island", data.getIslandId() == null ? null : data.getIslandId().toString());
        config.set("resets.count", data.getResetCount());
        config.set("resets.last", data.getLastReset());
        data.getChallengeCompletions().forEach((id, count) -> config.set("challenges." + id, count));
        plugin.getStorage().save(config, plugin.getStorage().playerFile(data.getUuid().toString()));
        data.clearDirty();
    }

    /** Opslaan en (indien offline) uit het geheugen halen. */
    public void unload(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            save(data);
            cache.remove(uuid);
        }
    }

    public void saveAll() {
        for (PlayerData data : new ArrayList<>(cache.values())) {
            if (data.isDirty()) {
                save(data);
            }
            if (Bukkit.getPlayer(data.getUuid()) == null) {
                cache.remove(data.getUuid());
            }
        }
    }

    public Collection<PlayerData> getCached() {
        return cache.values();
    }

    /** Zoekt een speler op naam (online of eerder gezien). Kan null teruggeven. */
    @SuppressWarnings("deprecation")
    public UUID findUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (PlayerData data : cache.values()) {
            if (name.equalsIgnoreCase(data.getName())) {
                return data.getUuid();
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        return null;
    }

    /** Naam van een speler, of "?" als onbekend. */
    public String getName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        PlayerData data = cache.get(uuid);
        if (data != null && data.getName() != null) {
            return data.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "?";
    }
}
