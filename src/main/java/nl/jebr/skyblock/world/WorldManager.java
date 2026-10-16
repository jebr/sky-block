package nl.jebr.skyblock.world;

import nl.jebr.skyblock.SkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/** Maakt en beheert de void-wereld waarin de eilanden staan. */
public class WorldManager {

    private final SkyBlockPlugin plugin;
    private World world;

    public WorldManager(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        String name = getWorldName();
        world = Bukkit.getWorld(name);
        if (world == null) {
            plugin.getLogger().info("SkyBlock-wereld '" + name + "' laden of aanmaken...");
            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .generator(new VoidGenerator(getIslandHeight()))
                    .createWorld();
        }
        if (world == null) {
            throw new IllegalStateException("Kon de SkyBlock-wereld niet aanmaken");
        }
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setSpawnLocation(0, getIslandHeight() + 1, 0);
    }

    public World getWorld() {
        if (world == null) {
            world = Bukkit.getWorld(getWorldName());
        }
        return world;
    }

    public String getWorldName() {
        return plugin.getConfig().getString("world.name", "skyblock_world");
    }

    public int getIslandHeight() {
        return plugin.getConfig().getInt("world.island-height", 100);
    }

    public int getSpacing() {
        return Math.max(64, plugin.getConfig().getInt("world.island-spacing", 400));
    }

    /** Is dit de SkyBlock-wereld? */
    public boolean isSkyBlockWorld(World other) {
        return other != null && other.getName().equals(getWorldName());
    }
}
