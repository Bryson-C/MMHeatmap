package org.bc.MMheatmap.poller;

import org.bukkit.Chunk;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;

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

    PlayerActionListener(FileConfiguration config) {
        PlayerActionListener.chunksSq = config.getInt("defaults.pollAreaChunkSize");
        System.out.println("ChunksSQ: " + chunksSq);
    }

    // Optimize memory, or make sure that memory doesn't get to insane
    static Map<String, Map<String, PlayerChunkInteractions>> playerActions = new HashMap<>();

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
        Player player = event.getPlayer();
        Chunk blockChunk = event.getBlockPlaced().getChunk();

        PlayerChunkInteractions actions = getPlayerChunkInteractions(blockChunk, player);
        actions.places++;
        player.sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPlayerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk blockChunk = event.getBlock().getChunk();

        PlayerChunkInteractions actions = getPlayerChunkInteractions(blockChunk, player);
        actions.breaks++;
        player.sendRichMessage("<b>chunk interactions: <reset>" + actions.toRichMessage());
    }

    public static Map<String, Map<String, PlayerChunkInteractions>> getPlayerActions() {
        return playerActions;
    }

    public static void clearPlayerActions() {
        playerActions.clear();
    }
}
