# Afspraken voor iedereen die aan deze plug-in werkt

Project: SkyBlock-plug-in voor **Spigot 1.21.1 API** (`org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT`), Java 21, Maven.
Package: `nl.jebr.skyblock`. Root: `/home/claude/sky-block`.

## Harde regels
- Er is **geen compiler** beschikbaar in deze omgeving. Schrijf code die in één keer compileert: controleer elke import,
  elke methode-naam en elke signatuur tegen wat je zeker weet van de Spigot 1.21.1 API en tegen de kernklassen hieronder
  (lees die bestanden!). Geen Paper-only API (geen Adventure/Component, geen `player.sendActionBar(String)` etc.).
- Geen extra dependencies. Geen Vault.
- Wijzig **geen** bestanden die je niet bezit. Als je iets in de kern mist, werk eromheen in je eigen klassen en meld het
  in je eindrapport.
- Alle spelerteksten via `plugin.getMessages()` met sleutels uit **jouw eigen** taalbestand
  `src/main/resources/lang/<naam>.yml` (Nederlands, `&`-kleurcodes, placeholders als `{player}`). Gebruik nooit een
  em-dash (—) in teksten.
- Commentaar en javadoc in het Nederlands.
- Materialen/enums met 1.21-namen (bijv. `GRASS_BLOCK`, `Enchantment.UNBREAKING`, `Particle.HAPPY_VILLAGER`,
  `Sound.ENTITY_PLAYER_LEVELUP`). Twijfel je over een naam, kies iets waar je zeker van bent.

## Kern (al geschreven, alleen lezen)
- `SkyBlockPlugin` – getters: `getMessages()`, `getStorage()`, `getWorldManager()`, `getPlayerDataManager()`,
  `getEconomy()`, `getUpgradeManager()`, `getIslandManager()`, `getLevelManager()`, `getChallengeManager()`,
  `getShopManager()`, `saveAll()`, `reloadAll()`.
- `config.Messages` – `get(key, placeholders...)`, `getList(key, ...)`, `send(sender, key, ...)` (met prefix),
  `sendRaw(sender, key, ...)`, `prefix()`, `static color(String)`. Placeholders als paren: `"{player}", name`.
  Algemene sleutels in `lang/core.yml` (`general.no-permission`, `general.no-island`, `general.not-enough-money` ...).
- `island.Island`, `island.IslandRole` (VISITOR < MEMBER < CO_OWNER < OWNER, `isAtLeast`), `island.IslandFlag`.
- `island.IslandManager` – `getIslandOf(UUID|Player)`, `getIslandAt(Location)`, `getIslandInCell(Location)`,
  `getIsland(UUID id)`, `getIslands()`, `getRadius(Island)`, `createIsland(Player)`, `deleteIsland(Island)`,
  `resetIsland(Player)` → `ResetResult`, `getResetCooldownRemaining(UUID)`, `cleanPlayer(Player)`,
  `addMember(Island, UUID)`, `removeMember(Island, UUID)`, `isFull(Island)`, `invite(Island, UUID inviter, UUID target)`,
  `getInvite(UUID)` → `Invite(islandId, inviter, expiresAt)`, `removeInvite(UUID)`, `teleportHome(Player, Island)`,
  `teleportWarp(Player, Island)`, `teleportToSpawn(Player)`, `updateBorder(Player)`, `refreshBorders(Island)`, `save(Island)`.
- `player.PlayerData` / `player.PlayerDataManager` – `get(UUID|Player)`, `load(UUID, name)`, `unload(UUID)`,
  `findUuid(String name)`, `getName(UUID)`.
- `economy.EconomyManager` – `getBalance`, `has`, `deposit`, `withdraw` (boolean), `set`, `format(double)`.
- `upgrade.UpgradeManager` / `upgrade.UpgradeType` – `getMaxLevel`, `getCurrentLevel`, `getCost(type, level)`,
  `getRadiusForLevel`, `getMemberLimitForLevel`, `getMemberLimit(Island)`, `getGeneratorBlocks(level)`,
  `rollGeneratorBlock(Island)`, `upgrade(Player, Island, UpgradeType)` → `Result`.
- `world.WorldManager` – `getWorld()`, `isSkyBlockWorld(World)`, `getIslandHeight()`, `getSpacing()`.
- `gui.Menu` (basisklasse, zie javadoc), `gui.ItemBuilder`, `gui.MenuListener`.
- `util.RegionScanner.scan(plugin, world, minX, minZ, maxX, maxZ, collectPositions, Consumer<Result>)`.

## Modulecontracten (exacte namen, iedereen rekent hierop)
| Klasse | Eigenaar | Contract |
|---|---|---|
| `listener.ProtectionListener(SkyBlockPlugin)` | listeners | `implements Listener` |
| `listener.GeneratorListener(SkyBlockPlugin)` | listeners | `implements Listener` |
| `listener.PlayerListener(SkyBlockPlugin)` | listeners | `implements Listener` |
| `command.IslandCommand(SkyBlockPlugin)` | commands | `implements TabExecutor` (/island) |
| `command.AdminCommand(SkyBlockPlugin)` | commands | `implements TabExecutor` (/isadmin) |
| `command.EconomyCommand(SkyBlockPlugin)` | commands | `implements TabExecutor` (/balance en /pay, kijk naar `command.getName()`) |
| `challenge.ChallengeManager(SkyBlockPlugin)` | menus | `reload()`, `Collection<Challenge> getChallenges()`, `Challenge getChallenge(String id)`, `boolean complete(Player, String id)` (controleert, beloont, stuurt berichten) |
| `shop.ShopManager(SkyBlockPlugin)` | menus | `reload()`, `double sellHand(Player)`, `double sellAll(Player)` (beide sturen zelf berichten, geven verdiend bedrag terug) |
| `level.LevelManager(SkyBlockPlugin)` | level | `reload()`, `void calculate(Island, Consumer<Long> callback)`, `boolean isCalculating(Island)`, `List<Island> getTopIslands(int limit)`, `int getBlockValue(Material)` |
| `gui.MainMenu(plugin, Player)` | menus | `open()`; werkt ook zonder eiland (knop "eiland maken") |
| `gui.SettingsMenu(plugin, Player, Island)` | menus | `open()` |
| `gui.MembersMenu(plugin, Player, Island)` | menus | `open()` |
| `gui.UpgradesMenu(plugin, Player, Island)` | menus | `open()` |
| `gui.WarpsMenu(plugin, Player)` | menus | `open()` |
| `gui.ChallengesMenu(plugin, Player)` | menus | `open()` |
| `gui.ShopMenu(plugin, Player)` | menus | `open()` |
| `gui.ConfirmMenu(plugin, Player, String title, Runnable onConfirm)` | menus | `open()` |
| `gui.TopMenu(plugin, Player)` | level | `open()` |

Alle menu's breiden `gui.Menu` uit.

## Taalbestanden (eigendom)
`core` (kern), `commands`, `protection`, `menus`, `challenges`, `shop`, `level`. Messages laadt precies deze namen.
