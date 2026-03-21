package org.bc.mMheatmap;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import org.bukkit.command.CommandSender;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.MarkerSet;
import org.joml.Vector2d;
import org.joml.Vector3d;

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

    /**
     * Builds the full "/mmheatmap" command tree and initializes the the Dynmap API
     * for the rest of the class to use.
     */
    public HeatmapCommand(DynmapCommonAPI api) {
        // Initializes the Dynmap wrapper object to simplify Dynmap Api calls
        dynWrapper = new DynWrapper(api);

        // Command root
        LiteralArgumentBuilder<CommandSourceStack> commandRoot = Commands.literal("mmheatmap");

        // Command subtrees
        LiteralArgumentBuilder<CommandSourceStack> placeRadius = Commands.literal("placeRadius")
                .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                    .then(Commands.argument("width", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("height", DoubleArgumentType.doubleArg())
                            .executes(context -> {

                                // parse position from command arguments
                                BlockPositionResolver resolver = context.getArgument("pos", BlockPositionResolver.class);
                                BlockPosition pos = resolver.resolve(context.getSource());
                                double x = pos.x();
                                double y = pos.y();
                                double z = pos.z();

                                double width = DoubleArgumentType.getDouble(context, "width");
                                double height = DoubleArgumentType.getDouble(context, "height");

                                // TODO: Get the world from the command sender
                                String world = "world";


                                CommandSender sender = context.getSource().getSender();

                                try {
                                    MarkerSet heatmapOverlay = DynWrapper.getAreaSetOrCreate("heatmapOverlay");
                                    DynWrapper.createCircleArea(heatmapOverlay, "area", world, new Vector3d(x,y,z), width, height);
                                } catch (Exception e) {
                                    System.err.printf("Failed To Create Marker: %s\n", e.getMessage());
                                    sender.sendRichMessage("<red>Failed Creating Dynmap Marker; See Console");
                                    return -1;
                                }

                                sender.sendRichMessage("Executing <blue>\"placeRadiusHere\"");
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                );

        LiteralArgumentBuilder<CommandSourceStack> placeGrid = Commands.literal("placeGrid")
                .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                        .then(Commands.argument("cellsizesq", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("cellcountsq", DoubleArgumentType.doubleArg())
                                        .executes(context -> {

                                            CommandSender sender = context.getSource().getSender();
                                            sender.sendRichMessage("<yellow> Command Is Currently Disabled.");
                                            return 1;
                                            /*
                                            // parse position from command arguments
                                            BlockPositionResolver resolver = context.getArgument("pos", BlockPositionResolver.class);
                                            BlockPosition pos = resolver.resolve(context.getSource());
                                            double x = pos.x();
                                            double y = pos.y();
                                            double z = pos.z();

                                            double cellsizeSq = DoubleArgumentType.getDouble(context, "cellsizesq");
                                            double cellcountSq = DoubleArgumentType.getDouble(context, "cellcountsq");

                                            // TODO: Get the world from the command sender
                                            String world = "world";


                                            try {
                                                MarkerSet set = DynWrapper.getAreaSetOrCreate("heatmap");
                                                DynWrapper.createGrid(set, cellsizeSq, (int)cellcountSq, new Vector2d(x, y));
                                            } catch (Exception e) {
                                                System.err.printf("Failed To Create Marker: %s\n", e.getMessage());
                                                sender.sendRichMessage("<red>Failed Creating Dynmap Marker; See Console");
                                                return -1;
                                            }

                                            sender.sendRichMessage("Executing <blue>\"placeGrid\"");

                                            return Command.SINGLE_SUCCESS;
                                            */
                                        })
                                )
                        )
                );

        LiteralArgumentBuilder<CommandSourceStack> divideWorld = Commands.literal("divideWorld")
                .then(Commands.argument("topleftpos", ArgumentTypes.blockPosition())
                        .then(Commands.argument("bottomrightpos", ArgumentTypes.blockPosition())
                                .then(Commands.argument("divisioncountsq", IntegerArgumentType.integer(1))
                                        .executes(context -> {


                                            // parse position from command arguments
                                            BlockPositionResolver resolver = context.getArgument("topleftpos", BlockPositionResolver.class);
                                            BlockPosition pos1 = resolver.resolve(context.getSource());
                                            Vector3d xyz1 = new Vector3d(pos1.x(),pos1.y(),pos1.z());

                                            BlockPositionResolver resolver2 = context.getArgument("bottomrightpos", BlockPositionResolver.class);
                                            BlockPosition pos2 = resolver2.resolve(context.getSource());
                                            Vector3d xyz2 = new Vector3d(pos2.x(),pos2.y(),pos2.z());

                                            int divisionCount = IntegerArgumentType.getInteger(context, "divisioncountsq");

                                            // TODO: Get the world from the command sender
                                            String world = "world";

                                            CommandSender sender = context.getSource().getSender();

                                            try {
                                                MarkerSet set = DynWrapper.getAreaSetOrCreate("heatmap");
                                                DynWrapper.divideWorld(set, xyz1, xyz2, divisionCount);
                                            } catch (Exception e) {
                                                System.err.printf("Failed To Create Marker: %s\n", e.getMessage());
                                                sender.sendRichMessage("<red>Failed Creating Dynmap Marker; See Console");
                                                return -1;
                                            }

                                            sender.sendRichMessage("Executing <blue>\"divideWorld\"");

                                            return Command.SINGLE_SUCCESS;

                                        })
                                )
                        )
                );
        // Append subtrees
        commandRoot.then(placeRadius);
        commandRoot.then(placeGrid);
        commandRoot.then(divideWorld);

        // Build command
        builtCommand = commandRoot.build();
    }

    public static LiteralCommandNode<CommandSourceStack> getBuiltCommand() {
        return builtCommand;
    }
}
