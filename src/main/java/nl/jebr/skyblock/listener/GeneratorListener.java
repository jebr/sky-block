package nl.jebr.skyblock.listener;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Cobblestone-generator: wanneer lava en water samen cobblestone vormen,
 * wordt het blok vervangen door een willekeurig blok uit de generatortabel
 * van het eiland (afhankelijk van het generatorniveau).
 *
 * <p>De hoofdroute is {@link BlockFormEvent}: daar wordt de nieuwe blockstate
 * aangepast zodat het event zelf het juiste blok zet. Als vangnet bekijkt de
 * {@link BlockFromToEvent}-variant één tick na het stromen of er alsnog
 * cobblestone is ontstaan die niet via een BlockFormEvent liep.</p>
 *
 * <p>Stone, obsidiaan en basalt worden nooit aangepast.</p>
 */
public class GeneratorListener implements Listener {

    /** Hoe lang (ms) een door BlockFormEvent afgehandeld blok als "afgehandeld" geldt. */
    private static final long HANDLED_TTL = 2000L;

    /** De zes directe buren van een blok. */
    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final SkyBlockPlugin plugin;

    /** Blokken die al via BlockFormEvent zijn afgehandeld, met tijdstip; voorkomt dubbel rollen. */
    private final Map<Block, Long> handled = new HashMap<>();

    public GeneratorListener(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Vervangt gevormde cobblestone door een blok uit de generatortabel.
     * Alleen in de SkyBlock-wereld en binnen een eiland.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (event.getNewState().getType() != Material.COBBLESTONE) {
            return;
        }
        Block block = event.getBlock();
        Island island = islandAt(block);
        if (island == null) {
            return;
        }
        markHandled(block);
        Material result = plugin.getUpgradeManager().rollGeneratorBlock(island);
        if (result != null && result != Material.COBBLESTONE && result.isBlock()) {
            event.getNewState().setType(result);
        }
    }

    /**
     * Vangnet: als lava naast water stroomt (of andersom) en het doelblok een
     * tick later cobblestone is zonder dat er een BlockFormEvent voor was,
     * wordt het alsnog vervangen.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Material source = event.getBlock().getType();
        Material other;
        if (source == Material.LAVA) {
            other = Material.WATER;
        } else if (source == Material.WATER) {
            other = Material.LAVA;
        } else {
            return;
        }
        Block to = event.getToBlock();
        if (to.getType() == Material.COBBLESTONE || !touches(to, other)) {
            return;
        }
        if (islandAt(to) == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (to.getType() != Material.COBBLESTONE || isHandled(to)) {
                return;
            }
            Island island = islandAt(to);
            if (island == null) {
                return;
            }
            // Meerdere stroom-events voor hetzelfde blok plannen meerdere controles: maar één keer rollen
            markHandled(to);
            Material result = plugin.getUpgradeManager().rollGeneratorBlock(island);
            if (result != null && result != Material.COBBLESTONE && result.isBlock()) {
                to.setType(result);
            }
        }, 1L);
    }

    // ------------------------------------------------------------------ hulpmethoden

    /** Het eiland op de plek van dit blok, alleen in de SkyBlock-wereld; anders null. */
    private Island islandAt(Block block) {
        if (!plugin.getWorldManager().isSkyBlockWorld(block.getWorld())) {
            return null;
        }
        return plugin.getIslandManager().getIslandAt(block.getLocation());
    }

    /** Grenst dit blok aan een blok van het opgegeven materiaal? */
    private static boolean touches(Block block, Material material) {
        for (BlockFace face : NEIGHBOURS) {
            if (block.getRelative(face).getType() == material) {
                return true;
            }
        }
        return false;
    }

    private void markHandled(Block block) {
        long now = System.currentTimeMillis();
        if (handled.size() > 256) {
            handled.values().removeIf(time -> now - time > HANDLED_TTL);
        }
        handled.put(block, now);
    }

    private boolean isHandled(Block block) {
        // Niet verwijderen: meerdere vangnet-controles voor hetzelfde blok in één tick zouden anders dubbel rollen
        Long time = handled.get(block);
        return time != null && System.currentTimeMillis() - time <= HANDLED_TTL;
    }
}
