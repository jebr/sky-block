package nl.jebr.skyblock.challenge;

import org.bukkit.Material;

import java.util.List;

/**
 * Een categorie challenges, bijvoorbeeld "Beginner". Een categorie wordt pas
 * ontgrendeld als het eiland het vereiste level heeft.
 */
public final class ChallengeCategory {

    private final String id;
    private final String name;
    private final Material icon;
    private final long requiredLevel;
    private final List<String> description;

    public ChallengeCategory(String id, String name, Material icon, long requiredLevel, List<String> description) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.requiredLevel = Math.max(0, requiredLevel);
        this.description = List.copyOf(description);
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

    /** Het eilandlevel dat nodig is om deze categorie te ontgrendelen. */
    public long getRequiredLevel() {
        return requiredLevel;
    }

    public List<String> getDescription() {
        return description;
    }
}
