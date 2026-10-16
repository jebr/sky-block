package nl.jebr.skyblock.island;

import nl.jebr.skyblock.SkyBlockPlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Bouwt het klassieke L-vormige starteiland met een boom en een startkist.
 *
 * <pre>
 *   z
 *   4  D D D
 *   3  D C D          C = kist, T = boom, H = home (middelpunt)
 *   2  D D D
 *   1  D D D D D D
 *   0  D H D D T D
 *  -1  D D D D D D
 *     -1 0 1 2 3 4  x
 * </pre>
 */
public class IslandBuilder {

    private final SkyBlockPlugin plugin;

    public IslandBuilder(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void build(Island island) {
        World world = island.getWorld();
        int cx = island.getCenterX();
        int cy = island.getCenterY();
        int cz = island.getCenterZ();

        // Chunks laden
        for (int x = cx - 2; x <= cx + 6; x += 4) {
            for (int z = cz - 2; z <= cz + 6; z += 4) {
                world.getChunkAt(x >> 4, z >> 4).load(true);
            }
        }

        // Grond: 3 lagen (2x aarde, 1x gras)
        for (int dx = -1; dx <= 4; dx++) {
            for (int dz = -1; dz <= 4; dz++) {
                boolean inLongPart = dz >= -1 && dz <= 1;
                boolean inShortPart = dx >= -1 && dx <= 1 && dz >= 2;
                if (!inLongPart && !inShortPart) {
                    continue;
                }
                world.getBlockAt(cx + dx, cy - 2, cz + dz).setType(Material.DIRT, false);
                world.getBlockAt(cx + dx, cy - 1, cz + dz).setType(Material.DIRT, false);
                world.getBlockAt(cx + dx, cy, cz + dz).setType(Material.GRASS_BLOCK, false);
            }
        }
        world.getBlockAt(cx, cy - 2, cz).setType(Material.BEDROCK, false);
        world.getBlockAt(cx + 2, cy - 2, cz).setType(Material.SAND, false);
        world.getBlockAt(cx + 2, cy - 1, cz).setType(Material.SAND, false);

        buildTree(world, cx + 3, cy + 1, cz);
        buildChest(world, cx, cy + 1, cz + 3);
    }

    private void buildTree(World world, int x, int y, int z) {
        int trunkHeight = 5;
        // Bladeren
        for (int dy = trunkHeight - 3; dy <= trunkHeight; dy++) {
            int radius = dy >= trunkHeight - 1 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (radius == 2 || dy == trunkHeight)) {
                        continue;
                    }
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (block.getType().isAir()) {
                        block.setType(Material.OAK_LEAVES, false);
                        // Zonder physics blijft de afstand op 7 staan en vergaan de bladeren vanzelf
                        // (de speler verliest dan zijn eerste saplings); daarom vast zetten
                        if (block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leaves) {
                            leaves.setPersistent(true);
                            block.setBlockData(leaves, false);
                        }
                    }
                }
            }
        }
        // Stam
        for (int dy = 0; dy < trunkHeight; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.OAK_LOG, false);
        }
    }

    private void buildChest(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false);
        if (block.getBlockData() instanceof Directional directional) {
            directional.setFacing(BlockFace.NORTH);
            block.setBlockData(directional, false);
        }
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("island.starter-chest");
        if (items == null) {
            return;
        }
        for (String key : items.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Onbekend item in starter-chest: " + key);
                continue;
            }
            int amount = Math.max(1, items.getInt(key, 1));
            while (amount > 0) {
                int stack = Math.min(amount, material.getMaxStackSize());
                chest.getBlockInventory().addItem(new ItemStack(material, stack));
                amount -= stack;
            }
        }
    }
}
