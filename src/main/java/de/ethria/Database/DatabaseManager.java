package de.ethria.Database;

import de.ethria.Config.HoloPanelConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import net.luckperms.api.LuckPerms;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final String type;
    private FileConfiguration config;
    private final String serverName;

    public DatabaseManager(JavaPlugin plugin) throws SQLException {
        this.plugin = plugin;
        this.serverName = resolveServerName();
        this.config = loadConfig();
        this.type = config.getString("database.type", "sqlite").trim().toLowerCase(Locale.ROOT);

        if ("sqlite".equals(type)) {
            File file = new File(plugin.getDataFolder(), config.getString("database.sqlite.file", "holopanel.db"));
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new SQLException("Could not create SQLite database directory: " + parent);
            }
            loadDriver("org.sqlite.JDBC");
        } else if ("mysql".equals(type)) {
            loadDriver("com.mysql.cj.jdbc.Driver");
        } else {
            throw new IllegalArgumentException("Unsupported database type '" + type + "'. Use sqlite or mysql.");
        }
    }

    public void initialize() throws SQLException {
        String idColumn = "sqlite".equals(type)
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        String sql = "CREATE TABLE IF NOT EXISTS holopanels ("
                + "id " + idColumn + ","
                + "name VARCHAR(128) NOT NULL,"
                + "headline TEXT,"
                + "item_base64 TEXT,"
                + "description TEXT,"
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            ensureNewFields(connection);
            makeColumnsNullable(connection);
            createPositionsTable(connection);
            migratePositionIdColumn(connection);
            migrateLegacyPositions(connection);
            dropLegacyPositionColumns(connection);
        }
        plugin.getLogger().info("HoloPanel database is ready (" + type + ").");
    }

    private void createPositionsTable(Connection connection) throws SQLException {
        String idColumn = "sqlite".equals(type)
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        String sql = "CREATE TABLE IF NOT EXISTS holopanel_positions ("
                + "pos_id " + idColumn + ","
                + "panel_id BIGINT NOT NULL,"
                + "server_name VARCHAR(128), world VARCHAR(128) NOT NULL,"
                + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, yaw FLOAT, pitch FLOAT,"
                + "width_blocks DOUBLE NOT NULL DEFAULT 1, height_blocks DOUBLE NOT NULL DEFAULT 1,"
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY (panel_id) REFERENCES holopanels(id) ON DELETE CASCADE)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            ensurePositionDimensions(connection);
            if ("sqlite".equals(type)) statement.execute("CREATE INDEX IF NOT EXISTS idx_holopanel_positions_panel_id ON holopanel_positions(panel_id)");
            else statement.execute("CREATE INDEX idx_holopanel_positions_panel_id ON holopanel_positions(panel_id)");
        } catch (SQLException exception) {
            if ("mysql".equals(type) && exception.getMessage() != null && exception.getMessage().toLowerCase(Locale.ROOT).contains("duplicate key name")) return;
            throw exception;
        }
    }

    private void ensurePositionDimensions(Connection connection) throws SQLException {
        for (String column : new String[]{"width_blocks", "height_blocks"}) {
            boolean exists = false;
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "holopanel_positions", column)) {
                exists = columns.next();
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE holopanel_positions ADD COLUMN " + column + " DOUBLE NOT NULL DEFAULT 1");
                }
            }
        }
    }

    private void migratePositionIdColumn(Connection connection) throws SQLException {
        boolean hasOldName = false;
        boolean hasNewName = false;
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "holopanel_positions", null)) {
            while (columns.next()) {
                String name = columns.getString("COLUMN_NAME");
                if ("position_id".equalsIgnoreCase(name)) hasOldName = true;
                if ("pos_id".equalsIgnoreCase(name)) hasNewName = true;
            }
        }
        if (hasOldName && !hasNewName) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE holopanel_positions RENAME COLUMN position_id TO pos_id");
            }
        }
    }

    private void migrateLegacyPositions(Connection connection) throws SQLException {
        if (!hasColumn(connection, "server_name") || !hasColumn(connection, "world")
                || !hasColumn(connection, "x") || !hasColumn(connection, "y") || !hasColumn(connection, "z")) return;
        String sql = "INSERT INTO holopanel_positions (pos_id, panel_id, server_name, world, x, y, z, yaw, pitch) "
                + "SELECT p.id, p.id, p.server_name, p.world, p.x, p.y, p.z, p.yaw, p.pitch FROM holopanels p "
                + "WHERE p.world IS NOT NULL AND p.x IS NOT NULL AND p.y IS NOT NULL AND p.z IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM holopanel_positions l WHERE l.panel_id = p.id)";
        try (Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }

    private void dropLegacyPositionColumns(Connection connection) throws SQLException {
        String[] columns = {"server_name", "world", "x", "y", "z", "yaw", "pitch"};
        for (String column : columns) {
            if (!hasColumn(connection, column)) continue;
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE holopanels DROP COLUMN " + column);
            }
        }
    }

    private boolean hasColumn(Connection connection, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "holopanels", columnName)) {
            return columns.next();
        }
    }

    private void ensureNewFields(Connection connection) throws SQLException {
        for (String column : new String[]{"headline", "item_base64"}) {
            boolean exists;
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "holopanels", column)) {
                exists = columns.next();
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE holopanels ADD COLUMN " + column + " TEXT");
                }
            }
        }
    }

    private void makeColumnsNullable(Connection connection) throws SQLException {
        if ("sqlite".equals(type)) {
            boolean nullable = true;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA table_info(holopanels)")) {
                while (result.next()) {
                    String column = result.getString("name");
                    if (!"id".equalsIgnoreCase(column) && !"name".equalsIgnoreCase(column)
                            && result.getInt("notnull") != 0) {
                        nullable = false;
                    }
                }
            }
            if (!nullable) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys=OFF");
                    statement.execute("ALTER TABLE holopanels RENAME TO holopanels_legacy");
                    statement.execute("CREATE TABLE holopanels (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(128) NOT NULL, headline TEXT, item_base64 TEXT, description TEXT, server_name VARCHAR(128), world VARCHAR(128), x DOUBLE, y DOUBLE, z DOUBLE, yaw FLOAT, pitch FLOAT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                    statement.execute("INSERT INTO holopanels (id, name, headline, item_base64, description, server_name, world, x, y, z, yaw, pitch, created_at) SELECT id, name, headline, item_base64, description, server_name, world, x, y, z, yaw, pitch, created_at FROM holopanels_legacy");
                    statement.execute("DROP TABLE holopanels_legacy");
                    statement.execute("PRAGMA foreign_keys=ON");
                }
            }
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE holopanels MODIFY description TEXT NULL");
        }
    }

    public long createPanel(String name, String description, String server, String world,
                            double x, double y, double z, float yaw, float pitch) throws SQLException {
        long id = createPanelName(name);
        addPanelLocation(id, server, world, x, y, z, yaw, pitch);
        updatePanelText(id, null, description);
        return id;
    }

    public String hologramEngine() {
        return config.getString("hologram.engine", "auto").trim().toLowerCase(Locale.ROOT);
    }

    public String getServerName() {
        return serverName;
    }

    private String resolveServerName() {
        LuckPerms luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            throw new IllegalArgumentException("LuckPerms API is unavailable; it is required to resolve the database server name.");
        }
        String name = luckPerms.getServerName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("LuckPerms returned an empty server name.");
        }
        if (Bukkit.getServer().getServerConfig().isProxyEnabled() && "global".equalsIgnoreCase(name.trim())) {
            throw new IllegalArgumentException("LuckPerms uses 'global' on a proxy backend. Set 'server' in plugins/LuckPerms/config.yml to this backend's proxy name.");
        }
        String resolved = name.trim();
        plugin.getLogger().info("Using LuckPerms server identifier for database records: " + resolved);
        return resolved;
    }

    public void reloadConfigDefaults() {
        config = loadConfig();
    }

    public long createPanelName(String name) throws SQLException {
        String sql = "INSERT INTO holopanels (name) VALUES (?)";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("Database did not return a generated HoloPanel ID.");
        }
    }

    public void completePanel(long id, String description, String server, String world,
                              double x, double y, double z, float yaw, float pitch) throws SQLException {
        updatePanelText(id, null, description);
        addPanelLocation(id, server, world, x, y, z, yaw, pitch);
    }

    public List<Panel> getPanels() throws SQLException {
        List<Panel> panels = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM holopanels ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) panels.add(readPanel(connection, result));
        }
        return panels;
    }

    public Panel getPanel(long id) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM holopanels WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readPanel(connection, result) : null;
            }
        }
    }

    public void updatePanelHeadline(long id, String headline) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE holopanels SET headline = ? WHERE id = ?")) {
            statement.setString(1, headline);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void updatePanelText(long id, String headline, String description) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE holopanels SET headline = ?, description = ? WHERE id = ?")) {
            statement.setString(1, headline);
            statement.setString(2, description);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    public void updatePanelItem(long id, String itemBase64) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE holopanels SET item_base64 = ? WHERE id = ?")) {
            statement.setString(1, itemBase64);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void updatePanelLocation(long id, String server, String world, double x, double y, double z,
                                    float yaw, float pitch) throws SQLException {
        addPanelLocation(id, server, world, x, y, z, yaw, pitch);
    }

    public long addPanelLocation(long panelId, String server, String world, double x, double y, double z,
                                 float yaw, float pitch) throws SQLException {
        return addPanelLocation(panelId, server, world, x, y, z, yaw, pitch, 1, 1);
    }

    public long addPanelLocation(long panelId, String server, String world, double x, double y, double z,
                                 float yaw, float pitch, double widthBlocks, double heightBlocks) throws SQLException {
        String sql = "INSERT INTO holopanel_positions (panel_id, server_name, world, x, y, z, yaw, pitch, width_blocks, height_blocks) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, panelId); statement.setString(2, serverName); statement.setString(3, world);
            statement.setDouble(4, x); statement.setDouble(5, y); statement.setDouble(6, z);
            statement.setFloat(7, yaw); statement.setFloat(8, pitch);
            statement.setDouble(9, widthBlocks); statement.setDouble(10, heightBlocks); statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) { if (keys.next()) return keys.getLong(1); }
            throw new SQLException("Database did not return a HoloPanel position ID.");
        }
    }

    public List<PanelLocation> getPanelLocations(long panelId) throws SQLException {
        List<PanelLocation> locations = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM holopanel_positions WHERE panel_id = ? ORDER BY pos_id")) {
            statement.setLong(1, panelId);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) locations.add(readLocation(result)); }
        }
        return locations;
    }

    public PanelLocation nearestPanelLocation(long panelId, String world, double x, double y, double z) throws SQLException {
        PanelLocation nearest = null; double closest = 4.0;
        for (PanelLocation location : getPanelLocations(panelId)) {
            if (!world.equals(location.world())) continue;
            double dx = x - location.x(), dy = y - location.y(), dz = z - location.z();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < closest) { closest = distance; nearest = location; }
        }
        return nearest;
    }

    public void deletePanelLocation(long panelId, long positionId) throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM holopanel_positions WHERE panel_id = ? AND pos_id = ?")) {
            statement.setLong(1, panelId);
            statement.setLong(2, positionId);
            statement.executeUpdate();
        }
    }

    private Panel readPanel(Connection connection, ResultSet result) throws SQLException {
        return new Panel(result.getLong("id"), result.getString("name"), result.getString("headline"),
                result.getString("item_base64"), result.getString("description"), getPanelLocations(result.getLong("id")));
    }

    private PanelLocation readLocation(ResultSet result) throws SQLException {
        return new PanelLocation(result.getLong("pos_id"), result.getLong("panel_id"), result.getString("server_name"), result.getString("world"),
                result.getDouble("x"), result.getDouble("y"), result.getDouble("z"), nullableFloat(result, "yaw"), nullableFloat(result, "pitch"),
                result.getDouble("width_blocks"), result.getDouble("height_blocks"));
    }

    private Double nullableDouble(ResultSet result, String column) throws SQLException {
        double value = result.getDouble(column);
        return result.wasNull() ? null : value;
    }

    private Float nullableFloat(ResultSet result, String column) throws SQLException {
        float value = result.getFloat(column);
        return result.wasNull() ? null : value;
    }

    public record Panel(long id, String name, String headline, String itemBase64, String description, List<PanelLocation> locations) { }
    public record PanelLocation(long id, long panelId, String server, String world, double x, double y, double z,
                                Float yaw, Float pitch, double widthBlocks, double heightBlocks) { }

    public void deletePanel(long id) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM holopanels WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        if ("sqlite".equals(type)) {
            File file = new File(plugin.getDataFolder(), config.getString("database.sqlite.file", "holopanel.db"));
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA foreign_keys=ON"); }
            return connection;
        }

        String host = config.getString("database.mysql.host", "localhost");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "holopanel");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + config.getBoolean("database.mysql.useSSL", true)
                + "&serverTimezone=UTC";
        return DriverManager.getConnection(url,
                config.getString("database.mysql.username", "root"),
                config.getString("database.mysql.password", ""));
    }

    private FileConfiguration loadConfig() {
        return new HoloPanelConfig(plugin).loadDatabaseConfig();
    }

    private void loadDriver(String className) throws SQLException {
        try {
            Class.forName(className, true, getClass().getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Could not load database driver " + className + ".", exception);
        }
    }
}
