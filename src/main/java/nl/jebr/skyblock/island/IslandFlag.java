package nl.jebr.skyblock.island;

import org.bukkit.Material;

/**
 * Instellingen die bepalen wat bezoekers (niet-leden) op een eiland mogen,
 * plus enkele algemene eilandinstellingen. Leden mogen altijd alles.
 */
public enum IslandFlag {
    VISITOR_BREAK("Blokken breken", Material.DIAMOND_PICKAXE, false),
    VISITOR_PLACE("Blokken plaatsen", Material.GRASS_BLOCK, false),
    VISITOR_DOORS("Deuren, knoppen en hendels", Material.OAK_DOOR, true),
    VISITOR_CONTAINERS("Kisten en ovens openen", Material.CHEST, false),
    VISITOR_ITEM_PICKUP("Items oppakken", Material.HOPPER, false),
    VISITOR_ITEM_DROP("Items laten vallen", Material.DROPPER, true),
    VISITOR_ANIMALS("Dieren slaan en gebruiken", Material.WHEAT, false),
    VISITOR_CRAFTING("Werkbanken en aambeelden", Material.CRAFTING_TABLE, true),
    PVP("PvP op het eiland", Material.IRON_SWORD, false),
    MONSTER_SPAWNING("Monsters spawnen", Material.ZOMBIE_HEAD, true),
    ANIMAL_SPAWNING("Dieren spawnen", Material.EGG, true);

    private final String displayName;
    private final Material icon;
    private final boolean defaultValue;

    IslandFlag(String displayName, Material icon, boolean defaultValue) {
        this.displayName = displayName;
        this.icon = icon;
        this.defaultValue = defaultValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }
}
