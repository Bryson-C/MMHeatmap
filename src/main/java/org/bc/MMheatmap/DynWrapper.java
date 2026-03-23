package org.bc.MMheatmap;

import org.bukkit.configuration.file.FileConfiguration;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.MarkerSet;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.io.File;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;


/**
 * A wrapper object to create dynmap shapes easier
 *
 * @author bc/Exo
 */
public class DynWrapper {
    private static DynmapCommonAPI api;

    /**
     * Constructor setting the Dynmap Api to a given api and config variable to be used within the entire class
     *
     * @param api The Dynmap Api object to be saved within the class for later method calls
     * @param config The plugin config to be used within the wrapper
     */
    public DynWrapper(DynmapCommonAPI api) {
        DynWrapper.api = api;
    }

    /**
     * Given an red, green, and blue color, from 0-255, it will convert the color into a packed integer
     *
     * @param r the amount of red from 0 - 255
     * @param g the amount of green from 0 - 255
     * @param b the amount of blue from 0 - 255
     * @return returns the red, green, blue color as a packed integer
     */
    private static int rgbToInteger(int r, int g, int b) {
        return ((r&0x0ff)<<16)|((g&0x0ff)<<8)|(b&0x0ff);
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
    public static void divideWorld(MarkerSet set, String world, Vector3d topLeft, Vector3d bottomRight, int divisionCountSq) {
        // get coords in an easier format
        double x1 = topLeft.x(), z1 = topLeft.z();
        double x2 = bottomRight.x(), z2 = bottomRight.z();

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
            set.createAreaMarker(indexX+","+indexY, "Cell " + indexX + ", " +indexY, false, world, xy1, xy2, true);
        }

        for (AreaMarker marker : set.getAreaMarkers()) {
            //opacity, hex color
            marker.setFillStyle(0.5, rgbToInteger(0,0,200));
            //weight, opacity, hex color
            marker.setLineStyle(1, 0.5, rgbToInteger(0,0,255));
        }
    }

    /**
     * Will get
     * @param topLeft
     * @param bottomRight
     * @param divisionCountSq
     * @param position
     * @return
     */
    public static int[] getDividedWorldCellFromPosition(Vector3d topLeft, Vector3d bottomRight, int divisionCountSq, Vector3d position) {

        double x1 = topLeft.x(), z1 = topLeft.z();
        double x2 = bottomRight.x(), z2 = bottomRight.z();

        // use math to get total size of area (in 2d, y coordinate doesnt matter)
        double width = Math.abs(x1-x2), height = Math.abs(z1-z2);

        // get the amount of space each tile should take in the full area
        double cellSizeWidth = (width/divisionCountSq), cellSizeHeight = (height/divisionCountSq);


        int x = (int)Math.floor((divisionCountSq/width)*position.x)+(divisionCountSq/2),
            y = (int)Math.floor((divisionCountSq/width)*position.z)+(divisionCountSq/2);


        return new int[]{x,y};
    }

}
