package nl.jebr.skyblock;

import nl.jebr.skyblock.challenge.ChallengeManager;
import nl.jebr.skyblock.command.AdminCommand;
import nl.jebr.skyblock.command.EconomyCommand;
import nl.jebr.skyblock.command.IslandCommand;
import nl.jebr.skyblock.config.Messages;
import nl.jebr.skyblock.economy.EconomyManager;
import nl.jebr.skyblock.gui.MenuListener;
import nl.jebr.skyblock.island.IslandManager;
import nl.jebr.skyblock.level.LevelManager;
import nl.jebr.skyblock.listener.GeneratorListener;
import nl.jebr.skyblock.listener.PlayerListener;
import nl.jebr.skyblock.listener.ProtectionListener;
import nl.jebr.skyblock.player.PlayerDataManager;
import nl.jebr.skyblock.shop.ShopManager;
import nl.jebr.skyblock.storage.Storage;
import nl.jebr.skyblock.upgrade.UpgradeManager;
import nl.jebr.skyblock.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hoofdklasse van de SkyBlock-plug-in. Maakt alle managers aan en registreert
 * listeners en commando's.
 */
public final class SkyBlockPlugin extends JavaPlugin {

    private Messages messages;
    private Storage storage;
    private WorldManager worldManager;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;
    private UpgradeManager upgradeManager;
    private IslandManager islandManager;
    private LevelManager levelManager;
    private ChallengeManager challengeManager;
    private ShopManager shopManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        storage = new Storage(this);
        worldManager = new WorldManager(this);
        worldManager.setup();

        playerDataManager = new PlayerDataManager(this);
        economyManager = new EconomyManager(this);
        upgradeManager = new UpgradeManager(this);
        islandManager = new IslandManager(this);
        islandManager.loadAll();

        levelManager = new LevelManager(this);
        challengeManager = new ChallengeManager(this);
        shopManager = new ShopManager(this);

        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GeneratorListener(this), this);

        registerCommand("island", new IslandCommand(this));
        registerCommand("isadmin", new AdminCommand(this));
        EconomyCommand economyCommand = new EconomyCommand(this);
        registerCommand("balance", economyCommand);
        registerCommand("pay", economyCommand);

        // Spelers die al online zijn (bij /reload) laden
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerDataManager.load(player.getUniqueId(), player.getName());
        }

        long autosaveTicks = Math.max(1, getConfig().getLong("autosave-interval", 5)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, autosaveTicks, autosaveTicks);

        getLogger().info("SkyBlock ingeschakeld met " + islandManager.getIslands().size() + " eilanden.");
    }

    @Override
    public void onDisable() {
        saveAll();
    }

    /** Maakt het mogelijk de void-generator te gebruiken via bukkit.yml of Multiverse. */
    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new nl.jebr.skyblock.world.VoidGenerator(getConfig().getInt("world.island-height", 100));
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Commando '" + name + "' ontbreekt in plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** Slaat alle eilanden en spelersgegevens op. */
    public void saveAll() {
        if (islandManager != null) {
            islandManager.saveAll();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
    }

    /** Herlaadt alle configuratiebestanden. */
    public void reloadAll() {
        reloadConfig();
        messages.reload();
        upgradeManager.reload();
        levelManager.reload();
        challengeManager.reload();
        shopManager.reload();
    }

    public Messages getMessages() {
        return messages;
    }

    public Storage getStorage() {
        return storage;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public EconomyManager getEconomy() {
        return economyManager;
    }

    public UpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public ChallengeManager getChallengeManager() {
        return challengeManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }
}
