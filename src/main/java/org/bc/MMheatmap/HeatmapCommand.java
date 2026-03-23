package org.bc.MMheatmap;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.dynmap.markers.MarkerSet;
import org.joml.Vector3d;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
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
                                Vector3d xyz1 = new Vector3d(pos1.x(),pos1.y(),pos1.z());

                                BlockPositionResolver resolver2 = context.getArgument("bottomrightpos", BlockPositionResolver.class);
                                BlockPosition pos2 = resolver2.resolve(context.getSource());
                                Vector3d xyz2 = new Vector3d(pos2.x(),pos2.y(),pos2.z());

                                int divisionCount = IntegerArgumentType.getInteger(context, "divisioncountsq");

                                CommandSender sender = context.getSource().getSender();

                                String world = (sender instanceof Player player) ? player.getWorld().getName() : config.getString("defaults.world_name");

                                long startTime = System.nanoTime();

                                try {
                                    try {
                                        database.insertNewHeatmapLayer(new HeatmapLayer(heatmapName, heatmapName, xyz1, xyz2, divisionCount, world));
                                        sender.sendRichMessage("<blue> Dividing World");
                                        MarkerSet set = DynWrapper.getAreaSetOrCreate(heatmapName);
                                        DynWrapper.divideWorld(set, world, xyz1, xyz2, divisionCount);
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

                                sender.sendRichMessage("Created Heatmap <blue>\""+heatmapName+"\"<reset>(<color:#30f000>" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms<reset>)");

                                return Command.SINGLE_SUCCESS;

                            })
                        )
                    )
                )
            );

        // Deletion Command
        LiteralArgumentBuilder<CommandSourceStack> deleteLayer = Commands.literal("delete")
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {

                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<blue> Deleting Heatmap Overlay");

                    long startTime = System.nanoTime();

                    String layerName = StringArgumentType.getString(context, "name");

                    // delete here
                    DynWrapper.deleteAreaSet(layerName);
                    try {
                        database.deleteHeatmapLayer(layerName, layerName);
                    } catch (HeatmapDatabase.NoSuchLayerException e) {
                        sender.sendRichMessage("<red>Layer \"" +layerName+ "\" Does Not Exist");
                        // Success since command functioned as intended
                        return Command.SINGLE_SUCCESS;
                    } catch (Exception e) {
                        sender.sendRichMessage("<red>Unknown Error Occurred Trying To Delete Layer \"" +layerName+ "\"");
                    }

                    sender.sendRichMessage("<color:#30f000> Done! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms)");

                    return Command.SINGLE_SUCCESS;
                })
            );

        // Testing Getting Specific Tile From Position
        LiteralArgumentBuilder<CommandSourceStack> getCellFromPos = Commands.literal("heatmapCellFromPos")
            .then(Commands.argument("name", StringArgumentType.string())
                .then(Commands.argument("position", ArgumentTypes.blockPosition())
                    .executes(context -> {
                        CommandSender sender = context.getSource().getSender();

                        BlockPositionResolver resolver = context.getArgument("position", BlockPositionResolver.class);
                        BlockPosition pos = resolver.resolve(context.getSource());
                        Vector3d at = new Vector3d(pos.x(),pos.y(),pos.z());

                        HeatmapLayer


                        int[] cellIndex = DynWrapper.getDividedWorldCellFromPosition(tl, br, divisionCount, at);

                        sender.sendRichMessage("Cell Of <red>" + at.x() + "<reset>, <green>" + at.y() + "<reset>, <blue>" + at.z() + "<reset>: (" + cellIndex[0] + ", " + cellIndex[1] + ")");

                        return Command.SINGLE_SUCCESS;
                    })
                )
            );

        // Get/List Heatmap Layers
        LiteralArgumentBuilder<CommandSourceStack> getHeatmapLayers = Commands.literal("heatmapLayers")
                .executes(context -> {

                    CommandSender sender = context.getSource().getSender();

                    long startTime = System.nanoTime();

                    ArrayList<HeatmapLayer> layers = database.getHeatmapLayers();
                    if (!layers.isEmpty()) {
                        sender.sendRichMessage("<u><b>Heatmap Layers:");
                        for (HeatmapLayer layer : layers) {
                            String message = String.format(
                                    " - <blue><b> %s <reset>(id: <blue><b>%s<reset>) -- <color:#ff00f9>(%.2f %.2f %.2f)<reset> to <color:#ff00f9>(%.2f, %.2f, %.2f) <reset> in world <color:#30f000><b>%s",
                                    layer.label, layer.id,
                                    layer.topLeft.x, layer.topLeft.y, layer.topLeft.z,
                                    layer.bottomRight.x,layer.bottomRight.y,layer.bottomRight.z,
                                    layer.world
                            );
                            // TODO: Strike through text if the world is not the current world
                            sender.sendRichMessage(message);
                            sender.sendRichMessage("Query Took <reset>(<color:#30f000>" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms<reset>)");
                        }
                    } else {
                        sender.sendRichMessage("No heatmap layers found!");
                        return -1;
                    }




                    return Command.SINGLE_SUCCESS;
                });



        // Append subtrees
        LiteralArgumentBuilder<CommandSourceStack> createHeatmap = Commands.literal("create");
        createHeatmap.then(divideWorld);
        commandRoot.then(createHeatmap);

        LiteralArgumentBuilder<CommandSourceStack> getCommand = Commands.literal("get");
        getCommand.then(getCellFromPos);
        getCommand.then(getHeatmapLayers);
        commandRoot.then(getCommand);
        commandRoot.then(deleteLayer);

        // Build command
        builtCommand = commandRoot.build();
    }

    public static LiteralCommandNode<CommandSourceStack> getBuiltCommand() {
        return builtCommand;
    }
}
