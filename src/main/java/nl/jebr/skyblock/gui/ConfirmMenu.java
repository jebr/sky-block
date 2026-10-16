package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Eenvoudig bevestigingsmenu met een groene bevestig- en een rode
 * annuleerknop. Bij bevestigen wordt het menu gesloten en daarna
 * {@code onConfirm} uitgevoerd (een tick later, buiten het klikevent).
 */
public class ConfirmMenu extends Menu {

    private final String title;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean handled;

    public ConfirmMenu(SkyBlockPlugin plugin, Player viewer, String title, Runnable onConfirm) {
        this(plugin, viewer, title, onConfirm, null);
    }

    /**
     * @param onCancel wordt uitgevoerd bij annuleren (bijv. terug naar het vorige menu); mag null zijn
     */
    public ConfirmMenu(SkyBlockPlugin plugin, Player viewer, String title, Runnable onConfirm, Runnable onCancel) {
        super(plugin, viewer, title, 3);
        this.title = title;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void build() {
        setItem(11, new ItemBuilder(Material.LIME_CONCRETE)
                .name(plugin.getMessages().get("menus.confirm.confirm-name"))
                .lore(plugin.getMessages().getList("menus.confirm.confirm-lore"))
                .build(), event -> finish(true));

        setItem(13, new ItemBuilder(Material.PAPER)
                .name(plugin.getMessages().get("menus.confirm.info-name", "{title}", title))
                .lore(plugin.getMessages().getList("menus.confirm.info-lore"))
                .build());

        setItem(15, new ItemBuilder(Material.RED_CONCRETE)
                .name(plugin.getMessages().get("menus.confirm.cancel-name"))
                .lore(plugin.getMessages().getList("menus.confirm.cancel-lore"))
                .build(), event -> finish(false));

        fillBorder();
        fillEmpty();
    }

    private void finish(boolean confirmed) {
        if (handled) {
            return;
        }
        handled = true;
        // Sluiten en uitvoeren buiten het klikevent om
        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.closeInventory();
            if (confirmed) {
                if (onConfirm != null) {
                    onConfirm.run();
                }
            } else if (onCancel != null) {
                onCancel.run();
            }
        });
    }
}
