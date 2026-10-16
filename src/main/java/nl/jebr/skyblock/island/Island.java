package nl.jebr.skyblock.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Een SkyBlock-eiland. Het eiland staat in een vaste gridcel; de beschermde zone
 * is een vierkant van (2 * radius + 1) blokken rond het middelpunt.
 *
 * <p>Alle wijzigingen markeren het eiland als "dirty" zodat het bij de volgende
 * autosave wordt opgeslagen.</p>
 */
public class Island {

    private final UUID id;
    private final int gridIndex;
    private final String worldName;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final long createdAt;

    private UUID owner;
    private String name;
    private final Map<UUID, IslandRole> members = new LinkedHashMap<>();
    private final Set<UUID> banned = new HashSet<>();
    private final Map<IslandFlag, Boolean> flags = new EnumMap<>(IslandFlag.class);

    // Home- en warplocatie als losse coördinaten zodat het laden niet afhangt van een geladen wereld
    private double homeX, homeY, homeZ;
    private float homeYaw, homePitch;
    private Double warpX, warpY, warpZ;
    private float warpYaw, warpPitch;
    private boolean warpEnabled;

    private boolean locked;
    private int sizeLevel = 1;
    private int memberLevel = 1;
    private int generatorLevel = 1;
    private long level;
    private long levelPoints;

    private transient boolean dirty;

    public Island(UUID id, int gridIndex, String worldName, int centerX, int centerY, int centerZ,
                  UUID owner, long createdAt) {
        this.id = id;
        this.gridIndex = gridIndex;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.owner = owner;
        this.createdAt = createdAt;
        this.members.put(owner, IslandRole.OWNER);
        this.homeX = centerX + 0.5;
        this.homeY = centerY + 1;
        this.homeZ = centerZ + 0.5;
        for (IslandFlag flag : IslandFlag.values()) {
            flags.put(flag, flag.getDefaultValue());
        }
    }

    // ------------------------------------------------------------------ locatie

    public UUID getId() {
        return id;
    }

    public int getGridIndex() {
        return gridIndex;
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public Location getCenter() {
        return new Location(getWorld(), centerX + 0.5, centerY, centerZ + 0.5);
    }

    /** Ligt deze locatie binnen de beschermde zone met de opgegeven straal? */
    public boolean contains(Location location, int radius) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(worldName)) {
            return false;
        }
        return Math.abs(location.getBlockX() - centerX) <= radius
                && Math.abs(location.getBlockZ() - centerZ) <= radius;
    }

    public Location getHome() {
        return new Location(getWorld(), homeX, homeY, homeZ, homeYaw, homePitch);
    }

    public void setHome(Location location) {
        this.homeX = location.getX();
        this.homeY = location.getY();
        this.homeZ = location.getZ();
        this.homeYaw = location.getYaw();
        this.homePitch = location.getPitch();
        markDirty();
    }

    public Location getWarp() {
        if (warpX == null) {
            return null;
        }
        return new Location(getWorld(), warpX, warpY, warpZ, warpYaw, warpPitch);
    }

    public void setWarp(Location location) {
        if (location == null) {
            warpX = warpY = warpZ = null;
            warpEnabled = false;
        } else {
            warpX = location.getX();
            warpY = location.getY();
            warpZ = location.getZ();
            warpYaw = location.getYaw();
            warpPitch = location.getPitch();
        }
        markDirty();
    }

    public boolean isWarpEnabled() {
        return warpEnabled && warpX != null;
    }

    public void setWarpEnabled(boolean warpEnabled) {
        this.warpEnabled = warpEnabled;
        markDirty();
    }

    // ------------------------------------------------------------------ leden

    public UUID getOwner() {
        return owner;
    }

    /** Draagt het eigenaarschap over; de oude eigenaar wordt mede-eigenaar. */
    public void setOwner(UUID newOwner) {
        if (owner != null && !owner.equals(newOwner)) {
            members.put(owner, IslandRole.CO_OWNER);
        }
        this.owner = newOwner;
        members.put(newOwner, IslandRole.OWNER);
        markDirty();
    }

    /** Alle leden inclusief eigenaar, met hun rol (alleen-lezen). */
    public Map<UUID, IslandRole> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    /** Aantal leden inclusief de eigenaar. */
    public int getMemberCount() {
        return members.size();
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    /** De rol van de speler; VISITOR als de speler geen lid is. */
    public IslandRole getRole(UUID player) {
        return members.getOrDefault(player, IslandRole.VISITOR);
    }

    /** Voegt een lid toe of wijzigt de rol. Gebruik setOwner voor OWNER. */
    public void setRole(UUID player, IslandRole role) {
        if (role == IslandRole.VISITOR) {
            removeMember(player);
            return;
        }
        if (role == IslandRole.OWNER) {
            setOwner(player);
            return;
        }
        if (player.equals(owner)) {
            return;
        }
        members.put(player, role);
        markDirty();
    }

    public void removeMember(UUID player) {
        if (player.equals(owner)) {
            return;
        }
        members.remove(player);
        markDirty();
    }

    // ------------------------------------------------------------------ bans

    public Set<UUID> getBanned() {
        return Collections.unmodifiableSet(banned);
    }

    public boolean isBanned(UUID player) {
        return banned.contains(player);
    }

    public void ban(UUID player) {
        banned.add(player);
        markDirty();
    }

    public void unban(UUID player) {
        banned.remove(player);
        markDirty();
    }

    // ------------------------------------------------------------------ instellingen

    public boolean getFlag(IslandFlag flag) {
        return flags.getOrDefault(flag, flag.getDefaultValue());
    }

    public void setFlag(IslandFlag flag, boolean value) {
        flags.put(flag, value);
        markDirty();
    }

    public Map<IslandFlag, Boolean> getFlags() {
        return Collections.unmodifiableMap(flags);
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        markDirty();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDirty();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // ------------------------------------------------------------------ upgrades en level

    public int getSizeLevel() {
        return sizeLevel;
    }

    public void setSizeLevel(int sizeLevel) {
        this.sizeLevel = sizeLevel;
        markDirty();
    }

    public int getMemberLevel() {
        return memberLevel;
    }

    public void setMemberLevel(int memberLevel) {
        this.memberLevel = memberLevel;
        markDirty();
    }

    public int getGeneratorLevel() {
        return generatorLevel;
    }

    public void setGeneratorLevel(int generatorLevel) {
        this.generatorLevel = generatorLevel;
        markDirty();
    }

    /** Het eilandlevel (berekend door de LevelManager). */
    public long getLevel() {
        return level;
    }

    public void setLevel(long level) {
        this.level = level;
        markDirty();
    }

    /** Totale blokwaarde (punten) waaruit het level is afgeleid. */
    public long getLevelPoints() {
        return levelPoints;
    }

    public void setLevelPoints(long levelPoints) {
        this.levelPoints = levelPoints;
        markDirty();
    }

    // ------------------------------------------------------------------ opslag

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    /** Alleen voor het laden uit opslag: ruwe homecoördinaten. */
    void loadHome(double x, double y, double z, float yaw, float pitch) {
        homeX = x;
        homeY = y;
        homeZ = z;
        homeYaw = yaw;
        homePitch = pitch;
    }

    /** Alleen voor het laden uit opslag: ruwe warpcoördinaten. */
    void loadWarp(double x, double y, double z, float yaw, float pitch, boolean enabled) {
        warpX = x;
        warpY = y;
        warpZ = z;
        warpYaw = yaw;
        warpPitch = pitch;
        warpEnabled = enabled;
    }

    /** Alleen voor het laden uit opslag. */
    void loadMember(UUID player, IslandRole role) {
        members.put(player, role);
    }

    /** Alleen voor het laden uit opslag. */
    void loadBan(UUID player) {
        banned.add(player);
    }

    /** Alleen voor het laden uit opslag. */
    void loadStats(int sizeLevel, int memberLevel, int generatorLevel, long level, long points, boolean locked) {
        this.sizeLevel = sizeLevel;
        this.memberLevel = memberLevel;
        this.generatorLevel = generatorLevel;
        this.level = level;
        this.levelPoints = points;
        this.locked = locked;
    }

    /** Alleen voor het laden uit opslag. */
    void loadFlag(IslandFlag flag, boolean value) {
        flags.put(flag, value);
    }

    double[] rawHome() {
        return new double[]{homeX, homeY, homeZ, homeYaw, homePitch};
    }

    double[] rawWarp() {
        if (warpX == null) {
            return null;
        }
        return new double[]{warpX, warpY, warpZ, warpYaw, warpPitch};
    }

    boolean rawWarpEnabled() {
        return warpEnabled;
    }
}
