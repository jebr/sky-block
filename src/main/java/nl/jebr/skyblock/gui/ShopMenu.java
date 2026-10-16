package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.shop.ShopCategory;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hoofdmenu van de shop: kies een categorie, verkoop alles of bekijk je saldo.
 */
public class ShopMenu extends Menu {

    /** Standaardvakjes voor categorieen als shop.yml geen slot opgeeft. */
    private static final int[] DEFAULT_SLOTS = {11, 13, 15, 20, 22, 24, 29, 31, 33, 12, 14, 21, 23, 30, 32};

    public ShopMenu(SkyBlockPlugin plugin, Player viewer) {
        super(plugin, viewer, plugin.getMessages().get("menus.shop.title"), 5);
    }

    @Override
    protected void build() {
        Messages messages = plugin.getMessages();
        List<ShopCategory> categories = plugin.getShopManager().getCategories();

        // Eerst de vaste slots uit shop.yml, daarna de rest automatisch
        Set<Integer> used = new HashSet<>();
        List<ShopCategory> automatic = new ArrayList<>();
        for (ShopCategory category : categories) {
            int slot = category.getSlot();
            if (slot >= 9 && slot < 36 && slot % 9 != 0 && slot % 9 != 8 && used.add(slot)) {
                placeCategory(slot, category);
            } else {
                automatic.add(category);
            }
        }
        int index = 0;
        for (ShopCategory category : automatic) {
            while (index < DEFAULT_SLOTS.length && used.contains(DEFAULT_SLOTS[index])) {
                index++;
            }
            if (index >= DEFAULT_SLOTS.length) {
                break;
            }
            used.add(DEFAULT_SLOTS[index]);
            placeCategory(DEFAULT_SLOTS[index], category);
        }

        if (categories.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(messages.get("menus.shop.empty-name"))
                    .lore(messages.getList("menus.shop.empty-lore"))
                    .build());
        }

        // Onderste rij: saldo, terug, alles verkopen
        setItem(38, balanceItem(plugin, viewer));
        setItem(40, MainMenu.backButton(plugin),
                event -> MainMenu.openLater(plugin, viewer, () -> new MainMenu(plugin, viewer)));
        setItem(42, sellAllItem(plugin), event -> {
            plugin.getShopManager().sellAll(viewer);
            refresh();
        });

        fillBorder();
        fillEmpty();
    }

    private void placeCategory(int slot, ShopCategory category) {
        Messages messages = plugin.getMessages();
        List<String> lore = new ArrayList<>(category.getDescription());
        lore.addAll(messages.getList("menus.shop.category-lore", "{count}", category.getItems().size()));
        setItem(slot, new ItemBuilder(category.getIcon())
                .name(messages.get("menus.shop.category-name", "{category}", category.getName()))
                .lore(lore)
                .hideAttributes()
                .build(), event -> MainMenu.openLater(plugin, viewer,
                () -> new ShopCategoryMenu(plugin, viewer, category.getId())));
    }

    /** Spelershoofd met het huidige saldo. */
    static org.bukkit.inventory.ItemStack balanceItem(SkyBlockPlugin plugin, Player viewer) {
        return new ItemBuilder(Material.PLAYER_HEAD)
                .skull(viewer)
                .name(plugin.getMessages().get("menus.common.balance-name"))
                .lore(plugin.getMessages().getList("menus.common.balance-lore",
                        "{balance}", plugin.getEconomy().format(plugin.getEconomy().getBalance(viewer.getUniqueId()))))
                .build();
    }

    /** Knop om alle verkoopbare items te verkopen. */
    static org.bukkit.inventory.ItemStack sellAllItem(SkyBlockPlugin plugin) {
        return new ItemBuilder(Material.HOPPER)
                .name(plugin.getMessages().get("menus.shop.sell-all-name"))
                .lore(plugin.getMessages().getList("menus.shop.sell-all-lore"))
                .build();
    }
}
