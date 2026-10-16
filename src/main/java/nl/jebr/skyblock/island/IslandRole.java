package nl.jebr.skyblock.island;

/**
 * Rollen op een eiland, van laag naar hoog.
 * <ul>
 *     <li>VISITOR: geen lid, alleen wat de eilandinstellingen (flags) toestaan</li>
 *     <li>MEMBER: mag bouwen, breken en alles gebruiken</li>
 *     <li>CO_OWNER: daarnaast uitnodigen, kicken, instellingen, warp, bannen</li>
 *     <li>OWNER: daarnaast upgrades, reset, overdragen, promoveren/degraderen</li>
 * </ul>
 */
public enum IslandRole {
    VISITOR(0, "Bezoeker"),
    MEMBER(1, "Lid"),
    CO_OWNER(2, "Mede-eigenaar"),
    OWNER(3, "Eigenaar");

    private final int weight;
    private final String displayName;

    IslandRole(int weight, String displayName) {
        this.weight = weight;
        this.displayName = displayName;
    }

    public int getWeight() {
        return weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Of deze rol minstens zo hoog is als de opgegeven rol. */
    public boolean isAtLeast(IslandRole other) {
        return weight >= other.weight;
    }
}
