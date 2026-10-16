package nl.jebr.skyblock.config;

import nl.jebr.skyblock.SkyBlockPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Beheert alle berichten. Berichten staan verdeeld over bestanden in de map
 * {@code lang/} (bijv. lang/core.yml, lang/commands.yml). Alle sleutels worden
 * samengevoegd in een platte map. Gebruik {@code &}-kleurcodes en
 * placeholders zoals {@code {player}}.
 *
 * <p>Gebruik: {@code messages.send(player, "island.created", "{name}", island.getName());}</p>
 */
public class Messages {

    /** Alle taalbestanden die in de jar zitten (src/main/resources/lang/NAAM.yml). */
    public static final String[] LANG_FILES = {
            "core", "commands", "protection", "menus", "challenges", "shop", "level"
    };

    private final SkyBlockPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();

    public Messages(SkyBlockPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        messages.clear();
        lists.clear();
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("Kon map lang/ niet aanmaken");
        }
        for (String name : LANG_FILES) {
            String resourcePath = "lang/" + name + ".yml";
            InputStream resource = plugin.getResource(resourcePath);
            if (resource == null) {
                continue;
            }
            File file = new File(plugin.getDataFolder(), resourcePath);
            if (!file.exists()) {
                plugin.saveResource(resourcePath, false);
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            for (String key : defaults.getKeys(true)) {
                if (config.isList(key)) {
                    lists.put(key, config.getStringList(key));
                } else if (!config.isConfigurationSection(key)) {
                    messages.put(key, config.getString(key, ""));
                }
            }
            // Eigen toegevoegde sleutels van de beheerder ook meenemen
            for (String key : config.getKeys(true)) {
                if (messages.containsKey(key) || lists.containsKey(key) || config.isConfigurationSection(key)) {
                    continue;
                }
                if (config.isList(key)) {
                    lists.put(key, config.getStringList(key));
                } else {
                    messages.put(key, config.getString(key, ""));
                }
            }
        }
    }

    /** Het voorvoegsel voor chatberichten (sleutel {@code prefix} in core.yml). */
    public String prefix() {
        return color(messages.getOrDefault("prefix", "&8[&bSkyBlock&8] &7"));
    }

    /**
     * Haalt een bericht op zonder prefix, met kleur en placeholders toegepast.
     *
     * @param key          berichtsleutel
     * @param placeholders paren van placeholder en waarde, bijv. "{player}", "Jan"
     */
    public String get(String key, Object... placeholders) {
        String raw = messages.get(key);
        if (raw == null) {
            return color("&cOntbrekend bericht: " + key);
        }
        return color(apply(raw, placeholders));
    }

    /** Haalt een lijst op (bijv. lore), met kleur en placeholders. */
    public List<String> getList(String key, Object... placeholders) {
        List<String> raw = lists.get(key);
        List<String> result = new ArrayList<>();
        if (raw == null) {
            String single = messages.get(key);
            if (single != null) {
                result.add(color(apply(single, placeholders)));
            }
            return result;
        }
        for (String line : raw) {
            result.add(color(apply(line, placeholders)));
        }
        return result;
    }

    public boolean has(String key) {
        return messages.containsKey(key) || lists.containsKey(key);
    }

    /** Stuurt een bericht met prefix. Lege berichten worden niet verstuurd. */
    public void send(CommandSender sender, String key, Object... placeholders) {
        String message = get(key, placeholders);
        if (ChatColor.stripColor(message).isEmpty()) {
            return;
        }
        sender.sendMessage(prefix() + message);
    }

    /** Stuurt een bericht zonder prefix. */
    public void sendRaw(CommandSender sender, String key, Object... placeholders) {
        for (String line : getList(key, placeholders)) {
            sender.sendMessage(line);
        }
    }

    private static String apply(String text, Object... placeholders) {
        String result = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
        }
        return result;
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
