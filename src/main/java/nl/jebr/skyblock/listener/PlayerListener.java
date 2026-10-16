package nl.jebr.skyblock.listener;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Algemene spelergebeurtenissen: laden en opslaan van spelersgegevens, de
 * wereldrand bijwerken, verbannen spelers en gesloten eilanden tegenhouden,
 * spelers uit de void redden en respawnen op het eigen eiland.
 */
public class PlayerListener implements Listener {

    /** Minimale tijd (ms) tussen twee meldingen over betreden aan dezelfde speler. */
    private static final long MESSAGE_COOLDOWN = 2000L;

    private final SkyBlockPlugin plugin;
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    /** Spelers voor wie al een void-redding gepland staat (voorkomt dubbele teleports). */
    private final Set<UUID> rescuing = new HashSet<>();

    public PlayerListener(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================== join en quit

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().load(player.getUniqueId(), player.getName());
        scheduleBorderUpdate(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getPlayerDataManager().unload(uuid);
        lastMessage.remove(uuid);
        rescuing.remove(uuid);
    }

    // ================================================================== teleporteren en wereldwissel

    /**
     * Houdt teleports naar een eiland tegen waar de speler verbannen is of dat op
     * slot staat. Ender pearls en chorus fruit worden al door de ProtectionListener
     * afgehandeld; dit vangt de overige oorzaken (commando's, plug-ins, portalen).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || !plugin.getWorldManager().isSkyBlockWorld(to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        Island target = islands().getIslandAt(to);
        Island from = islands().getIslandAt(event.getFrom());
        // Binnen hetzelfde eiland blijven mag altijd (anders zit een speler die net gelockt is vast)
        if (target != null && target != from) {
            String key = ProtectionListener.entryDenialKey(player, target);
            if (key != null) {
                event.setCancelled(true);
                sendLimited(player, key);
            }
        }
    }

    /** Wereldrand bijwerken na elke geslaagde teleport. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportDone(PlayerTeleportEvent event) {
        scheduleBorderUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        scheduleBorderUpdate(event.getPlayer());
    }

    // ================================================================== bewegen

    /**
     * Wordt alleen uitgevoerd bij een wissel van blok. Regelt de void-redding,
     * het tegenhouden bij het betreden van een eiland en de wereldrand bij een
     * wissel van gridcel.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        World world = to.getWorld();
        if (!plugin.getWorldManager().isSkyBlockWorld(world)) {
            return;
        }
        Player player = event.getPlayer();

        // 1. Void: onder de minimale hoogte
        if (to.getY() < world.getMinHeight()) {
            if (rescueFromVoid(player, from)) {
                return;
            }
        }

        // 2. Een eiland betreden (alleen bij wissel van X of Z)
        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            Island target = islands().getIslandAt(to);
            if (target != null && target != islands().getIslandAt(from)) {
                String key = ProtectionListener.entryDenialKey(player, target);
                if (key != null) {
                    pushBack(event, player, target);
                    sendLimited(player, key);
                    return;
                }
            }

            // 3. Wereldrand bij wissel van gridcel
            if (islands().getIslandInCell(from) != islands().getIslandInCell(to)) {
                scheduleBorderUpdate(player);
            }
        }
    }

    /**
     * Zet de speler terug: naar de vorige plek als die niet op het verboden eiland
     * ligt, anders (bijv. na een lock terwijl hij er al stond) naar de spawn.
     */
    private void pushBack(PlayerMoveEvent event, Player player, Island forbidden) {
        Location from = event.getFrom();
        if (islands().getIslandAt(from) != forbidden) {
            Location back = from.clone();
            back.setYaw(event.getTo().getYaw());
            back.setPitch(event.getTo().getPitch());
            event.setTo(back);
        } else {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    islands().teleportToSpawn(player);
                }
            });
        }
    }

    // ================================================================== void

    /** Void-schade (ook als PlayerMoveEvent het niet op tijd zag) omzetten in een redding. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID
                || !(event.getEntity() instanceof Player player)
                || !plugin.getWorldManager().isSkyBlockWorld(player.getWorld())) {
            return;
        }
        if (rescueFromVoid(player, player.getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Teleporteert de speler uit de void (als dat in de config aan staat): naar
     * zijn eigen eiland, anders naar het eiland in de gridcel waar hij was, anders
     * naar de spawn. De inventory blijft gewoon behouden.
     *
     * @param lastPosition een plek in de gridcel waar de speler vandaan kwam
     * @return true als er een redding is (of al was) ingepland
     */
    private boolean rescueFromVoid(Player player, Location lastPosition) {
        if (!plugin.getConfig().getBoolean("island.void-teleport", true)) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        player.setFallDistance(0);
        if (!rescuing.add(uuid)) {
            return true;
        }
        Island cellIsland = islands().getIslandInCell(lastPosition);
        // Teleporteren buiten het event om, een tick later
        Bukkit.getScheduler().runTask(plugin, () -> {
            rescuing.remove(uuid);
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            player.setFallDistance(0);
            Island own = islands().getIslandOf(player);
            if (own != null) {
                islands().teleportHome(player, own);
            } else if (cellIsland != null && islands().getIsland(cellIsland.getId()) != null
                    && ProtectionListener.entryDenialKey(player, cellIsland) == null) {
                islands().teleportWarp(player, cellIsland);
            } else {
                islands().teleportToSpawn(player);
            }
            plugin.getMessages().send(player, "protection.void-saved");
        });
        return true;
    }

    // ================================================================== respawn

    /** Respawnen op het eigen eiland, tenzij de speler een bed of ankerpunt heeft. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean("island.respawn-on-island", true)
                && !event.isBedSpawn() && !event.isAnchorSpawn()) {
            Island island = islands().getIslandOf(player);
            if (island != null) {
                Location home = island.getHome();
                if (home.getWorld() != null) {
                    event.setRespawnLocation(home);
                }
            }
        }
        scheduleBorderUpdate(player);
    }

    // ================================================================== hulpmethoden

    private IslandManager islands() {
        return plugin.getIslandManager();
    }

    /** Werkt de wereldrand 1 tick later bij (na de teleport/het event). */
    private void scheduleBorderUpdate(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                islands().updateBorder(player);
            }
        }, 1L);
    }

    /** Stuurt een melding, maar hooguit eens per {@link #MESSAGE_COOLDOWN} ms per speler. */
    private void sendLimited(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN) {
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        plugin.getMessages().send(player, key);
    }
}
