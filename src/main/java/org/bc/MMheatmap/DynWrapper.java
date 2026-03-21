package org.bc.MMheatmap;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.CircleMarker;
import org.dynmap.markers.MarkerSet;
import org.joml.Vector2d;
import org.joml.Vector3d;


/**
 * A wrapper object to create dynmap shapes easier
 *
 * @author bc/Exo
 */
public class DynWrapper {
    private static DynmapCommonAPI api;


    /**
     * Constructor setting the Dynmap Api to a given api variable to be used within the entire class
     *
     * @param api The Dynmap Api object to be saved within the class for later method calls
     */
    public DynWrapper(DynmapCommonAPI api) {
        DynWrapper.api = api;
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

    public static void createCircleArea(MarkerSet set, String label, String worldName, Vector3d xyz, double width, double height) {
        String id = "id";
        set.createCircleMarker(id, label, false, worldName, xyz.x(), xyz.y(), xyz.z(), width, height, false);

        int index = 0;
        for (CircleMarker mark : set.getCircleMarkers()) {
            if (mark.getLabel() == label) {
                System.out.printf("Debug: Found Marker %s @ index %d\n", label, index);
            }
            index++;
        }

    }

    /**
     * This Method is currently deprecated, simply for testing purposes.
     *
     * @param set
     * @param sizeSq
     * @param countSq
     * @param xy
     */
    @Deprecated()
    public static void createGrid(MarkerSet set, double sizeSq, int countSq, Vector2d xy) {
        double x = xy.x(), y = xy.y();
        for (int i = 0; i < countSq*countSq; i++) {
            int indexX = i % countSq, indexY = i / countSq;
            double cellX = x + (indexX*sizeSq);
            double cellY = y + (indexY*sizeSq);
            double[] xy1 = new double[]{cellX,cellY};
            double[] xy2 = new double[]{cellX+sizeSq, cellY+sizeSq};
            String id = indexX + "," + indexY;
            set.createAreaMarker(id, "Cell "+id, false, "world", xy2, xy1, false);
        }
        for (AreaMarker marker : set.getAreaMarkers()) {
            //opacity, color?
            marker.setFillStyle(0.5, 65280);
        }

    }

    /**
     * From top left coordinate: (x1,y1) to bottom left coordinate: (x2,y2)
     *
     * @param set
     * @param topLeft
     * @param bottomRight
     * @param divisionCountSq
     */
    public static void divideWorld(MarkerSet set, Vector3d topLeft, Vector3d bottomRight, int divisionCountSq) {
        // get coords in an easier format
        double x1 = topLeft.x(), y1 = topLeft.y(), z1 = topLeft.z();
        double x2 = bottomRight.x(), y2 = bottomRight.y(), z2 = bottomRight.z();

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

            // id, label, processLabelAsHtml???, world, [list of x coords], [list of y coords], persistent?
            set.createAreaMarker(indexX+","+indexY, "Cell " + indexX + ", " +indexY, false, "world", xy1, xy2, false);

            System.out.printf("Placing Square: %.1f %.1f -> %.1f %.1f\n", xy1[0],xy1[1],xy2[0],xy2[1]);
        }

        for (AreaMarker marker : set.getAreaMarkers()) {
            //opacity, color?
            marker.setFillStyle(0.5, 65280);
        }
    }


}
