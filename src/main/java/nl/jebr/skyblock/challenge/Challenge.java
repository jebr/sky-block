package nl.jebr.skyblock.challenge;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Een challenge uit challenges.yml. Wat er gecontroleerd wordt hangt af van het
 * {@link ChallengeType}:
 * <ul>
 *     <li>INVENTORY: {@link #getRequiredItems()} in de inventory, ingeleverd als {@link #isTakeItems()}</li>
 *     <li>ISLAND_LEVEL: eilandlevel minstens {@link #getRequiredLevel()}</li>
 *     <li>ISLAND_BLOCKS: {@link #getRequiredItems()} als blokken binnen {@link #getRadius()} blokken</li>
 * </ul>
 */
public final class Challenge {

    /** Een beloning: geld, items en console-commando's (met {@code {player}}). */
    public record Reward(double money, Map<Material, Integer> items, List<String> commands, int xp) {

        public Reward {
            items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
            commands = List.copyOf(commands);
        }

        public static Reward empty() {
            return new Reward(0, Map.of(), List.of(), 0);
        }

        public boolean isEmpty() {
            return money <= 0 && items.isEmpty() && commands.isEmpty() && xp <= 0;
        }
    }

    private final String id;
    private final String categoryId;
    private final String name;
    private final Material icon;
    private final List<String> description;
    private final ChallengeType type;
    private final Map<Material, Integer> requiredItems;
    private final boolean takeItems;
    private final long requiredLevel;
    private final int radius;
    private final Reward reward;
    private final Reward repeatReward;
    private final boolean repeatable;
    private final int maxCompletions;

    public Challenge(String id, String categoryId, String name, Material icon, List<String> description,
                     ChallengeType type, Map<Material, Integer> requiredItems, boolean takeItems,
                     long requiredLevel, int radius, Reward reward, Reward repeatReward,
                     boolean repeatable, int maxCompletions) {
        this.id = id.toLowerCase();
        this.categoryId = categoryId;
        this.name = name;
        this.icon = icon;
        this.description = List.copyOf(description);
        this.type = type;
        this.requiredItems = Collections.unmodifiableMap(new LinkedHashMap<>(requiredItems));
        this.takeItems = takeItems;
        this.requiredLevel = requiredLevel;
        this.radius = radius;
        this.reward = reward;
        this.repeatReward = repeatReward;
        this.repeatable = repeatable;
        this.maxCompletions = maxCompletions;
    }

    /** Unieke id in kleine letters. */
    public String getId() {
        return id;
    }

    public String getCategoryId() {
        return categoryId;
    }

    /** Weergavenaam met {@code &}-kleurcodes. */
    public String getName() {
        return name;
    }

    public Material getIcon() {
        return icon;
    }

    public List<String> getDescription() {
        return description;
    }

    public ChallengeType getType() {
        return type;
    }

    /** Vereiste items (INVENTORY) of blokken (ISLAND_BLOCKS) met aantallen. */
    public Map<Material, Integer> getRequiredItems() {
        return requiredItems;
    }

    /** Of de items bij INVENTORY worden ingeleverd. */
    public boolean isTakeItems() {
        return takeItems;
    }

    /** Vereist eilandlevel bij ISLAND_LEVEL. */
    public long getRequiredLevel() {
        return requiredLevel;
    }

    /** Zoekstraal rond de speler bij ISLAND_BLOCKS. */
    public int getRadius() {
        return radius;
    }

    /** Beloning bij de eerste keer voltooien. */
    public Reward getReward() {
        return reward;
    }

    /** Beloning bij herhalen (alleen als {@link #isRepeatable()}). */
    public Reward getRepeatReward() {
        return repeatReward;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    /** Maximaal aantal keer voltooien; 0 of lager is onbeperkt (bij herhaalbaar). */
    public int getMaxCompletions() {
        return maxCompletions;
    }

    /** De beloning die hoort bij een voltooiing als er al zoveel voltooiingen zijn. */
    public Reward getRewardFor(int completions) {
        return completions > 0 && repeatable ? repeatReward : reward;
    }

    /** Of de challenge nog (opnieuw) voltooid mag worden bij dit aantal voltooiingen. */
    public boolean canComplete(int completions) {
        if (completions <= 0) {
            return true;
        }
        if (!repeatable) {
            return false;
        }
        return maxCompletions <= 0 || completions < maxCompletions;
    }
}
