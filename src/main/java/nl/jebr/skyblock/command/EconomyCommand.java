package nl.jebr.skyblock.command;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * De economiecommando's /balance en /pay. Welk commando het is, volgt uit
 * {@link Command#getName()}.
 */
public class EconomyCommand implements TabExecutor {

    private final SkyBlockPlugin plugin;

    public EconomyCommand(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.getMessages();
    }

    private EconomyManager economy() {
        return plugin.getEconomy();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("pay")) {
            handlePay(sender, args, label);
        } else {
            handleBalance(sender, args);
        }
        return true;
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages().send(sender, "general.player-only");
                return;
            }
            messages().send(player, "commands.economy.balance",
                    "{balance}", economy().format(economy().getBalance(player.getUniqueId())));
            return;
        }
        if (!sender.hasPermission("skyblock.admin")) {
            messages().send(sender, "general.no-permission");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuid(args[0]);
        if (target == null) {
            messages().send(sender, "general.player-not-found", "{player}", args[0]);
            return;
        }
        messages().send(sender, "commands.economy.balance-other",
                "{player}", plugin.getPlayerDataManager().getName(target),
                "{balance}", economy().format(economy().getBalance(target)));
    }

    private void handlePay(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            messages().send(player, "commands.economy.pay-usage", "{label}", label);
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuid(args[0]);
        if (target == null) {
            messages().send(player, "general.player-not-found", "{player}", args[0]);
            return;
        }
        if (target.equals(player.getUniqueId())) {
            messages().send(player, "commands.economy.pay-self");
            return;
        }
        Double amount = CommandUtil.parseAmount(args[1]);
        if (amount == null || amount <= 0) {
            messages().send(player, "commands.economy.invalid-amount", "{input}", args[1]);
            return;
        }
        if (!economy().withdraw(player.getUniqueId(), amount)) {
            messages().send(player, "general.not-enough-money", "{amount}", economy().format(amount));
            return;
        }
        economy().deposit(target, amount);
        String formatted = economy().format(amount);
        String targetName = plugin.getPlayerDataManager().getName(target);
        messages().send(player, "commands.economy.pay-sent", "{player}", targetName, "{amount}", formatted);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            messages().send(online, "commands.economy.pay-received", "{player}", player.getName(), "{amount}", formatted);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length != 1) {
            return Collections.emptyList();
        }
        if (name.equals("pay")) {
            List<String> names = CommandUtil.onlineNames();
            names.remove(sender.getName());
            return CommandUtil.filter(args[0], names);
        }
        if (sender.hasPermission("skyblock.admin")) {
            return CommandUtil.filter(args[0], CommandUtil.onlineNames());
        }
        return Collections.emptyList();
    }
}
