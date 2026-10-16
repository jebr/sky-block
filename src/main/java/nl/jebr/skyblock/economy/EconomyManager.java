package nl.jebr.skyblock.economy;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.player.PlayerData;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

/** Eenvoudige ingebouwde economie op basis van PlayerData. */
public class EconomyManager {

    private final SkyBlockPlugin plugin;
    private final DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.of("nl", "NL")));

    public EconomyManager(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public double getBalance(UUID player) {
        return plugin.getPlayerDataManager().get(player).getBalance();
    }

    public boolean has(UUID player, double amount) {
        return getBalance(player) + 1e-9 >= amount;
    }

    public void deposit(UUID player, double amount) {
        if (amount <= 0) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        data.setBalance(round(data.getBalance() + amount));
    }

    /** Haalt geld af als de speler genoeg heeft. Geeft false terug als dat niet zo is. */
    public boolean withdraw(UUID player, double amount) {
        if (amount < 0) {
            return false;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data.getBalance() + 1e-9 < amount) {
            return false;
        }
        data.setBalance(round(data.getBalance() - amount));
        return true;
    }

    public void set(UUID player, double amount) {
        plugin.getPlayerDataManager().get(player).setBalance(round(Math.max(0, amount)));
    }

    /** Bedrag met valutasymbool, bijv. "$1.250,5". */
    public String format(double amount) {
        String symbol = plugin.getConfig().getString("economy.currency-symbol", "$");
        return symbol + format.format(amount);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
