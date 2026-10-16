package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.level.LevelManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ranglijst met de tien hoogste eilanden, opgesteld als piramide:
 * #1 bovenaan in het midden, daaronder #2 en #3, #4 tot en met #6 en #7 tot en met #10.
 * Klikken op een eiland met een open warp teleporteert de kijker erheen.
 */
public class TopMenu extends Menu {

    /** Vakjes voor rang 1 tot en met 10 (piramide). */
    private static final int[] RANK_SLOTS = {13, 21, 23, 29, 31, 33, 37, 39, 41, 43};
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 49;

    public TopMenu(SkyBlockPlugin plugin, Player viewer) {
        super(plugin, viewer, plugin.getMessages().get("level.menu.title"), 6);
    }

    @Override
    protected void build() {
        Messages messages = plugin.getMessages();
        LevelManager levelManager = plugin.getLevelManager();
        List<Island> top = levelManager.getTopIslands(RANK_SLOTS.length);

        for (int i = 0; i < RANK_SLOTS.length; i++) {
            int rank = i + 1;
            if (i < top.size()) {
                Island island = top.get(i);
                setItem(RANK_SLOTS[i], buildEntry(island, rank), event -> visit(island));
            } else {
                setItem(RANK_SLOTS[i], new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(messages.get("level.menu.empty-name", "{rank}", rank))
                        .lore(messages.getList("level.menu.empty-lore"))
                        .build());
            }
        }

        setItem(INFO_SLOT, buildInfo());
        setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name(messages.get("level.menu.close-name"))
                .lore(messages.getList("level.menu.close-lore"))
                .build(), event -> viewer.closeInventory());

        fillBorder();
        fillEmpty();
    }

    /** Spelershoofd met alle gegevens van een eiland in de ranglijst. */
    private ItemStack buildEntry(Island island, int rank) {
        Messages messages = plugin.getMessages();
        UUID owner = island.getOwner();
        String ownerName = plugin.getPlayerDataManager().getName(owner);
        String islandName = islandName(island, ownerName);

        List<String> memberNames = new ArrayList<>();
        for (UUID member : island.getMembers().keySet()) {
            if (!member.equals(owner)) {
                memberNames.add(plugin.getPlayerDataManager().getName(member));
            }
        }
        String members = memberNames.isEmpty()
                ? messages.get("level.menu.no-members")
                : String.join(", ", memberNames);

        Object[] placeholders = {
                "{rank}", rank,
                "{name}", islandName,
                "{owner}", ownerName,
                "{level}", island.getLevel(),
                "{points}", island.getLevelPoints(),
                "{members}", members,
                "{member-count}", island.getMemberCount()
        };

        List<String> lore = new ArrayList<>(messages.getList("level.menu.entry-lore", placeholders));
        lore.addAll(messages.getList(canVisit(island) ? "level.menu.entry-warp" : "level.menu.entry-no-warp",
                placeholders));

        return new ItemBuilder(Material.PLAYER_HEAD)
                .skull(Bukkit.getOfflinePlayer(owner))
                .name(messages.get(rankNameKey(rank), placeholders))
                .lore(lore)
                .amount(rank)
                .glow(rank == 1)
                .hideAttributes()
                .build();
    }

    /** Informatie over het eigen eiland van de kijker (of een melding als die er geen heeft). */
    private ItemStack buildInfo() {
        Messages messages = plugin.getMessages();
        LevelManager levelManager = plugin.getLevelManager();
        Island own = plugin.getIslandManager().getIslandOf(viewer);
        if (own == null) {
            return new ItemBuilder(Material.NETHER_STAR)
                    .name(messages.get("level.menu.info-name"))
                    .lore(messages.getList("level.menu.info-no-island"))
                    .build();
        }
        int rank = levelManager.getRank(own);
        return new ItemBuilder(Material.NETHER_STAR)
                .name(messages.get("level.menu.info-name"))
                .lore(messages.getList("level.menu.info-lore",
                        "{rank}", rank < 0 ? "?" : String.valueOf(rank),
                        "{level}", own.getLevel(),
                        "{points}", own.getLevelPoints(),
                        "{next}", levelManager.getPointsToNextLevel(own)))
                .build();
    }

    /** Kleurt de top 3 anders dan de rest. */
    private static String rankNameKey(int rank) {
        return switch (rank) {
            case 1 -> "level.menu.entry-name-1";
            case 2 -> "level.menu.entry-name-2";
            case 3 -> "level.menu.entry-name-3";
            default -> "level.menu.entry-name";
        };
    }

    private String islandName(Island island, String ownerName) {
        String name = island.getName();
        if (name == null || name.isBlank()) {
            return plugin.getMessages().get("level.menu.default-island-name", "{owner}", ownerName);
        }
        return name;
    }

    /** Mag de kijker via de warp naar dit eiland? */
    private boolean canVisit(Island island) {
        return island.isWarpEnabled() && !island.isLocked() && !island.isBanned(viewer.getUniqueId());
    }

    private void visit(Island island) {
        // Het eiland kan sinds het openen van het menu verwijderd zijn
        if (plugin.getIslandManager().getIsland(island.getId()) == null || !canVisit(island)) {
            plugin.getMessages().send(viewer, "level.menu.warp-unavailable");
            return;
        }
        viewer.closeInventory();
        plugin.getMessages().send(viewer, "level.menu.teleporting",
                "{name}", islandName(island, plugin.getPlayerDataManager().getName(island.getOwner())));
        plugin.getIslandManager().teleportWarp(viewer, island);
    }
}
