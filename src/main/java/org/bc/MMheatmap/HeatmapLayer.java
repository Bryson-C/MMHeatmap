package org.bc.MMheatmap;


import org.joml.Vector3d;

import java.util.Scanner;


/**
 * A container with all heatmap information so that the command arguments can be shortened.
 *
 * Once a command is used to create a heatmap, it will be saved in this format. The format will
 * be saved to the disk so it can be reloaded if the server restarts
 */
public class HeatmapLayer {
    String label, id;
    Vector3d topLeft, bottomRight;
    int divisions;
    String world;

    public HeatmapLayer(String id, String label, Vector3d pos_one, Vector3d pos_two, int divisions, String world) {
        this.id = id;
        this.label = label;
        this.topLeft = pos_one;
        this.bottomRight = pos_two;
        this.divisions = divisions;
        this.world = world;
    }

    public HeatmapLayer(String id, String label, String pos_one, String pos_two, int divisions, String world) {
        this.id = id;
        this.label = label;
        this.divisions = divisions;
        this.world = world;

        String[] p1 = pos_one.strip().split(",");
        this.topLeft = new Vector3d(Double.parseDouble(p1[0]), Double.parseDouble(p1[1]), Double.parseDouble(p1[2]));

        String[] p2 = pos_one.strip().split(",");
        this.bottomRight = new Vector3d(Double.parseDouble(p2[0]), Double.parseDouble(p2[1]), Double.parseDouble(p2[2]));
    }

    public static String vec3dToString(Vector3d pos) {
        return String.format("%.0f,%.0f,%.0f", pos.x, pos.y, pos.z);
    }
}
