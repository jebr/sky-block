package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lijst van alle openbare eilandwarps (warp aan en niet vergrendeld),
 * gesorteerd op eilandlevel. Bij meer dan 45 eilanden wordt er gepagineerd.
 */
public class WarpsMenu extends Menu {

    private static final int PER_PAGE = 45;

    private int page;

    public WarpsMenu(SkyBlockPlugin plugin, Player viewer) {
        this(plugin, viewer, 0);
    }

    public WarpsMenu(SkyBlockPlugin plugin, Player viewer, int page) {
        super(plugin, viewer, plugin.getMessages().get("menus.warps.title"), 6);
        this.page = Math.max(0, page);
    }

    @Override
    protected void build() {
        Messages messages = plugin.getMessages();
        List<Island> warps = new ArrayList<>();
        for (Island island : plugin.getIslandManager().getIslands()) {
            if (island.isWarpEnabled() && !island.isLocked()) {
                warps.add(island);
            }
        }
        warps.sort((a, b) -> Long.compare(b.getLevel(), a.getLevel()));

        int pages = Math.max(1, (warps.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) {
            page = pages - 1;
        }

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < warps.size(); i++) {
            Island island = warps.get(start + i);
            UUID owner = island.getOwner();
            String ownerName = plugin.getPlayerDataManager().getName(owner);
            String islandName = island.getName() == null || island.getName().isEmpty() ? ownerName : island.getName();
            boolean banned = island.isBanned(viewer.getUniqueId());
            setItem(i, new ItemBuilder(Material.PLAYER_HEAD)
                    .skull(Bukkit.getOfflinePlayer(owner))
                    .name(messages.get("menus.warps.island-name", "{name}", islandName))
                    .lore(messages.getList(banned ? "menus.warps.island-lore-banned" : "menus.warps.island-lore",
                            "{owner}", ownerName, "{level}", island.getLevel(),
                            "{members}", island.getMemberCount()))
                    .build(), event -> visit(island));
        }

        if (warps.isEmpty()) {
            setItem(22, new ItemBuilder(Material.STRUCTURE_VOID)
                    .name(messages.get("menus.warps.empty-name"))
                    .lore(messages.getList("menus.warps.empty-lore"))
                    .build());
        }

        // Onderste rij: navigatie
        if (page > 0) {
            setItem(45, new ItemBuilder(Material.ARROW)
                    .name(messages.get("menus.common.previous-page", "{page}", page, "{pages}", pages))
                    .build(), event -> {
                        page--;
                        refresh();
                    });
        }
        if (page < pages - 1) {
            setItem(53, new ItemBuilder(Material.ARROW)
                    .name(messages.get("menus.common.next-page", "{page}", page + 2, "{pages}", pages))
                    .build(), event -> {
                        page++;
                        refresh();
                    });
        }
        setItem(49, MainMenu.backButton(plugin),
                event -> MainMenu.openLater(plugin, viewer, () -> new MainMenu(plugin, viewer)));
        setItem(48, new ItemBuilder(Material.BOOK)
                .name(messages.get("menus.warps.info-name", "{page}", page + 1, "{pages}", pages))
                .lore(messages.getList("menus.warps.info-lore", "{count}", warps.size()))
                .build());

        ItemBuilder filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ");
        for (int slot = 45; slot < 54; slot++) {
            if (getInventory().getItem(slot) == null) {
                setItem(slot, filler.build());
            }
        }
    }

    private void visit(Island island) {
        Messages messages = plugin.getMessages();
        if (island.isBanned(viewer.getUniqueId())) {
            messages.send(viewer, "menus.warps.banned");
            return;
        }
        if (!island.isWarpEnabled() || island.isLocked()
                || plugin.getIslandManager().getIsland(island.getId()) == null) {
            messages.send(viewer, "menus.warps.unavailable");
            refresh();
            return;
        }
        MainMenu.closeThen(plugin, viewer, () -> {
            messages.send(viewer, "general.teleporting");
            plugin.getIslandManager().teleportWarp(viewer, island);
        });
    }
}
