package nl.jebr.skyblock.upgrade;

import org.bukkit.Material;

/** Soorten eilandupgrades. De configsleutel staat onder upgrades.&lt;key&gt; in config.yml. */
public enum UpgradeType {
    SIZE("size", "Eilandgrootte", Material.GRASS_BLOCK),
    MEMBERS("members", "Maximaal aantal leden", Material.PLAYER_HEAD),
    GENERATOR("generator", "Cobblestone-generator", Material.COBBLESTONE);

    private final String key;
    private final String displayName;
    private final Material icon;

    UpgradeType(String key, String displayName, Material icon) {
        this.key = key;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }
}
