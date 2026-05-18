package org.bc.MMheatmap;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.bukkit.configuration.file.FileConfiguration;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerSet;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.awt.*;
import java.awt.geom.Area;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;


/**
 * A wrapper object to create dynmap shapes and make functionality easier to work with
 *
 * @author bc/Exo
 */
public class DynWrapper {
    private static DynmapCommonAPI api;
    private static ArrayList<Color> colors;
    private static Logger logger;

    /**
     * Constructor setting the Dynmap Api to a given api and config variable to be used within the entire class
     *
     * @param api The Dynmap Api object to be saved within the class for later method calls
     * @param logger The plugin's logger to not flood the standard out
     */
    public DynWrapper(DynmapCommonAPI api, Logger logger) {
        DynWrapper.api = api;
        DynWrapper.colors = createColorArray();
        DynWrapper.logger = logger;
    }

    /**
     * Changes the dynmap wrapper's config settings to the plugin's config
     */
    public static void applyConfig() {
        DynWrapper.colors = createColorArray();
    }

    /**
     * Creates a list of colors based on the config, or defaults to red
     *
     * @return Returns either:
     *          1. A list of colors,
     *          2. A list of a color and a duplicate color (if only 1 color is set in the config)
     *          3. If the other options fail, default to an array of 2 red colors
     */
    private static ArrayList<Color> createColorArray() {
        ArrayList<Color> colors = new ArrayList<>();
        String colorConfig = HeatmapConfig.getCellActivityGradient();
        // this will make sure that if the colorConfig can not be gotten, the process will continue, and be handled
        // by the default case down in "if (colors.isEmpty())..."
        if (colorConfig == null) { colorConfig = ""; }
        // NOTE: THERE MUST BE A SPECIAL CASE FOR 1 ELEMENT COLOR ARRAY!!!!
        //       THE MATH DOES NOT SUPPORT 1 ELEMENT ARRAYS BY DEFAULT!!!!
        // SPECIAL CASE: 1 ELEMENT COLOR ARRAY WILL DUPLICATE THE FIRST COLOR
        for (String hex : colorConfig.split(",")) {
            colors.add(hexToColor(hex));
        }

        if (colors.isEmpty()) {
            // if there are no colors, default to 0
            colors.add(hexToColor("#ff0000"));
            colors.add(hexToColor("#ff0000"));
        } else if (colors.size() < 2) {
            // duplicate first color
            colors.add(colors.get(0));
        }
        return colors;
    }

    /**
     * Given an red, green, and blue color, from 0-255, it will convert the color into a packed integer
     *
     * @param r the amount of red from 0 - 255
     * @param g the amount of green from 0 - 255
     * @param b the amount of blue from 0 - 255
     * @return returns the red, green, blue color as a packed integer
     */
    public static int rgbToInteger(int r, int g, int b) {
        return ((r&0x0ff)<<16)|((g&0x0ff)<<8)|(b&0x0ff);
    }

    /**
     * Given a color `c`, this will return a packed integer value of the color
     * @param c The color to be transformed to a packed integer
     * @return Returns a packed integer from a color `c`
     */
    public static int colorToInteger(Color c) {
        return rgbToInteger(c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Takes a hexcode and converts it to a Java Color object
     *
     * @param hexCode The hexcode of a color
     * @return returns a Color object
     *
     * @see java.awt.Color
     */
    public static Color hexToColor(String hexCode) {
        return Color.decode(hexCode);
    }

    /**
     * Modified From: https://www.alanzucconi.com/2016/01/06/colour-interpolation/
     * @param a
     * @param b
     * @param t
     * @return Returns a color interpolated from 2 colors t amount
     */
    private static Color lerpRGB (Color a, Color b, double t)  {
        float ar = a.getRed()/255.f, ag = a.getGreen()/255.f, ab = a.getBlue()/255.f, aa = a.getAlpha()/255.f;
        float br = b.getRed()/255.f, bg = b.getGreen()/255.f, bb = b.getBlue()/255.f, ba = b.getAlpha()/255.f;
        return new Color
                (
                        (float)(ar + (br - ar) * t),
                        (float)(ag + (bg - ag) * t),
                        (float)(ab + (bb - ab) * t),
                        (float)(aa + (ba - aa) * t)
                );
    }

    /**
     * If the marker set already exists, the method will return that set, otherwise, it will create a set under that name and return it.
     *
     * @param areaSetName the name of the set to either get, or to be created and returned
     * @return Returns a previously created set, or a new set under the given "areaSetName" parameter
     */
    public static MarkerSet getAreaSetOrCreate(String areaSetName) {
        MarkerSet set = api.getMarkerAPI().getMarkerSet(areaSetName);
        if (set == null)
            // id, label, iconPointer, persistantflag
            set = api.getMarkerAPI().createMarkerSet(areaSetName, areaSetName, null, true);

        set.setHideByDefault(true);
        return set;
    }

    /**
     * Deletes an areaSet with the label "areaSetName"
     *
     * Implementation from https://github.com/webbukkit/dynmap/blob/93b454efb8802dc7406d6873434f2aeec5c636f4/DynmapCore/src/main/java/org/dynmap/markers/impl/MarkerAPIImpl.java#L2112
     *
     * @param areaSetName The label of the areaSet to be deleted
     */
    public static void deleteAreaSet(String areaSetName) {
        //
        // this is the same implementation (or should be) as "/dmarker deleteset label:"areaSetName" "
        Set<MarkerSet> sets = api.getMarkerAPI().getMarkerSets();
        MarkerSet set = null;
        for(MarkerSet s : sets) {
            if(s.getMarkerSetLabel().equals(areaSetName)) {
                set = s;
                break;
            }
        }
        if (set != null)
            set.deleteMarkerSet();
    }

    /**
     * From top left coordinate: (x1,y1) to bottom left coordinate: (x2,y2)
     *
     * @param set
     * @param topLeft
     * @param bottomRight
     * @param divisionCountSq
     */
    public static void divideWorld(MarkerSet set, String world, Vector2d topLeft, Vector2d bottomRight, int divisionCountSq) {
        // get coords in an easier format
        double x1 = topLeft.x(), z1 = topLeft.y();
        double x2 = bottomRight.x(), z2 = bottomRight.y();

        // use math to get total size of area (in 2d, y coordinate doesnt matter)
        double width = Math.abs(x1-x2);
        double height = Math.abs(z1-z2);

        // get the amount of space each tile should take in the full area
        double cellSizeWidth = (width/divisionCountSq);
        double cellSizeHeight = (height/divisionCountSq);

        for (int i = 0; i < divisionCountSq*divisionCountSq; i++) {
            int indexX = i%divisionCountSq, indexY = i/divisionCountSq;

            //area marker format
            //x: [x1, x2, ... xN]
            //y: [y1, y2, ... yN]

            double[] xy1 = new double[]{(indexX*cellSizeWidth)-(width/2), ((indexX*cellSizeWidth) + cellSizeWidth)-(width/2)};
            double[] xy2 = new double[]{(indexY*cellSizeHeight)-(height/2), (((indexY*cellSizeHeight) + cellSizeHeight))-(height/2)};

            // id, label, processLabelAsHtml, world, [list of x coords], [list of y coords], persistent
            // NOTE: To create a grid, this functionality should be used, since this is not desired (currently, simply comment it out)
            //set.createAreaMarker(indexX+","+indexY, "Cell " + indexX + ", " +indexY, false, world, xy1, xy2, true);
        }
        // Commented out as this will be unused
/*
        for (AreaMarker marker : set.getAreaMarkers()) {
            //opacity, hex color
            marker.setFillStyle(0.5, rgbToInteger(0,0,200));
            //weight, opacity, hex color
            marker.setLineStyle(1, 0.5, rgbToInteger(0,0,255));
        }
 */
    }

    /**
     * Will get the cell that the coordinates fall in
     *
     * @param topLeft The top-left point (x,y)
     * @param bottomRight The bottom-right point (x,y)
     * @param divisionCountSq The amount of cells horizontally and vertically to add to the layer (i.e. divisionCountSq * divisionCountSq)
     * @param position The point to find the cell of
     * @return The indices [x,y] of the cell
     */
    public static int[] getDividedWorldCellFromPosition(Vector2d topLeft, Vector2d bottomRight, int divisionCountSq, Vector2d position) {

        double x1 = topLeft.x(), z1 = topLeft.y();
        double x2 = bottomRight.x(), z2 = bottomRight.y();

        // use math to get total size of area (in 2d, y coordinate doesnt matter)
        double width = Math.abs(x1-x2), height = Math.abs(z1-z2);

        // get the amount of space each tile should take in the full area
        double cellSizeWidth = (width/divisionCountSq), cellSizeHeight = (height/divisionCountSq);


        int x = (int)Math.floor((divisionCountSq/width)*position.x)+(divisionCountSq/2),
            y = (int)Math.floor((divisionCountSq/width)*position.y)+(divisionCountSq/2);


        return new int[]{x,y};
    }


    /**
     * @param layer The heatmap layer to get the cells from
     * @param x1 The block coordinates of the x position in the first point
     * @param y1 The block coordinates of the y position in the first point
     * @param x2 The block coordinates of the x position in the second point
     * @param y2 The block coordinates of the y position in the second point
     * @return Returns an array of positions which are the top left point of the top left cell in the range,
     *         and the bottom left coordinate of the bottom right cell in format: [topLeftX, topLeftY, bottomRightX, bottomRightY]
     */
    public static double[] getUpperLeftAndBottomRightCellCoordsFromPoint(HeatmapLayer layer, int x1, int y1, int x2, int y2) {
        // get the cells which the area lies over (indices1[0], indices1[1]) -> (indices2[0], indices2[1])
        int[] indices1 = DynWrapper.getDividedWorldCellFromPosition(layer.topLeft, layer.bottomRight, layer.divisions, new Vector2d(x1, y1));
        int[] indices2 = DynWrapper.getDividedWorldCellFromPosition(layer.topLeft, layer.bottomRight, layer.divisions, new Vector2d(x2, y2));

        // then get the top left of the cell furthest to the left
        int minCellIndexX = Math.min(indices1[0], indices2[0]);
        int maxCellIndexX = Math.max(indices1[0], indices2[0]);
        // and the bottom right of the cell furthest to the right
        int minCellIndexY = Math.min(indices1[1], indices2[1]);
        int maxCellIndexY = Math.max(indices1[1], indices2[1]);

        // use math to get total size of area
        double width = Math.abs(layer.topLeft.x() - layer.bottomRight.x());
        double height = Math.abs(layer.topLeft.y() - layer.bottomRight.y());

        // get the amount of space each tile should take in the full area
        double cellSizeWidth = (width / layer.divisions);
        double cellSizeHeight = (height / layer.divisions);

        // Blocks In Top Left And Bottom Right Points Of The Cells Within The Range (xy1[0], xy2[0]) -> (xy1[1], xy2[1])
        return new double[]{
                (minCellIndexX * cellSizeWidth) - (width / 2), ((maxCellIndexX * cellSizeHeight) - (height / 2)),
                ((minCellIndexY * cellSizeWidth) + cellSizeWidth) - (width / 2), (((maxCellIndexY * cellSizeHeight) + cellSizeHeight)) - (height / 2)
        };
    }


    /**
     * When given a layer, cells will be created and styled based on activity levels in said cell
     *
     * @param layer The layer to add the cells to
     * @param set The set to add the markers to, the set will be cleared before usage to avoid errors caused by duplicate ids, generally will be the same as the layer name in this codebase
     * @param points The player activity points gotten from the database
     * @param flags Additional flags to add to the function (currently unused)
     */
    public static void createActiveHeatmapCellsFromCoords(HeatmapLayer layer, MarkerSet set, Map<String, Integer> points, String flags) {
        if (points.isEmpty()) { return; }

        // clear area first to avoid duplicate cell errors
        DynWrapper.deleteAreaSet(layer.label);
        set = getAreaSetOrCreate(layer.label);

        // get coords in an easier format
        double x1 = layer.topLeft.x(), z1 = layer.topLeft.y();
        double x2 = layer.bottomRight.x(), z2 = layer.bottomRight.y();

        // use math to get total size of area (in 2d, y coordinate doesnt matter)
        double width = Math.abs(x1-x2);
        double height = Math.abs(z1-z2);

        // get the amount of space each tile should take in the full area
        double cellSizeWidth = (width/layer.divisions);
        double cellSizeHeight = (height/layer.divisions);

        Map<String, Integer> deDuplicateMap = new HashMap<>();
        for (Map.Entry<String, Integer> kv : points.entrySet()) {
            String[] p1 = kv.getKey().strip().split(",");
            double x = Double.parseDouble(p1[0]), y = Double.parseDouble(p1[1]);
            int[] index = getDividedWorldCellFromPosition(layer.topLeft, layer.bottomRight, layer.divisions, new Vector2d(x, y));

            int activity = kv.getValue();
            String key = String.format("%d,%d", index[0],index[1]);
            if (deDuplicateMap.containsKey(key)) {
                activity += deDuplicateMap.get(key);
            }

            deDuplicateMap.put(key, activity);
        }

        // Get min and max values after the tiles have been de-duplicated
        int minActivity = Integer.MAX_VALUE, maxActivity = Integer.MIN_VALUE;
        for (var x : deDuplicateMap.values()) {
            minActivity = Math.min(minActivity, x);
            maxActivity = Math.max(maxActivity, x);
        }
        layer.maxActivity = maxActivity;
        layer.minActivity = minActivity;


        for (Map.Entry<String, Integer> kv : deDuplicateMap.entrySet()) {

            // Here, the area index has already been created from the above loop, so we just need to split it up again, no more parsing required!
            String[] p1 = kv.getKey().strip().split(",");
            int[] index = { Integer.parseInt(p1[0]), Integer.parseInt(p1[1]) };

            //area marker format
            //x: [x1, x2, ... xN]
            //y: [y1, y2, ... yN]
            double[] xy1 = new double[]{(index[0]*cellSizeWidth)-(width/2), ((index[0]*cellSizeWidth) + cellSizeWidth)-(width/2)};
            double[] xy2 = new double[]{(index[1]*cellSizeHeight)-(height/2), (((index[1]*cellSizeHeight) + cellSizeHeight))-(height/2)};


            // id, label, processLabelAsHtml, world, [list of x coords], [list of y coords], persistent
            AreaMarker marker = set.createAreaMarker(index[0] + "," + index[1], "Cell " + index[0] + ", " + index[1], false, layer.world, xy1, xy2, true);


            // formula for interpolating colors in n-sized rank 1 array
            // Math from: https://computergraphics.stackexchange.com/questions/3801/how-can-you-interpolate-over-an-array-of-say-5-colors
            // and: https://math.stackexchange.com/questions/754130/find-what-percent-x-is-between-two-numbers

            // NOTE: when only 1 cell exists

            Color c;
            // here we need a special case for only 1 cell of total activity. When only 1 cell exists the cell is black because it evaluates to 0???? and multiplying a color by 0 means black in rgb
            // while the chances of this happening is incredibly low, it's still something I don't like the look of so it should be fixed
            if (deDuplicateMap.size() <= 1) {
                // percentage should simply evaluate to 100% because 1/1 equals 100%
                c = colors.getLast();
            } else {

                // get percentage between minActivity to maxActivity
                int activity = kv.getValue();
                double percentage = (activity - minActivity)/(double)(maxActivity - minActivity);

                // get the 2 colors in the array to interpolate between
                int t1 = Math.clamp((int)Math.floor(percentage * colors.size()), 0, colors.size()-1);
                int t2 = Math.clamp((int)Math.ceil(percentage * colors.size()), 0, colors.size()-1);

                // get the amount of color to apply
                double amount = percentage-(t1/(double)colors.size());
                c = lerpRGB(colors.get(t1), colors.get(t2), amount);
            }
            // finally, set the color
            // marker may be null if the area already exists under the dynmap id
            if (marker != null) {
                marker.setFillStyle(HeatmapConfig.getCellOpacity(), colorToInteger(c));
                marker.setLineStyle(1, HeatmapConfig.getBorderOpacity(), colorToInteger(hexToColor(HeatmapConfig.getBorderColor())));
                marker.setDescription("Activity: " + kv.getValue());
            } else {
                logger.warning("Error styling area marker, marker is null");
            }
        }
    }

    /**
     * Very similar functionality as DynWrapper#createActiveHeatmapCellsFromCoords except its no the whole map being update, only a portion
     * @param layer The layer to update the cells from
     * @param set The dynmap set to add the cells to, generally will be the same as the layer name in this codebase
     * @param points The activity points to be updated, this will also control what cells will be updated since the positions are in the map's key
     * @param flags Additional flags to add to the function (currently unused)
     */
    public static void updateHeatmapCellsInRange(HeatmapLayer layer, MarkerSet set, Map<String, Integer> points, String flags) {
        if (points.isEmpty()) { return; }
        // Setting min to the max, and max to the min, will ensure these values will always be set so long as there is more than 1 point
        // we do this in a separate loop so that we can apply the correct color coating to the cells and avoid doing more math than necessary.
        // Here we also need to combine nearby areas if they will both be included inside the same heatmap tile, this is to avoid duplicate errors
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        Map<String, Integer> deDuplicateMap = new HashMap<>();
        for (Map.Entry<String, Integer> kv : points.entrySet()) {
            String[] p1 = kv.getKey().strip().split(",");
            double x = Double.parseDouble(p1[0]), y = Double.parseDouble(p1[1]);
            int[] index = getDividedWorldCellFromPosition(layer.topLeft, layer.bottomRight, layer.divisions, new Vector2d(x, y));

            minX = Math.min(index[0], minX);
            minY = Math.min(index[1], minY);
            maxX = Math.max(index[0], maxX);
            maxY = Math.max(index[1], maxY);

            int activity = kv.getValue();
            String key = String.format("%d,%d", index[0],index[1]);
            if (deDuplicateMap.containsKey(key)) {
                activity += deDuplicateMap.get(key);
            }

            deDuplicateMap.put(key, activity);
        }

        // get coords in an easier format
        double x1 = layer.topLeft.x(), z1 = layer.topLeft.y();
        double x2 = layer.bottomRight.x(), z2 = layer.bottomRight.y();

        // use math to get total size of area (in 2d, y coordinate doesnt matter)
        double width = Math.abs(x1-x2);
        double height = Math.abs(z1-z2);

        // get the amount of space each tile should take in the full area
        double cellSizeWidth = (width/layer.divisions);
        double cellSizeHeight = (height/layer.divisions);

        for (Map.Entry<String, Integer> kv : deDuplicateMap.entrySet()) {

            // Here, the area index has already been created from the above loop, so we just need to split it up again, no more parsing required!
            String[] p1 = kv.getKey().strip().split(",");
            int[] index = { Integer.parseInt(p1[0]), Integer.parseInt(p1[1]) };

            //area marker format
            //x: [x1, x2, ... xN]
            //y: [y1, y2, ... yN]
            double[] xy1 = new double[]{(index[0]*cellSizeWidth)-(width/2), ((index[0]*cellSizeWidth) + cellSizeWidth)-(width/2)};
            double[] xy2 = new double[]{(index[1]*cellSizeHeight)-(height/2), (((index[1]*cellSizeHeight) + cellSizeHeight))-(height/2)};

            AreaMarker marker = set.findAreaMarker(index[0]+","+index[1]);
            if (marker == null) {
                // id, label, processLabelAsHtml, world, [list of x coords], [list of y coords], persistent
                marker = set.createAreaMarker(index[0] + "," + index[1], "Cell " + index[0] + ", " + index[1], false, layer.world, xy1, xy2, true);
            }

            // formula for interpolating colors in n-sized rank 1 array
            // Math from: https://computergraphics.stackexchange.com/questions/3801/how-can-you-interpolate-over-an-array-of-say-5-colors
            // and: https://math.stackexchange.com/questions/754130/find-what-percent-x-is-between-two-numbers

            // get percentage between minActivity to maxActivity
            int activity = kv.getValue();
            double percentage = (activity - layer.minActivity)/(double)(layer.maxActivity - layer.minActivity);

            // get the 2 colors in the array to interpolate between
            int t1 = Math.clamp((int)Math.floor(percentage * colors.size()), 0, colors.size()-1);
            int t2 = Math.clamp((int)Math.ceil(percentage * colors.size()), 0, colors.size()-1);

            // get the amount of color to apply
            double amount = percentage-(t1/(double)colors.size());
            Color c = lerpRGB(colors.get(t1), colors.get(t2), amount);

            // finally, set the color
            // marker may be null if the area already exists under the dynmap id
            if (marker != null) {
                marker.setFillStyle(HeatmapConfig.getCellOpacity(), colorToInteger(c));
                marker.setLineStyle(1, HeatmapConfig.getBorderOpacity(), colorToInteger(hexToColor(HeatmapConfig.getBorderColor())));
                marker.setDescription("Cell ("+index[0]+", "+index[1]+") Activity: " + kv.getValue());
            } else {
                logger.warning("Error styling area marker, marker is null");
            }
        }

    }
}
