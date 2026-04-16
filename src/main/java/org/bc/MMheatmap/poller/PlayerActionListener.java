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
import org.bukkit.event.player.*;
import org.dynmap.markers.PlayerSet;

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

    PlayerActionListener(FileConfiguration config) {
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
        System.out.println("ChunksSQ: " + chunksSq);
        // load weights so we aren't reading from files constantly
    }

    // Optimize memory, or make sure that memory doesn't get to insane
    static Map<String, Map<String, PlayerChunkInteractions>> playerActions = new HashMap<>();


    /**
     * NOTE: Not To Be Used In Production, Testing Code Only
     *
     */
    public static void generateFakePlayerData(int count, int minActivity, int maxActivity) {
        Random r = new Random();
        for (int i = 0; i < count; i++) {
            String key = String.format("%s;%d,%d", "world", r.nextInt(2500)-1250, r.nextInt(2500)-1250);
            String playerName = "debugplayer";

            if (!playerActions.containsKey(key)) {
                playerActions.put(key, new HashMap<>());
            }
            Map<String, PlayerChunkInteractions> map = playerActions.get(key);
            if (!map.containsKey(playerName)) {
                map.put(playerName, new PlayerChunkInteractions());
            }
            PlayerChunkInteractions actions = map.get(playerName);

            actions.activity = r.nextInt(maxActivity-minActivity)+minActivity;
        }
    }


    private static PlayerChunkInteractions getPlayerChunkInteractions(Chunk chunk, Player player) {
        // Im using x,y instead of x,z because the heatmap is 2d, so y is the vertical axis
        String key = String.format("%s;%d,%d", chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

        if (!playerActions.containsKey(key)) {
            playerActions.put(key, new HashMap<>());
        }
        Map<String, PlayerChunkInteractions> map = playerActions.get(key);
        if (!map.containsKey(player.getName())) {
            map.put(player.getName(), new PlayerChunkInteractions());
        }

        PlayerChunkInteractions actions = map.get(player.getName());
        return actions;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerPlace(BlockPlaceEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerPlaceWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerBreak(BlockBreakEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBlock().getChunk(), event.getPlayer());
        actions.activity += playerBreakWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        Location loc = event.getLocation();
        if (loc == null) return;
        PlayerChunkInteractions actions = getPlayerChunkInteractions(loc.getChunk(), event.getPlayer());
        actions.activity += playerSetSpawnWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerBedEnter(PlayerBedEnterEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBed().getLocation().getChunk(), event.getPlayer());
        actions.activity += playerBedEnterWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerLeaveBed(PlayerBedLeaveEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBed().getLocation().getChunk(), event.getPlayer());
        actions.activity += playerLeaveBedWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerChangeBeaconEffect(PlayerChangeBeaconEffectEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBeacon().getLocation().getChunk(), event.getPlayer());
        actions.activity += playerChangeBeaconEffectWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerChangedWorldEvent(PlayerChangedWorldEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerChangedWorldEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerFishEvent(PlayerFishEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerFishEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerFlowerPotManipulateEvent(PlayerFlowerPotManipulateEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerFlowerPotManipulateEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerArmorStandManipulateEvent(PlayerArmorStandManipulateEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerArmorStandManipulateEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerHarvestBlockEvent(PlayerHarvestBlockEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerHarvestBlockEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerInsertLecternEvent(PlayerInsertLecternBookEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBlock().getChunk(), event.getPlayer());
        actions.activity += playerInsertLecternEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerItemFrameChangeEvent(PlayerItemFrameChangeEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerItemFrameChangeEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerPurchaseEvent(PlayerPurchaseEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerPurchaseEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerShearBlockEvent(PlayerShearBlockEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getBlock().getChunk(), event.getPlayer());
        actions.activity += playerShearBlockEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerShearEntityEvent(PlayerShearEntityEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerShearEntityEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerTakeLecternBookEvent(PlayerTakeLecternBookEvent event) {
        PlayerChunkInteractions actions = getPlayerChunkInteractions(event.getPlayer().getChunk(), event.getPlayer());
        actions.activity += playerTakeLecternBookEventWeight;
        event.getPlayer().sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }


    public static Map<String, Map<String, PlayerChunkInteractions>> getPlayerActions() {
        return playerActions;
    }

    public static void clearPlayerActions() {
        playerActions.clear();
    }
}
