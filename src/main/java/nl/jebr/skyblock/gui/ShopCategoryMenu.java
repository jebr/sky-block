package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.shop.ShopCategory;
import nl.jebr.skyblock.shop.ShopItem;
import nl.jebr.skyblock.shop.ShopManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * De artikelen van een shopcategorie.
 * <ul>
 *     <li>Linksklik: 1 eenheid kopen</li>
 *     <li>Shift-linksklik: 8 eenheden kopen</li>
 *     <li>Rechtsklik: 1 eenheid verkopen</li>
 *     <li>Shift-rechtsklik: alles van dit item verkopen</li>
 * </ul>
 */
public class ShopCategoryMenu extends Menu {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int BULK_UNITS = 8;

    private final String categoryId;
    private int page;

    public ShopCategoryMenu(SkyBlockPlugin plugin, Player viewer, String categoryId) {
        super(plugin, viewer, titleFor(plugin, categoryId), 6);
        this.categoryId = categoryId;
    }

    private static String titleFor(SkyBlockPlugin plugin, String categoryId) {
        ShopCategory category = plugin.getShopManager().getCategory(categoryId);
        return plugin.getMessages().get("menus.shop.category-title",
                "{category}", category == null ? categoryId : category.getName());
    }

    @Override
    protected void build() {
        Messages messages = plugin.getMessages();
        ShopCategory category = plugin.getShopManager().getCategory(categoryId);
        List<ShopItem> items = category == null ? List.of() : category.getItems();

        int pages = Math.max(1, (items.size() + SLOTS.length - 1) / SLOTS.length);
        if (page >= pages) {
            page = pages - 1;
        }
        int start = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && start + i < items.size(); i++) {
            ShopItem item = items.get(start + i);
            setItem(SLOTS[i], shopItem(item), event -> click(event, item));
        }

        if (items.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(messages.get("menus.shop.empty-name"))
                    .lore(messages.getList("menus.shop.empty-lore"))
                    .build());
        }

        // Bovenste rij: uitleg en saldo
        setItem(3, new ItemBuilder(Material.OAK_SIGN)
                .name(messages.get("menus.shop.help-name"))
                .lore(messages.getList("menus.shop.help-lore", "{bulk}", BULK_UNITS))
                .build());
        setItem(5, ShopMenu.balanceItem(plugin, viewer));

        // Onderste rij: navigatie, terug, alles verkopen
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
        setItem(48, new ItemBuilder(Material.ARROW)
                .name(messages.get("menus.shop.back-name"))
                .lore(messages.getList("menus.shop.back-lore"))
                .build(), event -> MainMenu.openLater(plugin, viewer, () -> new ShopMenu(plugin, viewer)));
        setItem(50, ShopMenu.sellAllItem(plugin), event -> {
            plugin.getShopManager().sellAll(viewer);
            refresh();
        });

        fillBorder();
        fillEmpty();
    }

    private org.bukkit.inventory.ItemStack shopItem(ShopItem item) {
        Messages messages = plugin.getMessages();
        List<String> lore = new ArrayList<>();
        lore.add(messages.get("menus.shop.item-unit", "{amount}", item.getAmount()));
        lore.add("");
        if (item.canBuy()) {
            lore.add(messages.get("menus.shop.item-buy",
                    "{price}", plugin.getEconomy().format(item.getBuyPrice()),
                    "{bulk}", BULK_UNITS,
                    "{bulk-price}", plugin.getEconomy().format(item.getBuyPrice() * BULK_UNITS)));
        } else {
            lore.add(messages.get("menus.shop.item-no-buy"));
        }
        if (item.canSell()) {
            lore.add(messages.get("menus.shop.item-sell",
                    "{price}", plugin.getEconomy().format(item.getSellPrice())));
            int have = plugin.getShopManager().countPlain(viewer, item.getMaterial());
            lore.add(messages.get("menus.shop.item-have", "{amount}", have,
                    "{price}", plugin.getEconomy().format(item.getSellPricePerItem() * have)));
        } else {
            lore.add(messages.get("menus.shop.item-no-sell"));
        }
        lore.add("");
        if (item.canBuy()) {
            lore.addAll(messages.getList("menus.shop.item-hint-buy", "{bulk}", BULK_UNITS));
        }
        if (item.canSell()) {
            lore.addAll(messages.getList("menus.shop.item-hint-sell"));
        }
        return new ItemBuilder(item.getMaterial(), item.getAmount())
                .name(messages.get("menus.shop.item-name", "{item}", item.getDisplayName()))
                .lore(lore)
                .hideAttributes()
                .build();
    }

    private void click(InventoryClickEvent event, ShopItem clicked) {
        ShopManager shop = plugin.getShopManager();
        // Na /isadmin reload kunnen prijzen veranderd of het item verdwenen zijn: actuele versie opzoeken
        ShopItem item = null;
        ShopCategory category = shop.getCategory(categoryId);
        if (category != null) {
            for (ShopItem candidate : category.getItems()) {
                if (candidate.getId().equals(clicked.getId())) {
                    item = candidate;
                    break;
                }
            }
        }
        if (item == null) {
            refresh();
            return;
        }
        ClickType click = event.getClick();
        switch (click) {
            case LEFT -> shop.buy(viewer, item, 1);
            case SHIFT_LEFT -> shop.buy(viewer, item, BULK_UNITS);
            case RIGHT -> shop.sell(viewer, item, 1);
            case SHIFT_RIGHT -> shop.sell(viewer, item, -1);
            default -> {
                return;
            }
        }
        refresh();
    }
}
