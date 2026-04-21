package org.bc.MMheatmap.poller;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import io.papermc.paper.event.player.*;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.dynmap.markers.PlayerSet;

import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * This class is used as the "Super Event Handler", its function is to listen to all events which would affect the heatmap's
 * activity level (such as block breaks and places, though more can be added).
 *
 * This class also is responsible for handling the area which the actions take place in.
 *
 *
 * FIXME: DO NOT USE 1x1 CHUNKS FOR LOCATION DATA, THIS IS A WASTE OF MEMORY BECAUSE WE USE A MAP FOR EACH "CHUNK",
 *        PREFERABLY USE 3x3 OR LARGER
 *        SINCE IM JUST TESTING, 1x1 WILL BE USED
 *
 * @author BC/EXO
 */
public class PlayerActionListener implements Listener {

    static FileConfiguration config;
    // The area to record over, so for instance, 1 means 1x1 chunks, 3 would mean a 3x3 chunk area
    static public int chunksSq;
    static public double playerPlaceWeight;
    static public double playerBreakWeight;
    static public double playerSetSpawnWeight;
    static public double playerBedEnterWeight;
    static public double playerLeaveBedWeight;
    static public double playerChangeBeaconEffectWeight;
    static public double playerChangedWorldEventWeight;
    static public double playerFishEventWeight;
    static public double playerFlowerPotManipulateEventWeight;
    static public double playerArmorStandManipulateEventWeight;
    static public double playerHarvestBlockEventWeight;
    static public double playerInsertLecternEventWeight;
    static public double playerItemFrameChangeEventWeight;
    static public double playerPurchaseEventWeight;
    static public double playerShearBlockEventWeight;
    static public double playerShearEntityEventWeight;
    static public double playerTakeLecternBookEventWeight;
    static public double playerInteractChestWeight;
    static public double playerInteractBarrelWeight;

    /**
     * Reads the config file for action weights and saves them in variables so that the file is not reading from disk frequently
     * @param config The config file with the action weights inside
     */
    PlayerActionListener(FileConfiguration config) {
        // load weights so we aren't reading from files constantly
        PlayerActionListener.chunksSq = config.getInt("defaults.pollAreaChunkSize");
        PlayerActionListener.playerPlaceWeight = config.getDouble("activityWeights.playerPlace");
        PlayerActionListener.playerBreakWeight = config.getDouble("activityWeights.playerBreak");
        PlayerActionListener.playerSetSpawnWeight = config.getDouble("activityWeights.playerSetSpawn");
        PlayerActionListener.playerBedEnterWeight = config.getDouble("activityWeights.playerBedEnter");
        PlayerActionListener.playerLeaveBedWeight = config.getDouble("activityWeights.playerLeaveBed");
        PlayerActionListener.playerChangeBeaconEffectWeight = config.getDouble("activityWeights.playerChangeBeaconEffect");
        PlayerActionListener.playerChangedWorldEventWeight = config.getDouble("activityWeights.playerChangedWorldEvent");
        PlayerActionListener.playerFishEventWeight = config.getDouble("activityWeights.playerFishEvent");
        PlayerActionListener.playerFlowerPotManipulateEventWeight = config.getDouble("activityWeights.playerFlowerPotManipulateEvent");
        PlayerActionListener.playerArmorStandManipulateEventWeight = config.getDouble("activityWeights.playerArmorStandManipulateEvent");
        PlayerActionListener.playerHarvestBlockEventWeight = config.getDouble("activityWeights.playerHarvestBlockEvent");
        PlayerActionListener.playerInsertLecternEventWeight = config.getDouble("activityWeights.playerInsertLecternEvent");
        PlayerActionListener.playerItemFrameChangeEventWeight = config.getDouble("activityWeights.playerItemFrameChangeEvent");
        PlayerActionListener.playerPurchaseEventWeight = config.getDouble("activityWeights.playerPurchaseEvent");
        PlayerActionListener.playerShearBlockEventWeight = config.getDouble("activityWeights.playerShearBlockEvent");
        PlayerActionListener.playerShearEntityEventWeight = config.getDouble("activityWeights.playerShearEntityEvent");
        PlayerActionListener.playerTakeLecternBookEventWeight = config.getDouble("activityWeights.playerTakeLecternBookEvent");
        PlayerActionListener.playerInteractChestWeight = config.getDouble("activityWeights.playerInteractChestEvent");
        PlayerActionListener.playerInteractBarrelWeight = config.getDouble("activityWeights.playerInteractBarrelEvent");
        PlayerActionListener.config = config;
    }

    // Optimize memory, or make sure that memory doesn't get to insane
    static Map<String, Map<String, PlayerChunkInteractions>> playerActions = new HashMap<>();


    /**
     * Generates fake player activity for testing the heatmap
     * Note: Data is not returned, to insert data to the database.
     * Another Note: because of the nature of the command, using a larger count may override older iterations of the internal loop,
     *               On the other hand, this is expected for random results -- this will be less of an issue with lower a count,
     *               or a larger surface area
     *
     * @param playerName The playername you want to insert the data under, should be unique compared to normal player names
     * @param world The world to add the data to, generally the world you are in
     * @param x1 Top left x point
     * @param y1 Top left y point
     * @param x2 Bottom right x point
     * @param y2 Bottom right y point
     * @param count How many data points to generate
     * @param minActivity minimum amount of activity that can be added for any data point
     * @param maxActivity maximum amount of activity that can be added for any data point
     * @return Returns a map of key ["world;x;y"] and sub key ["playerName"] and value of PlayerChunkInteractions
     */
    public static Map<String, Map<String, PlayerChunkInteractions>> generateFakePlayerData(String playerName, String world, int x1, int y1, int x2, int y2, int count, int minActivity, int maxActivity) {
        Random r = new Random();

        int chunkX1 = x1/16, chunkX2 = x2/16;
        int chunkY1 = y1/16, chunkY2 = y2/16;

        Map<String, Map<String, PlayerChunkInteractions>> map = new HashMap<>();

        for (int i = 0; i < count; i++) {
            String key = String.format("%s;%d,%d", world, r.nextInt(chunkX1, chunkX2), r.nextInt(chunkY1, chunkY2));

            if (!map.containsKey(key)) {
                map.put(key, new HashMap<>());
            }
            Map<String, PlayerChunkInteractions> m = map.get(key);
            if (!m.containsKey(playerName)) {
                m.put(playerName, new PlayerChunkInteractions(config));
            }

            PlayerChunkInteractions actions = m.get(playerName);
            actions.activity = r.nextInt(maxActivity-minActivity)+minActivity;
        }

        return map;
    }

    /**
     * Gets the chunk of an interaction from the `playerActions` field
     * @param chunk The chunk of the action
     * @param player The player who's doing said action
     * @return Returns a `PlayerChunkInteractions` object
     */
    private static PlayerChunkInteractions getPlayerChunkInteractions(Chunk chunk, Player player) {
        // Im using x,y instead of x,z because the heatmap is 2d, so y is the vertical axis
        String key = String.format("%s;%d,%d", chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

        if (!playerActions.containsKey(key)) {
            playerActions.put(key, new HashMap<>());
        }
        Map<String, PlayerChunkInteractions> map = playerActions.get(key);
        if (!map.containsKey(player.getName())) {
            map.put(player.getName(), new PlayerChunkInteractions(config));
        }

        PlayerChunkInteractions actions = map.get(player.getName());
        return actions;
    }

    // The following are all event listeners with weights: I will not be documenting them all

    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerPlace(InventoryOpenEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), (Player)event.getPlayer());
        if (event.getInventory().getType() == InventoryType.CHEST)
            actions.activity += playerInteractChestWeight;
        if (event.getInventory().getType() == InventoryType.BARREL)
            actions.activity += playerInteractBarrelWeight;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerPlace(BlockPlaceEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerPlaceWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerBreak(BlockBreakEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBlock().getChunk(), event.getPlayer());
        actions.activity += playerBreakWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        Location loc = event.getLocation();
        if (loc == null) return;
        PlayerChunkInteractions actions = getPlayerChunkInteractions(loc.getChunk(), event.getPlayer());
        actions.activity += playerSetSpawnWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerBedEnter(PlayerBedEnterEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBed().getLocation().getChunk(), event.getPlayer());
        actions.activity += playerBedEnterWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerLeaveBed(PlayerBedLeaveEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBed().getLocation().getChunk(), event.getPlayer());
        actions.activity += playerLeaveBedWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerChangeBeaconEffect(PlayerChangeBeaconEffectEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBeacon().getLocation().getChunk(), event.getPlayer());
        actions.activity += playerChangeBeaconEffectWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerChangedWorldEvent(PlayerChangedWorldEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerChangedWorldEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerFishEvent(PlayerFishEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerFishEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerFlowerPotManipulateEvent(PlayerFlowerPotManipulateEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerFlowerPotManipulateEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerArmorStandManipulateEvent(PlayerArmorStandManipulateEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerArmorStandManipulateEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerHarvestBlockEvent(PlayerHarvestBlockEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerHarvestBlockEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerInsertLecternEvent(PlayerInsertLecternBookEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBlock().getChunk(), event.getPlayer());
        actions.activity += playerInsertLecternEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerItemFrameChangeEvent(PlayerItemFrameChangeEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerItemFrameChangeEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerPurchaseEvent(PlayerPurchaseEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerPurchaseEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerShearBlockEvent(PlayerShearBlockEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBlock().getChunk(), event.getPlayer());
        actions.activity += playerShearBlockEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerShearEntityEvent(PlayerShearEntityEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerShearEntityEventWeight;
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerTakeLecternBookEvent(PlayerTakeLecternBookEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerTakeLecternBookEventWeight;
    }

    /**
     * Returns the `playerActions` map
     * @return Returns the `playerActions` map
     */
    public static Map<String, Map<String, PlayerChunkInteractions>> getPlayerActions() {
        return playerActions;
    }

    /**
     * Clears the `playerActions` map.
     * When inserting player activity into the database from the here. Make sure to clear out the `playerActions` map to not have duplicate data
     */
    public static void clearPlayerActions() {
        playerActions.clear();
    }
}
