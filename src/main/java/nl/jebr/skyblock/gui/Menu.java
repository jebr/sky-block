package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Basis voor alle GUI-menu's. Een subklasse vult het menu in {@link #build()}
 * met {@link #setItem(int, ItemStack, Consumer)}. Alle klikken worden
 * geannuleerd; alleen knoppen met een actie doen iets.
 *
 * <pre>
 * public class VoorbeeldMenu extends Menu {
 *     public VoorbeeldMenu(SkyBlockPlugin plugin, Player viewer) {
 *         super(plugin, viewer, "&amp;8Voorbeeld", 3);
 *     }
 *     protected void build() {
 *         setItem(13, new ItemBuilder(Material.DIAMOND).name("&amp;bKlik").build(), e -&gt; viewer.sendMessage("Hoi"));
 *     }
 * }
 * new VoorbeeldMenu(plugin, player).open();
 * </pre>
 */
public abstract class Menu implements InventoryHolder {

    protected final SkyBlockPlugin plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(SkyBlockPlugin plugin, Player viewer, String title, int rows) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, Messages.color(title));
    }

    /** Vult het menu. Wordt aangeroepen bij openen en bij {@link #refresh()}. */
    protected abstract void build();

    /** Bouwt het menu op en opent het voor de kijker. */
    public void open() {
        refresh();
        viewer.openInventory(inventory);
    }

    /** Leegt het menu en bouwt het opnieuw op (zonder het te sluiten). */
    public void refresh() {
        inventory.clear();
        actions.clear();
        build();
    }

    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /** Vult alle lege vakjes met grijs glas. */
    protected void fillEmpty() {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /** Vult de rand (bovenste en onderste rij plus zijkanten) met glas. */
    protected void fillBorder() {
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == size / 9 - 1 || col == 0 || col == 8) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        }
    }

    public int getSize() {
        return inventory.getSize();
    }

    void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
