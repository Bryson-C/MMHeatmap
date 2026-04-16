package org.bc.MMheatmap;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bc.MMheatmap.poller.PlayerActivityPoller;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.dynmap.markers.MarkerSet;
import org.joml.Vector2d;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class containing all the commands used to control the heatmap
 *
 * NOTE:
 *  The "PluginBootstrap" interface is declared as experimental, however, papermc documentation
 *  uses this inside their documentation under their "Command API" section
 *
 * TODO: Add "pollRegion" subcommand -- polls only a certain portion of the map, not the whole thing
 *
 *  @author BC/Exo
 */
public class HeatmapCommand {
    private static LiteralCommandNode<CommandSourceStack> builtCommand;
    private static HeatmapDatabase database;
    private static FileConfiguration config;

    /**
     * Builds the full "/mmheatmap" command tree and initializes the Dynmap API and Database for the rest of the class to use.
     *
     * The code is going to be very horizontal as a result of chaining many many features on a command builder, so Im using 1 line per command feature
     * (i.e.
     */
    public HeatmapCommand(HeatmapDatabase database, FileConfiguration config) {
        // Sets a database object to be used within some commands
        HeatmapCommand.database = database;
        // Sets the config file from the plugin to be used in the commands
        HeatmapCommand.config = config;

        // Command root
        LiteralArgumentBuilder<CommandSourceStack> commandRoot = Commands.literal("mmheatmap");

        // Larger Commands

        // Info/Help Command
        LiteralArgumentBuilder<CommandSourceStack> infoCommand = Commands.literal("info")
            .requires(sender -> sender.getSender().hasPermission("mmheatmap.help"))
            .executes(context -> {
                CommandSender sender = context.getSource().getSender();

                sender.sendRichMessage("<b>Command: /mmheatmap <reset><yellow>(to be added later)");

                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.literal("pollInfo")
                .requires(sender -> sender.getSender().hasPermission("mmheatmap.pollInfo"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<b>Latest Heatmap Polling Info:");
                    sender.sendRichMessage("<b><blue>========================");
                    sender.sendRichMessage("   Layers Polled: <green>" + PlayerActivityPoller.getActivelyPolledLayers());
                    sender.sendRichMessage("   Poll Frequency: <green>" + PlayerActivityPoller.getPollFrequencySeconds() + " seconds");
                    sender.sendRichMessage("   Paused: <green>" + PlayerActivityPoller.isPaused());
                    return Command.SINGLE_SUCCESS;
                })
            ).then(Commands.literal("layerInfo")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests((ctx, builder) -> { database.getHeatmapLayers().entrySet().stream().filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(x->builder.suggest(x.getKey())); return builder.buildFuture();})
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.layerInfo"))
                        .executes(context -> {
                            Runnable r = () -> {
                                HeatmapLayer layer = database.getHeatmapLayers().get(StringArgumentType.getString(context, "name"));

                                CommandSender sender = context.getSource().getSender();
                                sender.sendRichMessage("<b>Layer: <blue>" + layer.getLabel());
                                sender.sendRichMessage("<b><blue>========================");
                                sender.sendRichMessage("<b>World: <reset><green>" + layer.world);
                                sender.sendRichMessage("<b>Range: <reset><green>" + String.format("[%.0f, %.0f] -> [%.0f, %.0f]", layer.topLeft.x, layer.topLeft.y, layer.bottomRight.x, layer.bottomRight.y));
                                sender.sendRichMessage("<b>Divisions: <reset><green>" + layer.divisions);
                                if (layer.pollRangeSeconds == config.getInt("defaults.noUpdatePollRangeSeconds")) {
                                    String dateString = database.executeSql((connection) -> {
                                        try {
                                            PreparedStatement stmt = connection.prepareStatement("SELECT `fromToDate` FROM `heatmap_layers` WHERE `dyn_id` = ? AND `dyn_label` = ?");
                                            stmt.setString(1, layer.id);
                                            stmt.setString(2, layer.label);

                                            ResultSet set = stmt.executeQuery();
                                            // TODO: Consider Making This Function Return A List
                                            while (set.next()) {
                                                String str = set.getString("fromToDate");
                                                if (!str.isEmpty())
                                                    return str;
                                            }
                                        } catch (Exception e) {
                                            sender.sendRichMessage("<red> Error Getting Date Range Of Layer <b>" + layer.label);
                                        }
                                        return "";
                                    });
                                    try {
                                        String[] dates = dateString.split(",");
                                        sender.sendRichMessage("<b>Date Range: <reset><green>" + String.format("%s -> %s", dates[0], dates[1]));
                                    } catch (Exception e) {
                                        sender.sendRichMessage("<b>Date Range: <red>Error Getting Data");
                                        sender.sendRichMessage("<red>" + e.getMessage());
                                    }

                                } else {
                                    sender.sendRichMessage("<b>Poll Range (seconds): <reset><green>" + layer.pollRangeSeconds);
                                }
                                sender.sendRichMessage("<b>Activity (min-max): <reset><green>" + layer.minActivity + "; " + layer.maxActivity);
                                sender.sendRichMessage("<b><blue>========================");
                            };
                            new Thread(r).start();
                            return Command.SINGLE_SUCCESS;
                        })
                )
            );

        // Divide World Command
        LiteralArgumentBuilder<CommandSourceStack> divideWorld = Commands.literal("divideWorld")
            .then(Commands.argument("name", StringArgumentType.string())
                .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer()).then(Commands.argument("y2", IntegerArgumentType.integer())
                    .then(Commands.argument("divisioncountsq", IntegerArgumentType.integer(1))
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.divideWorld"))
                        // Default time period branch
                        .executes(context -> {
                            // Get Command Parameters
                            CommandSender sender = context.getSource().getSender();
                            String layername = StringArgumentType.getString(context, "name");
                            divideWorldCommandFunction(
                                    layername,
                                    sender,
                                    new Vector2d(IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "y1")),
                                    new Vector2d(IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "y2")),
                                    IntegerArgumentType.getInteger(context, "divisioncountsq"),
                                    config.getInt("defaults.pollRangeSeconds"),
                                    "", ""
                            );

                            pollHeatmapCommandFunction(sender, layername, null, null);

                            return Command.SINGLE_SUCCESS;
                        })
                        // custom time period branch
                        .then(Commands.argument("relativetimeperiod", StringArgumentType.string())
                            .requires(sender -> sender.getSender().hasPermission("mmheatmap.divideWorld"))
                            .executes(context -> {

                                CommandSender sender = context.getSource().getSender();
                                String timeString = StringArgumentType.getString(context, "relativetimeperiod");

                                long pollRangeSeconds = parseTimeStringToSeconds(timeString);
                                if (pollRangeSeconds == 0) {
                                    sender.sendRichMessage("<red>Failed Parsing Time String; Try Formats:");
                                    sender.sendRichMessage("\"2w,5d,7h,2m,10s\"");
                                    sender.sendRichMessage("\"2w5d7h2m10s\"");
                                    sender.sendRichMessage("\"5d2h\"");
                                    sender.sendRichMessage("\"2.50h\"");
                                    return -1;
                                }

                                String layername = StringArgumentType.getString(context, "name");
                                divideWorldCommandFunction(
                                    layername,
                                    sender,
                                    new Vector2d(IntegerArgumentType.getInteger(context,"x1"), IntegerArgumentType.getInteger(context, "y1")),
                                    new Vector2d(IntegerArgumentType.getInteger(context,"x2"), IntegerArgumentType.getInteger(context, "y2")),
                                    IntegerArgumentType.getInteger(context, "divisioncountsq"),
                                    (int)pollRangeSeconds,
                                    "",
                                    ""
                                );
                                pollHeatmapCommandFunction(sender, layername, null, null);

                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )))
            )
        );

        // Divide World No Update
        LiteralArgumentBuilder<CommandSourceStack> divideWorldNoUpdate = Commands.literal("divideWorldNoUpdate")
            .then(Commands.argument("name", StringArgumentType.string())
                .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer()).then(Commands.argument("y2", IntegerArgumentType.integer())
                    .then(Commands.argument("divisioncountsq", IntegerArgumentType.integer(1))
                        .then(Commands.argument("startdate", StringArgumentType.string())
                            .then(Commands.argument("enddate", StringArgumentType.string())
                                .requires(sender -> sender.getSender().hasPermission("mmheatmap.divideWorld"))
                                .executes(context -> {

                                    CommandSender sender = context.getSource().getSender();

                                    String startdate = StringArgumentType.getString(context, "startdate");
                                    String enddate = StringArgumentType.getString(context, "enddate");
                                    String layername = StringArgumentType.getString(context, "name");

                                    divideWorldCommandFunction(
                                            layername,
                                            sender,
                                            new Vector2d(IntegerArgumentType.getInteger(context,"x1"), IntegerArgumentType.getInteger(context, "y1")),
                                            new Vector2d(IntegerArgumentType.getInteger(context,"x2"), IntegerArgumentType.getInteger(context, "y2")),
                                            IntegerArgumentType.getInteger(context, "divisioncountsq"),
                                            false,
                                            startdate,
                                            enddate
                                    );

                                    pollHeatmapCommandFunction(sender, layername, startdate, enddate);

                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                    )
                )))
            )
        );

        // Deletion Command
        LiteralArgumentBuilder<CommandSourceStack> deleteLayer = Commands.literal("delete")
            .then(Commands.literal("layer")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests((ctx, builder) -> { database.getHeatmapLayers().entrySet().stream().filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(x->builder.suggest(x.getKey())); return builder.buildFuture();})
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.delete.layer"))
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
                    )
                )
                .then(Commands.literal("playerActivity")
                    .then(Commands.argument("arguments", StringArgumentType.string()))
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.delete.playerActivity"))
                        .executes(context -> {
                            // TODO: Implement
                            // @see HeatmapDatabase#deletePlayerActivity(String playerName, String world)
                            return Command.SINGLE_SUCCESS;
                        })
                );

        // polling command
        LiteralArgumentBuilder<CommandSourceStack> pollCommand = Commands.literal("poll")
            .then(Commands.literal("pollLayer")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests((ctx, builder) -> { database.getHeatmapLayers().entrySet().stream().filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(x->builder.suggest(x.getKey())); return builder.buildFuture();})
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.pollLayer"))
                        // this branch is for when no arguments are provided
                        .executes(context -> {
                            String layerName = StringArgumentType.getString(context, "name");
                            return pollHeatmapCommandFunction(context.getSource().getSender(), layerName, null, null);
                        })
                )
            )
            .then(Commands.literal("pollArea")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests((ctx, builder) -> { database.getHeatmapLayers().entrySet().stream().filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(x->builder.suggest(x.getKey())); return builder.buildFuture();})
                    .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer()).then(Commands.argument("y2", IntegerArgumentType.integer())
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.pollArea"))
                        .executes(context -> {

                            String layername = StringArgumentType.getString(context, "name");
                            HeatmapLayer layer = database.getHeatmapLayers().get(layername);
                            CommandSender sender = context.getSource().getSender();

                            // get the cells which the area lies over
                            int[] indices1 = DynWrapper.getDividedWorldCellFromPosition(
                                layer.topLeft, layer.bottomRight,  layer.divisions,
                                new Vector2d(IntegerArgumentType.getInteger(context,"x1"), IntegerArgumentType.getInteger(context, "y1"))
                            );
                            int[] indices2 = DynWrapper.getDividedWorldCellFromPosition(
                                layer.topLeft, layer.bottomRight,  layer.divisions,
                                new Vector2d(IntegerArgumentType.getInteger(context,"x2"), IntegerArgumentType.getInteger(context, "y2"))
                            );

                            // then get the top left of the cell furthest to the left
                            int minCellIndexX = Math.min(indices1[0], indices2[0]);
                            int maxCellIndexX = Math.max(indices1[0], indices2[0]);
                            // and the bottom right of the cell furthest to the right
                            int minCellIndexY = Math.min(indices1[1], indices2[1]);
                            int maxCellIndexY = Math.max(indices1[1], indices2[1]);

                            // get coords in an easier format
                            double x1 = layer.topLeft.x(), z1 = layer.topLeft.y();
                            double x2 = layer.bottomRight.x(), z2 = layer.bottomRight.y();

                            // use math to get total size of area
                            double width = Math.abs(x1-x2);
                            double height = Math.abs(z1-z2);

                            // get the amount of space each tile should take in the full area
                            double cellSizeWidth = (width/layer.divisions);
                            double cellSizeHeight = (height/layer.divisions);

                            double[] xy1 = new double[]{(minCellIndexX*cellSizeWidth)-(width/2), ((minCellIndexY*cellSizeWidth) + cellSizeWidth)-(width/2)};
                            double[] xy2 = new double[]{((maxCellIndexX*cellSizeHeight)-(height/2)), (((maxCellIndexY*cellSizeHeight) + cellSizeHeight))-(height/2)};

                            System.out.printf("Cell Range: [%d, %d] -> [%d, %d]\n", indices1[0], indices1[1], indices2[0], indices2[1]);
                            System.out.printf("Cell Range Blocks: [%.0f, %.0f] -> [%.0f, %.0f]\n", xy1[0], xy2[0], xy1[1], xy2[1]);
                            pollHeatmapArea(
                                sender,
                                layername,
                                new Vector2d(xy1[0], xy1[1]),
                                new Vector2d(xy2[0], xy2[1])
                            );

                            return Command.SINGLE_SUCCESS;
                        })
                    ))))
                )
            )
            .then(Commands.literal("pause").requires(sender -> sender.getSender().hasPermission("mmheatmap.poll.pause"))
                .executes(context -> {
                    PlayerActivityPoller.pausePolling();
                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<b>Pausing Heatmap Polling");
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(Commands.literal("resume").requires(sender -> sender.getSender().hasPermission("mmheatmap.poll.resume"))
                .executes(context -> {
                    PlayerActivityPoller.resumePolling();
                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<b>Resuming Heatmap Polling");
                    return Command.SINGLE_SUCCESS;
            })
            );

        // FIXME: Create warning message about layers being too dense for small areas (thereby creating points, rather than cells)
        //        I simply dont know the math behind this yet, so its not implemented
        // Modify Command
        LiteralArgumentBuilder<CommandSourceStack> modifyCommand = Commands.literal("modify")
            .then(Commands.argument("name", StringArgumentType.string())
                .suggests((ctx, builder) -> {database.getHeatmapLayers().entrySet().stream().filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(x->builder.suggest(x.getKey()));return builder.buildFuture();})
                .then(Commands.literal("points")
                    .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer()).then(Commands.argument("y2", IntegerArgumentType.integer())
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.modify"))
                        .executes(context -> {
                            Runnable r = () -> {
                                String layerName = StringArgumentType.getString(context, "name");
                                CommandSender sender = context.getSource().getSender();

                                HeatmapLayer layer = database.getHeatmapLayers().get(layerName);

                                // this branch needs to be handled differently because of the nature of not updating
                                if (layer.getPollRangeSeconds() == config.getInt("defaults.noUpdatePollRangeSeconds")) {
                                    sender.sendRichMessage("<yellow>Heatmap layer is set not to update. Manual date re-entry will be required");
                                    return;
                                }

                                int[] pos = {
                                        IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "y1"),
                                        IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "y2")
                                };

                                // Modify Layer Points
                                layer.topLeft = new Vector2d(pos[0], pos[1]);
                                layer.bottomRight = new Vector2d(pos[2], pos[3]);

                                database.executeSql((connection)->{
                                    try {
                                        PreparedStatement statement = connection.prepareStatement("UPDATE `heatmap_layers`SET `point_one_coords`= ?,`point_two_coords`= ? WHERE `dyn_id` = ? AND `dyn_label` = ?;");
                                        statement.setString(1, HeatmapLayer.vec2dToString(layer.topLeft));
                                        statement.setString(2, HeatmapLayer.vec2dToString(layer.bottomRight));
                                        statement.setString(3, layer.id);
                                        statement.setString(4, layer.label);

                                        statement.execute();
                                    } catch (Exception e) {
                                        sender.sendRichMessage("<red>Failed To Update Heatmap Layer In The Database");
                                        sender.sendRichMessage(e.getMessage());
                                    }
                                    // return type does not matter here
                                    return null;
                                });

                                // Recreate cells
                                // first, delete old cells to avoid duplication errors (also we simply don't need them)
                                DynWrapper.deleteAreaSet(layerName);
                                // recreate set after deletion
                                MarkerSet layerSet = DynWrapper.getAreaSetOrCreate(layerName);
                                // redivide world
                                DynWrapper.divideWorld(layerSet, layer.world, layer.topLeft, layer.bottomRight, layer.divisions);

                                pollHeatmapCommandFunction(sender, layerName, null, null);
                                sender.sendRichMessage("<green>Modified <blue><b>" + layerName + "<reset><green> to: " + String.format("[%d, %d] -> [%d, %d]", pos[0], pos[1], pos[2], pos[3]));
                            };

                            new Thread(r).start();

                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    )))
                )
                .then(Commands.literal("dateRange")
                    .then(Commands.argument("startdate", StringArgumentType.string())
                        .then(Commands.argument("enddate", StringArgumentType.string())
                            .requires(sender -> sender.getSender().hasPermission("mmheatmap.modify"))
                            .executes(context -> {

                                String layerName = StringArgumentType.getString(context, "name");
                                CommandSender sender = context.getSource().getSender();

                                String startdate = StringArgumentType.getString(context, "startdate");
                                String enddate = StringArgumentType.getString(context, "enddate");

                                if (!isValidDateString(startdate)) {
                                    sender.sendRichMessage("<red>Argument \"startdate\" Has Invalid Date Format, Format As Follows: \"yyyy-mm-dd hh:mm:ss\"");
                                    return Command.SINGLE_SUCCESS;
                                }
                                if (!isValidDateString(enddate)) {
                                    sender.sendRichMessage("<red>Argument \"enddate\" Has Invalid Date Format, Format As Follows: \"yyyy-mm-dd hh:mm:ss\"");
                                    return Command.SINGLE_SUCCESS;
                                }

                                HeatmapLayer layer = database.getHeatmapLayers().get(layerName);

                                if (layer.pollRangeSeconds != config.getInt("defaults.noUpdatePollRangeSeconds")) {
                                    sender.sendRichMessage("<red>dateRange modification setting is only to be used for non-updating maps, try \"/... modify relativetimeperiod\"");
                                    return Command.SINGLE_SUCCESS;
                                }

                                database.executeSql((connection)->{
                                    try {
                                        PreparedStatement statement = connection.prepareStatement("UPDATE `heatmap_layers`SET `fromToDate`= ? WHERE `dyn_id` = ? AND `dyn_label` = ?;");
                                        statement.setString(1, String.format("%s,%s", startdate, enddate));
                                        statement.setString(2, layer.id);
                                        statement.setString(3, layer.label);

                                        statement.execute();
                                    } catch (Exception e) {
                                        sender.sendRichMessage("<red>Failed To Update Heatmap Layer In The Database");
                                        sender.sendRichMessage(e.getMessage());
                                    }
                                    // return type does not matter here
                                    return null;
                                });

                                pollHeatmapCommandFunction(sender, layerName, startdate, enddate);

                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
                .then(Commands.literal("relativeTimePeriod")
                    .then(Commands.argument("relativetimeperiod", StringArgumentType.string())
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.modify"))
                        .executes(context -> {

                            Runnable r = () -> {
                                String layerName = StringArgumentType.getString(context, "name");
                                CommandSender sender = context.getSource().getSender();
                                HeatmapLayer layer = database.getHeatmapLayers().get(layerName);


                                if (layer.pollRangeSeconds == config.getInt("defaults.noUpdatePollRangeSeconds")) {
                                    sender.sendRichMessage("<red>relativetimeperiod modification setting is only to be used for updating maps, try \"/... modify dateRange\"");
                                    return;
                                }

                                long pollRangeSeconds = parseTimeStringToSeconds(StringArgumentType.getString(context, "relativetimeperiod"));
                                if (pollRangeSeconds == 0) {
                                    sender.sendRichMessage("<red>Failed Parsing Time String; Try Formats:");
                                    sender.sendRichMessage("\"2w,5d,7h,2m,10s\"");
                                    sender.sendRichMessage("\"2w5d7h2m10s\"");
                                    sender.sendRichMessage("\"5d2h\"");
                                    sender.sendRichMessage("\"2.50h\"");
                                    return;
                                }
                                layer.pollRangeSeconds = (int)pollRangeSeconds;

                                database.executeSql((connection)->{
                                    try {
                                        PreparedStatement statement = connection.prepareStatement("UPDATE `heatmap_layers` SET `poll_range_seconds` = ? WHERE `dyn_id` = ? AND `dyn_label` = ?;");
                                        statement.setInt(1, layer.pollRangeSeconds);
                                        statement.setString(2, layer.id);
                                        statement.setString(3, layer.label);

                                        statement.execute();
                                    } catch (Exception e) {
                                        sender.sendRichMessage("<red>Failed To Update Heatmap Layer In The Database");
                                        sender.sendRichMessage(e.getMessage());
                                    }
                                    // return type does not matter here
                                    return null;
                                });

                                pollHeatmapCommandFunction(sender, layerName, null, null);
                            };
                            new Thread(r).start();
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(Commands.literal("divisions")
                    .then(Commands.argument("divisions", IntegerArgumentType.integer(1))
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.modify"))
                        .executes(context -> {

                            Runnable r = () -> {
                                String layerName = StringArgumentType.getString(context, "name");
                                CommandSender sender = context.getSource().getSender();
                                HeatmapLayer layer = database.getHeatmapLayers().get(layerName);

                                layer.divisions = IntegerArgumentType.getInteger(context, "divisions");

                                database.executeSql((connection) -> {
                                    try {
                                        PreparedStatement statement = connection.prepareStatement("UPDATE `heatmap_layers` SET `divisions` = ? WHERE `dyn_id` = ? AND `dyn_label` = ?;");
                                        statement.setInt(1, layer.divisions);
                                        statement.setString(2, layer.id);
                                        statement.setString(3, layer.label);

                                        statement.execute();
                                    } catch (Exception e) {
                                        sender.sendRichMessage("<red>Failed To Update Heatmap Layer In The Database");
                                        sender.sendRichMessage(e.getMessage());
                                    }
                                    // return type does not matter here
                                    return null;
                                });

                                pollHeatmapCommandFunction(sender, layerName, null, null);
                            };
                            new Thread(r).start();
                            return Command.SINGLE_SUCCESS;
                        })
                ))
            );

        // Get/List Heatmap Layers
        LiteralArgumentBuilder<CommandSourceStack> getHeatmapLayers = Commands.literal("heatmapLayers")
            .requires(sender -> sender.getSender().hasPermission("mmheatmap.getLayers"))
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
        infoCommand.then(getHeatmapLayers);

        // Resync Heatmap Layers With Database
        LiteralArgumentBuilder<CommandSourceStack> resyncCommand = Commands.literal("resync")
            .requires(sender -> sender.getSender().hasPermission("mmheatmap.resync"))
            .executes(context -> {
                CommandSender sender = context.getSource().getSender();

                sender.sendRichMessage("<blue>Resyncing heatmap layers with database");
                long startTime = System.nanoTime();
                database.resyncHeatmapDatabase();
                sender.sendRichMessage("<color:#30f000> Done! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms)");

                return Command.SINGLE_SUCCESS;
            });


        // TODO: Benchmarking Command
        LiteralArgumentBuilder<CommandSourceStack> benchamarkCommand = Commands.literal("benchmark")
            .requires(sender -> sender.getSender().hasPermission("mmheatmap.benchmark"))
            .executes(context -> {

                    CommandSender sender = context.getSource().getSender();

                    long startTime = System.nanoTime();
                    sender.sendRichMessage("Query Took <reset>(<color:#30f000>" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms<reset>)");

                    return Command.SINGLE_SUCCESS;
                });




        // Append subtrees
        LiteralArgumentBuilder<CommandSourceStack> createHeatmap = Commands.literal("create");
        createHeatmap.then(divideWorld);
        createHeatmap.then(divideWorldNoUpdate);
        commandRoot.then(createHeatmap);

        commandRoot.then(deleteLayer);
        commandRoot.then(pollCommand);
        commandRoot.then(resyncCommand);
        commandRoot.then(modifyCommand);


        commandRoot.then(infoCommand);

        commandRoot.requires(sender -> sender.getSender().hasPermission("mmheatmap.all") || (sender instanceof ConsoleCommandSender));
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
     *
     * @param heatmapName
     * @param sender
     * @param xy1
     * @param xy2
     * @param divisionCount
     * @param pollRangeSeconds
     * @return
     */
    static private int divideWorldCommandFunction(String heatmapName, CommandSender sender, Vector2d xy1, Vector2d xy2, int divisionCount, int pollRangeSeconds, String fromDate, String toDate) {
        String world = (sender instanceof Player player) ? player.getWorld().getName() : config.getString("defaults.world_name");

        long startTime = System.nanoTime();

        try {
            try {

                int divisionCountDensityWarning = config.getInt("defaults.divisionDensityWarningCount");
                if (divisionCount > divisionCountDensityWarning) {
                    sender.sendRichMessage("<yellow>Warning, Division Count Above " + divisionCountDensityWarning + " The Dynmap May Have Severe Performance Issues; Especially Larger Surface Areas");
                }

                database.insertNewHeatmapLayer(new HeatmapLayer(heatmapName, heatmapName, xy1, xy2, divisionCount, world, pollRangeSeconds), fromDate, toDate);

                sender.sendRichMessage("<blue> Dividing World");
                DynWrapper.getAreaSetOrCreate(heatmapName);

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

        // check to see if the map will update, if so display that time, else display a different message
        if (pollRangeSeconds == config.getInt("defaults.noUpdatePollRangeSeconds")) {
            sender.sendRichMessage("Heatmap <blue><b>"+heatmapName+"<reset> Is Set To Not Update, To Change This See: <b>/mmheatmap modify...");
        } else {

            sender.sendRichMessage("Heatmap <blue><b>"+heatmapName+"<reset> Will Update Every " + config.get("defaults.pollFrequencySeconds") + " Seconds Containing The Past "+pollRangeSeconds+" Seconds Of Activity Data, To Change This See: <b>/mmheatmap modify...");
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * This function is to be used internally in this class, and passed to the "/mmheatmap poll" command branch.
     * The context comes from the ".execute(...)" part of the command tree. Args must be passed in as they will not
     * be gotten from the inside of this function.
     *
     * @param sender The sender of the command
     * @param layerName The name of the layer which needs to be polled
     * @param fromDate must be non-null if `toDate` is also non-null
     * @param toDate must be non-null if `fromDate` is also non-null
     * @return returns the command success code, generally SINGLE_SUCCESS (or 1)
     */
    static public int pollHeatmapCommandFunction(CommandSender sender, String layerName, String fromDate, String toDate) {
        Runnable run = ()->{
            long startTime = System.nanoTime();

            HeatmapLayer layer = database.getHeatmapLayers().get(layerName);

            // recreate set
            MarkerSet set = DynWrapper.getAreaSetOrCreate(layerName);

            // database query
            long queryStartTime = System.nanoTime();

            Map<String, Integer> activityEntries;
            if (fromDate == null && toDate == null) {
                activityEntries = database.getPlayerActivityEntriesForLayer(layer);
            } else {
                if (fromDate == null || toDate == null) {
                    sender.sendRichMessage("<red>If \"fromdate\" is valid, \"todate\" must also be valid");
                    return;
                } else {
                    if (isValidDateString(fromDate) && isValidDateString(toDate)) {
                        activityEntries = database.getPlayerActivityEntriesForLayerBetweenDates(layer, fromDate, toDate);
                    } else {
                        sender.sendRichMessage("<red>\"fromDate\" or \"toDate\" Argument Has Invalid Format, Format As Follows: \"yyyy-mm-dd hh:mm:ss\"");
                        return;
                    }
                }
            }
            long queryTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-queryStartTime);

            // create the heatmap cells
            long dynStartTime = System.nanoTime();

            DynWrapper.createActiveHeatmapCellsFromCoords(layer, set, activityEntries, "");

            // FIXME: Background Layer Takes Too Long To Generate And Covers Other Cells
            // add single background layer -- makes the map easier to read
            /*double[] xy1 = new double[]{layer.topLeft.x, layer.bottomRight.x};
            double[] xy2 = new double[]{layer.topLeft.y, layer.bottomRight.y};
            try {
                AreaMarker backgroundMarker = set.createAreaMarker("layer_background", "", false, layer.world, xy1, xy2, true);
                backgroundMarker.setFillStyle(
                        config.getDouble("colors.heatmapBaseLayerOpacity"),
                        DynWrapper.colorToInteger(DynWrapper.hexToColor((config.getString("colors.heatmapBaseLayerColor"))))
                );
                backgroundMarker.setRangeY(0,0);
            } catch (Exception e) {
                System.err.println("Error Filling Background: " + e.getMessage());
            }*/

            long dynTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-dynStartTime);

            if (config.getBoolean("defaults.logPollTime"))
                sender.sendRichMessage("<green>"+layerName+" Polling Completed! (Ttl: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms, <blue>Db: "+queryTime+"ms, <red>Dyn: "+dynTime+"ms<green>)");
        };

        if (!database.getHeatmapLayers().containsKey(layerName)) {
            sender.sendRichMessage("<red>Cannot Find Layer " + layerName);
            return -1;
        }

        new Thread(run).start();

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Same functionality as found in `see more` section
     *
     * @see HeatmapCommand#divideWorldCommandFunction(String, CommandSender, Vector2d, Vector2d, int, int, String, String)
     */
    static private int divideWorldCommandFunction(String heatmapName, CommandSender sender, Vector2d xy1, Vector2d xy2, int divisionCount, boolean doUpdate, String fromDate, String toDate) {
        int pollRangeSeconds = config.getInt((doUpdate) ? "defaults.pollRangeSeconds" : "defaults.noUpdatePollRangeSeconds");
        return divideWorldCommandFunction(heatmapName, sender, xy1, xy2, divisionCount, pollRangeSeconds, fromDate, toDate);
    }

    /**
     * Polls a small area of a larger map
     *
     * @param sender The sender of the command
     * @param layerName The layer of the area that needs to be polled
     * @param xy1 Position should be block coordinates, not chunk coordinates
     * @param xy2 Position should be block coordinates, not chunk coordinates
     * @return returns Command Success -- all errors are handled internally
     */
    static private int pollHeatmapArea(CommandSender sender, String layerName, Vector2d xy1, Vector2d xy2) {
        Runnable run = ()->{
            long startTime = System.nanoTime();

            HeatmapLayer layer = database.getHeatmapLayers().get(layerName);

            // recreate set
            MarkerSet set = DynWrapper.getAreaSetOrCreate(layerName);

            // database query
            long queryStartTime = System.nanoTime();


            long queryTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-queryStartTime);
            Map<String, Integer> activityEntries = database.getPlayerActivityEntriesForLayerInArea(layer, xy1, xy2);

            // create the heatmap cells
            long dynStartTime = System.nanoTime();

            DynWrapper.updateHeatmapCellsInRange(layer, set, activityEntries, "");

            // FIXME: Background Layer Takes Too Long To Generate And Covers Other Cells
            // add single background layer -- makes the map easier to read
            /*double[] xy1 = new double[]{layer.topLeft.x, layer.bottomRight.x};
            double[] xy2 = new double[]{layer.topLeft.y, layer.bottomRight.y};
            try {
                AreaMarker backgroundMarker = set.createAreaMarker("layer_background", "", false, layer.world, xy1, xy2, true);
                backgroundMarker.setFillStyle(
                        config.getDouble("colors.heatmapBaseLayerOpacity"),
                        DynWrapper.colorToInteger(DynWrapper.hexToColor((config.getString("colors.heatmapBaseLayerColor"))))
                );
                backgroundMarker.setRangeY(0,0);
            } catch (Exception e) {
                System.err.println("Error Filling Background: " + e.getMessage());
            }*/

            long dynTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-dynStartTime);

            if (config.getBoolean("defaults.logPollTime"))
                sender.sendRichMessage("<green>"+layerName+" Polling Completed! (Ttl: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms, <blue>Db: "+queryTime+"ms, <red>Dyn: "+dynTime+"ms<green>)");
        };

        if (!database.getHeatmapLayers().containsKey(layerName)) {
            sender.sendRichMessage("<red>Cannot Find Layer " + layerName);
            return -1;
        }

        new Thread(run).start();

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Valid time format examples:
     *  "2w,5d,7h,2m,10s"
     *  "2w5d7h2m10s"
     *  "5d2h"
     *  "2.50h"
     * @param timeString A string representing the time, examples given in doc comment, I don't know if negatives will work (I think they will), so don't try.
     *                   This function works better on the set of natural numbers, so stick to those unless strictly necessary.
     * @return returns the time in seconds, or 0 on error
     */
    public static long parseTimeStringToSeconds(String timeString) {

        long seconds = 0;

        try {
            Pattern pattern = Pattern.compile("([+-]?([0-9]*[.])?[0-9]+[wdhms])", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(timeString);
            while (matcher.find()) {
                String numericStr = matcher.group().split("[wdhms]")[0];
                String timeCode = matcher.group().split(numericStr)[1];

                double num = Double.parseDouble(numericStr);

                // get time character, and replace 'c' with it
                double d = switch (timeCode) {
                    case "w" -> (604800 * num);
                    case "d" -> (86400 * num);
                    case "h" -> (3600 * num);
                    case "m" -> (60 * num);
                    case "s" -> (num);
                    default -> 0;
                };
                seconds += (long) Math.floor(d);
            }
        } catch (Exception e) {
            return 0;
        }
        return seconds;
    }

    /**
     * This ensures a date format is as follows: "yyyy-mm-dd hh:mm:ss"
     * @param date The string which represents a date
     * @return Returns true if the date format is valid
     */
    public static boolean isValidDateString(String date) {
        Pattern pattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(date);
        return matcher.find();
    }

}
