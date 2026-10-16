package nl.jebr.skyblock.shop;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

/** Een categorie in de shop met een icoon en een lijst artikelen. */
public final class ShopCategory {

    private final String id;
    private final String name;
    private final Material icon;
    private final int slot;
    private final List<String> description;
    private final List<ShopItem> items;

    public ShopCategory(String id, String name, Material icon, int slot, List<String> description, List<ShopItem> items) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.slot = slot;
        this.description = List.copyOf(description);
        this.items = List.copyOf(items);
    }

    public String getId() {
        return id;
    }

    /** Weergavenaam met {@code &}-kleurcodes. */
    public String getName() {
        return name;
    }

    public Material getIcon() {
        return icon;
    }

    /** Gewenst vakje in het shopmenu, of -1 voor automatisch. */
    public int getSlot() {
        return slot;
    }

    public List<String> getDescription() {
        return Collections.unmodifiableList(description);
    }

    public List<ShopItem> getItems() {
        return items;
    }
}
