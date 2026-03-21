package org.bc.MMheatmap;


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
import org.dynmap.markers.MarkerSet;
import org.joml.Vector3d;

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

    /**
     * Builds the full "/mmheatmap" command tree and initializes the the Dynmap API
     * for the rest of the class to use.
     */
    public HeatmapCommand(DynmapCommonAPI api) {
        // Initializes the Dynmap wrapper object to simplify Dynmap Api calls
        dynWrapper = new DynWrapper(api);

        // Command root
        LiteralArgumentBuilder<CommandSourceStack> commandRoot = Commands.literal("mmheatmap");

        LiteralArgumentBuilder<CommandSourceStack> divideWorld = Commands.literal("create")
                .then(Commands.literal("divideWorld")
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
                                    sender.sendRichMessage("<blue> Dividing World");

                                    long startTime = System.nanoTime();

                                    try {
                                        MarkerSet set = DynWrapper.getAreaSetOrCreate("heatmap");
                                        DynWrapper.divideWorld(set, xyz1, xyz2, divisionCount);
                                    } catch (Exception e) {
                                        System.err.printf("Failed To Create Marker: %s\n", e.getMessage());
                                        sender.sendRichMessage("<red>Failed Creating Dynmap Marker; See Console");
                                        return -1;
                                    }

                                    sender.sendRichMessage("<color:#30f000> Done! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms)");

                                    return Command.SINGLE_SUCCESS;

                                })
                            )
                        )
                    )
                );

        LiteralArgumentBuilder<CommandSourceStack> deleteHeatmap = Commands.literal("deleteCurrentHeatmap")
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    sender.sendRichMessage("<blue> Deleting Heatmap Overlay");

                    long startTime = System.nanoTime();
                    // delete here
                    DynWrapper.deleteAreaSet("heatmap");

                    sender.sendRichMessage("<color:#30f000> Done! (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-startTime) + "ms)");

                    return Command.SINGLE_SUCCESS;
                });

        // Append subtrees
        commandRoot.then(divideWorld);
        commandRoot.then(deleteHeatmap);

        // Build command
        builtCommand = commandRoot.build();
    }

    public static LiteralCommandNode<CommandSourceStack> getBuiltCommand() {
        return builtCommand;
    }
}
