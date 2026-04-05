package org.bc.MMheatmap;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bc.MMheatmap.poller.PlayerActivityPoller;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.dynmap.markers.MarkerSet;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * A class containing all the commands used to control the heatmap
 *
 * NOTE:
 *  The "PluginBootstrap" interface is declared as experimental, however, papermc documentation
 *  uses this inside their documentation under their "Command API" section
 *
 *  @author BC/Exo
 */
public class HeatmapCommand {
    private static LiteralCommandNode<CommandSourceStack> builtCommand;
    private static DynWrapper dynWrapper;
    private static HeatmapDatabase database;
    private static FileConfiguration config;

    /**
     * Builds the full "/mmheatmap" command tree and initializes the Dynmap API and Database for the rest of the class to use.
     */
    public HeatmapCommand(DynWrapper wrapper, HeatmapDatabase database, FileConfiguration config) {
        // Initializes the Dynmap wrapper object to simplify Dynmap Api calls
        HeatmapCommand.dynWrapper = wrapper;
        // Sets a database object to be used within some commands
        HeatmapCommand.database = database;
        // Sets the config file from the plugin to be used in the commands
        HeatmapCommand.config = config;

        // Command root
        LiteralArgumentBuilder<CommandSourceStack> commandRoot = Commands.literal("mmheatmap");

        // Larger Commands

        // Help Command
        LiteralArgumentBuilder<CommandSourceStack> helpCommand = Commands.literal("help")
            .executes(context -> {
                CommandSender sender = context.getSource().getSender();

                sender.sendRichMessage("<b>Command: /mmheatmap <reset><yellow>(to be added later)");

                return Command.SINGLE_SUCCESS;
            });

        // Divide World Command
        LiteralArgumentBuilder<CommandSourceStack> divideWorld = Commands.literal("divideWorld")
            .then(Commands.argument("name", StringArgumentType.string())
                .then(Commands.argument("topleftpos", ArgumentTypes.blockPosition())
                    .then(Commands.argument("bottomrightpos", ArgumentTypes.blockPosition())
                        .then(Commands.argument("divisioncountsq", IntegerArgumentType.integer(1))
                            .executes(context -> {

                                String heatmapName = StringArgumentType.getString(context, "name");

                                // parse position from command arguments
                                BlockPositionResolver resolver = context.getArgument("topleftpos", BlockPositionResolver.class);
                                BlockPosition pos1 = resolver.resolve(context.getSource());
                                Vector2d xyz1 = new Vector2d(pos1.x(),pos1.z());

                                BlockPositionResolver resolver2 = context.getArgument("bottomrightpos", BlockPositionResolver.class);
                                BlockPosition pos2 = resolver2.resolve(context.getSource());
                                Vector2d xyz2 = new Vector2d(pos2.x(),pos2.z());

                                int divisionCount = IntegerArgumentType.getInteger(context, "divisioncountsq");

                                CommandSender sender = context.getSource().getSender();

                                String world = (sender instanceof Player player) ? player.getWorld().getName() : config.getString("defaults.world_name");

                                long startTime = System.nanoTime();

                                try {
                                    try {
                                        int pollRangeSeconds = config.getInt("defaults.pollRangeSeconds");
                                        database.insertNewHeatmapLayer(new HeatmapLayer(heatmapName, heatmapName, xyz1, xyz2, divisionCount, world, pollRangeSeconds));

                                        sender.sendRichMessage("<blue> Dividing World");
                                        MarkerSet set = DynWrapper.getAreaSetOrCreate(heatmapName);

                                        // Make sure this is on a different thread
                                        // TODO: call DynWrapper#createActiveCellsFromCoords(...)


                                    } catch (HeatmapDatabase.DuplicateLayerException dupE) {
                                        sender.sendRichMessage("<red>Heatmap layer \""+heatmapName+"\" already exists, use new name; or delete, then recreate heatmap layer");
                                        // This is a success because it functions as intended
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    catch (Exception e) {
                                        sender.sendRichMessage("<red>Unknown Error Occurred Trying To Create Layer \"" +heatmapName+"\"");
                                    }

                                } catch (Exception e) {
                                    System.err.printf("Failed To Create Marker: %s\n", e.getMessage());
                                    sender.sendRichMessage("<red>Failed Creating Dynmap Marker; See Console");
                                    return -1;
                                }

                                sender.sendRichMessage("Created Heatmap <blue><b>"+heatmapName+"<reset>(<color:#30f000>" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms<reset>)");
                                sender.sendRichMessage("Heatmap <blue><b>"+heatmapName+"<reset> Will Update Frequently, To Change This See: <b>/mmheatmap modify...");

                                return Command.SINGLE_SUCCESS;

                            })
                        )
                    )
                )
            );

        // Deletion Command
        LiteralArgumentBuilder<CommandSourceStack> deleteLayer = Commands.literal("delete")
            .then(Commands.argument("name", StringArgumentType.string())
            .suggests((ctx, builder) -> {
                    database.getHeatmapLayers().entrySet().stream()
                            .filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase()))
                            .forEach(x->builder.suggest(x.getKey()));
                    return builder.buildFuture();
                })
                .executes(context -> {

                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<blue> Deleting Heatmap Overlay");

                    long startTime = System.nanoTime();

                    String layerName = StringArgumentType.getString(context, "name");

                    // delete here
                    DynWrapper.deleteAreaSet(layerName);
                    try {
                        database.deleteHeatmapLayer(layerName);
                        sender.sendRichMessage("<color:#30f000> Done! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms)");
                    } catch (HeatmapDatabase.NoSuchLayerException e) {
                        sender.sendRichMessage("<red>Layer \"" +layerName+ "\" Does Not Exist");
                        // Success since command functioned as intended
                        return Command.SINGLE_SUCCESS;
                    } catch (Exception e) {
                        sender.sendRichMessage("<red>Unknown Error Occurred Trying To Delete Layer \"" +layerName+ "\"");
                    }

                    return Command.SINGLE_SUCCESS;
                })
            );

        // polling command
        LiteralArgumentBuilder<CommandSourceStack> pollCommand = Commands.literal("poll")
            .then(Commands.literal("pollLayer")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests((ctx, builder) -> {
                        database.getHeatmapLayers().entrySet().stream()
                                .filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                .forEach(x->builder.suggest(x.getKey()));
                        return builder.buildFuture();
                    }).then(Commands.argument("arguments", StringArgumentType.greedyString())
                        // this branch is for when arguments are passed into the function
                        .executes(context -> {
                            String args = StringArgumentType.getString(context, "arguments");
                            String layerName = StringArgumentType.getString(context, "name");
                            return pollHeatmapCommandFunction(context.getSource().getSender(), layerName, args);
                        })
                    // this branch is for when no arguments are provided
                    ).executes(context -> {
                        String layerName = StringArgumentType.getString(context, "name");
                        return pollHeatmapCommandFunction(context.getSource().getSender(), layerName, "");
                    })
                )
            ).then(Commands.literal("pause").executes(context -> {
                    PlayerActivityPoller.pausePolling();
                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<b>Pausing Heatmap Polling");
                    return Command.SINGLE_SUCCESS;
                })
            ).then(Commands.literal("resume").executes(context -> {
                    PlayerActivityPoller.resumePolling();
                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<b>Resuming Heatmap Polling");
                    return Command.SINGLE_SUCCESS;
                })
            ).then(Commands.literal("info").executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<b>Latest Heatmap Polling Info:");
                    sender.sendRichMessage("<b><blue>========================");
                    sender.sendRichMessage("   Layers Polled: <green>" + PlayerActivityPoller.getActivelyPolledLayers());
                    sender.sendRichMessage("   Poll Frequency: <green>" + PlayerActivityPoller.getPollFrequencySeconds() + " seconds");
                    sender.sendRichMessage("   Paused: <green>" + PlayerActivityPoller.isPaused());
                    return Command.SINGLE_SUCCESS;
                })
            );

        // Modify Command
        LiteralArgumentBuilder<CommandSourceStack> modifyCommand = Commands.literal("modify")
            .then(Commands.argument("name", StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    database.getHeatmapLayers().entrySet().stream()
                            .filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase()))
                            .forEach(x->builder.suggest(x.getKey()));
                    return builder.buildFuture();
                }).then(Commands.argument("arguments", StringArgumentType.greedyString())
                    // this branch is for when arguments are passed into the function
                    .executes(context -> {
                        String layerName = StringArgumentType.getString(context, "name");
                        String args = StringArgumentType.getString(context, "arguments");
                        CommandSender sender = context.getSource().getSender();

                        sender.sendRichMessage("<green><b>TODO: ADD MODIFY COMMAND");
                        sender.sendRichMessage("<b>Args: <reset>"+args);

                        return Command.SINGLE_SUCCESS;
                    })
                )
            );

        // Get/List Heatmap Layers
        LiteralArgumentBuilder<CommandSourceStack> getHeatmapLayers = Commands.literal("heatmapLayers")
            .executes(context -> {

                CommandSender sender = context.getSource().getSender();

                long startTime = System.nanoTime();

                Map<String, HeatmapLayer> layers = database.getHeatmapLayers();
                if (!layers.isEmpty()) {
                    sender.sendRichMessage("<u><b>Heatmap Layers:");
                    layers.forEach((name,l) -> {
                        String message = String.format(
                                " - <blue><b> %s <reset>(id: <blue><b>%s<reset>) -- <color:#ff00f9>(%.2f %.2f)<reset> to <color:#ff00f9>(%.2f, %.2f) <reset> in world <color:#30f000><b>%s",
                                l.label, l.id,
                                l.topLeft.x, l.topLeft.y,
                                l.bottomRight.x,l.bottomRight.y,
                                l.world
                        );
                        // TODO: Strike through text if the world is not the current world
                        sender.sendRichMessage(message);
                    });
                    sender.sendRichMessage("Query Took <reset>(<color:#30f000>" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms<reset>)");
                } else {
                    sender.sendRichMessage("No heatmap layers found!");
                    // Success because this functions as its supposed to
                    return Command.SINGLE_SUCCESS;
                }

                return Command.SINGLE_SUCCESS;
            });// Get/List Heatmap Layers


        // Resync Heatmap Layers With Database
        LiteralArgumentBuilder<CommandSourceStack> resyncCommand = Commands.literal("resync")
            .executes(context -> {
                CommandSender sender = context.getSource().getSender();

                sender.sendRichMessage("<blue>Resyncing heatmap layers with database");
                long startTime = System.nanoTime();
                database.resyncHeatmapLayers();
                sender.sendRichMessage("<color:#30f000> Done! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms)");

                return Command.SINGLE_SUCCESS;
            });

        // Append subtrees
        LiteralArgumentBuilder<CommandSourceStack> createHeatmap = Commands.literal("create");
        createHeatmap.then(divideWorld);
        commandRoot.then(createHeatmap);

        LiteralArgumentBuilder<CommandSourceStack> getCommand = Commands.literal("get");
        getCommand.then(getHeatmapLayers);

        commandRoot.then(getCommand);
        commandRoot.then(deleteLayer);
        commandRoot.then(pollCommand);
        commandRoot.then(resyncCommand);
        commandRoot.then(modifyCommand);
        commandRoot.then(helpCommand);

        // Build command
        builtCommand = commandRoot.build();
    }

    /**
     * Returns the command that was built in the constructor, this function itself has no additional functionality other
     * than being a getter
     *
     * @return Returns the command that was built in the constructor
     */
    public static LiteralCommandNode<CommandSourceStack> getBuiltCommand() {
        return builtCommand;
    }

    /**
     * This function is to be used internally in this class, and passed to the "/mmheatmap poll" command branch.
     * The context comes from the ".execute(...)" part of the command tree. Args must be passed in as they will not
     * be gotten from the inside of this function.
     *
     * @param sender
     * @param layerName
     * @param args A greedy string of args formatted as such: "key:value key2:value2 ... keyN:valueN".
     *             An empty string may be used if no arguments are needed
     * @return returns the command success code, generally SINGLE_SUCCESS (or 1)
     */
    static public int pollHeatmapCommandFunction(CommandSender sender, String layerName, String args) {
        String[] argKVPairs = args.split(" ");

        for (var kv : argKVPairs) {
            String[] split = kv.split(":");
            if (split.length >= 2)
                sender.sendRichMessage("<green><b>" + split[0] + "<reset>: " + split[1]);
        }


        Runnable run = ()->{
            long startTime = System.nanoTime();

            HeatmapLayer layer = database.getHeatmapLayers().get(layerName);

            // recreate set
            MarkerSet set = DynWrapper.getAreaSetOrCreate(layerName);

            DynWrapper.createActiveHeatmapCellsFromCoords(layer, set, database.getPlayerActivityEntriesForLayer("player", layer), "");

            if (config.getBoolean("defaults.logPollTime"))
                sender.sendRichMessage("<color:#30f000> Heatmap Polling Completed! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms)");
        };

        new Thread(run).start();

        return Command.SINGLE_SUCCESS;
    }
}
