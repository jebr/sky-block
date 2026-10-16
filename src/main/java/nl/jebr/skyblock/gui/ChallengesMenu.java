package nl.jebr.skyblock.gui;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.challenge.Challenge;
import nl.jebr.skyblock.challenge.ChallengeCategory;
import nl.jebr.skyblock.challenge.ChallengeManager;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.shop.ShopItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Challenges per categorie. De bovenste rij bevat tabbladen voor de
 * categorieen; daaronder staan de challenges van de gekozen categorie.
 */
public class ChallengesMenu extends Menu {

    /** Vakjes voor de categorie-tabs in de bovenste rij. */
    private static final int[] TAB_SLOTS = {1, 2, 3, 4, 5, 6, 7};
    /** Vakjes voor challenges binnen de rand (4 rijen van 7). */
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private String categoryId;
    private int page;

    public ChallengesMenu(SkyBlockPlugin plugin, Player viewer) {
        this(plugin, viewer, null);
    }

    public ChallengesMenu(SkyBlockPlugin plugin, Player viewer, String categoryId) {
        super(plugin, viewer, plugin.getMessages().get("menus.challenges.title"), 6);
        this.categoryId = categoryId;
    }

    @Override
    protected void build() {
        Messages messages = plugin.getMessages();
        ChallengeManager manager = plugin.getChallengeManager();
        Island island = plugin.getIslandManager().getIslandOf(viewer);
        List<ChallengeCategory> categories = manager.getCategories();

        if (categories.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(messages.get("menus.challenges.none-name"))
                    .lore(messages.getList("menus.challenges.none-lore"))
                    .build());
            addBottomRow(messages, 1);
            fillBorder();
            fillEmpty();
            return;
        }

        // Standaard de eerste categorie kiezen
        if (categoryId == null || manager.getCategory(categoryId) == null) {
            categoryId = categories.get(0).getId();
        }

        // Tabbladen
        for (int i = 0; i < categories.size() && i < TAB_SLOTS.length; i++) {
            ChallengeCategory category = categories.get(i);
            boolean selected = category.getId().equals(categoryId);
            boolean unlocked = manager.isUnlocked(category, island);
            int total = 0;
            int done = 0;
            for (Challenge challenge : manager.getChallenges(category.getId())) {
                total++;
                if (manager.getCompletions(viewer, challenge) > 0) {
                    done++;
                }
            }
            List<String> lore = new ArrayList<>(category.getDescription());
            lore.addAll(messages.getList(unlocked ? "menus.challenges.tab-lore" : "menus.challenges.tab-lore-locked",
                    "{done}", done, "{total}", total, "{level}", category.getRequiredLevel()));
            if (selected) {
                lore.addAll(messages.getList("menus.challenges.tab-selected"));
            }
            setItem(TAB_SLOTS[i], new ItemBuilder(unlocked ? category.getIcon() : Material.IRON_BARS)
                    .name(messages.get(unlocked ? "menus.challenges.tab-name" : "menus.challenges.tab-name-locked",
                            "{category}", category.getName()))
                    .lore(lore)
                    .glow(selected)
                    .hideAttributes()
                    .build(), event -> {
                        categoryId = category.getId();
                        page = 0;
                        refresh();
                    });
        }

        // Challenges van de gekozen categorie
        List<Challenge> list = manager.getChallenges(categoryId);
        int pages = Math.max(1, (list.size() + SLOTS.length - 1) / SLOTS.length);
        if (page >= pages) {
            page = pages - 1;
        }
        int start = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && start + i < list.size(); i++) {
            Challenge challenge = list.get(start + i);
            setItem(SLOTS[i], challengeItem(challenge), event -> {
                manager.complete(viewer, challenge.getId());
                refresh();
            });
        }

        addBottomRow(messages, pages);
        fillBorder();
        fillEmpty();
    }

    private void addBottomRow(Messages messages, int pages) {
        setItem(49, MainMenu.backButton(plugin),
                event -> MainMenu.openLater(plugin, viewer, () -> new MainMenu(plugin, viewer)));
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
    }

    /** Bouwt het icoon van een challenge met beschrijving, vereisten, beloning en status. */
    private org.bukkit.inventory.ItemStack challengeItem(Challenge challenge) {
        Messages messages = plugin.getMessages();
        ChallengeManager manager = plugin.getChallengeManager();
        ChallengeManager.Status status = manager.getStatus(viewer, challenge);
        int completions = manager.getCompletions(viewer, challenge);

        List<String> lore = new ArrayList<>();
        for (String line : challenge.getDescription()) {
            lore.add(messages.get("menus.challenges.description-line", "{line}", line));
        }
        lore.add("");

        // Vereisten
        lore.add(messages.get("menus.challenges.requirements-header"));
        switch (challenge.getType()) {
            case INVENTORY -> {
                for (Map.Entry<Material, Integer> entry : challenge.getRequiredItems().entrySet()) {
                    lore.add(messages.get("menus.challenges.requirement-item",
                            "{amount}", entry.getValue(), "{item}", ShopItem.formatMaterial(entry.getKey())));
                }
                lore.add(messages.get(challenge.isTakeItems()
                        ? "menus.challenges.items-taken" : "menus.challenges.items-kept"));
            }
            case ISLAND_LEVEL -> lore.add(messages.get("menus.challenges.requirement-level",
                    "{level}", challenge.getRequiredLevel()));
            case ISLAND_BLOCKS -> {
                for (Map.Entry<Material, Integer> entry : challenge.getRequiredItems().entrySet()) {
                    lore.add(messages.get("menus.challenges.requirement-block",
                            "{amount}", entry.getValue(), "{item}", ShopItem.formatMaterial(entry.getKey())));
                }
                lore.add(messages.get("menus.challenges.blocks-radius", "{radius}", challenge.getRadius()));
            }
        }
        lore.add("");

        // Beloning
        Challenge.Reward reward = challenge.getRewardFor(completions);
        lore.add(messages.get(completions > 0 && challenge.isRepeatable()
                ? "menus.challenges.repeat-reward-header" : "menus.challenges.reward-header"));
        lore.addAll(manager.rewardLines(reward));
        if (challenge.isRepeatable() && completions <= 0) {
            lore.add(messages.get("menus.challenges.repeatable-note"));
        }
        lore.add("");

        // Status
        String max = challenge.getMaxCompletions() > 0 ? String.valueOf(challenge.getMaxCompletions()) : "∞";
        switch (status) {
            case LOCKED -> {
                ChallengeCategory category = manager.getCategory(challenge.getCategoryId());
                lore.addAll(messages.getList("menus.challenges.status-locked",
                        "{level}", category == null ? 0 : category.getRequiredLevel()));
            }
            case AVAILABLE -> lore.addAll(messages.getList("menus.challenges.status-available"));
            case REPEATABLE -> lore.addAll(messages.getList("menus.challenges.status-repeatable",
                    "{count}", completions, "{max}", max));
            case COMPLETED -> lore.addAll(messages.getList("menus.challenges.status-completed",
                    "{count}", completions));
        }

        Material icon = switch (status) {
            case LOCKED -> Material.GRAY_DYE;
            case COMPLETED -> Material.LIME_DYE;
            default -> challenge.getIcon();
        };
        String nameKey = switch (status) {
            case LOCKED -> "menus.challenges.name-locked";
            case COMPLETED -> "menus.challenges.name-completed";
            default -> "menus.challenges.name-available";
        };
        return new ItemBuilder(icon)
                .name(messages.get(nameKey, "{challenge}", challenge.getName()))
                .lore(lore)
                .glow(status == ChallengeManager.Status.REPEATABLE)
                .hideAttributes()
                .build();
    }
}
