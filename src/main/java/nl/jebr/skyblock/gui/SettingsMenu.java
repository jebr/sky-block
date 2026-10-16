package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandFlag;
import nl.jebr.skyblock.island.IslandRole;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Instellingen (flags) van een eiland. Elke instelling is een knop die
 * aan- of uitgezet kan worden door een mede-eigenaar of de eigenaar.
 */
public class SettingsMenu extends Menu {

    /** Vakjes voor de flags: rij 2 en 3 binnen de rand. */
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final Island island;

    public SettingsMenu(SkyBlockPlugin plugin, Player viewer, Island island) {
        super(plugin, viewer, plugin.getMessages().get("menus.settings.title"), 5);
        this.island = island;
    }

    @Override
    protected void build() {
        Messages messages = plugin.getMessages();
        boolean canEdit = island.getRole(viewer.getUniqueId()).isAtLeast(IslandRole.CO_OWNER);
        IslandFlag[] flags = IslandFlag.values();
        for (int i = 0; i < flags.length && i < SLOTS.length; i++) {
            IslandFlag flag = flags[i];
            boolean enabled = island.getFlag(flag);
            String status = messages.get(enabled ? "menus.settings.status-on" : "menus.settings.status-off");
            ItemBuilder builder = new ItemBuilder(flag.getIcon())
                    .name(messages.get(enabled ? "menus.settings.flag-name-on" : "menus.settings.flag-name-off",
                            "{flag}", flag.getDisplayName()))
                    .lore(messages.getList(canEdit ? "menus.settings.flag-lore" : "menus.settings.flag-lore-locked",
                            "{status}", status,
                            "{default}", messages.get(flag.getDefaultValue() ? "menus.settings.status-on" : "menus.settings.status-off")))
                    .glow(enabled)
                    .hideAttributes();
            setItem(SLOTS[i], builder.build(), event -> toggle(flag));
        }

        setItem(4, new ItemBuilder(Material.COMPARATOR)
                .name(messages.get("menus.settings.info-name"))
                .lore(messages.getList("menus.settings.info-lore"))
                .build());

        setItem(40, MainMenu.backButton(plugin),
                event -> MainMenu.openLater(plugin, viewer, () -> new MainMenu(plugin, viewer)));

        fillBorder();
        fillEmpty();
    }

    private void toggle(IslandFlag flag) {
        Messages messages = plugin.getMessages();
        if (plugin.getIslandManager().getIsland(island.getId()) == null) {
            messages.send(viewer, "general.no-island");
            MainMenu.closeThen(plugin, viewer, null);
            return;
        }
        if (!island.getRole(viewer.getUniqueId()).isAtLeast(IslandRole.CO_OWNER)) {
            messages.send(viewer, "general.role-too-low", "{role}", IslandRole.CO_OWNER.getDisplayName());
            return;
        }
        boolean value = !island.getFlag(flag);
        island.setFlag(flag, value);
        messages.send(viewer, value ? "menus.settings.toggled-on" : "menus.settings.toggled-off",
                "{flag}", flag.getDisplayName());
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, value ? 1.4f : 0.8f);
        refresh();
    }
}
