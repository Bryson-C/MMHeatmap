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

        // save the config
        // save config so data can be fetched, only need to use this when I start using the config
        saveConfig();
        // it is vital that the config is initialized. Even if it only has default values, it should have SOMETHING.
        // without the database fields however, there is not much that can be done
        // TODO: Prevent Functioning Unless Database Is Verified To Be Connected i.e. Database Fields In Config Work
        new HeatmapConfig(getPlugin(this.getClass()));
        HeatmapConfig.applyConfig();
        
        // Register the dynmap listener to allow access to the API once it becomes available
        DynmapCommonAPIListener.register(new DynmapListener());

        // Create a database object
        HeatmapDatabase database = new HeatmapDatabase(getLogger());

        // Create a dynmap wrapper
        new DynWrapper(DynmapListener.getApi(), getLogger());

        // Enable Poller
        new PlayerActivityPoller(getPlugin(MMheatmap.class), getServer(), database);

        // Enable Command
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            // Command's variables are static, they don't need an object to pass around the command after it is created once...
            new HeatmapCommand(database, getPlugin(this.getClass()));
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
