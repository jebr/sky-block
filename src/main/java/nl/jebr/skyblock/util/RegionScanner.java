package nl.jebr.skyblock.util;

import nl.jebr.skyblock.SkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Scant een rechthoekig gebied zonder de server te laten haperen: chunks worden
 * verspreid over meerdere ticks geladen en als snapshot gekopieerd, het tellen
 * gebeurt asynchroon. De callback wordt op de hoofdthread aangeroepen.
 *
 * <pre>
 * RegionScanner.scan(plugin, world, minX, minZ, maxX, maxZ, false, result -&gt; {
 *     int diamonds = result.getCount(Material.DIAMOND_BLOCK);
 * });
 * </pre>
 */
public final class RegionScanner {

    private static final int CHUNKS_PER_TICK = 4;

    private RegionScanner() {
    }

    /** Resultaat van een scan. */
    public static final class Result {
        private final Map<Material, Integer> counts;
        private final List<int[]> positions;

        Result(Map<Material, Integer> counts, List<int[]> positions) {
            this.counts = counts;
            this.positions = positions;
        }

        /** Aantal blokken per materiaal (lucht niet meegeteld). */
        public Map<Material, Integer> getCounts() {
            return counts;
        }

        public int getCount(Material material) {
            return counts.getOrDefault(material, 0);
        }

        /** Posities {x, y, z} van alle niet-luchtblokken (alleen als daarom gevraagd is). */
        public List<int[]> getPositions() {
            return positions;
        }
    }

    /**
     * @param collectPositions ook de posities van alle niet-luchtblokken verzamelen
     * @param callback         wordt op de hoofdthread aangeroepen met het resultaat
     */
    public static void scan(SkyBlockPlugin plugin, World world, int minX, int minZ, int maxX, int maxZ,
                            boolean collectPositions, Consumer<Result> callback) {
        List<int[]> chunkCoords = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                chunkCoords.add(new int[]{chunkX, chunkZ});
            }
        }
        List<ChunkSnapshot> snapshots = new ArrayList<>(chunkCoords.size());
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                for (int i = 0; i < CHUNKS_PER_TICK && index < chunkCoords.size(); i++, index++) {
                    int[] coords = chunkCoords.get(index);
                    snapshots.add(world.getChunkAt(coords[0], coords[1]).getChunkSnapshot(true, false, false));
                }
                if (index < chunkCoords.size()) {
                    return;
                }
                cancel();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Map<Material, Integer> counts = new EnumMap<>(Material.class);
                    List<int[]> positions = collectPositions ? new ArrayList<>() : null;
                    for (ChunkSnapshot snapshot : snapshots) {
                        int baseX = snapshot.getX() << 4;
                        int baseZ = snapshot.getZ() << 4;
                        for (int lx = 0; lx < 16; lx++) {
                            int x = baseX + lx;
                            if (x < minX || x > maxX) {
                                continue;
                            }
                            for (int lz = 0; lz < 16; lz++) {
                                int z = baseZ + lz;
                                if (z < minZ || z > maxZ) {
                                    continue;
                                }
                                int top = Math.min(maxY - 1, snapshot.getHighestBlockYAt(lx, lz));
                                for (int y = minY; y <= top; y++) {
                                    Material type = snapshot.getBlockType(lx, y, lz);
                                    if (type.isAir()) {
                                        continue;
                                    }
                                    counts.merge(type, 1, Integer::sum);
                                    if (positions != null) {
                                        positions.add(new int[]{x, y, z});
                                    }
                                }
                            }
                        }
                    }
                    Result result = new Result(counts, positions == null ? List.of() : positions);
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
                });
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
