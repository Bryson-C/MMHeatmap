package org.bc.MMheatmap;

import org.joml.Vector2d;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * A container with all heatmap information so that the command arguments can be shortened.
 *
 * Once a command is used to create a heatmap, it will be saved in this format
 *
 * @author BC/Exo
 */
public class HeatmapLayer {
    String label, id;
    Vector2d topLeft, bottomRight;
    int divisions;
    String world;
    // This is a bit of a confusing field, but basically, it will represent the range of time from (now-pollRangeSeconds, now)
    // to poll player date over. Given a really large number, all data will be present, otherwise, it will represent the time from
    // now to N seconds ago, where N is `pollRangeSeconds`
    int pollRangeSeconds;

    /**
     * A class which formats dates for heatmap layers.
     * The format is always: yyyy-mm-dd hh:mm:ss
      */
    static public class DateFormat {
        static public String getDateAsString(LocalDateTime date) {
            return date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        }
        static public LocalDateTime nowDate() {
            return LocalDateTime.now();
        }
        static public LocalDateTime getDateNSecondsAgo(LocalDateTime date, long seconds) {
            return date.minusSeconds(seconds);
        }
        static public LocalDateTime getDateFromString(String dateString) {
            return LocalDateTime.parse(dateString,DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        }
    }

    /**
     * Sets all the required parameters to be used in a heatmap layer
     * @param id The dynmap id (the same as the dynmap label in this codebase)
     * @param label The dynmap label
     * @param pos_one Point 1, the top left point in the layer
     * @param pos_two Point 2, the bottom right point in the layer
     * @param divisions The amount of cells to divide the world in along the x and y axis
     * @param world The name of the world where the division is being preformed
     * @param pollRangeSeconds
     */
    public HeatmapLayer(String id, String label, Vector2d pos_one, Vector2d pos_two, int divisions, String world, int pollRangeSeconds) {
        this.id = id;
        this.label = label;
        this.topLeft = pos_one;
        this.bottomRight = pos_two;
        this.divisions = divisions;
        this.world = world;
        this.pollRangeSeconds = pollRangeSeconds;
    }

    /**
     * The difference between this constructor and the other, is that this constructor will parse position points in the format:
     * "x,y" when given as a string and turn that into (x: double, y: double)
     *
     * @see HeatmapLayer#HeatmapLayer(String, String, Vector2d, Vector2d, int, String, int)  HeatmapLayer
     */
    public HeatmapLayer(String id, String label, String pos_one, String pos_two, int divisions, String world, int pollRangeSeconds) {
        this.id = id;
        this.label = label;
        this.divisions = divisions;
        this.world = world;
        this.pollRangeSeconds = pollRangeSeconds;

        String[] p1 = pos_one.strip().split(",");
        this.topLeft = new Vector2d(Double.parseDouble(p1[0]), Double.parseDouble(p1[1]));

        String[] p2 = pos_two.strip().split(",");
        this.bottomRight = new Vector2d(Double.parseDouble(p2[0]), Double.parseDouble(p2[1]));
    }

    public static String vec2dToString(Vector2d pos) {
        return String.format("%.0f,%.0f", pos.x, pos.y);
    }


    public int getPollRangeSeconds() { return pollRangeSeconds; }

    public String getLabel() {return label; }
}
