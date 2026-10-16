package nl.jebr.skyblock.command;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.challenge.Challenge;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.gui.ChallengesMenu;
import nl.jebr.skyblock.gui.ConfirmMenu;
import nl.jebr.skyblock.gui.MainMenu;
import nl.jebr.skyblock.gui.MembersMenu;
import nl.jebr.skyblock.gui.SettingsMenu;
import nl.jebr.skyblock.gui.ShopMenu;
import nl.jebr.skyblock.gui.TopMenu;
import nl.jebr.skyblock.gui.UpgradesMenu;
import nl.jebr.skyblock.gui.WarpsMenu;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandManager;
import nl.jebr.skyblock.island.IslandRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 * Het spelercommando /island (aliassen is, skyblock, sb). Zonder argumenten
 * opent het het hoofdmenu; daarnaast zijn er subcommando's voor alles rond
 * het eiland.
 */
public class IslandCommand implements TabExecutor {

    /** Maximale zichtbare lengte van een eilandnaam. */
    private static final int MAX_NAME_LENGTH = 32;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "create", "home", "go", "sethome", "reset", "info", "level", "top",
            "invite", "accept", "deny", "kick", "leave", "transfer", "promote", "demote",
            "members", "settings", "upgrades", "lock", "unlock", "ban", "unban", "visit",
            "setwarp", "delwarp", "warp", "warps", "challenges", "challenge", "shop", "sell",
            "name", "chat");

    private final SkyBlockPlugin plugin;

    public IslandCommand(SkyBlockPlugin plugin) {
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
        if (args.length == 0) {
            Player player = requirePlayer(sender);
            if (player != null) {
                new MainMenu(plugin, player).open();
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> messages().sendRaw(sender, "commands.help.island", "{label}", label);
            case "info" -> handleInfo(sender, args);
            default -> {
                if (!SUBCOMMANDS.contains(sub)) {
                    messages().send(sender, "commands.unknown-subcommand", "{label}", label);
                    return true;
                }
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                handlePlayerCommand(player, sub, args, label);
            }
        }
        return true;
    }

    private void handlePlayerCommand(Player player, String sub, String[] args, String label) {
        switch (sub) {
            case "create" -> handleCreate(player);
            case "home", "go" -> handleHome(player);
            case "sethome" -> handleSetHome(player);
            case "reset" -> handleReset(player);
            case "level" -> handleLevel(player);
            case "top" -> new TopMenu(plugin, player).open();
            case "invite" -> handleInvite(player, args, label);
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "kick" -> handleKick(player, args, label);
            case "leave" -> handleLeave(player);
            case "transfer" -> handleTransfer(player, args, label);
            case "promote" -> handlePromote(player, args, label, true);
            case "demote" -> handlePromote(player, args, label, false);
            case "members" -> {
                Island island = requireIsland(player);
                if (island != null) {
                    new MembersMenu(plugin, player, island).open();
                }
            }
            case "settings" -> {
                Island island = requireIsland(player);
                if (island != null && requireRole(player, island, IslandRole.CO_OWNER)) {
                    new SettingsMenu(plugin, player, island).open();
                }
            }
            case "upgrades" -> {
                Island island = requireIsland(player);
                if (island != null) {
                    new UpgradesMenu(plugin, player, island).open();
                }
            }
            case "lock" -> handleLock(player, true);
            case "unlock" -> handleLock(player, false);
            case "ban" -> handleBan(player, args, label);
            case "unban" -> handleUnban(player, args, label);
            case "visit" -> handleVisit(player, args, label, false);
            case "warp" -> handleVisit(player, args, label, true);
            case "setwarp" -> handleSetWarp(player);
            case "delwarp" -> handleDelWarp(player);
            case "warps" -> new WarpsMenu(plugin, player).open();
            case "challenges" -> new ChallengesMenu(plugin, player).open();
            case "challenge" -> handleChallenge(player, args, label);
            case "shop" -> new ShopMenu(plugin, player).open();
            case "sell" -> handleSell(player, args, label);
            case "name" -> handleName(player, args, label);
            case "chat" -> handleChat(player, args, label);
            default -> messages().send(player, "commands.unknown-subcommand", "{label}", label);
        }
    }

    // ================================================================== eiland zelf

    private void handleCreate(Player player) {
        if (islands().getIslandOf(player) != null) {
            messages().send(player, "general.already-has-island");
            return;
        }
        messages().send(player, "commands.create.creating");
        islands().createIsland(player);
        messages().send(player, "commands.create.success");
    }

    private void handleHome(Player player) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        messages().send(player, "general.teleporting");
        islands().teleportHome(player, island);
    }

    private void handleSetHome(Player player) {
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER) || !requireOnOwnIsland(player, island)) {
            return;
        }
        island.setHome(player.getLocation());
        messages().send(player, "commands.sethome.success");
    }

    private void handleReset(Player player) {
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.OWNER)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean bypass = player.hasPermission("skyblock.bypass");
        long remaining = islands().getResetCooldownRemaining(uuid);
        if (remaining > 0 && !bypass) {
            messages().send(player, "commands.reset.cooldown",
                    "{time}", CommandUtil.formatDuration(messages(), remaining));
            return;
        }
        int maxResets = plugin.getConfig().getInt("island.max-resets", -1);
        int used = plugin.getPlayerDataManager().get(player).getResetCount();
        if (maxResets >= 0 && used >= maxResets && !bypass) {
            messages().send(player, "commands.reset.max-resets", "{max}", maxResets);
            return;
        }
        if (maxResets >= 0) {
            messages().send(player, "commands.reset.warning-limited", "{left}", Math.max(0, maxResets - used));
        } else {
            messages().send(player, "commands.reset.warning");
        }

        new ConfirmMenu(plugin, player, messages().get("commands.reset.confirm-title"), () -> {
            // Leden vooraf onthouden: na de reset zijn ze geen lid meer
            Island current = islands().getIslandOf(player);
            List<UUID> members = new ArrayList<>();
            if (current != null) {
                members.addAll(current.getMembers().keySet());
            }
            IslandManager.ResetResult result = islands().resetIsland(player);
            switch (result) {
                case SUCCESS -> {
                    messages().send(player, "commands.reset.success");
                    for (UUID member : members) {
                        Player online = Bukkit.getPlayer(member);
                        if (online != null && !member.equals(uuid)) {
                            messages().send(online, "commands.reset.member-notify", "{player}", player.getName());
                        }
                    }
                }
                case NO_ISLAND -> messages().send(player, "general.no-island");
                case NOT_OWNER -> messages().send(player, "general.role-too-low",
                        "{role}", IslandRole.OWNER.getDisplayName());
                case COOLDOWN -> messages().send(player, "commands.reset.cooldown",
                        "{time}", CommandUtil.formatDuration(messages(), islands().getResetCooldownRemaining(uuid)));
                case MAX_RESETS -> messages().send(player, "commands.reset.max-resets", "{max}", maxResets);
            }
        }).open();
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Island island;
        if (args.length >= 2) {
            UUID target = resolve(sender, args[1]);
            if (target == null) {
                return;
            }
            island = islands().getIslandOf(target);
            if (island == null) {
                messages().send(sender, "commands.no-island-other",
                        "{player}", plugin.getPlayerDataManager().getName(target));
                return;
            }
        } else {
            Player player = requirePlayer(sender);
            if (player == null) {
                return;
            }
            island = requireIsland(player);
            if (island == null) {
                return;
            }
        }
        CommandUtil.sendIslandInfo(plugin, sender, island);
    }

    private void handleLevel(Player player) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        if (plugin.getLevelManager().isCalculating(island)) {
            messages().send(player, "commands.level.busy");
            return;
        }
        messages().send(player, "commands.level.calculating");
        plugin.getLevelManager().calculate(island, level -> {
            if (player.isOnline()) {
                messages().send(player, "commands.level.result",
                        "{level}", level, "{points}", island.getLevelPoints());
            }
        });
    }

    // ================================================================== leden

    private void handleInvite(Player player, String[] args, String label) {
        if (!requireArgs(player, args, 2, "commands.usage.invite", label)) {
            return;
        }
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER)) {
            return;
        }
        UUID targetId = resolve(player, args[1]);
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            messages().send(player, "commands.player-offline", "{player}", args[1]);
            return;
        }
        if (target.equals(player)) {
            messages().send(player, "commands.invite.self");
            return;
        }
        if (island.isMember(targetId)) {
            messages().send(player, "commands.invite.already-member", "{player}", target.getName());
            return;
        }
        if (islands().getIslandOf(targetId) != null) {
            messages().send(player, "commands.invite.has-island", "{player}", target.getName());
            return;
        }
        if (islands().isFull(island)) {
            messages().send(player, "commands.island-full",
                    "{limit}", plugin.getUpgradeManager().getMemberLimit(island));
            return;
        }
        islands().invite(island, player.getUniqueId(), targetId);
        long timeout = plugin.getConfig().getLong("island.invite-timeout", 120);
        messages().send(player, "commands.invite.sent", "{player}", target.getName(), "{seconds}", timeout);
        messages().send(target, "commands.invite.received",
                "{player}", player.getName(), "{island}", CommandUtil.islandName(plugin, island), "{seconds}", timeout);
    }

    private void handleAccept(Player player) {
        UUID uuid = player.getUniqueId();
        IslandManager.Invite invite = islands().getInvite(uuid);
        if (invite == null) {
            messages().send(player, "commands.invite.none");
            return;
        }
        if (islands().getIslandOf(player) != null) {
            messages().send(player, "commands.invite.leave-first");
            return;
        }
        Island island = islands().getIsland(invite.islandId());
        if (island == null) {
            islands().removeInvite(uuid);
            messages().send(player, "commands.invite.none");
            return;
        }
        if (islands().isFull(island)) {
            islands().removeInvite(uuid);
            messages().send(player, "commands.island-full",
                    "{limit}", plugin.getUpgradeManager().getMemberLimit(island));
            return;
        }
        islands().cleanPlayer(player);
        islands().addMember(island, uuid);
        CommandUtil.notifyMembers(plugin, island, uuid, "commands.invite.joined-notify", "{player}", player.getName());
        messages().send(player, "commands.invite.accepted", "{island}", CommandUtil.islandName(plugin, island));
        islands().teleportHome(player, island);
    }

    private void handleDeny(Player player) {
        UUID uuid = player.getUniqueId();
        IslandManager.Invite invite = islands().getInvite(uuid);
        if (invite == null) {
            messages().send(player, "commands.invite.none");
            return;
        }
        islands().removeInvite(uuid);
        messages().send(player, "commands.invite.denied");
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) {
            messages().send(inviter, "commands.invite.denied-notify", "{player}", player.getName());
        }
    }

    private void handleKick(Player player, String[] args, String label) {
        if (!requireArgs(player, args, 2, "commands.usage.kick", label)) {
            return;
        }
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER)) {
            return;
        }
        UUID target = resolve(player, args[1]);
        if (target == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        if (target.equals(player.getUniqueId())) {
            messages().send(player, "commands.kick.self");
            return;
        }
        if (!island.isMember(target)) {
            messages().send(player, "commands.not-member", "{player}", targetName);
            return;
        }
        if (island.getRole(target).isAtLeast(island.getRole(player.getUniqueId()))) {
            messages().send(player, "commands.kick.higher-role", "{player}", targetName);
            return;
        }
        islands().removeMember(island, target);
        messages().send(player, "commands.kick.success", "{player}", targetName);
        CommandUtil.notifyMembers(plugin, island, player.getUniqueId(), "commands.kick.notify",
                "{player}", targetName, "{kicker}", player.getName());
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            messages().send(online, "commands.kick.kicked", "{player}", player.getName());
        }
    }

    private void handleLeave(Player player) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        if (island.getOwner().equals(player.getUniqueId())) {
            messages().send(player, "commands.leave.owner");
            return;
        }
        islands().removeMember(island, player.getUniqueId());
        messages().send(player, "commands.leave.success");
        CommandUtil.notifyMembers(plugin, island, player.getUniqueId(), "commands.leave.notify",
                "{player}", player.getName());
    }

    private void handleTransfer(Player player, String[] args, String label) {
        if (!requireArgs(player, args, 2, "commands.usage.transfer", label)) {
            return;
        }
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.OWNER)) {
            return;
        }
        UUID target = resolve(player, args[1]);
        if (target == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        if (target.equals(player.getUniqueId())) {
            messages().send(player, "commands.transfer.self");
            return;
        }
        if (!island.isMember(target)) {
            messages().send(player, "commands.not-member", "{player}", targetName);
            return;
        }
        String title = messages().get("commands.transfer.confirm-title", "{player}", targetName);
        new ConfirmMenu(plugin, player, title, () -> {
            // Opnieuw controleren: er kan intussen iets veranderd zijn
            if (islands().getIsland(island.getId()) == null
                    || !island.getOwner().equals(player.getUniqueId())
                    || !island.isMember(target)) {
                messages().send(player, "commands.transfer.failed");
                return;
            }
            island.setOwner(target);
            messages().send(player, "commands.transfer.success", "{player}", targetName);
            CommandUtil.notifyMembers(plugin, island, player.getUniqueId(), "commands.transfer.notify",
                    "{player}", targetName, "{old}", player.getName());
        }).open();
    }

    private void handlePromote(Player player, String[] args, String label, boolean promote) {
        if (!requireArgs(player, args, 2, promote ? "commands.usage.promote" : "commands.usage.demote", label)) {
            return;
        }
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.OWNER)) {
            return;
        }
        UUID target = resolve(player, args[1]);
        if (target == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        if (!island.isMember(target)) {
            messages().send(player, "commands.not-member", "{player}", targetName);
            return;
        }
        IslandRole role = island.getRole(target);
        if (role == IslandRole.OWNER) {
            messages().send(player, "commands.promote.owner");
            return;
        }
        IslandRole newRole;
        if (promote) {
            if (role != IslandRole.MEMBER) {
                messages().send(player, "commands.promote.already-highest", "{player}", targetName);
                return;
            }
            newRole = IslandRole.CO_OWNER;
        } else {
            if (role != IslandRole.CO_OWNER) {
                messages().send(player, "commands.promote.already-lowest", "{player}", targetName);
                return;
            }
            newRole = IslandRole.MEMBER;
        }
        island.setRole(target, newRole);
        messages().send(player, "commands.promote.success",
                "{player}", targetName, "{role}", newRole.getDisplayName());
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            messages().send(online, "commands.promote.notify", "{role}", newRole.getDisplayName());
        }
    }

    // ================================================================== toegang

    private void handleLock(Player player, boolean lock) {
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER)) {
            return;
        }
        if (island.isLocked() == lock) {
            messages().send(player, lock ? "commands.lock.already-locked" : "commands.lock.already-unlocked");
            return;
        }
        island.setLocked(lock);
        if (!lock) {
            messages().send(player, "commands.lock.unlocked");
            return;
        }
        messages().send(player, "commands.lock.locked");
        int radius = islands().getRadius(island);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (island.isMember(online.getUniqueId()) || online.hasPermission("skyblock.bypass")) {
                continue;
            }
            if (island.contains(online.getLocation(), radius)) {
                islands().teleportToSpawn(online);
                messages().send(online, "commands.lock.removed-visitor");
            }
        }
    }

    private void handleBan(Player player, String[] args, String label) {
        if (!requireArgs(player, args, 2, "commands.usage.ban", label)) {
            return;
        }
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER)) {
            return;
        }
        UUID target = resolve(player, args[1]);
        if (target == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        if (target.equals(player.getUniqueId())) {
            messages().send(player, "commands.ban.self");
            return;
        }
        if (island.isMember(target)) {
            messages().send(player, "commands.ban.member", "{player}", targetName);
            return;
        }
        if (island.isBanned(target)) {
            messages().send(player, "commands.ban.already", "{player}", targetName);
            return;
        }
        island.ban(target);
        // Een openstaande uitnodiging voor dit eiland vervalt
        IslandManager.Invite invite = islands().getInvite(target);
        if (invite != null && invite.islandId().equals(island.getId())) {
            islands().removeInvite(target);
        }
        messages().send(player, "commands.ban.success", "{player}", targetName);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            messages().send(online, "commands.ban.banned", "{island}", CommandUtil.islandName(plugin, island));
            if (island.contains(online.getLocation(), islands().getRadius(island))
                    && !online.hasPermission("skyblock.bypass")) {
                islands().teleportToSpawn(online);
            }
        }
    }

    private void handleUnban(Player player, String[] args, String label) {
        if (!requireArgs(player, args, 2, "commands.usage.unban", label)) {
            return;
        }
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER)) {
            return;
        }
        UUID target = resolve(player, args[1]);
        if (target == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        if (!island.isBanned(target)) {
            messages().send(player, "commands.ban.not-banned", "{player}", targetName);
            return;
        }
        island.unban(target);
        messages().send(player, "commands.ban.unbanned", "{player}", targetName);
    }

    /** Bezoeken (warp als die aanstaat, anders home) of expliciet naar de warp. */
    private void handleVisit(Player player, String[] args, String label, boolean warpOnly) {
        if (!requireArgs(player, args, 2, warpOnly ? "commands.usage.warp" : "commands.usage.visit", label)) {
            return;
        }
        UUID target = resolve(player, args[1]);
        if (target == null) {
            return;
        }
        String targetName = plugin.getPlayerDataManager().getName(target);
        Island island = islands().getIslandOf(target);
        if (island == null) {
            messages().send(player, "commands.no-island-other", "{player}", targetName);
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean member = island.isMember(uuid);
        boolean bypass = player.hasPermission("skyblock.bypass");
        if (warpOnly && !island.isWarpEnabled()) {
            messages().send(player, "commands.warp.not-available", "{player}", targetName);
            return;
        }
        if (!member && !bypass) {
            if (island.isBanned(uuid)) {
                messages().send(player, "commands.visit.banned", "{player}", targetName);
                return;
            }
            if (island.isLocked()) {
                messages().send(player, "commands.visit.locked", "{player}", targetName);
                return;
            }
        }
        messages().send(player, "commands.visit.teleporting", "{island}", CommandUtil.islandName(plugin, island));
        if (island.isWarpEnabled()) {
            islands().teleportWarp(player, island);
        } else {
            islands().teleportHome(player, island);
        }
    }

    private void handleSetWarp(Player player) {
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER) || !requireOnOwnIsland(player, island)) {
            return;
        }
        island.setWarp(player.getLocation());
        island.setWarpEnabled(true);
        messages().send(player, "commands.warp.set");
    }

    private void handleDelWarp(Player player) {
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER)) {
            return;
        }
        if (island.getWarp() == null) {
            messages().send(player, "commands.warp.none");
            return;
        }
        island.setWarp(null);
        messages().send(player, "commands.warp.deleted");
    }

    // ================================================================== overig

    private void handleChallenge(Player player, String[] args, String label) {
        if (!requireArgs(player, args, 2, "commands.usage.challenge", label)) {
            return;
        }
        // Eerst exact zoeken, daarna in kleine letters (PlayerData slaat ids in kleine letters op)
        String id = args[1];
        if (plugin.getChallengeManager().getChallenge(id) == null) {
            id = args[1].toLowerCase(Locale.ROOT);
        }
        if (plugin.getChallengeManager().getChallenge(id) == null) {
            messages().send(player, "commands.challenge.unknown", "{id}", args[1]);
            return;
        }
        plugin.getChallengeManager().complete(player, id);
    }

    private void handleSell(Player player, String[] args, String label) {
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "hand";
        switch (mode) {
            case "hand" -> plugin.getShopManager().sellHand(player);
            case "all" -> plugin.getShopManager().sellAll(player);
            default -> messages().send(player, "commands.usage.sell", "{label}", label);
        }
    }

    private void handleName(Player player, String[] args, String label) {
        if (!requireArgs(player, args, 2, "commands.usage.name", label)) {
            return;
        }
        Island island = requireIsland(player);
        if (island == null || !requireRole(player, island, IslandRole.CO_OWNER)) {
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        String visible = ChatColor.stripColor(Messages.color(name)).trim();
        if (visible.isEmpty()) {
            messages().send(player, "commands.name.empty");
            return;
        }
        if (visible.length() > MAX_NAME_LENGTH) {
            messages().send(player, "commands.name.too-long", "{max}", MAX_NAME_LENGTH);
            return;
        }
        island.setName(name);
        messages().send(player, "commands.name.success", "{name}", Messages.color(name));
    }

    private void handleChat(Player player, String[] args, String label) {
        if (!requireArgs(player, args, 2, "commands.usage.chat", label)) {
            return;
        }
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        // Eerst het sjabloon kleuren, daarna pas de tekst invoegen: spelers kunnen zo geen kleurcodes gebruiken
        String line = messages().get("commands.chat.format",
                "{player}", player.getName(),
                "{role}", island.getRole(player.getUniqueId()).getDisplayName())
                .replace("{message}", text);
        for (UUID member : island.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    // ================================================================== hulpmethoden

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages().send(sender, "general.player-only");
        return null;
    }

    private Island requireIsland(Player player) {
        Island island = islands().getIslandOf(player);
        if (island == null) {
            messages().send(player, "general.no-island");
        }
        return island;
    }

    private boolean requireRole(Player player, Island island, IslandRole role) {
        if (island.getRole(player.getUniqueId()).isAtLeast(role)) {
            return true;
        }
        messages().send(player, "general.role-too-low", "{role}", role.getDisplayName());
        return false;
    }

    private boolean requireOnOwnIsland(Player player, Island island) {
        Island here = islands().getIslandAt(player.getLocation());
        if (here != null && here.getId().equals(island.getId())) {
            return true;
        }
        messages().send(player, "commands.not-on-island");
        return false;
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

    // ================================================================== tab-completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtil.filter(args[0], SUBCOMMANDS);
        }
        if (args.length != 2) {
            return Collections.emptyList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String token = args[1];
        switch (sub) {
            case "invite", "ban", "visit", "warp", "info" -> {
                return CommandUtil.filter(token, otherOnlineNames(sender));
            }
            case "kick", "promote", "demote", "transfer" -> {
                return CommandUtil.filter(token, memberNames(sender));
            }
            case "unban" -> {
                return CommandUtil.filter(token, bannedNames(sender));
            }
            case "sell" -> {
                return CommandUtil.filter(token, Arrays.asList("hand", "all"));
            }
            case "challenge" -> {
                List<String> ids = new ArrayList<>();
                for (Challenge challenge : plugin.getChallengeManager().getChallenges()) {
                    ids.add(challenge.getId());
                }
                return CommandUtil.filter(token, ids);
            }
            default -> {
                return Collections.emptyList();
            }
        }
    }

    private List<String> otherOnlineNames(CommandSender sender) {
        List<String> names = CommandUtil.onlineNames();
        names.remove(sender.getName());
        return names;
    }

    private List<String> memberNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        if (!(sender instanceof Player player)) {
            return names;
        }
        Island island = islands().getIslandOf(player);
        if (island == null) {
            return names;
        }
        for (UUID member : island.getMembers().keySet()) {
            if (!member.equals(player.getUniqueId())) {
                names.add(plugin.getPlayerDataManager().getName(member));
            }
        }
        return names;
    }

    private List<String> bannedNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        if (!(sender instanceof Player player)) {
            return names;
        }
        Island island = islands().getIslandOf(player);
        if (island == null) {
            return names;
        }
        for (UUID banned : island.getBanned()) {
            names.add(plugin.getPlayerDataManager().getName(banned));
        }
        return names;
    }
}
