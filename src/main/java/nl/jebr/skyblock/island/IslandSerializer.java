package nl.jebr.skyblock.island;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Zet eilanden om van en naar YAML. */
final class IslandSerializer {

    private IslandSerializer() {
    }

    static YamlConfiguration serialize(Island island) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", island.getId().toString());
        config.set("grid-index", island.getGridIndex());
        config.set("world", island.getWorldName());
        config.set("center.x", island.getCenterX());
        config.set("center.y", island.getCenterY());
        config.set("center.z", island.getCenterZ());
        config.set("created", island.getCreatedAt());
        config.set("owner", island.getOwner().toString());
        config.set("name", island.getName());
        config.set("locked", island.isLocked());

        island.getMembers().forEach((uuid, role) -> config.set("members." + uuid, role.name()));

        List<String> banned = new ArrayList<>();
        island.getBanned().forEach(uuid -> banned.add(uuid.toString()));
        config.set("banned", banned);

        island.getFlags().forEach((flag, value) -> config.set("flags." + flag.name(), value));

        double[] home = island.rawHome();
        config.set("home.x", home[0]);
        config.set("home.y", home[1]);
        config.set("home.z", home[2]);
        config.set("home.yaw", home[3]);
        config.set("home.pitch", home[4]);

        double[] warp = island.rawWarp();
        if (warp != null) {
            config.set("warp.x", warp[0]);
            config.set("warp.y", warp[1]);
            config.set("warp.z", warp[2]);
            config.set("warp.yaw", warp[3]);
            config.set("warp.pitch", warp[4]);
            config.set("warp.enabled", island.rawWarpEnabled());
        }

        config.set("upgrades.size", island.getSizeLevel());
        config.set("upgrades.members", island.getMemberLevel());
        config.set("upgrades.generator", island.getGeneratorLevel());
        config.set("level.value", island.getLevel());
        config.set("level.points", island.getLevelPoints());
        return config;
    }

    static Island deserialize(YamlConfiguration config) {
        UUID id = UUID.fromString(config.getString("id"));
        UUID owner = UUID.fromString(config.getString("owner"));
        Island island = new Island(
                id,
                config.getInt("grid-index"),
                config.getString("world"),
                config.getInt("center.x"),
                config.getInt("center.y"),
                config.getInt("center.z"),
                owner,
                config.getLong("created", System.currentTimeMillis()));
        island.setName(config.getString("name"));

        ConfigurationSection members = config.getConfigurationSection("members");
        if (members != null) {
            for (String key : members.getKeys(false)) {
                try {
                    IslandRole role = IslandRole.valueOf(members.getString(key, "MEMBER"));
                    UUID uuid = UUID.fromString(key);
                    if (!uuid.equals(owner) && role != IslandRole.OWNER) {
                        island.loadMember(uuid, role);
                    }
                } catch (IllegalArgumentException ignored) {
                    // ongeldige regel overslaan
                }
            }
        }

        for (String uuid : config.getStringList("banned")) {
            try {
                island.loadBan(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
                // overslaan
            }
        }

        ConfigurationSection flags = config.getConfigurationSection("flags");
        if (flags != null) {
            for (String key : flags.getKeys(false)) {
                try {
                    island.loadFlag(IslandFlag.valueOf(key), flags.getBoolean(key));
                } catch (IllegalArgumentException ignored) {
                    // onbekende flag overslaan
                }
            }
        }

        if (config.contains("home.x")) {
            island.loadHome(config.getDouble("home.x"), config.getDouble("home.y"), config.getDouble("home.z"),
                    (float) config.getDouble("home.yaw"), (float) config.getDouble("home.pitch"));
        }
        if (config.contains("warp.x")) {
            island.loadWarp(config.getDouble("warp.x"), config.getDouble("warp.y"), config.getDouble("warp.z"),
                    (float) config.getDouble("warp.yaw"), (float) config.getDouble("warp.pitch"),
                    config.getBoolean("warp.enabled"));
        }

        island.loadStats(
                config.getInt("upgrades.size", 1),
                config.getInt("upgrades.members", 1),
                config.getInt("upgrades.generator", 1),
                config.getLong("level.value"),
                config.getLong("level.points"),
                config.getBoolean("locked"));
        island.clearDirty();
        return island;
    }
}
