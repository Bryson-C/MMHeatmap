package org.bc.MMheatmap;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bc.MMheatmap.poller.PlayerActivityPoller;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPIListener;


/**
 * The main plugin "entry point" (?), This enables all sub-systems required for the plugin to work.
 *
 * @author BC/Exo
 */
public final class MMheatmap extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic

        // save config so data can be fetched, only need to use this when I start using the config
        getPlugin(MMheatmap.class).saveConfig();

        // Register the dynmap listener to allow access to the API once it becomes available
        DynmapCommonAPIListener.register(new DynmapListener());

        // Create a database object
        HeatmapDatabase database = new HeatmapDatabase(getConfig());

        // Create a dynmap wrapper
        DynWrapper wrapper = new DynWrapper(DynmapListener.getApi(), getConfig());

        // Enable Poller
        new PlayerActivityPoller(getPlugin(MMheatmap.class), getServer(), database, getConfig());

        // Enable Command
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            // Command's variables are static, they don't need an object to pass around the command after it is created once...
            new HeatmapCommand(database, getConfig());
            // ^ this allows to just pass in the HeatmapCommand as a static class
            commands.registrar().register(HeatmapCommand.getBuiltCommand());
        });


    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        saveConfig();
    }
}
