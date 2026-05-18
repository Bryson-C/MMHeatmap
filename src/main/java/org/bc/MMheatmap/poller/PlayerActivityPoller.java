package org.bc.MMheatmap.poller;

import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import org.bc.MMheatmap.HeatmapCommand;
import org.bc.MMheatmap.HeatmapConfig;
import org.bc.MMheatmap.HeatmapDatabase;
import org.bc.MMheatmap.HeatmapLayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.time.Duration;


/**
 * Class containing the player poller. Will gather activity information over a set period of time (using PlayerActions class), then upload the results
 * to a database.
 *
 * Note: on creation this class, event listeners related to player activity will be created
 *
 * @see PlayerActionListener
 *
 * @author BC/Exo
 */
public class PlayerActivityPoller {
    private static PlayerActivityPoller instance = null;
    private static HeatmapDatabase database;
    private static boolean paused = false;
    private static int pollFrequencySeconds;
    private static int activelyPolledLayers;

    static public void pausePolling() { paused = true; }
    static public void resumePolling() { paused = false; }
    static public boolean isPaused() { return paused; }
    static public int getPollFrequencySeconds() { return pollFrequencySeconds; }
    static public int getActivelyPolledLayers() { return activelyPolledLayers; }

    /**
     * Sets up the poller to be called every N seconds (set in the config)
     *
     * @param plugin The Bukkit plugin object
     * @param server The server object
     * @param database The HeatmapDatabase object
     *
     * @see HeatmapDatabase
     */
    public PlayerActivityPoller(Plugin plugin, Server server, HeatmapDatabase database) {
        if (instance != null) return;

        PlayerActivityPoller.instance = this;
        PlayerActivityPoller.database = database;
        PlayerActivityPoller.pollFrequencySeconds = HeatmapConfig.getPollFrequencySeconds();

        // This is not called asyncronously because it will need to access other data, for thread safety, I will live this synchronized (for now)
        server.getScheduler().runTaskTimerAsynchronously(plugin, /* Lambda: */ task -> {
            // Do not do work if the poller was paused
            if (paused) return;


            var playerActions = PlayerActionListener.getPlayerActions();

            // Typically I would not recommend using "var", but god do I hate typing collection types
            for (var entry : playerActions.entrySet()) {
                String[] split = entry.getKey().split(";");

                if (split.length < 2) {
                    // System.err.println("Failed Splitting Location Data; Skipping");
                    continue;
                }

                String worldName = split[0];
                String[] chunkStr = split[1].split(",");
                for (var nameInteractionPair : entry.getValue().entrySet()) {
                    String playerName = nameInteractionPair.getKey();

                    int areaSize = PlayerActionListener.chunksSq * 16;
                    double x = Double.parseDouble(chunkStr[0]);
                    double y = Double.parseDouble(chunkStr[1]);

                    // Chunk to coords
                    int chunkX = (int)(x*areaSize) - (areaSize/-2);
                    int chunkY = (int)(y*areaSize) - (areaSize/-2);

                    Vector2d location = new Vector2d(chunkX, chunkY);

                    // upload all activity
                    database.insertPlayerActivity(playerName, worldName, location, nameInteractionPair.getValue());
                }
            }
            activelyPolledLayers = 0;
            int dontPollSeconds = HeatmapConfig.getNoUpdatePoolRangeSeconds();
            for (HeatmapLayer layer : database.getHeatmapLayers().values()) {
                // If a layer is set to not be polled, skip over it
                if (layer.getPollRangeSeconds() == dontPollSeconds) continue;

                activelyPolledLayers++;
                HeatmapCommand.pollHeatmapCommandFunction(server.getConsoleSender(), layer.getLabel(), null, null);
            }

            // clear activity over the period of time
            PlayerActionListener.clearPlayerActions();

        } /* End of the lambda */, 0, Tick.tick().fromDuration(Duration.ofSeconds(pollFrequencySeconds)));

        server.getPluginManager().registerEvents(new PlayerActionListener(), plugin);
    }
}
