package org.bc.MMheatmap;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bc.MMheatmap.poller.PlayerChunkInteractions;
import org.bukkit.configuration.file.FileConfiguration;
import org.joml.Vector2d;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * A class used for database connections. When given a config file which must contain
 * - database.address (example: "jdbc:mysql://address:port/databaseName")
 * - database.username
 * - database.password
 *
 * Note: The database is not responsible for threading, this means that its no longer an implementation issue, but a skill issue:
 *       it's called passing blame, and it works
 *
 * @author BC/Exo
 */
public class HeatmapDatabase {
    private static HikariDataSource dataSource;

    // try to keep this up to date with each deletion and insertion of the database,
    // this will save a lot of time
    private static Map<String, HeatmapLayer> cachedLayers = new HashMap<>();
    // Try to keep this up to date, we can use a binary search to increase query speeds (I believe)

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
        resyncHeatmapDatabase();
    }

    /**
     * Clears the layer cache then gets all heatmap layers from the database and stores them in a layer cache for quicker access.
     * For users, to get layers see "HeatmapDatabase.getHeatmapLayers(...)"
     *
     * @see HeatmapLayer
     */
    private void cacheHeatmapLayers() {
        cachedLayers.clear();
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("SELECT `dyn_id`, `dyn_label`, `point_one_coords`, `point_two_coords`, `divisions`, `world_name`, `poll_range_seconds` From `heatmap_layers`");
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                String layerName = result.getString("dyn_label");
                HeatmapLayer layer = new HeatmapLayer(
                        result.getString("dyn_id"),
                        layerName,
                        result.getString("point_one_coords"),
                        result.getString("point_two_coords"),
                        result.getInt("divisions"),
                        result.getString("world_name"),
                        result.getInt("poll_range_seconds")
                );
                cachedLayers.put(layerName, layer);
            }
        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
    }


    /**
     * Returns the cached database layers, shouldn't become unsynced from the database, but in case it does,
     * "HeatmapDatabase.cachedHeatmapLayers()" should be called internally or for external resyncing call
     * "HeatmapDatabase.resyncHeatmapLayers()"
     *
     * @return The cached database layers
     */
    public Map<String, HeatmapLayer> getHeatmapLayers() {
        return cachedLayers;
    }

    /**
     * For internal use only!
     * Call preferably when the database object is created
     *
     * Checks to see if the table where heatmap layers are stored exists, if not, it creates it.
     */
    private void createLayerTableIfNotExists() {
        try (Connection connection = dataSource.getConnection()) {
            String Sql = "CREATE TABLE IF NOT EXISTS `heatmap_layers` (`id` INT NOT NULL AUTO_INCREMENT ," +
                         " `dyn_id` VARCHAR(32) NOT NULL , `dyn_label` VARCHAR(32) NOT NULL , `point_one_coords` VARCHAR(64) NOT NULL ," +
                         " `point_two_coords` VARCHAR(64) NOT NULL , `divisions` INT NOT NULL , `world_name` VARCHAR(32) NOT NULL , `poll_range_seconds` INT NOT NULL, " +
                         " `fromToDate` VARCHAR(48) NOT NULL, PRIMARY KEY (`id`)) ENGINE = InnoDB;";
            PreparedStatement statement = connection.prepareStatement(Sql);

            statement.execute();
        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
    }

    /**
     * For internal use only!
     * Call preferably when the database object is created
     *
     * Checks to see if the table where player activity is stored exists, if not, it creates it.
     */
    private void createPlayerActivityTableIfNotExists() {
        try (Connection connection = dataSource.getConnection()) {
            String Sql = "CREATE TABLE IF NOT EXISTS `player_activity` (`id` INT NOT NULL AUTO_INCREMENT ,"+
                         " `player_name` VARCHAR(64) NOT NULL , `xpos` INT NOT NULL, `ypos` INT NOT NULL , `world_name` VARCHAR(64) NOT NULL ," +
                         " `activity_level` INT NOT NULL , `datetime` DATETIME NOT NULL, PRIMARY KEY (`id`)) ENGINE = InnoDB;";
            PreparedStatement statement = connection.prepareStatement(Sql);

            statement.execute();
        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
    }


    /**
     * Clears the cached layers, then creates the cache
     */
    public void resyncHeatmapDatabase() {
        createLayerTableIfNotExists();
        createPlayerActivityTableIfNotExists();
        cacheHeatmapLayers();
    }

    /**
     * An interface used to run custom sql commands in a lambda
     * @param <T> The return type of the lambda
     */
    interface HeatmapSqlFunction<T> {
        T run(Connection connection);
    }

    /**
     * Used to run a custom sql command as a lambda
     * @param function The lambda to be executed, its given a connection as its only parameter
     * @return Returns the value of the lambda after being ran
     * @param <T> The type of the return
     */
    public <T> T executeSql(HeatmapSqlFunction<T> function) {
        try (Connection connection = dataSource.getConnection()) {
            return function.run(connection);
        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
        return null;
    }


    /**
     * Used to notify that a layer under some name already exists
     */
    public static class DuplicateLayerException extends Exception {
        public DuplicateLayerException(String m) {
            super(m);
        }
    }

    /**
     * Inserts a new heatmap layer into the database and layer cache. This code will not run, and throws an exception if a layer under the layer label and id already exists.
     * If layer already exists, the function will throw a DuplicateLayerException to be handled outside of this functions
     *
     * NOTE: When inserting a new layer, if the name or id (in this case both since I use the label as the id) is the same as an existing layer,
     * and the division count is the same, nothing happens on the dynmap side.
     *
     * @param layer A heatmap layer containing necessary information to be stored onto the database
     *
     * @throws DuplicateLayerException When layer already exists within the database
     */
    public void insertNewHeatmapLayer(HeatmapLayer layer, String fromDate, String toDate) throws DuplicateLayerException {
        // first, check to see if a layer already exists, if so, throw DuplicateLayerException, and handle the error in the heatmap command
        if (getHeatmapLayers().containsKey(layer.label)) {
            throw new DuplicateLayerException("Another layer of the same name exists");
        }

        try (Connection connection = dataSource.getConnection()) {
            // This does give a warning that sql injection may occur, but this is not true unless the developer creates a sql injection since the user has no control over the
            // from_datetime field
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO `heatmap_layers`(`dyn_id`, `dyn_label`, `point_one_coords`, `point_two_coords`, `divisions`, `world_name`, `poll_range_seconds`, `fromToDate`) VALUES (?,?,?,?,?,?,?,?)");

            statement.setString(1, layer.id);
            statement.setString(2, layer.label);
            statement.setString(3, HeatmapLayer.vec2dToString(layer.topLeft));
            statement.setString(4, HeatmapLayer.vec2dToString(layer.bottomRight));
            statement.setInt(5, layer.divisions);
            statement.setString(6, layer.world);
            statement.setInt(7, layer.pollRangeSeconds);
            statement.setString(8, fromDate+","+toDate);

            statement.execute();
            // also add the layer to the layer cache
            cachedLayers.put(layer.label, layer);

        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
    }

    /**
     * An exception used to signal that a heatmap layer does not exist
     */
    public static class NoSuchLayerException extends Exception {
        public NoSuchLayerException(String m) {
            super(m);
        }
    }

    /**
     * Deletes a heatmap layer from the database and cached layers if it exists, otherwise throws a NoSuchLayerException to be handled outside of this function
     *
     * @param name a heatmap name: either an id or label (generally within this codebase, the id and label will be the same)
     * @throws NoSuchLayerException When the given layer does not exist under the id and label (which are treated as the same in this codebase)
     */
    public void deleteHeatmapLayer(String name) throws NoSuchLayerException {
        // Check cache first
        if (!cachedLayers.containsKey(name)) {
            throw new NoSuchLayerException("No Such Layer Exists, Cannot Delete Layer");
        }

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM `heatmap_layers` WHERE dyn_id = ? AND dyn_label = ?");

            statement.setString(1, name);
            statement.setString(2, name);
            statement.execute();

            // if the layer did exist, it should be removed from the cache
            cachedLayers.remove(name);

        }
        catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
    }

    /**
     * Inserts the given parameters into the database.
     * Note: On calling the function, the date will automatically be inserted into the database at the time of the function call
     *
     * @param playerName The player who's activity is being logged
     * @param world The world where the player's activity is logged
     * @param location The location/area where the player is being active
     * @param activity The activity in a region
     */
    public void insertPlayerActivity(String playerName, String world, Vector2d location, PlayerChunkInteractions activity) {
        // Source - https://stackoverflow.com/a/7579328
        // Posted by Pratik, modified by community. See post 'Timeline' for change history
        // Retrieved 2026-03-29, License - CC BY-SA 3.0
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String nowInstant = LocalDateTime.now().format(dateFormat);

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO `player_activity`(`player_name`, `xpos`, `ypos`, `world_name`, `activity_level`, `datetime`) VALUES (?,?,?,?,?,'"+nowInstant+":00')");

            statement.setString(1, playerName);
            statement.setInt(2, (int)location.x);
            statement.setInt(3, (int)location.y);
            statement.setString(4, world);
            statement.setInt(5, activity.calculateActivityLevel());

            statement.execute();

        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }
    }

    /**
     * TODO: Implement using datetime information, and confirmation
     * @param playerName
     * @param world
     */
    public void deletePlayerActivity(String playerName, String world) {}


    /**
     * Gets all player activity within the heatmap layer's parameters:
     * - within the surface area of the top-left and bottom-right points
     * - within the poll range
     * - and within the world
     *
     * NOTE: Since maps do not support duplicate keys, more processing is required to make sure all data is included, not a big deal, nor is it really worth bringing up,
     *       but I figured I'd mention it
     *
     * @param layer The layer to get the activity from (using the parameters from the layer)
     * @return Returns the activity as a map with a key: "{xpos},{ypos}" and a value of the activity level the polling area
     */
    public Map<String, Integer> getPlayerActivityEntriesForLayer(HeatmapLayer layer) {
        Map<String, Integer> activity = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            String dateString = HeatmapLayer.DateFormat.getDateAsString(HeatmapLayer.DateFormat.getDateNSecondsAgo(HeatmapLayer.DateFormat.nowDate(),layer.pollRangeSeconds));
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT `xpos`, `ypos`, `activity_level` from `player_activity` WHERE" +
                        " xpos > ? AND xpos < ? AND ypos > ? AND ypos < ? AND datetime >= '"+dateString+"' AND world_name = ?;"
            );

            statement.setInt(1, (int)layer.topLeft.x);
            statement.setInt(2, (int)layer.bottomRight.x);
            statement.setInt(3, (int)layer.topLeft.y);
            statement.setInt(4, (int)layer.bottomRight.y);
            statement.setString(5, layer.world);

            ResultSet result = statement.executeQuery();

            // Here is not adding up duplicate areas correctly, it only takes the most recent entries
            while (result.next()) {
                String key = result.getString("xpos")+","+result.getInt("ypos");
                int activityLevel = result.getInt("activity_level");
                // if activity in an area already exists, grab that, then add the new activity, and push it back on the map
                if (activity.containsKey(key))
                    activityLevel += activity.get(key);
                // this is the case that always run, regardless if the activity area exists or not, the data (either new or updated) will be pushed back
                activity.put(key, activityLevel);
            }

        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }

        return activity;
    }

    /**
     * Functionality roughly the same as found in the "see more" function, but between 2 given points
     * @see HeatmapDatabase#getPlayerActivityEntriesForLayer(HeatmapLayer)
     */
    public Map<String, Integer> getPlayerActivityEntriesForLayerInArea(HeatmapLayer layer, Vector2d xy1, Vector2d xy2) {
        Map<String, Integer> activity = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            String dateString = HeatmapLayer.DateFormat.getDateAsString(HeatmapLayer.DateFormat.getDateNSecondsAgo(HeatmapLayer.DateFormat.nowDate(),layer.pollRangeSeconds));
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT `xpos`, `ypos`, `activity_level` from `player_activity` WHERE" +
                            " xpos > ? AND xpos < ? AND ypos > ? AND ypos < ? AND datetime >= '"+dateString+"' AND world_name = ?;"
            );

            statement.setInt(1, (int)xy1.x);
            statement.setInt(2, (int)xy2.x);
            statement.setInt(3, (int)xy1.y);
            statement.setInt(4, (int)xy2.y);
            statement.setString(5, layer.world);

            ResultSet result = statement.executeQuery();

            // Here is not adding up duplicate areas correctly, it only takes the most recent entries
            while (result.next()) {
                String key = result.getString("xpos")+","+result.getInt("ypos");
                int activityLevel = result.getInt("activity_level");
                // if activity in an area already exists, grab that, then add the new activity, and push it back on the map
                if (activity.containsKey(key))
                    activityLevel += activity.get(key);
                // this is the case that always run, regardless if the activity area exists or not, the data (either new or updated) will be pushed back
                activity.put(key, activityLevel);
            }

        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
        }

        return activity;
    }

    /**
     * Functionality roughly the same as found in the "see more" function, but between 2 dates (fromDate and toDate).
     * Dates must be in the format: "yyyy-mm-dd hh:mm:ss"
     * @see HeatmapDatabase#getPlayerActivityEntriesForLayer(HeatmapLayer)
     */
    public Map<String, Integer> getPlayerActivityEntriesForLayerBetweenDates(HeatmapLayer layer, String fromDate, String toDate) {
        Map<String, Integer> activity = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            String f = fromDate.replace("\"","");
            String t = toDate.replace("\"","");
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT `xpos`, `ypos`, `activity_level` from `player_activity` WHERE" +
                        " xpos > ? AND xpos < ? AND ypos > ? AND ypos < ? AND datetime >= '"+f+"' AND datetime <= '"+t+"' AND world_name = ?;"
            );

            statement.setInt(1, (int)layer.topLeft.x);
            statement.setInt(2, (int)layer.bottomRight.x);
            statement.setInt(3, (int)layer.topLeft.y);
            statement.setInt(4, (int)layer.bottomRight.y);
            statement.setString(5, layer.world);

            System.out.println("Query: "+statement);

            ResultSet result = statement.executeQuery();

            // Here is not adding up duplicate areas correctly, it only takes the most recent entries
            while (result.next()) {
                String key = result.getString("xpos")+","+result.getInt("ypos");
                int activityLevel = result.getInt("activity_level");
                // if activity in an area already exists, grab that, then add the new activity, and push it back on the map
                if (activity.containsKey(key))
                    activityLevel += activity.get(key);
                // this is the case that always run, regardless if the activity area exists or not, the data (either new or updated) will be pushed back
                activity.put(key, activityLevel);
            }

        } catch (Exception e) {
            // Handle any exceptions that arise from getting / handing the exception
            System.err.println("Failed Running Database Query: " + e.getMessage());
            System.err.println("If Query Failed, Double Check Time Format Is \"yyyy-mm-dd hh:mm:ss\"");
        }

        return activity;
    }
}
