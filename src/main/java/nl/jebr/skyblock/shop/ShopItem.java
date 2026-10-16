package nl.jebr.skyblock.shop;

import org.bukkit.Material;

/**
 * Een artikel in de shop. Prijzen gelden per eenheid van {@link #getAmount()}
 * stuks. Een prijs van 0 of lager betekent dat kopen of verkopen niet kan.
 */
public final class ShopItem {

    private final String id;
    private final Material material;
    private final int amount;
    private final double buyPrice;
    private final double sellPrice;

    public ShopItem(String id, Material material, int amount, double buyPrice, double sellPrice) {
        this.id = id;
        this.material = material;
        this.amount = Math.max(1, amount);
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    /** Aantal stuks per eenheid. */
    public int getAmount() {
        return amount;
    }

    /** Koopprijs per eenheid. */
    public double getBuyPrice() {
        return buyPrice;
    }

    /** Verkoopprijs per eenheid. */
    public double getSellPrice() {
        return sellPrice;
    }

    public boolean canBuy() {
        return buyPrice > 0;
    }

    public boolean canSell() {
        return sellPrice > 0;
    }

    /** Verkoopprijs voor een los item. */
    public double getSellPricePerItem() {
        return sellPrice / amount;
    }

    /** Leesbare naam van het materiaal, bijv. "Cobblestone" of "Oak Log". */
    public String getDisplayName() {
        return formatMaterial(material);
    }

    /** Zet een materiaalnaam om naar een leesbare naam (COAL_ORE wordt "Coal Ore"). */
    public static String formatMaterial(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
