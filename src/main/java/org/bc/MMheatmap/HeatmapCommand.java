package org.bc.MMheatmap;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bc.MMheatmap.poller.PlayerActionListener;
import org.bc.MMheatmap.poller.PlayerActivityPoller;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.dynmap.markers.MarkerSet;
import org.joml.Vector2d;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class containing all the commands used to control the heatmap
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
     */
    public HeatmapCommand(HeatmapDatabase database, FileConfiguration config) {
        // Sets a database object to be used within some commands
        HeatmapCommand.database = database;
        // Sets the config file from the plugin to be used in the commands
        HeatmapCommand.config = config;

        // Command root
        LiteralArgumentBuilder<CommandSourceStack> commandRoot = Commands.literal("mmheatmap");


        // Info/Help Command
        LiteralArgumentBuilder<CommandSourceStack> infoCommand = Commands.literal("info")
            .requires(sender -> sender.getSender().hasPermission("mmheatmap.help"))
            .executes(context -> {
                CommandSender sender = context.getSource().getSender();

                sender.sendRichMessage("<green>Full MMHeatmap Documentation Found: <white><b><click:open_url:'https://github.com/Bryson-C/MMHeatmap'>here");

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
                        .then(Commands.argument("world", ArgumentTypes.world())
                            .requires(sender -> sender.getSender().hasPermission("mmheatmap.divideWorld"))
                            // Default time period branch
                            .executes(context -> {
                                // Get Command Parameters
                                Runnable r = () -> {
                                    CommandSender sender = context.getSource().getSender();
                                    String layername = StringArgumentType.getString(context, "name");
                                    World world = context.getArgument("world", World.class);
                                    // both following functions should be threaded
                                    divideWorldCommandFunction(
                                            layername,
                                            sender,
                                            new Vector2d(IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "y1")),
                                            new Vector2d(IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "y2")),
                                            IntegerArgumentType.getInteger(context, "divisioncountsq"),
                                            config.getInt("defaults.pollRangeSeconds"),
                                            world.getName(),
                                            "", ""
                                    );

                                    pollHeatmapCommandFunction(sender, layername, null, null);
                                };
                                new Thread(r).start();

                                return Command.SINGLE_SUCCESS;
                            })
                        )
                        // custom time period branch
                        .then(Commands.argument("relativetimeperiod", StringArgumentType.string())
                            .then(Commands.argument("world", ArgumentTypes.world())
                                .requires(sender -> sender.getSender().hasPermission("mmheatmap.divideWorld"))
                                .executes(context -> {

                                    Runnable r = () -> {

                                        CommandSender sender = context.getSource().getSender();
                                        String timeString = StringArgumentType.getString(context, "relativetimeperiod");
                                        World world = context.getArgument("world", World.class);

                                        long pollRangeSeconds = parseTimeStringToSeconds(timeString);
                                        if (pollRangeSeconds == 0) {
                                            sender.sendRichMessage("<red>Failed Parsing Time String; Try Formats:");
                                            sender.sendRichMessage("\"2w,5d,7h,2m,10s\"");
                                            sender.sendRichMessage("\"2w5d7h2m10s\"");
                                            sender.sendRichMessage("\"5d2h\"");
                                            sender.sendRichMessage("\"2.50h\"");
                                            return;
                                        }

                                        String layername = StringArgumentType.getString(context, "name");
                                        divideWorldCommandFunction(
                                                layername,
                                                sender,
                                                new Vector2d(IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "y1")),
                                                new Vector2d(IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "y2")),
                                                IntegerArgumentType.getInteger(context, "divisioncountsq"),
                                                (int) pollRangeSeconds,
                                                world.getName(),
                                                "",
                                                ""
                                        );
                                        pollHeatmapCommandFunction(sender, layername, null, null);
                                    };

                                    new Thread(r).start();

                                    return Command.SINGLE_SUCCESS;
                                })
                            )
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
                                .then(Commands.argument("world", ArgumentTypes.world())
                                    .requires(sender -> sender.getSender().hasPermission("mmheatmap.divideWorld"))
                                    .executes(context -> {
                                        Runnable r = () -> {
                                            CommandSender sender = context.getSource().getSender();

                                            World world = context.getArgument("world", World.class);
                                            String startdate = StringArgumentType.getString(context, "startdate");
                                            String enddate = StringArgumentType.getString(context, "enddate");
                                            String layername = StringArgumentType.getString(context, "name");

                                            divideWorldCommandFunction(
                                                    layername,
                                                    sender,
                                                    new Vector2d(IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "y1")),
                                                    new Vector2d(IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "y2")),
                                                    IntegerArgumentType.getInteger(context, "divisioncountsq"),
                                                    false,
                                                    world.getName(),
                                                    startdate,
                                                    enddate
                                            );

                                            pollHeatmapCommandFunction(sender, layername, startdate, enddate);
                                        };
                                        new Thread(r).start();
                                        return Command.SINGLE_SUCCESS;
                                    })
                                )
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

                        Runnable r = () -> {
                            CommandSender sender = context.getSource().getSender();
                            sender.sendRichMessage("<blue> Deleting Heatmap Overlay");

                            long startTime = System.nanoTime();

                            String layerName = StringArgumentType.getString(context, "name");

                            // delete here
                            DynWrapper.deleteAreaSet(layerName);
                            try {
                                database.deleteHeatmapLayer(layerName);
                                sender.sendRichMessage("<color:#30f000> Done! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + "ms)");
                            } catch (HeatmapDatabase.NoSuchLayerException e) {
                                sender.sendRichMessage("<red>Layer \"" + layerName + "\" Does Not Exist");
                                // Success since command functioned as intended
                                return;
                            } catch (Exception e) {
                                sender.sendRichMessage("<red>Unknown Error Occurred Trying To Delete Layer \"" + layerName + "\"");
                            }
                        };
                        new Thread(r).start();

                        return Command.SINGLE_SUCCESS;
                    })

                )
            )
            // This command needs to function primarily in 2 ways:
            // 1. Deleting a single player's data over a certain period of time in a specific range
            // 2. Deleting all player data in an area over a certain period of time
            .then(Commands.literal("playerActivity")
                .then(Commands.argument("world", ArgumentTypes.world())
                    .then(Commands.argument("player", StringArgumentType.string())
                    .suggests((ctx, builder) -> { builder.suggest("player_name"); builder.suggest("ALL_PLAYERS"); return builder.buildFuture();})
                        .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer()).then(Commands.argument("y2", IntegerArgumentType.integer())
                            .then(Commands.literal("dateRange")
                                // specific date branch
                                .then(Commands.argument("startdate", StringArgumentType.string())
                                    .then(Commands.argument("enddate", StringArgumentType.string())
                                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.delete.playerActivity"))
                                        .executes(context -> {
                                            CommandSender sender = context.getSource().getSender();
                                            Runnable r = () -> {
                                                sender.sendRichMessage("<green> Deleting Player Activity");
                                                long start = System.nanoTime();
                                                deletePlayerActivity(
                                                        sender,
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "startdate"),
                                                        StringArgumentType.getString(context, "enddate"),
                                                        IntegerArgumentType.getInteger(context, "x1"),
                                                        IntegerArgumentType.getInteger(context, "y1"),
                                                        IntegerArgumentType.getInteger(context, "x2"),
                                                        IntegerArgumentType.getInteger(context, "y2"),
                                                        context.getArgument("world", World.class)
                                                );
                                                sender.sendRichMessage("<green> Done <blue>("+Duration.ofNanos(System.nanoTime()-start).toMillis()+" ms)");
                                            };
                                            new Thread(r).start();
                                            return Command.SINGLE_SUCCESS;
                                        })
                                    )
                                )
                            )
                            // relative range branch
                            .then(Commands.literal("relativeTimePeriod")
                                .then(Commands.argument("relativetimerange", StringArgumentType.string())

                                    .requires(sender -> sender.getSender().hasPermission("mmheatmap.delete.playerActivity"))
                                    .executes(context -> {
                                        Runnable r = () -> {
                                            CommandSender sender = context.getSource().getSender();
                                            long pollRangeSeconds = parseTimeStringToSeconds(StringArgumentType.getString(context, "relativetimerange"));
                                            if (pollRangeSeconds == 0) {
                                                sender.sendRichMessage("<red>Failed Parsing Time String; Try Formats:");
                                                sender.sendRichMessage("\"2w,5d,7h,2m,10s\"");
                                                sender.sendRichMessage("\"2w5d7h2m10s\"");
                                                sender.sendRichMessage("\"5d2h\"");
                                                sender.sendRichMessage("\"2.50h\"");
                                                return;
                                            }
                                            String startdate = HeatmapLayer.DateFormat.getDateAsString(HeatmapLayer.DateFormat.getDateNSecondsAgo(HeatmapLayer.DateFormat.nowDate(), pollRangeSeconds));
                                            String enddate = HeatmapLayer.DateFormat.getDateAsString(HeatmapLayer.DateFormat.nowDate());


                                            sender.sendRichMessage("<green> Deleting Player Activity");
                                            long start = System.nanoTime();

                                            deletePlayerActivity(
                                                    sender,
                                                    StringArgumentType.getString(context, "player"),
                                                    startdate,
                                                    enddate,
                                                    IntegerArgumentType.getInteger(context, "x1"),
                                                    IntegerArgumentType.getInteger(context, "y1"),
                                                    IntegerArgumentType.getInteger(context, "x2"),
                                                    IntegerArgumentType.getInteger(context, "y2"),
                                                    context.getArgument("world", World.class)
                                            );

                                            sender.sendRichMessage("<green> Done <blue>("+Duration.ofNanos(System.nanoTime()-start).toMillis()+" ms)");
                                        };

                                        new Thread(r).start();
                                        return Command.SINGLE_SUCCESS;
                                    })
                                )
                            )
                        )))
                    )
                )
            )
        );

        // polling command
        LiteralArgumentBuilder<CommandSourceStack> pollCommand = Commands.literal("poll")
            .then(Commands.literal("pollLayer")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests((ctx, builder) -> { database.getHeatmapLayers().entrySet().stream().filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(x->builder.suggest(x.getKey())); return builder.buildFuture();})
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.poll.pollLayer"))
                        // this branch is for when no arguments are provided
                        .executes(context -> {
                            Runnable r = () -> {
                                String layerName = StringArgumentType.getString(context, "name");
                                pollHeatmapCommandFunction(context.getSource().getSender(), layerName, null, null);
                            };
                            new Thread(r).start();
                            return Command.SINGLE_SUCCESS;
                        })
                )
            )
            .then(Commands.literal("pollArea")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests((ctx, builder) -> { database.getHeatmapLayers().entrySet().stream().filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(x->builder.suggest(x.getKey())); return builder.buildFuture();})
                    .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer()).then(Commands.argument("y2", IntegerArgumentType.integer())
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.poll.pollArea"))
                        .executes(context -> {

                            Runnable r = () -> {
                                String layername = StringArgumentType.getString(context, "name");
                                HeatmapLayer layer = database.getHeatmapLayers().get(layername);
                                CommandSender sender = context.getSource().getSender();

                                double[] xy1xy2 = DynWrapper.getUpperLeftAndBottomRightCellCoordsFromPoint(
                                        layer,
                                        IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "y1"),
                                        IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "y2")
                                );

                                pollHeatmapArea(
                                        sender,
                                        layername,
                                        new Vector2d(xy1xy2[0], xy1xy2[1]),
                                        new Vector2d(xy1xy2[2], xy1xy2[3])
                                );
                            };

                            new Thread(r).start();

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

        // Modify Command
        LiteralArgumentBuilder<CommandSourceStack> modifyCommand = Commands.literal("modify")
            .then(Commands.argument("name", StringArgumentType.string())
                .suggests((ctx, builder) -> {database.getHeatmapLayers().entrySet().stream().filter((entry) -> entry.getKey().toLowerCase().startsWith(builder.getRemainingLowerCase())).forEach(x->builder.suggest(x.getKey()));return builder.buildFuture();})
                .then(Commands.literal("points")
                    .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer()).then(Commands.argument("y2", IntegerArgumentType.integer())
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.modify.points"))
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
                            .requires(sender -> sender.getSender().hasPermission("mmheatmap.modify.dateRange"))
                            .executes(context -> {
                                Runnable r = () -> {
                                    String layerName = StringArgumentType.getString(context, "name");
                                    CommandSender sender = context.getSource().getSender();

                                    String startdate = StringArgumentType.getString(context, "startdate");
                                    String enddate = StringArgumentType.getString(context, "enddate");

                                    if (!isValidDateString(startdate)) {
                                        sender.sendRichMessage("<red>Argument \"startdate\" Has Invalid Date Format, Format As Follows: \"yyyy-mm-dd hh:mm:ss\"");
                                        return;
                                    }
                                    if (!isValidDateString(enddate)) {
                                        sender.sendRichMessage("<red>Argument \"enddate\" Has Invalid Date Format, Format As Follows: \"yyyy-mm-dd hh:mm:ss\"");
                                        return;
                                    }

                                    HeatmapLayer layer = database.getHeatmapLayers().get(layerName);

                                    if (layer.pollRangeSeconds != config.getInt("defaults.noUpdatePollRangeSeconds")) {
                                        sender.sendRichMessage("<red>dateRange modification setting is only to be used for non-updating maps, try \"/... modify relativetimeperiod\"");
                                        return;
                                    }

                                    database.executeSql((connection) -> {
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
                                };
                                new Thread(r).start();

                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
                .then(Commands.literal("relativeTimePeriod")
                    .then(Commands.argument("relativetimeperiod", StringArgumentType.string())
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.modify.relativeTimePeriod"))
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
                        .requires(sender -> sender.getSender().hasPermission("mmheatmap.modify.divisions"))
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
            .requires(sender -> sender.getSender().hasPermission("mmheatmap.info.heatmapLayers"))
            .executes(context -> {

                // not threaded because all calls get data from memory, so every operation should be fast
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
                Runnable r = () -> {
                    CommandSender sender = context.getSource().getSender();

                    sender.sendRichMessage("<blue>Resyncing heatmap layers with database");
                    long startTime = System.nanoTime();
                    database.resyncHeatmapDatabase();
                    sender.sendRichMessage("<color:#30f000> Done! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + "ms)");
                };
                new Thread(r).start();

                return Command.SINGLE_SUCCESS;
            });

        // TODO: Insert data into database as soon as the command is ran
        LiteralArgumentBuilder<CommandSourceStack> fakePlayerDataCommand = Commands.literal("generateFakeData")
            .then(Commands.argument("playername", StringArgumentType.string())
                .then(Commands.argument("world", ArgumentTypes.world())
                    .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer()).then(Commands.argument("y2", IntegerArgumentType.integer())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1)).then(Commands.argument("minactivity", IntegerArgumentType.integer(1)).then(Commands.argument("maxactivity", IntegerArgumentType.integer(1))
                            .requires(sender -> sender.getSender().hasPermission("mmheatmap.generateFakePlayerData"))
                            .executes(context -> {
                                int count = IntegerArgumentType.getInteger(context, "count");
                                CommandSender sender = context.getSource().getSender();
                                sender.sendRichMessage("<green>Generating Fake Player Data");
                                Runnable r = () -> {
                                    int x1 = IntegerArgumentType.getInteger(context, "x1"), x2 = IntegerArgumentType.getInteger(context, "x2");
                                    int y1 = IntegerArgumentType.getInteger(context, "y1"), y2 = IntegerArgumentType.getInteger(context, "y2");

                                    long start = System.nanoTime();
                                    PlayerActionListener.generateFakePlayerData(
                                            StringArgumentType.getString(context, "playername"),
                                            context.getArgument("world", World.class).getName(),
                                            x1, y1, x2, y2,
                                            count,
                                            IntegerArgumentType.getInteger(context, "minactivity"),
                                            IntegerArgumentType.getInteger(context, "maxactivity")
                                    );
                                    sender.sendRichMessage("<green>Done <blue>("+ Duration.ofNanos(System.nanoTime() - start).toMillis() +"ms)");
                                    sender.sendRichMessage("<green>Changes will appear after next poll period");
                                };

                                new Thread(r).start();
                                return Command.SINGLE_SUCCESS;
                            })
                        )))
                    )))
                )
            )
        );


        // TODO: Benchmarking Command
        LiteralArgumentBuilder<CommandSourceStack> benchamarkCommand = Commands.literal("benchmark")
            .requires(sender -> sender.getSender().hasPermission("mmheatmap.benchmark"))
            .executes(context -> {


                    CommandSender sender = context.getSource().getSender();

                    sender.sendRichMessage("<yellow> Not implemented yet");

                    long startTime = System.nanoTime();
                    sender.sendRichMessage("Query Took <reset>(<color:#30f000>" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms<reset>)");

                    return Command.SINGLE_SUCCESS;
                });




        // Append subtrees
        LiteralArgumentBuilder<CommandSourceStack> createHeatmap = Commands.literal("create");
        createHeatmap.then(divideWorld);
        createHeatmap.then(divideWorldNoUpdate);
        commandRoot.then(createHeatmap);

        commandRoot.then(fakePlayerDataCommand);
        commandRoot.then(deleteLayer);
        commandRoot.then(pollCommand);
        commandRoot.then(resyncCommand);
        commandRoot.then(modifyCommand);


        commandRoot.then(infoCommand);

        commandRoot.requires(sender -> sender.getSender().hasPermission("mmheatmap.root") || (sender instanceof ConsoleCommandSender));
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
     * Contains the functionality to divide the world into multiple cells on the dynmap side
     *
     * NOTE: not threaded by default
     *
     * @param heatmapName The layer name
     * @param sender The command sender
     * @param xy1 The top-left point (x1,y1)
     * @param xy2 The bottom-right point (x2, y2)
     * @param divisionCount How many cells to make horizontally and vertically
     * @param pollRangeSeconds How much time worth of data to include in the layer after polling. Time is in the range [Now-(pollRangeSeconds), Now]
     * @return Returns a success code
     */
    static private int divideWorldCommandFunction(String heatmapName, CommandSender sender, Vector2d xy1, Vector2d xy2, int divisionCount, int pollRangeSeconds, String world, String fromDate, String toDate) {

        if (world.isEmpty())
            world =  config.getString("defaults.world_name");

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
                sender.sendRichMessage("<red>Heatmap layer \"" + heatmapName + "\" already exists, use new name; or delete, then recreate heatmap layer");
                // This is a success because it functions as intended
                return Command.SINGLE_SUCCESS;
            } catch (Exception e) {
                sender.sendRichMessage("<red>Unknown Error Occurred Trying To Create Layer \"" + heatmapName + "\"");
            }

        } catch (Exception e) {
            sender.sendRichMessage("<red>Failed To Create Marker: " + e.getMessage());
            return Command.SINGLE_SUCCESS;
        }

        sender.sendRichMessage("Created Heatmap <blue><b>" + heatmapName + "<reset>(<color:#30f000>" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + "ms<reset>)");

        // check to see if the map will update, if so display that time, else display a different message
        if (pollRangeSeconds == config.getInt("defaults.noUpdatePollRangeSeconds")) {
            sender.sendRichMessage("Heatmap <blue><b>" + heatmapName + "<reset> Is Set To Not Update, To Change This See: <b>/mmheatmap modify...");
        } else {

            sender.sendRichMessage("Heatmap <blue><b>" + heatmapName + "<reset> Will Update Every " + config.get("defaults.pollFrequencySeconds") + " Seconds Containing The Past " + pollRangeSeconds + " Seconds Of Activity Data, To Change This See: <b>/mmheatmap modify...");
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * This function is to be used internally in this class, and passed to the "/mmheatmap poll" command branch.
     * The context comes from the ".execute(...)" part of the command tree. Args must be passed in as they will not
     * be gotten from the inside of this function.
     *
     * NOTE: Not threaded by default
     *
     * @param sender The sender of the command
     * @param layerName The name of the layer which needs to be polled
     * @param fromDate must be non-null if `toDate` is also non-null
     * @param toDate must be non-null if `fromDate` is also non-null
     * @return returns the command success code, generally SINGLE_SUCCESS (or 1)
     */
    static public int pollHeatmapCommandFunction(CommandSender sender, String layerName, String fromDate, String toDate) {
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
                return Command.SINGLE_SUCCESS;
            } else {
                if (isValidDateString(fromDate) && isValidDateString(toDate)) {
                    activityEntries = database.getPlayerActivityEntriesForLayerBetweenDates(layer, fromDate, toDate);
                } else {
                    sender.sendRichMessage("<red>\"fromDate\" or \"toDate\" Argument Has Invalid Format, Format As Follows: \"yyyy-mm-dd hh:mm:ss\"");
                    return Command.SINGLE_SUCCESS;
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


        if (!database.getHeatmapLayers().containsKey(layerName)) {
            sender.sendRichMessage("<red>Cannot Find Layer " + layerName);
            return -1;
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Same functionality as found in `see more` section
     *
     * @see HeatmapCommand#divideWorldCommandFunction(String, CommandSender, Vector2d, Vector2d, int, int, String, String, String)
     */
    static private int divideWorldCommandFunction(String heatmapName, CommandSender sender, Vector2d xy1, Vector2d xy2, int divisionCount, boolean doUpdate, String world, String fromDate, String toDate) {
        int pollRangeSeconds = config.getInt((doUpdate) ? "defaults.pollRangeSeconds" : "defaults.noUpdatePollRangeSeconds");
        return divideWorldCommandFunction(heatmapName, sender, xy1, xy2, divisionCount, pollRangeSeconds, world, fromDate, toDate);
    }

    /**
     * Polls a small area of a larger map
     * NOTE: not threaded by default
     *
     * @param sender The sender of the command
     * @param layerName The layer of the area that needs to be polled
     * @param xy1 Position should be block coordinates, not chunk coordinates
     * @param xy2 Position should be block coordinates, not chunk coordinates
     * @return returns Command Success -- all errors are handled internally
     */
    static private int pollHeatmapArea(CommandSender sender, String layerName, Vector2d xy1, Vector2d xy2) {

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


        if (!database.getHeatmapLayers().containsKey(layerName)) {
            sender.sendRichMessage("<red>Cannot Find Layer " + layerName);
            return -1;
        }

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
     *
     * @param date The string which represents a date
     * @return Returns true if the date format is valid
     */
    public static boolean isValidDateString(String date) {
        Pattern pattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(date);
        return matcher.find();
    }

    // TODO: Finish implementing

    /**
     * NOTE: Not threaded by default
     * @param sender
     * @param targetPlayer
     * @param startdate
     * @param enddate
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @param world
     */
    private static void deletePlayerActivity(CommandSender sender, String targetPlayer, String startdate, String enddate, int x1, int y1, int x2, int y2, World world) {

        // Because of the nature of deleting data, all maps using the data must be re-polled. This can be an expensive operation, and take a while, so first pause polling
        PlayerActivityPoller.pausePolling();

        database.executeSql((connection) -> {
            try {

                if (!isValidDateString(startdate)) {
                    sender.sendRichMessage("<red>Argument \"startdate\" Has Invalid Date Format, Format As Follows: \"yyyy-mm-dd hh:mm:ss\"");
                    return -1;
                }
                if (!isValidDateString(enddate)) {
                    sender.sendRichMessage("<red>Argument \"enddate\" Has Invalid Date Format, Format As Follows: \"yyyy-mm-dd hh:mm:ss\"");
                    return -1;
                }


                PreparedStatement stmt;

                // normal case: player is named, include that in the parameters
                if (!targetPlayer.equals("ALL_PLAYERS")) {
                    stmt = connection.prepareStatement("DELETE FROM `player_activity` WHERE xpos > ? AND xpos < ? AND ypos > ? AND ypos < ? AND world_name = ? AND datetime >= '" + startdate + "' AND datetime <= '" + enddate + "' AND player_name = ?");
                    stmt.setString(6, targetPlayer);
                }
                // special case to include all players, in this case, player name should not be included
                else {
                    stmt = connection.prepareStatement("DELETE FROM `player_activity` WHERE xpos > ? AND xpos < ? AND ypos > ? AND ypos < ? AND world_name = ? AND datetime >= '" + startdate + "' AND datetime <= '" + enddate + "'");
                }
                stmt.setInt(1, x1);
                stmt.setInt(2, x2);
                stmt.setInt(3, y1);
                stmt.setInt(4, y2);
                stmt.setString(5, world.getName());

                stmt.execute();
            } catch (Exception e) {
                sender.sendRichMessage("<red> Error Deleting Player Data: " + e.getMessage());
            }

            return 0;
        });

        for (HeatmapLayer layer : database.getHeatmapLayers().values()) {
            // skip layers that are not in the world
            if (!layer.world.equals(world.getName())) continue;

            // For cell recreation, since data is simply missing, we have to use the expensive operation of "recreating" the map to ensure data is accurate.

            long start = System.nanoTime();

            // Recreate cells
            // first, delete old cells to avoid duplication errors (also we simply don't need them)
            DynWrapper.deleteAreaSet(layer.label);
            // recreate set after deletion
            MarkerSet layerSet = DynWrapper.getAreaSetOrCreate(layer.label);
            // redivide world
            DynWrapper.divideWorld(layerSet, layer.world, layer.topLeft, layer.bottomRight, layer.divisions);


            // a special case must be used to get non-updating heatmap's from and to dates
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
                    pollHeatmapCommandFunction(sender, layer.label, dates[0],dates[1]);
                } catch (Exception e) {
                    sender.sendRichMessage("<b>Date Range: <red>Error Getting Data");
                    sender.sendRichMessage("<red>" + e.getMessage());
                }
            } else {
                pollHeatmapCommandFunction(sender, layer.label, null, null);
            }

            sender.sendRichMessage("<green>Re-polled Layer <blue>("+Duration.ofNanos(System.nanoTime()-start).toMillis()+" ms)");
        }

        // Once that is done, we can resume polling
        PlayerActivityPoller.resumePolling();

    }

}
