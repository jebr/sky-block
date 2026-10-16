package nl.jebr.skyblock.command;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.gui.ConfirmMenu;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandManager;
import nl.jebr.skyblock.player.PlayerData;
import nl.jebr.skyblock.upgrade.UpgradeType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Het beheercommando /isadmin (permissie skyblock.admin).
 */
public class AdminCommand implements TabExecutor {

    private static final String PERMISSION = "skyblock.admin";

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "reload", "delete", "tp", "info", "setlevel", "addmember", "removemember",
            "setowner", "money", "resetchallenges", "resetcount", "recalc", "save");

    /** Subcommando's waarvan het eerste argument een spelernaam is. */
    private static final List<String> PLAYER_ARG = Arrays.asList(
            "delete", "tp", "info", "setlevel", "addmember", "removemember", "setowner",
            "resetchallenges", "resetcount", "recalc");

    private final SkyBlockPlugin plugin;

    public AdminCommand(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.getMessages();
    }

    private IslandManager islands() {
        return plugin.getIslandManager();
    }

    // ================================================================== uitvoeren

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            messages().sendRaw(sender, "commands.help.admin", "{label}", label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> messages().sendRaw(sender, "commands.help.admin", "{label}", label);
            case "reload" -> {
                plugin.reloadAll();
                messages().send(sender, "general.reloaded");
            }
            case "save" -> {
                plugin.saveAll();
                messages().send(sender, "commands.admin.saved");
            }
            case "delete" -> handleDelete(sender, args, label);
            case "tp" -> handleTeleport(sender, args, label);
            case "info" -> handleInfo(sender, args, label);
            case "setlevel" -> handleSetLevel(sender, args, label);
            case "addmember" -> handleAddMember(sender, args, label);
            case "removemember" -> handleRemoveMember(sender, args, label);
            case "setowner" -> handleSetOwner(sender, args, label);
            case "money" -> handleMoney(sender, args, label);
            case "resetchallenges" -> handleResetChallenges(sender, args, label);
            case "resetcount" -> handleResetCount(sender, args, label);
            case "recalc" -> handleRecalc(sender, args, label);
            default -> messages().send(sender, "commands.unknown-subcommand", "{label}", label);
        }
        return true;
    }

    private void handleDelete(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 2, "commands.admin.usage.delete", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        Island island = target == null ? null : requireIslandOf(sender, target);
        if (island == null) {
            return;
        }
        String name = plugin.getPlayerDataManager().getName(target);
        Runnable delete = () -> {
            if (islands().getIsland(island.getId()) == null) {
                messages().send(sender, "commands.admin.delete.already-gone");
                return;
            }
            islands().deleteIsland(island);
            messages().send(sender, "commands.admin.delete.success", "{player}", name);
        };
        if (sender instanceof Player player) {
            new ConfirmMenu(plugin, player, messages().get("commands.admin.delete.confirm-title", "{player}", name), delete).open();
        } else {
            delete.run();
        }
    }

    private void handleTeleport(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "general.player-only");
            return;
        }
        if (!requireArgs(sender, args, 2, "commands.admin.usage.tp", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        Island island = target == null ? null : requireIslandOf(sender, target);
        if (island == null) {
            return;
        }
        messages().send(player, "general.teleporting");
        islands().teleportHome(player, island);
    }

    private void handleInfo(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 2, "commands.admin.usage.info", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        if (target == null) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target);
        messages().sendRaw(sender, "commands.admin.player-info",
                "{player}", plugin.getPlayerDataManager().getName(target),
                "{uuid}", target,
                "{balance}", plugin.getEconomy().format(data.getBalance()),
                "{resets}", data.getResetCount(),
                "{challenges}", data.getChallengeCompletions().size());
        Island island = islands().getIslandOf(target);
        if (island == null) {
            messages().send(sender, "commands.no-island-other", "{player}", plugin.getPlayerDataManager().getName(target));
            return;
        }
        CommandUtil.sendIslandInfo(plugin, sender, island);
        messages().sendRaw(sender, "commands.admin.island-extra",
                "{id}", island.getId(),
                "{x}", island.getCenterX(),
                "{z}", island.getCenterZ(),
                "{banned}", island.getBanned().size());
    }

    private void handleSetLevel(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 4, "commands.admin.usage.setlevel", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        Island island = target == null ? null : requireIslandOf(sender, target);
        if (island == null) {
            return;
        }
        UpgradeType type = parseUpgrade(args[2]);
        if (type == null) {
            messages().send(sender, "commands.admin.setlevel.unknown-upgrade", "{input}", args[2]);
            return;
        }
        Integer requested = CommandUtil.parseInt(args[3]);
        if (requested == null) {
            messages().send(sender, "general.invalid-number", "{input}", args[3]);
            return;
        }
        int max = plugin.getUpgradeManager().getMaxLevel(type);
        int level = Math.max(1, Math.min(max, requested));
        switch (type) {
            case SIZE -> island.setSizeLevel(level);
            case MEMBERS -> island.setMemberLevel(level);
            case GENERATOR -> island.setGeneratorLevel(level);
        }
        islands().refreshBorders(island);
        messages().send(sender, "commands.admin.setlevel.success",
                "{upgrade}", type.getDisplayName(),
                "{player}", plugin.getPlayerDataManager().getName(target),
                "{level}", level, "{max}", max);
    }

    private void handleAddMember(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 3, "commands.admin.usage.addmember", label)) {
            return;
        }
        UUID owner = resolve(sender, args[1]);
        Island island = owner == null ? null : requireIslandOf(sender, owner);
        if (island == null) {
            return;
        }
        UUID target = resolve(sender, args[2]);
        if (target == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        if (island.isMember(target)) {
            messages().send(sender, "commands.admin.addmember.already", "{player}", targetName);
            return;
        }
        if (islands().getIslandOf(target) != null) {
            messages().send(sender, "commands.admin.addmember.has-island", "{player}", targetName);
            return;
        }
        // Beheerders mogen de ledenlimiet overschrijden; wel melden
        if (islands().isFull(island)) {
            messages().send(sender, "commands.admin.addmember.over-limit");
        }
        islands().addMember(island, target);
        messages().send(sender, "commands.admin.addmember.success",
                "{player}", targetName, "{owner}", plugin.getPlayerDataManager().getName(island.getOwner()));
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            messages().send(online, "commands.admin.addmember.notify", "{island}", CommandUtil.islandName(plugin, island));
        }
    }

    private void handleRemoveMember(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 2, "commands.admin.usage.removemember", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        Island island = target == null ? null : requireIslandOf(sender, target);
        if (island == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        if (island.getOwner().equals(target)) {
            messages().send(sender, "commands.admin.removemember.owner", "{player}", targetName);
            return;
        }
        islands().removeMember(island, target);
        messages().send(sender, "commands.admin.removemember.success", "{player}", targetName);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            messages().send(online, "commands.admin.removemember.notify");
        }
    }

    private void handleSetOwner(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 2, "commands.admin.usage.setowner", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        Island island = target == null ? null : requireIslandOf(sender, target);
        if (island == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        if (island.getOwner().equals(target)) {
            messages().send(sender, "commands.admin.setowner.already", "{player}", targetName);
            return;
        }
        island.setOwner(target);
        messages().send(sender, "commands.admin.setowner.success", "{player}", targetName);
        CommandUtil.notifyMembers(plugin, island, null, "commands.transfer.notify",
                "{player}", targetName, "{old}", sender.getName());
    }

    private void handleMoney(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 4, "commands.admin.usage.money", label)) {
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!action.equals("give") && !action.equals("take") && !action.equals("set")) {
            messages().send(sender, "commands.admin.usage.money", "{label}", label);
            return;
        }
        UUID target = resolve(sender, args[2]);
        if (target == null) {
            return;
        }
        Double amount = CommandUtil.parseAmount(args[3]);
        if (amount == null) {
            messages().send(sender, "general.invalid-number", "{input}", args[3]);
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        switch (action) {
            case "give" -> plugin.getEconomy().deposit(target, amount);
            case "take" -> {
                if (!plugin.getEconomy().withdraw(target, amount)) {
                    // Niet genoeg: tot nul afhalen
                    plugin.getEconomy().set(target, 0);
                }
            }
            default -> plugin.getEconomy().set(target, amount);
        }
        messages().send(sender, "commands.admin.money.success",
                "{player}", targetName,
                "{balance}", plugin.getEconomy().format(plugin.getEconomy().getBalance(target)));
    }

    private void handleResetChallenges(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 2, "commands.admin.usage.resetchallenges", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        if (target == null) {
            return;
        }
        plugin.getPlayerDataManager().get(target).resetChallenges();
        messages().send(sender, "commands.admin.resetchallenges.success",
                "{player}", plugin.getPlayerDataManager().getName(target));
    }

    private void handleResetCount(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 2, "commands.admin.usage.resetcount", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        if (target == null) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target);
        data.setResetCount(0);
        data.setLastReset(0L);
        messages().send(sender, "commands.admin.resetcount.success",
                "{player}", plugin.getPlayerDataManager().getName(target));
    }

    private void handleRecalc(CommandSender sender, String[] args, String label) {
        if (!requireArgs(sender, args, 2, "commands.admin.usage.recalc", label)) {
            return;
        }
        UUID target = resolve(sender, args[1]);
        Island island = target == null ? null : requireIslandOf(sender, target);
        if (island == null) {
            return;
        }
        if (plugin.getLevelManager().isCalculating(island)) {
            messages().send(sender, "commands.level.busy");
            return;
        }
        String name = plugin.getPlayerDataManager().getName(target);
        messages().send(sender, "commands.level.calculating");
        plugin.getLevelManager().calculate(island, level -> {
            if (!(sender instanceof Player player) || player.isOnline()) {
                messages().send(sender, "commands.admin.recalc.result",
                        "{player}", name, "{level}", level, "{points}", island.getLevelPoints());
            }
        });
    }

    // ================================================================== hulpmethoden

    private UpgradeType parseUpgrade(String input) {
        for (UpgradeType type : UpgradeType.values()) {
            if (type.getKey().equalsIgnoreCase(input) || type.name().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }

    private boolean requireArgs(CommandSender sender, String[] args, int needed, String usageKey, String label) {
        if (args.length >= needed) {
            return true;
        }
        messages().send(sender, usageKey, "{label}", label);
        return false;
    }

    private UUID resolve(CommandSender sender, String name) {
        UUID uuid = plugin.getPlayerDataManager().findUuid(name);
        if (uuid == null) {
            messages().send(sender, "general.player-not-found", "{player}", name);
        }
        return uuid;
    }

    private Island requireIslandOf(CommandSender sender, UUID player) {
        Island island = islands().getIslandOf(player);
        if (island == null) {
            messages().send(sender, "commands.no-island-other", "{player}", plugin.getPlayerDataManager().getName(player));
        }
        return island;
    }

    // ================================================================== tab-completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return CommandUtil.filter(args[0], SUBCOMMANDS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("money")) {
                return CommandUtil.filter(args[1], Arrays.asList("give", "take", "set"));
            }
            if (PLAYER_ARG.contains(sub)) {
                return CommandUtil.filter(args[1], CommandUtil.onlineNames());
            }
            return Collections.emptyList();
        }
        if (args.length == 3) {
            switch (sub) {
                case "setlevel" -> {
                    List<String> keys = new ArrayList<>();
                    for (UpgradeType type : UpgradeType.values()) {
                        keys.add(type.getKey());
                    }
                    return CommandUtil.filter(args[2], keys);
                }
                case "addmember", "money" -> {
                    return CommandUtil.filter(args[2], CommandUtil.onlineNames());
                }
                default -> {
                    return Collections.emptyList();
                }
            }
        }
        if (args.length == 4 && sub.equals("setlevel")) {
            UpgradeType type = parseUpgrade(args[2]);
            if (type == null) {
                return Collections.emptyList();
            }
            List<String> levels = new ArrayList<>();
            for (int i = 1; i <= plugin.getUpgradeManager().getMaxLevel(type); i++) {
                levels.add(String.valueOf(i));
            }
            return CommandUtil.filter(args[3], levels);
        }
        return Collections.emptyList();
    }
}
