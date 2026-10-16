package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandRole;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Overzicht van de eilandleden. De eigenaar kan leden promoveren en
 * degraderen; eigenaar en mede-eigenaars kunnen leden verwijderen.
 */
public class MembersMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final Island island;

    public MembersMenu(SkyBlockPlugin plugin, Player viewer, Island island) {
        super(plugin, viewer, plugin.getMessages().get("menus.members.title"), 5);
        this.island = island;
    }

    @Override
    protected void build() {
        Messages messages = plugin.getMessages();
        IslandRole viewerRole = island.getRole(viewer.getUniqueId());

        // Eigenaar eerst, daarna mede-eigenaars, daarna leden
        List<Map.Entry<UUID, IslandRole>> members = new ArrayList<>(island.getMembers().entrySet());
        members.sort((a, b) -> Integer.compare(b.getValue().getWeight(), a.getValue().getWeight()));

        int index = 0;
        for (Map.Entry<UUID, IslandRole> entry : members) {
            if (index >= SLOTS.length) {
                break;
            }
            UUID uuid = entry.getKey();
            IslandRole role = entry.getValue();
            String name = plugin.getPlayerDataManager().getName(uuid);
            boolean online = Bukkit.getPlayer(uuid) != null;

            List<String> lore = new ArrayList<>(messages.getList("menus.members.member-lore",
                    "{role}", role.getDisplayName(),
                    "{status}", messages.get(online ? "menus.members.online" : "menus.members.offline")));
            if (!uuid.equals(viewer.getUniqueId()) && role != IslandRole.OWNER) {
                if (viewerRole == IslandRole.OWNER) {
                    lore.addAll(messages.getList(role == IslandRole.MEMBER
                            ? "menus.members.hint-promote" : "menus.members.hint-demote"));
                    lore.addAll(messages.getList("menus.members.hint-kick"));
                } else if (viewerRole == IslandRole.CO_OWNER && role == IslandRole.MEMBER) {
                    lore.addAll(messages.getList("menus.members.hint-kick"));
                }
            }

            setItem(SLOTS[index++], new ItemBuilder(Material.PLAYER_HEAD)
                    .skull(Bukkit.getOfflinePlayer(uuid))
                    .name(messages.get("menus.members.member-name", "{player}", name, "{role}", role.getDisplayName()))
                    .lore(lore)
                    .build(), event -> click(event, uuid));
        }

        int limit = plugin.getUpgradeManager().getMemberLimit(island);
        setItem(4, new ItemBuilder(Material.BOOK)
                .name(messages.get("menus.members.info-name"))
                .lore(messages.getList("menus.members.info-lore",
                        "{count}", island.getMemberCount(), "{limit}", limit))
                .build());

        setItem(40, MainMenu.backButton(plugin),
                event -> MainMenu.openLater(plugin, viewer, () -> new MainMenu(plugin, viewer)));

        fillBorder();
        fillEmpty();
    }

    private void click(InventoryClickEvent event, UUID target) {
        Messages messages = plugin.getMessages();
        IslandRole viewerRole = island.getRole(viewer.getUniqueId());
        IslandRole targetRole = island.getRole(target);
        if (target.equals(viewer.getUniqueId()) || targetRole == IslandRole.OWNER || targetRole == IslandRole.VISITOR) {
            return;
        }
        String name = plugin.getPlayerDataManager().getName(target);

        if (event.isShiftClick()) {
            // Kicken: eigenaar mag iedereen, mede-eigenaar alleen gewone leden
            boolean allowed = viewerRole == IslandRole.OWNER
                    || (viewerRole == IslandRole.CO_OWNER && targetRole == IslandRole.MEMBER);
            if (!allowed) {
                messages.send(viewer, "menus.members.cannot-kick", "{player}", name);
                return;
            }
            MainMenu.openLater(plugin, viewer, () -> new ConfirmMenu(plugin, viewer,
                    messages.get("menus.members.kick-confirm-title", "{player}", name),
                    () -> kick(target, name),
                    () -> new MembersMenu(plugin, viewer, island).open()));
            return;
        }

        if (!event.isLeftClick()) {
            return;
        }
        if (viewerRole != IslandRole.OWNER) {
            messages.send(viewer, "general.role-too-low", "{role}", IslandRole.OWNER.getDisplayName());
            return;
        }
        IslandRole newRole = targetRole == IslandRole.MEMBER ? IslandRole.CO_OWNER : IslandRole.MEMBER;
        island.setRole(target, newRole);
        messages.send(viewer, newRole == IslandRole.CO_OWNER ? "menus.members.promoted" : "menus.members.demoted",
                "{player}", name, "{role}", newRole.getDisplayName());
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            messages.send(online, "menus.members.role-changed", "{role}", newRole.getDisplayName(),
                    "{player}", viewer.getName());
        }
        refresh();
    }

    private void kick(UUID target, String name) {
        Messages messages = plugin.getMessages();
        // Opnieuw controleren: de situatie kan veranderd zijn tijdens het bevestigen
        IslandRole viewerRole = island.getRole(viewer.getUniqueId());
        IslandRole targetRole = island.getRole(target);
        boolean allowed = targetRole != IslandRole.OWNER && targetRole != IslandRole.VISITOR
                && (viewerRole == IslandRole.OWNER || (viewerRole == IslandRole.CO_OWNER && targetRole == IslandRole.MEMBER));
        if (!allowed) {
            messages.send(viewer, "menus.members.cannot-kick", "{player}", name);
            return;
        }
        plugin.getIslandManager().removeMember(island, target);
        messages.send(viewer, "menus.members.kicked", "{player}", name);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            messages.send(online, "menus.members.you-were-kicked", "{player}", viewer.getName());
        }
        new MembersMenu(plugin, viewer, island).open();
    }
}
