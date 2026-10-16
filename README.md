# SkyBlock

Een volledige SkyBlock-spelmodus voor **Spigot 1.21.x** (Java 21). Geen andere plug-ins nodig.

## Downloaden

De nieuwste jar staat altijd bij **Releases → `latest`** (`SkyBlock-1.0.0.jar`). Elke push naar `main` bouwt automatisch
een nieuwe versie via GitHub Actions.

## Installeren

1. Zet `SkyBlock-1.0.0.jar` in de map `plugins/` van je Spigot 1.21.x-server (Java 21).
2. Start de server. De plug-in maakt de void-wereld `skyblock_world` aan en de map `plugins/SkyBlock/` met alle configs.
3. Typ in het spel `/is` en klik op **Eiland maken**.

Tip: wil je dat spelers direct in de SkyBlock-wereld spawnen, zet dan in `server.properties` het `level-name`
op een kleine lobbywereld en laat spelers met `/is` naar hun eiland gaan. De void-generator is ook bruikbaar via
`bukkit.yml` of Multiverse met generator `SkyBlock`.

## Wat zit erin

- **Eilanden**: klassiek L-vormig starteiland met boom en startkist, in een raster in een eigen void-wereld.
  Home, reset (met wachttijd en maximum), naam, wereldrand rond je eiland, terug naar je eiland als je in de void valt.
- **Teams**: uitnodigen, accepteren, kicken, verlaten, overdragen. Rollen: Eigenaar, Mede-eigenaar, Lid, Bezoeker.
- **Bescherming**: niemand bouwt op jouw eiland behalve leden. Per eiland instelbaar wat bezoekers mogen
  (breken, plaatsen, deuren, kisten, items, dieren, werkbanken), PvP, monster- en dierspawns. Lock en bans.
- **Cobblestone-generator**: lava + water geeft cobble, en met upgrades ook ertsen.
- **Eilandlevel en ranglijst**: blokwaarden in `levels.yml`, top 10 in een menu.
- **Challenges**: 32 challenges in 4 categorieën (Beginner, Gemiddeld, Gevorderd, Expert) die openen bij een hoger
  eilandlevel. Items inleveren, blokken op je eiland, of een eilandlevel halen. Herhaalbaar met beloningen.
- **Economie en shop**: eigen saldo, `/pay`, shop met 6 categorieën om te kopen en verkopen.
- **Upgrades**: eilandgrootte, maximaal aantal leden en generatorniveau.
- **Warps**: zet een warp en laat anderen je eiland bezoeken.
- **Menu's** voor alles: hoofdmenu, instellingen, leden, upgrades, challenges, shop, warps, top.

## Commando's

| Commando | Uitleg |
|---|---|
| `/is` | Hoofdmenu |
| `/is create`, `home`, `sethome`, `reset` | Eiland maken, erheen gaan, home zetten, opnieuw beginnen |
| `/is info [speler]`, `level`, `top` | Informatie, level berekenen, ranglijst |
| `/is invite <speler>`, `accept`, `deny` | Spelers uitnodigen en uitnodigingen beantwoorden |
| `/is kick`, `leave`, `transfer`, `promote`, `demote` | Ledenbeheer |
| `/is members`, `settings`, `upgrades` | Menu's |
| `/is lock`, `unlock`, `ban`, `unban` | Toegang regelen |
| `/is visit <speler>`, `setwarp`, `delwarp`, `warp <speler>`, `warps` | Bezoeken en warps |
| `/is challenges`, `challenge <id>` | Challenges |
| `/is shop`, `sell [hand\|all]` | Shop |
| `/is name <naam>`, `chat <bericht>` | Eilandnaam en teamchat |
| `/balance [speler]`, `/pay <speler> <bedrag>` | Economie |
| `/isadmin ...` | Beheer: reload, save, delete, tp, info, setlevel, addmember, removemember, setowner, money, resetchallenges, resetcount, recalc |

Aliassen: `/island`, `/is`, `/skyblock`, `/sb` en `/isadmin`, `/sbadmin`.

## Permissies

| Permissie | Standaard | Uitleg |
|---|---|---|
| `skyblock.use` | iedereen | Spelen |
| `skyblock.admin` | op | Beheercommando's |
| `skyblock.bypass` | op | Negeert eilandbescherming, locks, bans en reset-wachttijd |

## Configuratie

Alles staat in `plugins/SkyBlock/`:

- `config.yml`: wereld, afstand tussen eilanden, startkist, startsaldo, upgradekosten en generatorkansen.
- `challenges.yml`, `shop.yml`, `levels.yml`: challenges, shopprijzen en blokwaarden.
- `lang/*.yml`: alle teksten (Nederlands), met `&`-kleurcodes.
- `data/`: eilanden en spelers (YAML).

Na aanpassen: `/isadmin reload`.

## Zelf bouwen

```
mvn package
```
De jar komt in `target/`.
