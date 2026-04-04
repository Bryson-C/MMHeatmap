package org.bc.MMheatmap.poller;

import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import org.bc.MMheatmap.HeatmapDatabase;
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

    /**
     * Sets up the poller to be called every N seconds (set in the config)
     *
     * @param plugin The Bukkit plugin object
     * @param server The server object
     * @param database The HeatmapDatabase object
     * @param config The config file to set defaults about the poller
     *
     * @see HeatmapDatabase
     */
    public PlayerActivityPoller(Plugin plugin, Server server, HeatmapDatabase database, FileConfiguration config) {
        if (instance != null) return;

        PlayerActivityPoller.instance = this;
        PlayerActivityPoller.database = database;
        PlayerActivityPoller.pollFrequencySeconds = config.getInt("defaults.pollFrequencySeconds");

        // This is not called asyncronously because it will need to access other data, for thread safety, I will live this synchronized (for now)
        server.getScheduler().runTaskTimerAsynchronously(plugin, /* Lambda: */ task -> {
            // Do not do work if the poller was paused
            if (paused) return;

            var playerActions = PlayerActionListener.getPlayerActions();

            // Typically I would not recommend using "var", but god do I hate typing collection types
            for (var entry : playerActions.entrySet()) {
                String[] split = entry.getKey().split(";");

                if (split.length < 2) {
                    System.err.println("Failed Splitting Location Data; Skipping");
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
                    // FIXME: I THINK MY MATH IS OFF
                    int chunkX = (int)(x*areaSize) - (areaSize/-2);
                    int chunkY = (int)(y*areaSize) - (areaSize/-2);

                    Vector2d location = new Vector2d(chunkX, chunkY);

                    database.insertPlayerActivity(playerName, worldName, location, nameInteractionPair.getValue());

                    server.broadcast(Component.text(String.format("%s Interaction In %s: ", playerName, split[1])));
                    server.broadcast(Component.text(nameInteractionPair.getValue().toString()));
                }
            }

            PlayerActionListener.clearPlayerActions();

        } /* End of the lambda */, 0, Tick.tick().fromDuration(Duration.ofSeconds(pollFrequencySeconds)));

        server.getPluginManager().registerEvents(new PlayerActionListener(config), plugin);
    }
}
