package org.bc.MMheatmap;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;


/**
 * A class used for database connections. When given a config file which must contain
 * - database.address (example: "jdbc:mysql://address:port/databaseName")
 * - database.username
 * - database.password
 *
 * @author BC/Exo
 */
public class HeatmapDatabase {
    private static HikariDataSource dataSource;
    // try to keep this up to date with each deletion and insertion of the database,
    // this will save a lot of time, allow user to resync with database
    private static Map<String, HeatmapLayer> cachedLayers;

    /**
     * Creates a database data source from a given config file
     *
     * Implementation From: https://docs.papermc.io/paper/dev/using-databases/
     *
     * @param configFile The plugins config file containing database info
     *
     * @see HeatmapDatabase
     */
    public HeatmapDatabase(FileConfiguration configFile) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(configFile.getString("database.address")); // Address of your running MySQL database
        config.setUsername(configFile.getString("database.username")); // Username
        config.setPassword(configFile.getString("database.password")); // Password
        config.setMaximumPoolSize(10); // Pool size defaults to 10
        config.addDataSourceProperty("", ""); // MISC settings to add

        dataSource = new HikariDataSource(config);
    }

    /**
     * Gets all heatmap layers from the database and returns them as HeatmapLayer objects
     * @return Returns an array list of HeatmapLayer objects
     *
     * @see HeatmapLayer
     */
    public ArrayList<HeatmapLayer> getHeatmapLayers() {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT `dyn_id`, `dyn_label`, `point_one_coords`, `point_two_coords`, `divisions`, `world_name` From `heatmap_layers`");
            ResultSet result = statement.executeQuery();

            ArrayList<HeatmapLayer> layers = new ArrayList<>();
            while (result.next()) {
                HeatmapLayer layer = new HeatmapLayer(
                        result.getString("dyn_id"),
                        result.getString("dyn_label"),
                        result.getString("point_one_coords"),
                        result.getString("point_two_coords"),
                        result.getInt("divisions"),
                        result.getString("world_name")
                );
                layers.add(layer);
            }

            connection.close();

            return layers;
        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
        return null;
    }

    /**
     * Used to notify that a layer under some name already exists
     */
    class DuplicateLayerException extends Exception {
        public DuplicateLayerException(String m) {
            super(m);
        }
    }

    /**
     * Inserts a new heatmap layer into the database. This code will not run, and throws an exception if a layer under the layer label and id already exists.
     * If layer already exists, the function will throw a DuplicateLayerException to be handled outside of this functions
     *
     * NOTE: When inserting a new layer, if the name or id (in this case both since I use the label as the id) is the same as an existing layer,
     * and the division count is the same, nothing happens on the dynmap side.
     *
     * @param layer A heatmap layer containing necessary information to be stored onto the database
     *
     * @throws DuplicateLayerException When layer already exists within the database
     */
    public void insertNewHeatmapLayer(HeatmapLayer layer) throws DuplicateLayerException {
        // first, check to see if a layer already exists, if so, throw DuplicateLayerException, and handle the error in the heatmap command
        for (HeatmapLayer l : getHeatmapLayers()) {
            if (l.label.equals(layer.label) || l.id.equals(layer.label)) {
                throw new DuplicateLayerException("Another layer of the same name exists");
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO `heatmap_layers`(`dyn_id`, `dyn_label`, `point_one_coords`, `point_two_coords`, `divisions`, `world_name`) VALUES (?,?,?,?,?,?)");

            statement.setString(1, layer.id);
            statement.setString(2, layer.label);
            statement.setString(3, HeatmapLayer.vec3dToString(layer.topLeft));
            statement.setString(4, HeatmapLayer.vec3dToString(layer.bottomRight));
            statement.setInt(5, layer.divisions);
            statement.setString(6, layer.world);

            statement.execute();

        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
    }

    /**
     * An exception used to signal that a heatmap layer does not exist
     */
    class NoSuchLayerException extends Exception {
        public NoSuchLayerException(String m) {
            super(m);
        }
    }

    /**
     * Deletes a heatmap layer from the database if it exists, otherwise throws a NoSuchLayerException to be handled outside of this function
     *
     * @param id a heatmap id, generally within this codebase, the id and label will be the same
     * @param label a heatmap label, generally within this codebase, the id and label will be the same
     * @throws NoSuchLayerException When the given layer does not exist under the id and label (which are treated as the same in this codebase)
     */
    public void deleteHeatmapLayer(String id, String label) throws NoSuchLayerException {

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM `heatmap_layers` WHERE dyn_id = ? AND dyn_label = ?");

            statement.setString(1, id);
            statement.setString(2, label);


            if (!statement.execute()) {
                throw new NoSuchLayerException("No Such Layer Exists, Cannot Delete Layer");
            }
        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
    }
}
