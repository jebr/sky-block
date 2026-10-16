package nl.jebr.skyblock.listener;

import nl.jebr.skyblock.SkyBlockPlugin;
import nl.jebr.skyblock.island.Island;
import nl.jebr.skyblock.island.IslandFlag;
import nl.jebr.skyblock.island.IslandManager;
import nl.jebr.skyblock.island.IslandRole;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Eilandbescherming in de SkyBlock-wereld.
 *
 * <ul>
 *     <li>Leden (MEMBER of hoger) mogen op hun eiland alles.</li>
 *     <li>Bezoekers mogen alleen wat de {@link IslandFlag}s van het eiland toestaan.</li>
 *     <li>Buiten elk eiland (void tussen eilanden of buiten de straal) mag niemand bouwen,
 *         breken of met blokken werken.</li>
 *     <li>Spelers met {@code skyblock.bypass} worden nergens tegengehouden.</li>
 * </ul>
 *
 * <p>Daarnaast worden explosies, vloeistoffen, zuigers en brand binnen de eilandgrens
 * gehouden en wordt natuurlijk spawnen geregeld met de spawn-flags.</p>
 */
public class ProtectionListener implements Listener {

    /** Permissie die alle bescherming negeert. */
    static final String BYPASS = "skyblock.bypass";

    /** Minimale tijd (ms) tussen twee meldingen aan dezelfde speler. */
    private static final long MESSAGE_COOLDOWN = 2000L;

    /** Spawnredenen die als "natuurlijk" gelden en dus door de spawn-flags worden geregeld. */
    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAL_REASONS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.JOCKEY,
            CreatureSpawnEvent.SpawnReason.MOUNT,
            CreatureSpawnEvent.SpawnReason.TRAP
    );

    /** Blokken die als container gelden, ook als hun blockstate geen InventoryHolder is. */
    private static final Set<Material> CONTAINER_BLOCKS = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.HOPPER,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.DISPENSER, Material.DROPPER, Material.CRAFTER,
            Material.BREWING_STAND, Material.JUKEBOX, Material.LECTERN,
            Material.ENDER_CHEST, Material.CHISELED_BOOKSHELF, Material.DECORATED_POT
    );

    /** Werkbanken en vergelijkbare blokken (flag VISITOR_CRAFTING). */
    private static final Set<Material> CRAFTING_BLOCKS = EnumSet.of(
            Material.CRAFTING_TABLE, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.ENCHANTING_TABLE, Material.SMITHING_TABLE, Material.STONECUTTER,
            Material.LOOM, Material.GRINDSTONE, Material.CARTOGRAPHY_TABLE
    );

    /**
     * Overige blokken waarmee een bezoeker niets mag doen, als aanvulling op
     * {@link Material#isInteractable()}.
     */
    private static final Set<Material> OTHER_INTERACTABLE = EnumSet.of(
            Material.COMPOSTER, Material.NOTE_BLOCK, Material.REPEATER, Material.COMPARATOR,
            Material.DAYLIGHT_DETECTOR, Material.CAKE, Material.RESPAWN_ANCHOR, Material.BELL,
            Material.SWEET_BERRY_BUSH, Material.BEEHIVE, Material.BEE_NEST, Material.REDSTONE_WIRE,
            Material.DRAGON_EGG, Material.SPAWNER, Material.FARMLAND, Material.TURTLE_EGG,
            Material.JIGSAW, Material.STRUCTURE_BLOCK
    );

    /**
     * Wat een actie vereist. {@code FREE} betekent dat iedereen het mag,
     * {@code MEMBER} dat alleen leden het mogen; de overige waarden horen bij een flag.
     */
    private enum Need {
        FREE(null),
        MEMBER(null),
        BREAK(IslandFlag.VISITOR_BREAK),
        PLACE(IslandFlag.VISITOR_PLACE),
        DOORS(IslandFlag.VISITOR_DOORS),
        CONTAINERS(IslandFlag.VISITOR_CONTAINERS),
        CRAFTING(IslandFlag.VISITOR_CRAFTING),
        ANIMALS(IslandFlag.VISITOR_ANIMALS);

        private final IslandFlag flag;

        Need(IslandFlag flag) {
            this.flag = flag;
        }
    }

    private final SkyBlockPlugin plugin;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public ProtectionListener(SkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================== hulpmethoden (publiek)

    /**
     * Heeft de speler volledige toegang op deze locatie? Dat is zo als het eiland
     * op die locatie hem als MEMBER of hoger kent. Buiten elk eiland heeft niemand
     * volledige toegang. De bypass-permissie wordt hier niet meegenomen.
     */
    public boolean hasFullAccess(Player player, Location location) {
        Island island = islands().getIslandAt(location);
        return island != null && island.getRole(player.getUniqueId()).isAtLeast(IslandRole.MEMBER);
    }

    /**
     * Bepaalt of de speler dit eiland mag betreden. Geeft de berichtsleutel terug
     * als dat niet mag ({@code protection.banned} of {@code protection.locked}),
     * anders {@code null}. Leden en spelers met bypass mogen altijd.
     */
    static String entryDenialKey(Player player, Island island) {
        if (island == null || player.hasPermission(BYPASS)) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        if (island.isMember(uuid)) {
            return null;
        }
        if (island.isBanned(uuid)) {
            return "protection.banned";
        }
        if (island.isLocked()) {
            return "protection.locked";
        }
        return null;
    }

    // ================================================================== blokken breken en plaatsen

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        check(event, event.getPlayer(), event.getBlock().getLocation(), Need.BREAK, false, true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        check(event, event.getPlayer(), event.getBlockPlaced().getLocation(), Need.PLACE, false, true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ItemStack result = event.getItemStack();
        // Melken van een koe/geit loopt ook via dit event: dat valt onder dieren
        Need need = result != null && result.getType() == Material.MILK_BUCKET ? Need.ANIMALS : Need.BREAK;
        check(event, event.getPlayer(), event.getBlockClicked().getLocation(), need, need == Need.ANIMALS, true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        check(event, event.getPlayer(), target.getLocation(), Need.PLACE, false, true);
    }

    /** Vis, axolotl of kikkervisje in een emmer scheppen. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        check(event, event.getPlayer(), event.getEntity().getLocation(), Need.ANIMALS, true, true);
    }

    /** Schilderijen, item frames en leash-knopen ophangen. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            check(event, player, event.getEntity().getLocation(), Need.PLACE, false, true);
        }
    }

    /** Armor stands, boten, minecarts en end crystals plaatsen. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            check(event, player, event.getEntity().getLocation(), Need.PLACE, false, true);
        }
    }

    /** Hangende entiteiten (schilderijen, item frames) breken: alleen leden. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = resolvePlayer(event.getRemover());
        if (player != null) {
            check(event, player, event.getEntity().getLocation(), Need.MEMBER, false, true);
        }
    }

    /** Boten en minecarts beschadigen valt onder breken. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = resolvePlayer(event.getAttacker());
        if (player != null) {
            check(event, player, event.getVehicle().getLocation(), Need.BREAK, false, true);
        }
    }

    // ================================================================== interactie met blokken

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !isSkyBlockWorld(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        Location location = block.getLocation();
        Material type = block.getType();

        if (event.getAction() == Action.PHYSICAL) {
            // Drukplaten en struikeldraad vallen onder deuren; vertrappen van akkers e.d. alleen leden
            Need need = Tag.PRESSURE_PLATES.isTagged(type) || type == Material.TRIPWIRE ? Need.DOORS : Need.MEMBER;
            if (!isAllowed(player, location, need, false)) {
                event.setCancelled(true); // geen melding: zou spammen bij elke stap
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 1. Het blok zelf gebruiken
        if (event.useInteractedBlock() != Event.Result.DENY) {
            Need blockNeed = classifyBlock(block);
            if (!isAllowed(player, location, blockNeed, false)) {
                event.setUseInteractedBlock(Event.Result.DENY);
                notifyDenied(player);
            }
        }

        // 2. Een voorwerp op het blok gebruiken (gereedschap, aansteker, beendermeel, spawn eggs)
        ItemStack item = event.getItem();
        if (item != null && event.useItemInHand() != Event.Result.DENY) {
            Need itemNeed = classifyItem(item.getType());
            Location target = block.getRelative(event.getBlockFace()).getLocation();
            if (!isAllowed(player, location, itemNeed, false) || !isAllowed(player, target, itemNeed, false)) {
                event.setUseItemInHand(Event.Result.DENY);
                notifyDenied(player);
            }
        }
    }

    /** Spelers die vuur aansteken; brand die over de eilandgrens springt. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (!isSkyBlockWorld(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null) {
            check(event, player, block.getLocation(), Need.PLACE, false, true);
            return;
        }
        Block source = event.getIgnitingBlock();
        if (source != null && crossesBorder(source, block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Block source = event.getIgnitingBlock();
        if (source != null && isSkyBlockWorld(event.getBlock().getWorld()) && crossesBorder(source, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (!isSkyBlockWorld(event.getBlock().getWorld())) {
            return;
        }
        boolean fire = event.getNewState().getType() == Material.FIRE
                || event.getNewState().getType() == Material.SOUL_FIRE
                || event.getSource().getType() == Material.FIRE
                || event.getSource().getType() == Material.SOUL_FIRE;
        if (fire && crossesBorder(event.getSource(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // ================================================================== interactie met entiteiten

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Need need = classifyEntity(entity);
        check(event, event.getPlayer(), entity.getLocation(), need, need == Need.ANIMALS, true);
    }

    /** Rechtsklik op een precieze plek van een entiteit (vooral armor stands). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof ArmorStand) {
            check(event, event.getPlayer(), entity.getLocation(), Need.MEMBER, false, true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        check(event, event.getPlayer(), event.getRightClicked().getLocation(), Need.MEMBER, false, true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        check(event, event.getPlayer(), event.getEntity().getLocation(), Need.ANIMALS, true, true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        check(event, event.getPlayer(), event.getEntity().getLocation(), Need.ANIMALS, true, true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        check(event, event.getPlayer(), event.getEntity().getLocation(), Need.ANIMALS, true, true);
    }

    /** Dieren of item frames naar je toe trekken met een hengel. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Entity caught = event.getCaught();
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || caught == null || caught instanceof Player) {
            return;
        }
        Need need = classifyEntity(caught);
        check(event, event.getPlayer(), caught.getLocation(), need, need == Need.ANIMALS, true);
    }

    /** PvP, dieren slaan en item frames/armor stands beschadigen. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!isSkyBlockWorld(victim.getWorld())) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (victim instanceof Player target) {
            if (target.equals(attacker)) {
                return;
            }
            if (!isPvpAllowed(attacker, target)) {
                event.setCancelled(true);
                notifyDenied(attacker);
            }
            return;
        }
        Need need;
        if (victim instanceof Hanging || victim instanceof ArmorStand) {
            need = Need.MEMBER;
        } else if (victim instanceof Animals || victim instanceof AbstractVillager) {
            need = Need.ANIMALS;
        } else {
            return;
        }
        check(event, attacker, victim.getLocation(), need, need == Need.ANIMALS, true);
    }

    // ================================================================== items

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player) {
            // Geen melding: dit event vuurt elke tick zolang de speler bij het item staat
            check(event, player, event.getItem().getLocation(), IslandFlag.VISITOR_ITEM_PICKUP, true, false);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        check(event, event.getPlayer(), event.getArrow().getLocation(), IslandFlag.VISITOR_ITEM_PICKUP, true, false);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        check(event, event.getPlayer(), event.getPlayer().getLocation(), IslandFlag.VISITOR_ITEM_DROP, true, true);
    }

    // ================================================================== spawnen

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isSkyBlockWorld(event.getLocation().getWorld())
                || !NATURAL_REASONS.contains(event.getSpawnReason())) {
            return;
        }
        Island island = islands().getIslandAt(event.getLocation());
        if (island == null) {
            // Tussen de eilanden spawnt niets vanzelf
            event.setCancelled(true);
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity instanceof Enemy) {
            if (!island.getFlag(IslandFlag.MONSTER_SPAWNING)) {
                event.setCancelled(true);
            }
        } else if (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Ambient) {
            if (!island.getFlag(IslandFlag.ANIMAL_SPAWNING)) {
                event.setCancelled(true);
            }
        }
    }

    // ================================================================== explosies, vloeistoffen, zuigers

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isSkyBlockWorld(event.getEntity().getWorld())) {
            removeOutsideBlocks(event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isSkyBlockWorld(event.getBlock().getWorld())) {
            removeOutsideBlocks(event.blockList());
        }
    }

    /** Vloeistoffen (en de drakenei-teleport) mogen niet van binnen een eiland naar buiten. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        if (!isSkyBlockWorld(from.getWorld())) {
            return;
        }
        Island source = islandAt(from);
        if (source != null && islandAt(event.getToBlock()) != source) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        if (!isSkyBlockWorld(piston.getWorld())) {
            return;
        }
        Island island = islandAt(piston);
        BlockFace direction = event.getDirection();
        if (islandAt(piston.getRelative(direction)) != island) {
            event.setCancelled(true);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (islandAt(block) != island || islandAt(block.getRelative(direction)) != island) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Block piston = event.getBlock();
        if (!isSkyBlockWorld(piston.getWorld())) {
            return;
        }
        Island island = islandAt(piston);
        BlockFace direction = event.getDirection();
        for (Block block : event.getBlocks()) {
            // Richting-onafhankelijk: het blok en beide buren langs de zuigeras moeten op hetzelfde eiland liggen
            if (islandAt(block) != island
                    || islandAt(block.getRelative(direction)) != island
                    || islandAt(block.getRelative(direction.getOppositeFace())) != island) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ================================================================== teleporteren

    /** Ender pearls en chorus fruit naar een eiland waar je verbannen bent of dat op slot is. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }
        Location to = event.getTo();
        if (to == null || !isSkyBlockWorld(to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        Island target = islands().getIslandAt(to);
        if (target == null || target == islands().getIslandAt(event.getFrom())) {
            return; // binnen hetzelfde eiland blijven mag altijd
        }
        String key = entryDenialKey(player, target);
        if (key != null) {
            event.setCancelled(true);
            sendLimited(player, key);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessage.remove(event.getPlayer().getUniqueId());
    }

    // ================================================================== interne hulpmethoden

    private IslandManager islands() {
        return plugin.getIslandManager();
    }

    private boolean isSkyBlockWorld(World world) {
        return plugin.getWorldManager().isSkyBlockWorld(world);
    }

    private Island islandAt(Block block) {
        return islands().getIslandAt(block.getLocation());
    }

    /**
     * Mag de speler deze actie op deze locatie uitvoeren?
     *
     * @param need         wat de actie vereist
     * @param freeOutside  of de actie buiten elk eiland is toegestaan (bijv. items laten vallen)
     */
    private boolean isAllowed(Player player, Location location, Need need, boolean freeOutside) {
        if (need == Need.FREE || location == null || !isSkyBlockWorld(location.getWorld())) {
            return true;
        }
        if (player.hasPermission(BYPASS)) {
            return true;
        }
        Island island = islands().getIslandAt(location);
        if (island == null) {
            return freeOutside;
        }
        UUID uuid = player.getUniqueId();
        if (island.getRole(uuid).isAtLeast(IslandRole.MEMBER)) {
            return true;
        }
        if (island.isBanned(uuid) || need.flag == null) {
            return false;
        }
        return island.getFlag(need.flag);
    }

    /** Variant van {@link #isAllowed(Player, Location, Need, boolean)} voor een losse flag. */
    private boolean isAllowed(Player player, Location location, IslandFlag flag, boolean freeOutside) {
        if (location == null || !isSkyBlockWorld(location.getWorld()) || player.hasPermission(BYPASS)) {
            return true;
        }
        Island island = islands().getIslandAt(location);
        if (island == null) {
            return freeOutside;
        }
        UUID uuid = player.getUniqueId();
        if (island.getRole(uuid).isAtLeast(IslandRole.MEMBER)) {
            return true;
        }
        return !island.isBanned(uuid) && island.getFlag(flag);
    }

    /** Annuleert het event (met melding) als de actie niet mag. */
    private void check(Cancellable event, Player player, Location location, Need need,
                       boolean freeOutside, boolean message) {
        if (!isAllowed(player, location, need, freeOutside)) {
            event.setCancelled(true);
            if (message) {
                notifyDenied(player);
            }
        }
    }

    /** Annuleert het event (met melding) als de flag dit niet toestaat. */
    private void check(Cancellable event, Player player, Location location, IslandFlag flag,
                       boolean freeOutside, boolean message) {
        if (!isAllowed(player, location, flag, freeOutside)) {
            event.setCancelled(true);
            if (message) {
                notifyDenied(player);
            }
        }
    }

    /** PvP mag alleen als zowel aanvaller als slachtoffer op een eiland met PVP aan staan. */
    private boolean isPvpAllowed(Player attacker, Player victim) {
        if (attacker.hasPermission(BYPASS)) {
            return true;
        }
        Island victimIsland = islands().getIslandAt(victim.getLocation());
        if (victimIsland == null || !victimIsland.getFlag(IslandFlag.PVP)) {
            return false;
        }
        Island attackerIsland = islands().getIslandAt(attacker.getLocation());
        return attackerIsland != null && attackerIsland.getFlag(IslandFlag.PVP);
    }

    /** Bepaalt welke flag nodig is om met dit blok te interageren. */
    private Need classifyBlock(Block block) {
        Material type = block.getType();
        if (Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)
                || Tag.BUTTONS.isTagged(type) || Tag.PRESSURE_PLATES.isTagged(type) || type == Material.LEVER) {
            return Need.DOORS;
        }
        if (CRAFTING_BLOCKS.contains(type)) {
            return Need.CRAFTING;
        }
        if (CONTAINER_BLOCKS.contains(type) || Tag.SHULKER_BOXES.isTagged(type)) {
            return Need.CONTAINERS;
        }
        boolean interactable = type.isInteractable();
        if (interactable && block.getState() instanceof InventoryHolder) {
            return Need.CONTAINERS;
        }
        if (interactable || OTHER_INTERACTABLE.contains(type)
                || Tag.BEDS.isTagged(type) || Tag.CANDLES.isTagged(type) || Tag.CANDLE_CAKES.isTagged(type)
                || Tag.FLOWER_POTS.isTagged(type) || Tag.CAMPFIRES.isTagged(type) || Tag.ALL_SIGNS.isTagged(type)
                || Tag.CAVE_VINES.isTagged(type) || Tag.CAULDRONS.isTagged(type)) {
            return Need.MEMBER;
        }
        return Need.FREE;
    }

    /** Bepaalt of een voorwerp in de hand blokken verandert wanneer je het op een blok gebruikt. */
    private static Need classifyItem(Material type) {
        String name = type.name();
        if (name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                || type == Material.SHEARS || type == Material.BRUSH || type == Material.HONEYCOMB) {
            return Need.BREAK;
        }
        if (type == Material.FLINT_AND_STEEL || type == Material.FIRE_CHARGE || type == Material.BONE_MEAL
                || name.endsWith("_SPAWN_EGG")) {
            return Need.PLACE;
        }
        return Need.FREE;
    }

    /**
     * Bepaalt wat nodig is om met deze entiteit te interageren (rechtsklik).
     * Handelen met villagers blijft vrij; villagers slaan valt onder VISITOR_ANIMALS
     * (zie {@link #onDamage(EntityDamageByEntityEvent)}).
     */
    private static Need classifyEntity(Entity entity) {
        if (entity instanceof Hanging || entity instanceof ArmorStand) {
            return Need.MEMBER;
        }
        if (entity instanceof Animals) {
            return Need.ANIMALS;
        }
        if (entity instanceof AbstractVillager) {
            return Need.FREE; // villagers zijn ook InventoryHolder, maar handelen blijft vrij
        }
        if (entity instanceof InventoryHolder) {
            return Need.CONTAINERS; // minecart met kist, kistboot, hopper-minecart
        }
        return Need.FREE;
    }

    /** De speler achter een schade- of breekactie: direct, via projectiel, wolk of TNT. */
    private static Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player source) {
            return source;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source;
        }
        return null;
    }

    /** Ligt het doelblok op een ander eiland (of buiten elk eiland) dan het bronblok? */
    private boolean crossesBorder(Block from, Block to) {
        return islandAt(from) != islandAt(to);
    }

    /** Haalt blokken buiten elke eilandstraal uit de lijst van een explosie. */
    private void removeOutsideBlocks(List<Block> blocks) {
        blocks.removeIf(block -> islandAt(block) == null);
    }

    private void notifyDenied(Player player) {
        sendLimited(player, "protection.denied");
    }

    /** Stuurt een melding, maar hooguit eens per {@link #MESSAGE_COOLDOWN} ms per speler. */
    private void sendLimited(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN) {
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        plugin.getMessages().send(player, key);
    }
}
