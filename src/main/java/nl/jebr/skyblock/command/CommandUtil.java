package nl.jebr.skyblock.command;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandRole;
import nl.jebr.skyblock.upgrade.UpgradeManager;
import nl.jebr.skyblock.upgrade.UpgradeType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gedeelde hulpmethoden voor de commando's: tab-completion, bedragen
 * inlezen en eilandinformatie tonen.
 */
final class CommandUtil {

    private CommandUtil() {
    }

    /** Filtert de opties op wat de speler al getypt heeft en sorteert ze. */
    static List<String> filter(String token, Collection<String> options) {
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(token, options, result);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    /** Namen van alle online spelers. */
    static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    /**
     * Leest een bedrag in. Geeft null terug als het geen geldig, eindig,
     * niet-negatief getal met hoogstens twee decimalen is.
     */
    static Double parseAmount(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(input.replace(',', '.'));
            if (value.signum() < 0 || value.stripTrailingZeros().scale() > 2) {
                return null;
            }
            double amount = value.doubleValue();
            if (Double.isInfinite(amount) || Double.isNaN(amount)) {
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Leest een geheel getal in, of null als dat niet lukt. */
    static Integer parseInt(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Wachttijd in seconden als leesbare tekst. */
    static String formatDuration(Messages messages, long seconds) {
        long minutes = seconds / 60;
        long rest = seconds % 60;
        return messages.get("commands.time-format", "{minutes}", minutes, "{seconds}", rest);
    }

    /** De weergavenaam van een eiland (met kleuren); valt terug op de naam van de eigenaar. */
    static String islandName(SkyBlockPlugin plugin, Island island) {
        String name = island.getName();
        if (name == null || name.isEmpty()) {
            name = plugin.getPlayerDataManager().getName(island.getOwner());
        }
        return Messages.color(name);
    }

    /** Stuurt een berichtsleutel naar alle online leden van het eiland. */
    static void notifyMembers(SkyBlockPlugin plugin, Island island, UUID except, String key, Object... placeholders) {
        for (UUID member : island.getMembers().keySet()) {
            if (member.equals(except)) {
                continue;
            }
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                plugin.getMessages().send(online, key, placeholders);
            }
        }
    }

    /** Toont alle informatie over een eiland. */
    static void sendIslandInfo(SkyBlockPlugin plugin, CommandSender sender, Island island) {
        Messages messages = plugin.getMessages();
        UpgradeManager upgrades = plugin.getUpgradeManager();

        List<String> memberParts = new ArrayList<>();
        for (Map.Entry<UUID, IslandRole> entry : island.getMembers().entrySet()) {
            memberParts.add(messages.get("commands.info.member-entry",
                    "{player}", plugin.getPlayerDataManager().getName(entry.getKey()),
                    "{role}", entry.getValue().getDisplayName()));
        }
        String separator = messages.get("commands.info.member-separator");
        String yes = messages.get("commands.info.yes");
        String no = messages.get("commands.info.no");

        String warp;
        if (island.getWarp() == null) {
            warp = messages.get("commands.info.warp-none");
        } else {
            warp = island.isWarpEnabled() ? messages.get("commands.info.warp-on") : messages.get("commands.info.warp-off");
        }

        messages.sendRaw(sender, "commands.info.lines",
                "{name}", islandName(plugin, island),
                "{owner}", plugin.getPlayerDataManager().getName(island.getOwner()),
                "{member_count}", island.getMemberCount(),
                "{member_limit}", upgrades.getMemberLimit(island),
                "{members}", String.join(separator, memberParts),
                "{level}", island.getLevel(),
                "{points}", island.getLevelPoints(),
                "{radius}", upgrades.getRadius(island),
                "{size}", island.getSizeLevel(),
                "{size_max}", upgrades.getMaxLevel(UpgradeType.SIZE),
                "{members_level}", island.getMemberLevel(),
                "{members_max}", upgrades.getMaxLevel(UpgradeType.MEMBERS),
                "{generator}", island.getGeneratorLevel(),
                "{generator_max}", upgrades.getMaxLevel(UpgradeType.GENERATOR),
                "{locked}", island.isLocked() ? yes : no,
                "{warp}", warp);
    }
}
