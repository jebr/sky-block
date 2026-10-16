package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandRole;
import nl.jebr.skyblock.shop.ShopItem;
import nl.jebr.skyblock.upgrade.UpgradeManager;
import nl.jebr.skyblock.upgrade.UpgradeType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Overzicht van de eilandupgrades met huidig en volgend niveau. Alleen de
 * eigenaar kan upgraden; het geld gaat van zijn eigen saldo af.
 */
public class UpgradesMenu extends Menu {

    private static final int[] SLOTS = {20, 22, 24};

    private final Island island;

    public UpgradesMenu(SkyBlockPlugin plugin, Player viewer, Island island) {
        super(plugin, viewer, plugin.getMessages().get("menus.upgrades.title"), 5);
        this.island = island;
    }

    @Override
    protected void build() {
        Messages messages = plugin.getMessages();
        UpgradeManager upgrades = plugin.getUpgradeManager();
        boolean owner = island.getRole(viewer.getUniqueId()).isAtLeast(IslandRole.OWNER);

        UpgradeType[] types = UpgradeType.values();
        for (int i = 0; i < types.length; i++) {
            UpgradeType type = types[i];
            int slot = i < SLOTS.length ? SLOTS[i] : 10 + i;
            int current = upgrades.getCurrentLevel(island, type);
            int max = upgrades.getMaxLevel(type);
            boolean maxed = current >= max;

            List<String> lore = new ArrayList<>(messages.getList("menus.upgrades.level-lore",
                    "{level}", current, "{max}", max));
            lore.add("");
            lore.add(messages.get("menus.upgrades.current-header"));
            lore.addAll(describe(type, current));
            if (maxed) {
                lore.add("");
                lore.addAll(messages.getList("menus.upgrades.maxed"));
            } else {
                double cost = upgrades.getCost(type, current + 1);
                lore.add("");
                lore.add(messages.get("menus.upgrades.next-header", "{level}", current + 1));
                lore.addAll(describe(type, current + 1));
                lore.add("");
                lore.add(messages.get("menus.upgrades.cost", "{cost}", plugin.getEconomy().format(cost)));
                lore.add("");
                if (!owner) {
                    lore.addAll(messages.getList("menus.upgrades.owner-only"));
                } else if (plugin.getEconomy().has(viewer.getUniqueId(), cost)) {
                    lore.addAll(messages.getList("menus.upgrades.click"));
                } else {
                    lore.addAll(messages.getList("menus.upgrades.too-expensive"));
                }
            }

            setItem(slot, new ItemBuilder(type.getIcon())
                    .name(messages.get("menus.upgrades.name", "{upgrade}", type.getDisplayName(), "{level}", current))
                    .lore(lore)
                    .glow(maxed)
                    .hideAttributes()
                    .build(), event -> upgrade(type));
        }

        setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .skull(viewer)
                .name(messages.get("menus.common.balance-name"))
                .lore(messages.getList("menus.common.balance-lore",
                        "{balance}", plugin.getEconomy().format(plugin.getEconomy().getBalance(viewer.getUniqueId()))))
                .build());

        setItem(40, MainMenu.backButton(plugin),
                event -> MainMenu.openLater(plugin, viewer, () -> new MainMenu(plugin, viewer)));

        fillBorder();
        fillEmpty();
    }

    /** Beschrijft wat een niveau oplevert (straal, ledenlimiet of generatorkansen). */
    private List<String> describe(UpgradeType type, int level) {
        Messages messages = plugin.getMessages();
        UpgradeManager upgrades = plugin.getUpgradeManager();
        List<String> lines = new ArrayList<>();
        switch (type) {
            case SIZE -> {
                int radius = upgrades.getRadiusForLevel(level);
                lines.add(messages.get("menus.upgrades.value-size", "{radius}", radius, "{size}", radius * 2 + 1));
            }
            case MEMBERS -> lines.add(messages.get("menus.upgrades.value-members",
                    "{limit}", upgrades.getMemberLimitForLevel(level)));
            case GENERATOR -> {
                Map<Material, Integer> blocks = upgrades.getGeneratorBlocks(level);
                int total = 0;
                for (int weight : blocks.values()) {
                    total += weight;
                }
                for (Map.Entry<Material, Integer> entry : blocks.entrySet()) {
                    double percent = total <= 0 ? 0 : entry.getValue() * 100.0 / total;
                    lines.add(messages.get("menus.upgrades.value-generator",
                            "{block}", ShopItem.formatMaterial(entry.getKey()),
                            "{percent}", formatPercent(percent)));
                }
            }
        }
        return lines;
    }

    private static String formatPercent(double percent) {
        if (Math.abs(percent - Math.round(percent)) < 0.05) {
            return String.valueOf(Math.round(percent));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", percent).replace('.', ',');
    }

    private void upgrade(UpgradeType type) {
        Messages messages = plugin.getMessages();
        if (!island.getRole(viewer.getUniqueId()).isAtLeast(IslandRole.OWNER)) {
            messages.send(viewer, "general.role-too-low", "{role}", IslandRole.OWNER.getDisplayName());
            return;
        }
        int next = plugin.getUpgradeManager().getCurrentLevel(island, type) + 1;
        double cost = plugin.getUpgradeManager().getCost(type, next);
        UpgradeManager.Result result = plugin.getUpgradeManager().upgrade(viewer, island, type);
        switch (result) {
            case SUCCESS -> {
                messages.send(viewer, "menus.upgrades.success", "{upgrade}", type.getDisplayName(),
                        "{level}", next, "{cost}", plugin.getEconomy().format(cost));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            case MAX_LEVEL -> messages.send(viewer, "menus.upgrades.already-max", "{upgrade}", type.getDisplayName());
            case NOT_ENOUGH_MONEY -> messages.send(viewer, "general.not-enough-money",
                    "{amount}", plugin.getEconomy().format(cost));
        }
        refresh();
    }
}
