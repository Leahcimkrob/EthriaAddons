package de.ethria.Config.EventChests;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.ethria.main.Main.Prefix;

public class DataRS {

    private final String folderPath = "plugins/EthriaAddons/Addons/EventChests";
    private final String databasePath = "plugins/EthriaAddons/Addons/EventChests/data.db";

    public DataRS() {
        createFolder();
        createTables();
    }

    public void createFolder() {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private void createTables() {
        String createChestsTable = "CREATE TABLE IF NOT EXISTS chests ("
                + "chestnr INTEGER PRIMARY KEY,"
                + "chestname TEXT"
                + ");";

        String createItemsTable = "CREATE TABLE IF NOT EXISTS items ("
                + "nr INTEGER PRIMARY KEY,"
                + "chestname TEXT,"
                + "slot INTEGER,"
                + "item TEXT,"
                + "FOREIGN KEY(chestname) REFERENCES chests(chestname)"
                + ");";

        String createLocationTable = "CREATE TABLE IF NOT EXISTS locations ("
                + "nr INTEGER PRIMARY KEY,"
                + "chestname TEXT,"
                + "loc TEXT,"
                + "FOREIGN KEY(chestname) REFERENCES chests(chestname)"
                + ");";

        String createPlayersTable = "CREATE TABLE IF NOT EXISTS players ("
                + "nr INTEGER PRIMARY KEY,"
                + "chestname TEXT,"
                + "slot INTEGER,"
                + "playerUUID TEXT,"
                + "FOREIGN KEY(chestname) REFERENCES chests(chestname)"
                + ");";

        try (Connection connection = connect()) {
            try (PreparedStatement stmt1 = connection.prepareStatement(createChestsTable);
                 PreparedStatement stmt2 = connection.prepareStatement(createItemsTable);
                 PreparedStatement stmt3 = connection.prepareStatement(createLocationTable);
                 PreparedStatement stmt4 = connection.prepareStatement(createPlayersTable)) {

                stmt1.execute();
                stmt2.execute();
                stmt3.execute();
                stmt4.execute();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean createChest(String chestName) {
        String checkChestExistsSql = "SELECT * FROM chests WHERE chestname = ?";
        String insertChestSql = "INSERT INTO chests (chestname) VALUES (?)";

        try (Connection connection = connect();
             PreparedStatement checkStmt = connection.prepareStatement(checkChestExistsSql)) {
            checkStmt.setString(1, chestName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    return false;
                }
            }

            try (PreparedStatement insertStmt = connection.prepareStatement(insertChestSql)) {
                insertStmt.setString(1, chestName);
                insertStmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteChest(String chestName) {
        String checkChestExistsSql = "SELECT * FROM chests WHERE chestname = ?";
        String deleteChestSql = "DELETE FROM chests WHERE chestname = ?";

        try (Connection connection = connect();
             PreparedStatement checkStmt = connection.prepareStatement(checkChestExistsSql)) {
            checkStmt.setString(1, chestName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    // Chest exists, proceed to delete
                    try (PreparedStatement deleteStmt = connection.prepareStatement(deleteChestSql)) {
                        deleteStmt.setString(1, chestName);
                        deleteStmt.executeUpdate();
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getChestsCount() {
        String countChestsSql = "SELECT COUNT(*) FROM chests";

        try (Connection connection = connect();
             PreparedStatement countStmt = connection.prepareStatement(countChestsSql);
             ResultSet rs = countStmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public ArrayList<String> getChests() {
        String getChestsSql = "SELECT chestname FROM chests";

        ArrayList<String> chests = new ArrayList<>();

        try (Connection connection = connect();
             PreparedStatement getStmt = connection.prepareStatement(getChestsSql);
             ResultSet rs = getStmt.executeQuery()) {

            while (rs.next()) {
                chests.add(rs.getString("chestname"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return chests;
    }

    public boolean checkChestEntry(String chestName) {
        String checkChestExistsSql = "SELECT * FROM chests WHERE chestname = ?";

        try (Connection connection = connect();
             PreparedStatement checkStmt = connection.prepareStatement(checkChestExistsSql)) {
            checkStmt.setString(1, chestName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean createItem(String chestname, String item) {
        try (Connection connection = connect()) {
            // Check if the chest exists
            if (!chestExists(chestname, connection)) {
                System.out.println("Chest does not exist.");
                return false;
            }

            // Find an available slot (0 to 26)
            int availableSlot = findAvailableSlot(chestname, connection);
            if (availableSlot == -1) {
                System.out.println("All slots are occupied.");
                return false;
            }

            // Insert the item into the database
            String insertItemQuery = "INSERT INTO items (chestname, slot, item) VALUES (?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insertItemQuery)) {
                statement.setString(1, chestname);
                statement.setInt(2, availableSlot);
                statement.setString(3, item);
                statement.executeUpdate();
            }

            System.out.println("Item created successfully.");
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteItem(String chestName, String item, int slot) {
        String checkItemExistsSql = "SELECT * FROM items WHERE chestname = ? AND item = ? AND slot = ?";
        String deleteItemSql = "DELETE FROM items WHERE chestname = ? AND item = ? AND slot = ?";

        try (Connection connection = connect()) {
            // Check if the item exists in the specified chest and slot
            try (PreparedStatement checkStmt = connection.prepareStatement(checkItemExistsSql)) {
                checkStmt.setString(1, chestName);
                checkStmt.setString(2, item);
                checkStmt.setInt(3, slot);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // Item exists, proceed to delete
                        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteItemSql)) {
                            deleteStmt.setString(1, chestName);
                            deleteStmt.setString(2, item);
                            deleteStmt.setInt(3, slot);
                            deleteStmt.executeUpdate();
                            System.out.println("Item deleted successfully.");
                            return true;
                        }
                    } else {
                        System.out.println("Item does not exist in the specified chest and slot.");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // Item does not exist or an error occurred
    }

    public void deleteAllChestItems(String chestName, Player player) {
        String getItemsSql = "SELECT slot, item FROM items WHERE chestname = ?";

        try (Connection connection = connect()) {
            // Retrieve all items for the specified chest
            try (PreparedStatement getStmt = connection.prepareStatement(getItemsSql)) {
                getStmt.setString(1, chestName);
                try (ResultSet rs = getStmt.executeQuery()) {
                    List<ItemSlot> items = new ArrayList<>();
                    while (rs.next()) {
                        int slot = rs.getInt("slot");
                        String item = rs.getString("item");
                        items.add(new ItemSlot(slot, item));
                    }

                    // Delete each item using deleteItem method
                    for (ItemSlot itemSlot : items) {
                        deleteItem(chestName, itemSlot.item, itemSlot.slot);
                    }

                    player.sendMessage(Prefix + "§aEs wurden §e" + items.size() + " §aItems von der EventChest §e" + ChatColor.translateAlternateColorCodes('&', chestName) + " §aentfernt.");
                }
            }
        } catch (SQLException e) {
            player.sendMessage(Prefix + "§4Ein Fehler beim entfernen aller Items aus der EventChest §e'" + ChatColor.translateAlternateColorCodes('&', chestName) + "'§4ist aufgetreten. Weitere Details in der Konsole!");
            Bukkit.getConsoleSender().sendMessage(Prefix + "§4Ein Fehler beim entfernen aller Items aus der EventChest §e'" + ChatColor.translateAlternateColorCodes('&', chestName) + "'§4ist aufgetreten:");
            e.printStackTrace();
        }
    }

    // Inner class to hold slot and item
    private static class ItemSlot {
        int slot;
        String item;

        ItemSlot(int slot, String item) {
            this.slot = slot;
            this.item = item;
        }
    }


    private static boolean chestExists(String chestname, Connection connection) throws SQLException {
        String checkChestQuery = "SELECT COUNT(*) FROM chests WHERE chestname = ?";
        try (PreparedStatement statement = connection.prepareStatement(checkChestQuery)) {
            statement.setString(1, chestname);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static int findAvailableSlot(String chestname, Connection connection) throws SQLException {
        String checkOccupiedSlotsQuery = "SELECT COUNT(*) FROM items WHERE chestname = ?";
        try (PreparedStatement statement = connection.prepareStatement(checkOccupiedSlotsQuery)) {
            statement.setString(1, chestname);
            try (ResultSet resultSet = statement.executeQuery()) {
                int occupiedSlots = resultSet.getInt(1);
                if (occupiedSlots >= 27) {
                    return -1; // All slots are occupied
                } else {
                    // Find the first available slot
                    String checkSlotQuery = "SELECT slot FROM items WHERE chestname = ?";
                    try (PreparedStatement slotStatement = connection.prepareStatement(checkSlotQuery)) {
                        slotStatement.setString(1, chestname);
                        try (ResultSet slotResultSet = slotStatement.executeQuery()) {
                            boolean[] slots = new boolean[27];
                            while (slotResultSet.next()) {
                                int slot = slotResultSet.getInt("slot");
                                slots[slot] = true;
                            }
                            for (int i = 0; i < slots.length; i++) {
                                if (!slots[i]) {
                                    return i;
                                }
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }

    public String[][] readItems(String chestname) {
        String[][] itemsArray = new String[27][2]; // 27 slots, each containing an item

        // Initialize itemsArray with empty values
        for (int i = 0; i < itemsArray.length; i++) {
            itemsArray[i][0] = Integer.toString(i); // Slot number
            itemsArray[i][1] = ""; // Empty item initially
        }
        try (Connection connection = connect()) {
            String readItemsQuery = "SELECT slot, item FROM items WHERE chestname = ?";
            try (PreparedStatement statement = connection.prepareStatement(readItemsQuery)) {
                statement.setString(1, chestname);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        int slot = resultSet.getInt("slot");
                        String item = resultSet.getString("item");
                        itemsArray[slot][1] = item; // Set the item at the corresponding slot
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return itemsArray;
    }

    public boolean placeChest(String chestName, Location location) {
        String insertLocationSql = "INSERT INTO locations (chestname, loc) VALUES (?, ?)";

        try (Connection connection = connect();
             PreparedStatement insertStmt = connection.prepareStatement(insertLocationSql)) {
            insertStmt.setString(1, chestName);
            insertStmt.setString(2, locationToString(location));
            insertStmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String readChestLocation(Location location) {
        String readChestLocationSql = "SELECT chestname FROM locations WHERE loc = ?";

        try (Connection connection = connect();
             PreparedStatement readStmt = connection.prepareStatement(readChestLocationSql)) {
            readStmt.setString(1, locationToString(location));

            try (ResultSet rs = readStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("chestname");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ArrayList<Location> getLocations() {
        ArrayList<Location> locations = new ArrayList<>();

        String selectLocationsSql = "SELECT loc FROM locations";

        try (Connection connection = connect();
             PreparedStatement selectStmt = connection.prepareStatement(selectLocationsSql);
             ResultSet resultSet = selectStmt.executeQuery()) {

            while (resultSet.next()) {
                String locationString = resultSet.getString("loc");
                Location location = stringToLocation(locationString);
                if (location != null) {
                    locations.add(location);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return locations;
    }

    private String locationToString(Location location) {
        return location.getWorld().getName() + ";" +
                location.getX() + ";" +
                location.getY() + ";" +
                location.getZ();
    }

    private Location stringToLocation(String locationString) {
        String[] parts = locationString.split(";");
        if (parts.length != 4) {
            return null; // Invalid format
        }
        try {
            String worldName = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new Location(Bukkit.getWorld(worldName), x, y, z);
        } catch (NullPointerException | IllegalArgumentException e) {
            e.printStackTrace();
            return null; // Error parsing location
        }
    }

    public boolean deleteChestLocation(Location location) {
        String deleteEntrySql = "DELETE FROM locations WHERE loc = ?";

        try (Connection connection = connect();
             PreparedStatement deleteStmt = connection.prepareStatement(deleteEntrySql)) {
            deleteStmt.setString(1, locationToString(location));
            int rowsAffected = deleteStmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteAllChestLocation(String chestName) {
        String deleteEntrySql = "DELETE FROM locations WHERE chestname = ?";

        try (Connection connection = connect();
             PreparedStatement deleteStmt = connection.prepareStatement(deleteEntrySql)) {
            deleteStmt.setString(1, chestName);
            int rowsAffected = deleteStmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setSlot(String chestName, int slot, UUID playerUUID) {
        String sql = "INSERT OR REPLACE INTO players (chestname, slot, playerUUID) VALUES (?, ?, ?)";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, chestName);
            statement.setInt(2, slot);
            statement.setString(3, String.valueOf(playerUUID));
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean readSlot(String chestName, int slot, UUID playerUUID) {
        String sql = "SELECT playerUUID FROM players WHERE chestname = ? AND slot = ? AND playerUUID = ?";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, chestName);
            statement.setInt(2, slot);
            statement.setString(3, String.valueOf(playerUUID));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteAllSlot(String chestName, UUID playerUUID) {
        String deleteEntrySql = "DELETE FROM players WHERE chestname = ? AND playerUUID = ?";
        try (Connection connection = connect();
             PreparedStatement deleteStmt = connection.prepareStatement(deleteEntrySql)) {
            deleteStmt.setString(1, chestName);
            deleteStmt.setString(2, playerUUID.toString());
            int rowsAffected = deleteStmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteAllSlotChest(String chestName) {
        String deleteEntrySql = "DELETE FROM players WHERE chestname = ?";
        try (Connection connection = connect();
             PreparedStatement deleteStmt = connection.prepareStatement(deleteEntrySql)) {
            deleteStmt.setString(1, chestName);
            int rowsAffected = deleteStmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }



}
