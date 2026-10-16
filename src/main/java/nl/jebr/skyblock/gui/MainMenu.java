package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandManager;
import nl.jebr.skyblock.island.IslandRole;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

/**
 * Het hoofdmenu van /island. Zonder eiland toont het een grote knop om een
 * eiland te maken; met eiland geeft het toegang tot alle onderdelen.
 */
public class MainMenu extends Menu {

    public MainMenu(SkyBlockPlugin plugin, Player viewer) {
        super(plugin, viewer, plugin.getMessages().get("menus.main.title"), 5);
    }

    /**
     * Sluit het huidige menu en opent een ander menu een tick later, zodat dit
     * niet midden in een klikevent gebeurt.
     */
    static void openLater(SkyBlockPlugin plugin, Player player, Supplier<? extends Menu> menu) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                menu.get().open();
            }
        });
    }

    /** Sluit het menu een tick later en voert daarna de actie uit. */
    static void closeThen(SkyBlockPlugin plugin, Player player, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (action != null && player.isOnline()) {
                action.run();
            }
        });
    }

    /** Of dit menu nog open staat bij de kijker. */
    boolean isOpenFor(Player player) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == this;
    }

    @Override
    protected void build() {
        Island island = plugin.getIslandManager().getIslandOf(viewer);
        if (island == null) {
            buildNoIsland();
        } else {
            buildWithIsland(island);
        }
        fillBorder();
        fillEmpty();
    }

    // ================================================================== zonder eiland

    private void buildNoIsland() {
        Messages messages = plugin.getMessages();
        setItem(22, new ItemBuilder(Material.GRASS_BLOCK)
                .name(messages.get("menus.main.create-name"))
                .lore(messages.getList("menus.main.create-lore"))
                .glow(true)
                .build(), event -> closeThen(plugin, viewer, () -> {
                    if (plugin.getIslandManager().getIslandOf(viewer) != null) {
                        messages.send(viewer, "general.already-has-island");
                        return;
                    }
                    messages.send(viewer, "menus.main.creating");
                    plugin.getIslandManager().createIsland(viewer);
                    messages.send(viewer, "menus.main.created");
                }));

        setItem(20, new ItemBuilder(Material.ENDER_PEARL)
                .name(messages.get("menus.main.warps-name"))
                .lore(messages.getList("menus.main.warps-lore"))
                .build(), event -> openLater(plugin, viewer, () -> new WarpsMenu(plugin, viewer)));

        setItem(24, new ItemBuilder(Material.GOLDEN_HELMET)
                .name(messages.get("menus.main.top-name"))
                .lore(messages.getList("menus.main.top-lore"))
                .hideAttributes()
                .build(), event -> openLater(plugin, viewer, () -> new TopMenu(plugin, viewer)));

        setItem(40, closeButton(), event -> closeThen(plugin, viewer, null));
    }

    // ================================================================== met eiland

    private void buildWithIsland(Island island) {
        Messages messages = plugin.getMessages();
        IslandRole role = island.getRole(viewer.getUniqueId());
        boolean coOwner = role.isAtLeast(IslandRole.CO_OWNER);
        boolean owner = role.isAtLeast(IslandRole.OWNER);
        String islandName = island.getName() == null ? plugin.getPlayerDataManager().getName(island.getOwner()) : island.getName();

        // Rij 1: home, level, top
        setItem(11, new ItemBuilder(Material.OAK_DOOR)
                .name(messages.get("menus.main.home-name"))
                .lore(messages.getList("menus.main.home-lore"))
                .build(), event -> closeThen(plugin, viewer, () -> {
                    Island current = plugin.getIslandManager().getIslandOf(viewer);
                    if (current == null) {
                        messages.send(viewer, "general.no-island");
                        return;
                    }
                    messages.send(viewer, "general.teleporting");
                    plugin.getIslandManager().teleportHome(viewer, current);
                }));

        boolean calculating = plugin.getLevelManager().isCalculating(island);
        setItem(13, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name(messages.get("menus.main.level-name", "{level}", island.getLevel()))
                .lore(messages.getList(calculating ? "menus.main.level-lore-busy" : "menus.main.level-lore",
                        "{level}", island.getLevel(), "{points}", island.getLevelPoints(), "{name}", islandName))
                .build(), event -> recalculate());

        setItem(15, new ItemBuilder(Material.GOLDEN_HELMET)
                .name(messages.get("menus.main.top-name"))
                .lore(messages.getList("menus.main.top-lore"))
                .hideAttributes()
                .build(), event -> openLater(plugin, viewer, () -> new TopMenu(plugin, viewer)));

        // Rij 2: leden, instellingen, upgrades, warps
        int limit = plugin.getUpgradeManager().getMemberLimit(island);
        setItem(19, new ItemBuilder(Material.PLAYER_HEAD)
                .name(messages.get("menus.main.members-name"))
                .lore(messages.getList("menus.main.members-lore",
                        "{count}", island.getMemberCount(), "{limit}", limit))
                .build(), event -> openLater(plugin, viewer, () -> new MembersMenu(plugin, viewer, island)));

        setItem(21, new ItemBuilder(Material.COMPARATOR)
                .name(messages.get("menus.main.settings-name"))
                .lore(messages.getList(coOwner ? "menus.main.settings-lore" : "menus.main.settings-lore-locked"))
                .build(), event -> {
                    if (!island.getRole(viewer.getUniqueId()).isAtLeast(IslandRole.CO_OWNER)) {
                        messages.send(viewer, "general.role-too-low", "{role}", IslandRole.CO_OWNER.getDisplayName());
                        return;
                    }
                    openLater(plugin, viewer, () -> new SettingsMenu(plugin, viewer, island));
                });

        setItem(23, new ItemBuilder(Material.ANVIL)
                .name(messages.get("menus.main.upgrades-name"))
                .lore(messages.getList("menus.main.upgrades-lore"))
                .build(), event -> openLater(plugin, viewer, () -> new UpgradesMenu(plugin, viewer, island)));

        setItem(25, new ItemBuilder(Material.ENDER_PEARL)
                .name(messages.get("menus.main.warps-name"))
                .lore(messages.getList("menus.main.warps-lore"))
                .build(), event -> openLater(plugin, viewer, () -> new WarpsMenu(plugin, viewer)));

        // Rij 3: challenges, saldo, shop
        setItem(29, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(messages.get("menus.main.challenges-name"))
                .lore(messages.getList("menus.main.challenges-lore"))
                .build(), event -> openLater(plugin, viewer, () -> new ChallengesMenu(plugin, viewer)));

        double balance = plugin.getEconomy().getBalance(viewer.getUniqueId());
        setItem(31, new ItemBuilder(Material.PLAYER_HEAD)
                .skull(viewer)
                .name(messages.get("menus.main.balance-name", "{player}", viewer.getName()))
                .lore(messages.getList("menus.main.balance-lore", "{balance}", plugin.getEconomy().format(balance)))
                .build());

        setItem(33, new ItemBuilder(Material.EMERALD)
                .name(messages.get("menus.main.shop-name"))
                .lore(messages.getList("menus.main.shop-lore"))
                .build(), event -> openLater(plugin, viewer, () -> new ShopMenu(plugin, viewer)));

        // Onderste rij: vergrendelen, sluiten, resetten
        boolean locked = island.isLocked();
        setItem(38, new ItemBuilder(locked ? Material.IRON_DOOR : Material.OAK_FENCE_GATE)
                .name(messages.get(locked ? "menus.main.lock-name-locked" : "menus.main.lock-name-open"))
                .lore(messages.getList(coOwner ? "menus.main.lock-lore" : "menus.main.lock-lore-no-permission"))
                .build(), event -> toggleLock(island));

        setItem(40, closeButton(), event -> closeThen(plugin, viewer, null));

        setItem(42, new ItemBuilder(Material.TNT)
                .name(messages.get("menus.main.reset-name"))
                .lore(messages.getList(owner ? "menus.main.reset-lore" : "menus.main.reset-lore-no-permission"))
                .build(), event -> reset(island));
    }

    private ItemStack closeButton() {
        return new ItemBuilder(Material.BARRIER)
                .name(plugin.getMessages().get("menus.common.close-name"))
                .build();
    }

    /** Standaard terugknop naar het hoofdmenu (gedeeld door alle submenu's). */
    static ItemStack backButton(SkyBlockPlugin plugin) {
        return new ItemBuilder(Material.ARROW)
                .name(plugin.getMessages().get("menus.common.back-name"))
                .lore(plugin.getMessages().getList("menus.common.back-lore"))
                .build();
    }

    // ================================================================== acties

    private void recalculate() {
        Messages messages = plugin.getMessages();
        Island island = plugin.getIslandManager().getIslandOf(viewer);
        if (island == null) {
            messages.send(viewer, "general.no-island");
            return;
        }
        if (plugin.getLevelManager().isCalculating(island)) {
            messages.send(viewer, "menus.main.level-busy");
            return;
        }
        messages.send(viewer, "menus.main.level-started");
        plugin.getLevelManager().calculate(island, level -> {
            if (viewer.isOnline()) {
                messages.send(viewer, "menus.main.level-done", "{level}", level);
            }
            if (isOpenFor(viewer)) {
                refresh();
            }
        });
        refresh();
    }

    private void toggleLock(Island island) {
        Messages messages = plugin.getMessages();
        if (plugin.getIslandManager().getIsland(island.getId()) == null) {
            messages.send(viewer, "general.no-island");
            closeThen(plugin, viewer, null);
            return;
        }
        if (!island.getRole(viewer.getUniqueId()).isAtLeast(IslandRole.CO_OWNER)) {
            messages.send(viewer, "general.role-too-low", "{role}", IslandRole.CO_OWNER.getDisplayName());
            return;
        }
        boolean locked = !island.isLocked();
        island.setLocked(locked);
        messages.send(viewer, locked ? "menus.main.locked" : "menus.main.unlocked");
        if (locked) {
            // Net als bij /is lock: bezoekers die nu op het eiland staan naar de spawn sturen
            int radius = plugin.getIslandManager().getRadius(island);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (island.isMember(online.getUniqueId()) || online.hasPermission("skyblock.bypass")) {
                    continue;
                }
                if (island.contains(online.getLocation(), radius)) {
                    plugin.getIslandManager().teleportToSpawn(online);
                    messages.send(online, "commands.lock.removed-visitor");
                }
            }
        }
        refresh();
    }

    private void reset(Island island) {
        Messages messages = plugin.getMessages();
        if (!island.getRole(viewer.getUniqueId()).isAtLeast(IslandRole.OWNER)) {
            messages.send(viewer, "general.role-too-low", "{role}", IslandRole.OWNER.getDisplayName());
            return;
        }
        openLater(plugin, viewer, () -> new ConfirmMenu(plugin, viewer, messages.get("menus.main.reset-confirm-title"),
                () -> {
                    IslandManager.ResetResult result = plugin.getIslandManager().resetIsland(viewer);
                    switch (result) {
                        case SUCCESS -> messages.send(viewer, "menus.main.reset-success");
                        case NO_ISLAND -> messages.send(viewer, "general.no-island");
                        case NOT_OWNER -> messages.send(viewer, "general.role-too-low",
                                "{role}", IslandRole.OWNER.getDisplayName());
                        case COOLDOWN -> messages.send(viewer, "menus.main.reset-cooldown",
                                "{seconds}", plugin.getIslandManager().getResetCooldownRemaining(viewer.getUniqueId()));
                        case MAX_RESETS -> messages.send(viewer, "menus.main.reset-max",
                                "{max}", plugin.getConfig().getInt("island.max-resets", -1));
                    }
                },
                () -> new MainMenu(plugin, viewer).open()));
    }
}
