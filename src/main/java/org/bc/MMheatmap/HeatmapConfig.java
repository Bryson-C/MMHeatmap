package org.bc.MMheatmap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * A class to store configuration data about the heatmap and reload the data when necessary/on command
 */
public class HeatmapConfig {
    private static JavaPlugin plugin;

    public HeatmapConfig(JavaPlugin plugin) {
        HeatmapConfig.plugin = plugin;
    }

    public static class ActionWeights {
        static public double placeBlock;
        static public double breakBlock;
        static public double setSpawn;
        static public double bedEnter;
        static public double leaveBed;
        static public double changeBeaconEffect;
        static public double changedWorldEvent;
        static public double fishEvent;
        static public double flowerPotManipulateEvent;
        static public double armorStandManipulateEvent;
        static public double harvestBlockEvent;
        static public double insertLecternEvent;
        static public double itemFrameChangeEvent;
        static public double purchaseEvent;
        static public double shearBlockEvent;
        static public double shearEntityEvent;
        static public double takeLecternBookEvent;
        static public double interactChest;
        static public double interactBarrel;

        public static void applyConfig(FileConfiguration config) {
            placeBlock = config.getDouble("activityWeights.playerPlace", 1);
            breakBlock = config.getDouble("activityWeights.playerBreak", 1);
            setSpawn = config.getDouble("activityWeights.playerSetSpawn", 1.5);
            bedEnter = config.getDouble("activityWeights.playerBedEnter", 0.5);
            leaveBed = config.getDouble("activityWeights.playerLeaveBed", 0);
            changeBeaconEffect = config.getDouble("activityWeights.playerChangeBeaconEffect", 1);
            changedWorldEvent = config.getDouble("activityWeights.playerChangedWorldEvent", 1);
            fishEvent = config.getDouble("activityWeights.playerFishEvent", 0);
            flowerPotManipulateEvent = config.getDouble("activityWeights.playerFlowerPotManipulateEvent", 0.5);
            armorStandManipulateEvent = config.getDouble("activityWeights.playerArmorStandManipulateEvent", 0.5);
            harvestBlockEvent = config.getDouble("activityWeights.playerHarvestBlockEvent", 0.75);
            insertLecternEvent = config.getDouble("activityWeights.playerInsertLecternEvent", 0);
            itemFrameChangeEvent = config.getDouble("activityWeights.playerItemFrameChangeEvent", 0);
            purchaseEvent = config.getDouble("activityWeights.playerPurchaseEvent", 0.25);
            shearBlockEvent = config.getDouble("activityWeights.playerShearBlockEvent", 1);
            shearEntityEvent = config.getDouble("activityWeights.playerShearEntityEvent", 0);
            takeLecternBookEvent = config.getDouble("activityWeights.playerTakeLecternBookEvent", 0);
            interactChest = config.getDouble("activityWeights.playerInteractChestEvent", 1);
            interactBarrel = config.getDouble("activityWeights.playerInteractBarrelEvent", 1);
            /*
            System.out.println("Config placeBlock: " + placeBlock);
                    System.out.println("Config breakBlock: " + breakBlock);
            System.out.println("Config setSpawn: " + setSpawn);
                    System.out.println("Config bedEnter: " + bedEnter);
            System.out.println("Config leaveBed: " + leaveBed);
                    System.out.println("Config changeBeaconEffect: " + changeBeaconEffect);
            System.out.println("Config changedWorldEvent: " + changedWorldEvent);
                    System.out.println("Config fishEvent: " + fishEvent);
            System.out.println("Config flowerPotManipulateEvent: " + flowerPotManipulateEvent);
                    System.out.println("Config armorStandManipulateEvent: " + armorStandManipulateEvent);
            System.out.println("Config harvestBlockEvent: " + harvestBlockEvent);
                    System.out.println("Config insertLecternEvent: " + insertLecternEvent);
            System.out.println("Config itemFrameChangeEvent: " + itemFrameChangeEvent);
                    System.out.println("Config purchaseEvent: " + purchaseEvent);
            System.out.println("Config shearBlockEvent: " + shearBlockEvent);
                    System.out.println("Config shearEntityEvent: " + shearEntityEvent);
            System.out.println("Config takeLecternBookEvent: " + takeLecternBookEvent);
                    System.out.println("Config interactChest: " + interactChest);
            System.out.println("Config interactBarrel: " + interactBarrel);
            */
        }
    }

    public static class Database {
        static public String address, username, password;
        static public String serverTablePrefix;
        public static void applyConfig(FileConfiguration config) {
            address = config.getString("database.address");
            username = config.getString("database.username");
            password = config.getString("database.password");
            serverTablePrefix = config.getString("database.serverTablePrefix");
            /*
            System.out.println("Config address: " + address);
            System.out.println("Config username: " + username);
            System.out.println("Config password: " + password);
            System.out.println("Config serverTablePrefix: " + serverTablePrefix);
            */
        }
    }

    private static int pollAreaChunkSize;
    public static int getPollAreaChunkSize() { return pollAreaChunkSize; }

    private static int maxActivityPerPoll;
    public static int getMaxActivityPerPoll() { return maxActivityPerPoll; }

    private static int minimumActivityForRecording;
    public static int getMinimumActivityForRecording() { return minimumActivityForRecording; }

    private static String cellActivityGradient;
    public static String getCellActivityGradient() { return cellActivityGradient; }

    private static double cellOpacity;
    public static double getCellOpacity() { return cellOpacity; }

    private static double borderOpacity;
    public static double getBorderOpacity() { return borderOpacity; };

    private static int noUpdatePoolRangeSeconds;
    public static int getNoUpdatePoolRangeSeconds() { return noUpdatePoolRangeSeconds; }

    private static int pollRangeSeconds;
    public static int getPollRangeSeconds() { return pollRangeSeconds; }

    private static String defaultWorldName;
    public static String getDefaultWorldName() { return defaultWorldName; }

    private static int divisionDensityWarningCount;
    public static int getDivisionDensityWarningCount() { return divisionDensityWarningCount; }

    private static int pollFrequencySeconds;
    public static int getPollFrequencySeconds() { return pollFrequencySeconds; }

    private static boolean logPollTime;
    public static boolean getLogPollTime() { return logPollTime; }

    private static String borderColor;
    public static String getBorderColor() { return borderColor; }


    public static void applyConfig() {
        FileConfiguration config = plugin.getConfig();
        ActionWeights.applyConfig(config);
        Database.applyConfig(config);
        pollAreaChunkSize = config.getInt("defaults.pollAreaChunkSize", 1);
        maxActivityPerPoll = config.getInt("activityWeights.maxActivityPerPoll", 20);
        minimumActivityForRecording = config.getInt("activityWeights.minimumActivityForRecording", 5);
        cellActivityGradient = config.getString("colors.cellActivityGradient", "#0F7299,#57C785,#EDDD53,#FF4400");
        noUpdatePoolRangeSeconds = config.getInt("defaults.noUpdatePollRangeSeconds", 2147483647);
        pollRangeSeconds = config.getInt("defaults.pollRangeSeconds", 2147483646);
        defaultWorldName = config.getString("defaults.world_name", "world");
        divisionDensityWarningCount = config.getInt("defaults.divisionDensityWarningCount", 128);
        pollFrequencySeconds = config.getInt("defaults.pollFrequencySeconds", 300);
        logPollTime = config.getBoolean("defaults.logPollTime", false);
        cellOpacity = config.getDouble("colors.cellOpacity", 0.50);
        borderOpacity = config.getDouble("colors.borderOpacity", 0.70);
        borderColor = config.getString("colors.borderColor", "#E0E0E0");
        /*
        System.out.println("Config pollAreaChunkSize:" + pollAreaChunkSize);
        System.out.println("Config maxActivityPerPoll:" +         maxActivityPerPoll);
        System.out.println("Config minimumActivityForRecording:" + minimumActivityForRecording);
        System.out.println("Config cellActivityGradient:" +         cellActivityGradient);
        System.out.println("Config noUpdatePoolRangeSeconds:" + noUpdatePoolRangeSeconds);
        System.out.println("Config pollRangeSeconds:" +         pollRangeSeconds);
        System.out.println("Config defaultWorldName:" + defaultWorldName);
        System.out.println("Config divisionDensityWarningCount:" +         divisionDensityWarningCount);
        System.out.println("Config pollFrequencySeconds:" + pollFrequencySeconds);
        System.out.println("Config logPollTime:" +         logPollTime);
        System.out.println("Config cellOpacity:" + cellOpacity);
        System.out.println("Config borderOpacity:" +         borderOpacity);
        System.out.println("Config borderColor:" + borderColor);
        */
    }

    public static void reload() {
        // make sure to save the actual file so changes made in it persist
        plugin.saveConfig();
        // reload it from disk cause the data in memory will not match the disk if changed from the config file
        plugin.reloadConfig();
        // apply the changes to the class, so each object needing data from here can retrieve the new data
        applyConfig();
    }
}
