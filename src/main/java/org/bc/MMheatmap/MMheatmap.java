package org.bc.MMheatmap;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapCommonAPIListener;


/**
 * @author BC/Exo
 */
public final class MMheatmap extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic

        // Register the dynmap listener to allow access to the API once it becomes available
        DynmapCommonAPIListener.register(new DynmapListener());

        // Enable Command
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            // Command's variables are static, they don't need an object to pass around the command after it is created once...
            new HeatmapCommand(DynmapListener.getApi());
            // ^ this allows to just pass in the HeatmapCommand as a static class
            commands.registrar().register(HeatmapCommand.getBuiltCommand());
        });

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
