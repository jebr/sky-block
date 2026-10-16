package nl.jebr.skyblock.player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persoonlijke gegevens van een speler. */
public class PlayerData {

    private final UUID uuid;
    private String name;
    private double balance;
    private UUID islandId;
    private int resetCount;
    private long lastReset;
    private final Map<String, Integer> challengeCompletions = new HashMap<>();

    private transient boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            markDirty();
        }
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
        markDirty();
    }

    /** Het eiland waarvan de speler lid of eigenaar is, of null. */
    public UUID getIslandId() {
        return islandId;
    }

    public void setIslandId(UUID islandId) {
        this.islandId = islandId;
        markDirty();
    }

    public int getResetCount() {
        return resetCount;
    }

    public void setResetCount(int resetCount) {
        this.resetCount = resetCount;
        markDirty();
    }

    public long getLastReset() {
        return lastReset;
    }

    public void setLastReset(long lastReset) {
        this.lastReset = lastReset;
        markDirty();
    }

    /** Hoe vaak de speler een challenge heeft voltooid (0 als nooit). */
    public int getChallengeCompletions(String challengeId) {
        return challengeCompletions.getOrDefault(challengeId.toLowerCase(), 0);
    }

    public void addChallengeCompletion(String challengeId) {
        challengeCompletions.merge(challengeId.toLowerCase(), 1, Integer::sum);
        markDirty();
    }

    public Map<String, Integer> getChallengeCompletions() {
        return Collections.unmodifiableMap(challengeCompletions);
    }

    public void resetChallenges() {
        challengeCompletions.clear();
        markDirty();
    }

    void loadChallenge(String id, int count) {
        challengeCompletions.put(id.toLowerCase(), count);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }
}
