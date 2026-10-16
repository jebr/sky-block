package nl.jebr.skyblock.island;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.player.PlayerData;
import nl.jebr.skyblock.util.RegionScanner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Centrale plek voor alles rond eilanden: aanmaken, verwijderen, resetten,
 * leden, uitnodigingen, teleporteren en de wereldrand.
 */
public class IslandManager {

    /** Uitkomst van een reset. */
    public enum ResetResult { SUCCESS, NO_ISLAND, NOT_OWNER, COOLDOWN, MAX_RESETS }

    /** Een openstaande uitnodiging. */
    public record Invite(UUID islandId, UUID inviter, long expiresAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private static final int BLOCKS_CLEARED_PER_TICK = 4000;

    private final SkyBlockPlugin plugin;
    private final IslandBuilder builder;
    private final Map<UUID, Island> islands = new ConcurrentHashMap<>();
    private final Map<Long, Island> byGrid = new ConcurrentHashMap<>();
    private final Set<Integer> usedIndices = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Invite> invites = new HashMap<>();

    public IslandManager(SkyBlockPlugin plugin) {
        this.plugin = plugin;
        this.builder = new IslandBuilder(plugin);
    }

    // ================================================================== laden en opslaan

    public void loadAll() {
        File[] files = plugin.getStorage().getIslandFolder().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                Island island = IslandSerializer.deserialize(YamlConfiguration.loadConfiguration(file));
                register(island);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Kon eiland " + file.getName() + " niet laden", e);
            }
        }
    }

    public void save(Island island) {
        plugin.getStorage().save(IslandSerializer.serialize(island), plugin.getStorage().islandFile(island.getId().toString()));
        island.clearDirty();
    }

    public void saveAll() {
        for (Island island : islands.values()) {
            if (island.isDirty()) {
                save(island);
            }
        }
    }

    private void register(Island island) {
        islands.put(island.getId(), island);
        int[] grid = gridPosition(island.getGridIndex());
        byGrid.put(gridKey(grid[0], grid[1]), island);
        usedIndices.add(island.getGridIndex());
    }

    // ================================================================== opvragen

    public Collection<Island> getIslands() {
        return Collections.unmodifiableCollection(islands.values());
    }

    public Island getIsland(UUID islandId) {
        return islandId == null ? null : islands.get(islandId);
    }

    /** Het eiland waarvan de speler lid of eigenaar is, of null. */
    public Island getIslandOf(UUID player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        Island island = getIsland(data.getIslandId());
        if (island != null && island.isMember(player)) {
            return island;
        }
        if (data.getIslandId() != null) {
            data.setIslandId(null);
        }
        // Terugvaloptie: zoeken in de ledenlijsten
        for (Island candidate : islands.values()) {
            if (candidate.isMember(player)) {
                data.setIslandId(candidate.getId());
                return candidate;
            }
        }
        return null;
    }

    public Island getIslandOf(Player player) {
        return getIslandOf(player.getUniqueId());
    }

    /** Het eiland op deze locatie (binnen de beschermde straal), of null. */
    public Island getIslandAt(Location location) {
        Island island = getIslandInCell(location);
        if (island == null) {
            return null;
        }
        return island.contains(location, getRadius(island)) ? island : null;
    }

    /**
     * Het eiland in de gridcel van deze locatie, ongeacht de straal. Handig om
     * te bepalen of iemand buiten de rand van een eiland staat.
     */
    public Island getIslandInCell(Location location) {
        if (location == null || !plugin.getWorldManager().isSkyBlockWorld(location.getWorld())) {
            return null;
        }
        int spacing = plugin.getWorldManager().getSpacing();
        int gx = Math.round((float) location.getBlockX() / spacing);
        int gz = Math.round((float) location.getBlockZ() / spacing);
        return byGrid.get(gridKey(gx, gz));
    }

    public int getRadius(Island island) {
        return plugin.getUpgradeManager().getRadius(island);
    }

    // ================================================================== aanmaken

    /**
     * Maakt een nieuw eiland voor de speler, bouwt het starteiland en
     * teleporteert de speler erheen. De speler mag nog geen eiland hebben.
     */
    public Island createIsland(Player player) {
        World world = plugin.getWorldManager().getWorld();
        int index = nextFreeIndex();
        int[] grid = gridPosition(index);
        int spacing = plugin.getWorldManager().getSpacing();
        int height = plugin.getWorldManager().getIslandHeight();

        Island island = new Island(UUID.randomUUID(), index, world.getName(),
                grid[0] * spacing, height, grid[1] * spacing, player.getUniqueId(), System.currentTimeMillis());
        island.setName(player.getName());
        register(island);
        builder.build(island);
        save(island);

        PlayerData data = plugin.getPlayerDataManager().get(player);
        data.setIslandId(island.getId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                teleportHome(player, island);
            }
        }, 2L);
        return island;
    }

    private int nextFreeIndex() {
        int index = 1; // index 0 = (0,0) blijft vrij voor de spawn
        synchronized (usedIndices) {
            while (usedIndices.contains(index)) {
                index++;
            }
            usedIndices.add(index);
        }
        return index;
    }

    // ================================================================== verwijderen en resetten

    /**
     * Verwijdert het eiland: alle leden verliezen het eiland, spelers op het
     * eiland gaan naar de spawn en de blokken worden op de achtergrond gewist.
     */
    public void deleteIsland(Island island) {
        for (UUID member : new ArrayList<>(island.getMembers().keySet())) {
            PlayerData data = plugin.getPlayerDataManager().get(member);
            if (island.getId().equals(data.getIslandId())) {
                data.setIslandId(null);
            }
        }
        int radius = getRadius(island);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (island.contains(online.getLocation(), radius + 16)) {
                teleportToSpawn(online);
            }
        }
        islands.remove(island.getId());
        int[] grid = gridPosition(island.getGridIndex());
        byGrid.remove(gridKey(grid[0], grid[1]));
        invites.values().removeIf(invite -> invite.islandId().equals(island.getId()));
        plugin.getStorage().delete(plugin.getStorage().islandFile(island.getId().toString()));

        // De gridindex blijft bezet tot het gebied leeg is
        int maxRadius = plugin.getUpgradeManager().getRadiusForLevel(
                plugin.getUpgradeManager().getMaxLevel(nl.jebr.skyblock.upgrade.UpgradeType.SIZE));
        int clearRadius = Math.max(radius, maxRadius);
        clearRegion(island, clearRadius, () -> usedIndices.remove(island.getGridIndex()));
    }

    private void clearRegion(Island island, int radius, Runnable whenDone) {
        World world = island.getWorld();
        if (world == null) {
            whenDone.run();
            return;
        }
        int minX = island.getCenterX() - radius;
        int maxX = island.getCenterX() + radius;
        int minZ = island.getCenterZ() - radius;
        int maxZ = island.getCenterZ() + radius;

        // Entiteiten (behalve spelers) verwijderen
        for (Entity entity : world.getEntities()) {
            Location loc = entity.getLocation();
            if (!(entity instanceof Player) && loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                    && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ) {
                entity.remove();
            }
        }

        RegionScanner.scan(plugin, world, minX, minZ, maxX, maxZ, true, result -> {
            List<int[]> positions = result.getPositions();
            new BukkitRunnable() {
                private int index;

                @Override
                public void run() {
                    int end = Math.min(positions.size(), index + BLOCKS_CLEARED_PER_TICK);
                    for (; index < end; index++) {
                        int[] pos = positions.get(index);
                        Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
                        if (block.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                            holder.getInventory().clear();
                        }
                        block.setType(Material.AIR, false);
                    }
                    if (index >= positions.size()) {
                        cancel();
                        whenDone.run();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        });
    }

    /**
     * Reset het eiland van de eigenaar: het oude eiland wordt verwijderd (leden
     * verliezen hun plek) en de eigenaar krijgt een nieuw starteiland.
     */
    public ResetResult resetIsland(Player owner) {
        Island island = getIslandOf(owner);
        if (island == null) {
            return ResetResult.NO_ISLAND;
        }
        if (!island.getOwner().equals(owner.getUniqueId())) {
            return ResetResult.NOT_OWNER;
        }
        PlayerData data = plugin.getPlayerDataManager().get(owner);
        if (getResetCooldownRemaining(owner.getUniqueId()) > 0 && !owner.hasPermission("skyblock.bypass")) {
            return ResetResult.COOLDOWN;
        }
        int maxResets = plugin.getConfig().getInt("island.max-resets", -1);
        if (maxResets >= 0 && data.getResetCount() >= maxResets && !owner.hasPermission("skyblock.bypass")) {
            return ResetResult.MAX_RESETS;
        }

        // Leden die online zijn ook opschonen
        for (UUID member : island.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                cleanPlayer(online);
            }
        }
        deleteIsland(island);
        data.setResetCount(data.getResetCount() + 1);
        data.setLastReset(System.currentTimeMillis());
        if (plugin.getConfig().getBoolean("island.reset-clears-balance", false)) {
            plugin.getEconomy().set(owner.getUniqueId(), plugin.getConfig().getDouble("economy.starting-balance", 100.0));
        }
        createIsland(owner);
        return ResetResult.SUCCESS;
    }

    /** Resterende wachttijd in seconden voor een nieuwe reset. */
    public long getResetCooldownRemaining(UUID player) {
        long cooldown = plugin.getConfig().getLong("island.reset-cooldown", 600) * 1000L;
        long last = plugin.getPlayerDataManager().get(player).getLastReset();
        long remaining = last + cooldown - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    /** Wist de inventory, ender chest en XP (als dat in de config aanstaat). */
    public void cleanPlayer(Player player) {
        if (!plugin.getConfig().getBoolean("island.reset-clears-inventory", true)) {
            return;
        }
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.setExp(0);
        player.setLevel(0);
    }

    // ================================================================== leden en uitnodigingen

    /** Voegt de speler toe als lid. Controleert de limiet niet; zie {@link #isFull(Island)}. */
    public void addMember(Island island, UUID player) {
        island.setRole(player, IslandRole.MEMBER);
        island.unban(player);
        plugin.getPlayerDataManager().get(player).setIslandId(island.getId());
        invites.remove(player);
    }

    /** Verwijdert een lid (niet de eigenaar) en stuurt het naar de spawn als het op het eiland staat. */
    public void removeMember(Island island, UUID player) {
        if (player.equals(island.getOwner())) {
            return;
        }
        island.removeMember(player);
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (island.getId().equals(data.getIslandId())) {
            data.setIslandId(null);
        }
        Player online = Bukkit.getPlayer(player);
        if (online != null && island.contains(online.getLocation(), getRadius(island))) {
            teleportToSpawn(online);
        }
    }

    public boolean isFull(Island island) {
        return island.getMemberCount() >= plugin.getUpgradeManager().getMemberLimit(island);
    }

    /** Maakt een uitnodiging aan (vervangt een eventuele oude). */
    public void invite(Island island, UUID inviter, UUID target) {
        long timeout = plugin.getConfig().getLong("island.invite-timeout", 120) * 1000L;
        invites.put(target, new Invite(island.getId(), inviter, System.currentTimeMillis() + timeout));
    }

    /** De geldige uitnodiging voor deze speler, of null. */
    public Invite getInvite(UUID target) {
        Invite invite = invites.get(target);
        if (invite == null) {
            return null;
        }
        if (invite.isExpired() || !islands.containsKey(invite.islandId())) {
            invites.remove(target);
            return null;
        }
        return invite;
    }

    public void removeInvite(UUID target) {
        invites.remove(target);
    }

    // ================================================================== teleporteren

    /** Teleporteert naar de home van het eiland, op een veilige plek. */
    public void teleportHome(Player player, Island island) {
        teleportSafely(player, island.getHome(), island);
    }

    /** Teleporteert naar de warp van het eiland (of de home als er geen warp is). */
    public void teleportWarp(Player player, Island island) {
        Location warp = island.getWarp();
        teleportSafely(player, warp != null ? warp : island.getHome(), island);
    }

    public void teleportToSpawn(Player player) {
        World main = Bukkit.getWorlds().get(0);
        player.teleport(main.getSpawnLocation());
        player.setFallDistance(0);
    }

    private void teleportSafely(Player player, Location target, Island island) {
        if (island.getWorld() == null) {
            // Wereld niet geladen (bijv. hernoemd in de config): niet crashen maar naar de spawn
            teleportToSpawn(player);
            return;
        }
        Location destination = findSafe(target, island);
        destination.getChunk().load(true);
        player.setFallDistance(0);
        player.teleport(destination);
        Bukkit.getScheduler().runTaskLater(plugin, () -> updateBorder(player), 2L);
    }

    private Location findSafe(Location target, Island island) {
        if (isSafe(target)) {
            return target;
        }
        World world = island.getWorld();
        int radius = Math.min(getRadius(island), 20);
        Location center = island.getCenter();
        // Zoek in een spiraal rond het middelpunt naar een veilige plek
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int x = center.getBlockX() + dx;
                    int z = center.getBlockZ() + dz;
                    Block top = world.getHighestBlockAt(x, z);
                    if (top.getType().isSolid() && top.getY() > world.getMinHeight()) {
                        Location candidate = new Location(world, x + 0.5, top.getY() + 1, z + 0.5,
                                target.getYaw(), target.getPitch());
                        if (isSafe(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        // Niets gevonden: maak een noodplatform onder het middelpunt
        Location platform = island.getCenter();
        Block floor = world.getBlockAt(platform.getBlockX(), island.getCenterY(), platform.getBlockZ());
        floor.setType(Material.COBBLESTONE);
        world.getBlockAt(floor.getX(), floor.getY() + 1, floor.getZ()).setType(Material.AIR);
        world.getBlockAt(floor.getX(), floor.getY() + 2, floor.getZ()).setType(Material.AIR);
        return new Location(world, floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
    }

    private boolean isSafe(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return ground.getType().isSolid()
                && ground.getType() != Material.MAGMA_BLOCK
                && !feet.getType().isSolid() && !head.getType().isSolid()
                && feet.getType() != Material.LAVA && head.getType() != Material.LAVA;
    }

    // ================================================================== wereldrand

    /** Werkt de wereldrand bij voor alle online spelers op dit eiland. */
    public void refreshBorders(Island island) {
        int radius = getRadius(island) + 16;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (island.contains(player.getLocation(), radius)) {
                updateBorder(player);
            }
        }
    }

    /** Toont de wereldrand van het eiland waar de speler staat, of verwijdert hem. */
    public void updateBorder(Player player) {
        if (!plugin.getConfig().getBoolean("island.show-border", true)) {
            return;
        }
        Island island = getIslandInCell(player.getLocation());
        if (island == null) {
            if (player.getWorldBorder() != null) {
                player.setWorldBorder(null);
            }
            return;
        }
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(island.getCenterX() + 0.5, island.getCenterZ() + 0.5);
        border.setSize(getRadius(island) * 2 + 1);
        border.setWarningDistance(0);
        border.setDamageAmount(0);
        player.setWorldBorder(border);
    }

    // ================================================================== grid

    /** Gridcoördinaten {x, z} voor een index (vierkante spiraal rond 0,0). */
    public static int[] gridPosition(int n) {
        if (n == 0) {
            return new int[]{0, 0};
        }
        int k = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2);
        int t = 2 * k + 1;
        int m = t * t;
        t = t - 1;
        if (n >= m - t) {
            return new int[]{k - (m - n), -k};
        }
        m -= t;
        if (n >= m - t) {
            return new int[]{-k, -k + (m - n)};
        }
        m -= t;
        if (n >= m - t) {
            return new int[]{-k + (m - n), k};
        }
        return new int[]{k, k - (m - n - t)};
    }

    private static long gridKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
