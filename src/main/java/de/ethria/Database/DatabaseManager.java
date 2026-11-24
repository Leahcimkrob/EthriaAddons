package de.ethria.Database;

import de.ethria.Config.HoloPanelConfig;
import de.ethria.main.Main;
import de.ethria.Config.YamlDefaults;
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
    private final Main plugin;
    private String type;
    private String autoMessageType;
    private FileConfiguration config;
    private FileConfiguration autoMessageConfig;
    private final String serverName;

    public DatabaseManager(Main plugin) throws SQLException {
        this.plugin = plugin;
        this.serverName = resolveServerName();
        this.config = loadConfig();
        this.type = config.getString("database.type", "sqlite").trim().toLowerCase(Locale.ROOT);
        this.autoMessageConfig = loadAutoMessageConfig();
        this.autoMessageType = autoMessageConfig.getString("database.type", "sqlite").trim().toLowerCase(Locale.ROOT);

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
        if ("sqlite".equals(autoMessageType)) {
            File file = new File(plugin.getDataFolder(), autoMessageConfig.getString("database.sqlite.file", "automessage.db"));
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new SQLException("Could not create SQLite database directory: " + parent);
            }
            loadDriver("org.sqlite.JDBC");
        } else if ("mysql".equals(autoMessageType)) {
            loadDriver("com.mysql.cj.jdbc.Driver");
        } else {
            throw new IllegalArgumentException("Unsupported AutoMessage database type '" + autoMessageType + "'. Use sqlite or mysql.");
        }
    }

    public void initialize() throws SQLException {
        initializeHoloPanel();
        initializeAutoMessage();
    }

    public void initializeHoloPanel() throws SQLException {
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

    public void initializeAutoMessage() throws SQLException {
        String autoMessageIdColumn = "sqlite".equals(autoMessageType)
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        String autoMessageSql = "CREATE TABLE IF NOT EXISTS automessages ("
                + "id " + autoMessageIdColumn + ","
                + "name VARCHAR(128) NOT NULL UNIQUE,"
                + "description TEXT,"
                + "item_base64 TEXT"
                + ")";
        try (Connection connection = connectAutoMessage(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(autoMessageSql);
            dropAutoMessageActive(connection);
            ensureAutoMessageServerTables(connection);
        }
        plugin.getLogger().info("AutoMessage database is ready (" + autoMessageType + ").");
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
        autoMessageConfig = loadAutoMessageConfig();
        type = config.getString("database.type", "sqlite").trim().toLowerCase(Locale.ROOT);
        autoMessageType = autoMessageConfig.getString("database.type", "sqlite").trim().toLowerCase(Locale.ROOT);
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

    public long createAutoMessage(String name) throws SQLException {
        String sql = "INSERT INTO automessages (name, description) VALUES (?, ?)";
        try (Connection connection = connectAutoMessage();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            connection.setAutoCommit(false);
            try {
            statement.setString(1, name);
            List<String> defaultLines = plugin.autoMessageConfig().getStringList("AutomessageDefaultText");
            statement.setString(2, String.join("\n", defaultLines));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    addAutoMessageServer(connection, id, serverName);
                    connection.commit();
                    return id;
                }
            }
            throw new SQLException("Database did not return a generated AutoMessage ID.");
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<String> getAutoMessageNames() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = connectAutoMessage();
             PreparedStatement statement = connection.prepareStatement("SELECT name FROM automessages ORDER BY name");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) names.add(result.getString("name"));
        }
        return names;
    }

    public List<AutoMessage> getAutoMessages() throws SQLException {
        List<AutoMessage> messages = new ArrayList<>();
        try (Connection connection = connectAutoMessage();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, description, item_base64 FROM automessages ORDER BY name");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) messages.add(new AutoMessage(result.getLong("id"), result.getString("name"), result.getString("description"), result.getString("item_base64"), null, true));
        }
        return messages;
    }

    public AutoMessage getAutoMessage(String name) throws SQLException {
        try (Connection connection = connectAutoMessage();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, description, item_base64 FROM automessages WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new AutoMessage(result.getLong("id"), result.getString("name"), result.getString("description"), result.getString("item_base64"), null, true) : null;
            }
        }
    }

    public void updateAutoMessageName(long id, String name) throws SQLException {
        try (Connection connection = connectAutoMessage(); PreparedStatement statement = connection.prepareStatement("UPDATE automessages SET name = ? WHERE id = ?")) {
            statement.setString(1, name);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void updateAutoMessageText(long id, String text) throws SQLException {
        try (Connection connection = connectAutoMessage(); PreparedStatement statement = connection.prepareStatement("UPDATE automessages SET description = ? WHERE id = ?")) {
            statement.setString(1, text);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void updateAutoMessageItem(long id, String itemBase64) throws SQLException {
        try (Connection connection = connectAutoMessage(); PreparedStatement statement = connection.prepareStatement("UPDATE automessages SET item_base64 = ? WHERE id = ?")) {
            statement.setString(1, itemBase64);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public void deleteAutoMessage(long id) throws SQLException {
        try (Connection connection = connectAutoMessage()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement links = connection.prepareStatement("DELETE FROM automessage_servers WHERE automessage_id = ?")) {
                    links.setLong(1, id); links.executeUpdate();
                }
                try (PreparedStatement message = connection.prepareStatement("DELETE FROM automessages WHERE id = ?")) {
                    message.setLong(1, id); message.executeUpdate();
                }
                // Keep servers as a shared registry; deleting a message must not remove server rows.
                connection.commit();
            } catch (SQLException exception) { connection.rollback(); throw exception; }
        }
    }

    public record AutoMessage(long id, String name, String description, String itemBase64, String serverName, boolean active) { }

    private void ensureAutoMessageServerTables(Connection connection) throws SQLException {
        String id = "sqlite".equals(autoMessageType) ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS servers (id " + id + ", servername VARCHAR(128) NOT NULL UNIQUE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS automessage_servers (automessage_id BIGINT NOT NULL, server_id BIGINT NOT NULL, PRIMARY KEY (automessage_id, server_id), FOREIGN KEY (automessage_id) REFERENCES automessages(id) ON DELETE CASCADE, FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE)");
        }
        boolean legacy = false;
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "automessages", "server_name")) { legacy = columns.next(); }
        if (legacy) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO servers (servername) SELECT DISTINCT server_name FROM automessages WHERE server_name IS NOT NULL AND server_name <> '' AND NOT EXISTS (SELECT 1 FROM servers WHERE servers.servername = automessages.server_name)");
                statement.executeUpdate("INSERT INTO automessage_servers (automessage_id, server_id) SELECT a.id, s.id FROM automessages a JOIN servers s ON s.servername = a.server_name WHERE NOT EXISTS (SELECT 1 FROM automessage_servers x WHERE x.automessage_id = a.id AND x.server_id = s.id)");
                statement.execute("ALTER TABLE automessages DROP COLUMN server_name");
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO servers (servername) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM servers WHERE servername = ?)")) {
            insert.setString(1, serverName); insert.setString(2, serverName); insert.executeUpdate();
        }
    }

    private void addAutoMessageServer(Connection connection, long messageId, String targetServer) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO servers (servername) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM servers WHERE servername = ?)")) {
            insert.setString(1, targetServer); insert.setString(2, targetServer); insert.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO automessage_servers (automessage_id, server_id) SELECT ?, id FROM servers WHERE servername = ?")) {
            insert.setLong(1, messageId); insert.setString(2, targetServer); insert.executeUpdate();
        }
    }

    public boolean isAutoMessageAssignedToServer(long messageId, String targetServer) throws SQLException {
        try (Connection connection = connectAutoMessage(); PreparedStatement query = connection.prepareStatement("SELECT 1 FROM automessage_servers x JOIN servers s ON s.id=x.server_id WHERE x.automessage_id=? AND s.servername=?")) {
            query.setLong(1, messageId); query.setString(2, targetServer);
            try (ResultSet result = query.executeQuery()) { return result.next(); }
        }
    }

    public boolean isAutoMessageServerAssigned(long messageId, String targetServer) throws SQLException {
        return isAutoMessageAssignedToServer(messageId, targetServer);
    }

    public List<String> getAutoMessageServers() throws SQLException {
        List<String> servers = new ArrayList<>();
        try (Connection connection = connectAutoMessage(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT servername FROM servers ORDER BY servername")) {
            while (result.next()) servers.add(result.getString(1));
        }
        return servers;
    }

    public List<String> getServersForAutoMessage(long messageId) throws SQLException {
        List<String> servers = new ArrayList<>();
        try (Connection connection = connectAutoMessage(); PreparedStatement query = connection.prepareStatement(
                "SELECT s.servername FROM servers s JOIN automessage_servers x ON x.server_id=s.id WHERE x.automessage_id=? ORDER BY s.servername")) {
            query.setLong(1, messageId);
            try (ResultSet result = query.executeQuery()) { while (result.next()) servers.add(result.getString(1)); }
        }
        return servers;
    }

    public void setAutoMessageServer(long messageId, String targetServer, boolean enabled) throws SQLException {
        try (Connection connection = connectAutoMessage()) {
            if (enabled) addAutoMessageServer(connection, messageId, targetServer);
            else try (PreparedStatement statement = connection.prepareStatement("DELETE FROM automessage_servers WHERE automessage_id=? AND server_id=(SELECT id FROM servers WHERE servername=?)")) {
                statement.setLong(1, messageId); statement.setString(2, targetServer); statement.executeUpdate();
            }
        }
    }

    public void toggleAutoMessageServer(long messageId, String targetServer) throws SQLException {
        setAutoMessageServer(messageId, targetServer, !isAutoMessageAssignedToServer(messageId, targetServer));
    }

    private void dropAutoMessageActive(Connection connection) throws SQLException {
        boolean exists;
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "automessages", "active")) { exists = columns.next(); }
        if (!exists) return;
        try (Statement statement = connection.createStatement()) { statement.execute("ALTER TABLE automessages DROP COLUMN active"); }
        catch (SQLException exception) { throw new SQLException("Could not remove obsolete automessages.active column.", exception); }
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
        return connectMySql(host, port, database, config);
    }

    private Connection connectAutoMessage() throws SQLException {
        if ("sqlite".equals(autoMessageType)) {
            File file = new File(plugin.getDataFolder(), autoMessageConfig.getString("database.sqlite.file", "automessage.db"));
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA foreign_keys=ON"); }
            return connection;
        }
        String host = autoMessageConfig.getString("database.mysql.host", "localhost");
        int port = autoMessageConfig.getInt("database.mysql.port", 3306);
        String schema = autoMessageConfig.getString("database.mysql.database", "automessage");
        return connectMySql(host, port, schema, autoMessageConfig);
    }

    private Connection connectMySql(String host, int port, String schema, FileConfiguration selected) throws SQLException {
        String credentials = "?useSSL=" + selected.getBoolean("database.mysql.useSSL", true) + "&serverTimezone=UTC";
        String user = selected.getString("database.mysql.username", "root");
        String password = selected.getString("database.mysql.password", "");
        try {
            return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + schema + credentials, user, password);
        } catch (SQLException firstFailure) {
            if (!isUnknownDatabase(firstFailure)) throw firstFailure;
            if (schema == null || !schema.matches("[A-Za-z0-9_$-]+")) {
                throw new SQLException("Configured MySQL database name is invalid: " + schema, firstFailure);
            }
            try (Connection serverConnection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + credentials, user, password);
                 Statement statement = serverConnection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + schema + "`");
            } catch (SQLException createFailure) {
                createFailure.addSuppressed(firstFailure);
                throw createFailure;
            }
            return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + schema + credentials, user, password);
        }
    }

    private boolean isUnknownDatabase(SQLException exception) {
        for (SQLException cause = exception; cause != null; cause = cause.getNextException()) {
            if (cause.getErrorCode() == 1049 || "42000".equals(cause.getSQLState())) return true;
        }
        return false;
    }

    /** Copies a data set to the selected database backend without changing active configuration. */
    public void convert(String area, String from, String to) throws SQLException {
        String sourceType = normalizeType(from);
        String targetType = normalizeType(to);
        if (sourceType.equals(targetType)) throw new IllegalArgumentException("Source and target must differ.");
        boolean autoMessage = "automessage".equalsIgnoreCase(area);
        FileConfiguration selected = autoMessage ? autoMessageConfig : config;
        try (Connection source = connectFor(selected, sourceType);
             Connection target = connectFor(selected, targetType)) {
            target.setAutoCommit(false);
            try {
                if (autoMessage) {
                    ensureAutoMessageTable(target, targetType);
                    mergeAutoMessages(source, target, targetType);
                } else {
                    ensurePanelTables(target, targetType);
                    copyTable(source, target, "holopanels");
                    copyTable(source, target, "holopanel_positions");
                }
                target.commit();
            } catch (SQLException exception) {
                target.rollback();
                throw exception;
            }
        }
    }
    public java.util.logging.Logger getPluginLogger() { return plugin.getLogger(); }

    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("sqlite", "mysql").contains(type)) throw new IllegalArgumentException("Type must be sqlite or mysql.");
        return type;
    }

    private Connection connectFor(FileConfiguration selected, String type) throws SQLException {
        boolean autoMessage = selected == autoMessageConfig;
        String selectedType = selected.getString("database.type", "sqlite").trim().toLowerCase(Locale.ROOT);
        if (!type.equals(selectedType)) {
            // Conversion source/target intentionally uses the same area's stored credentials.
        }
        if ("sqlite".equals(type)) {
            String fallback = autoMessage ? "automessage.db" : "holopanel.db";
            File file = new File(plugin.getDataFolder(), selected.getString("database.sqlite.file", fallback));
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new SQLException("Could not create SQLite directory: " + parent);
            return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        }
        String schema = autoMessage ? "automessage" : "holopanel";
        return connectMySql(selected.getString("database.mysql.host", "localhost"),
                selected.getInt("database.mysql.port", 3306), selected.getString("database.mysql.database", schema), selected);
    }

    private void ensureAutoMessageTable(Connection connection, String dbType) throws SQLException {
        String id = "sqlite".equals(dbType) ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS automessages (id " + id + ",name VARCHAR(128) NOT NULL UNIQUE,description TEXT,item_base64 TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS servers (id " + id + ",servername VARCHAR(128) NOT NULL UNIQUE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS automessage_servers (automessage_id BIGINT NOT NULL,server_id BIGINT NOT NULL,PRIMARY KEY (automessage_id,server_id))");
        }
    }

    private void ensurePanelTables(Connection connection, String dbType) throws SQLException {
        String id = "sqlite".equals(dbType) ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS holopanels (id " + id + ",name VARCHAR(128) NOT NULL,headline TEXT,item_base64 TEXT,description TEXT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS holopanel_positions (pos_id " + id + ",panel_id BIGINT NOT NULL,server_name VARCHAR(128),world VARCHAR(128),x DOUBLE,y DOUBLE,z DOUBLE,yaw FLOAT,pitch FLOAT,width_blocks DOUBLE NOT NULL DEFAULT 1,height_blocks DOUBLE NOT NULL DEFAULT 1)");
        }
    }

    private void copyTable(Connection source, Connection target, String table) throws SQLException {
        try (Statement s = source.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM " + table)) {
            java.sql.ResultSetMetaData md = rs.getMetaData(); int count = md.getColumnCount();
            StringBuilder cols = new StringBuilder(), marks = new StringBuilder();
            for (int i=1;i<=count;i++) { if(i>1){cols.append(',');marks.append(',');} cols.append(md.getColumnName(i)); marks.append('?'); }
            String insertSql = "sqlite".equalsIgnoreCase(target.getMetaData().getDatabaseProductName())
                    ? "INSERT OR IGNORE INTO " + table + " (" + cols + ") VALUES (" + marks + ")"
                    : "INSERT IGNORE INTO " + table + " (" + cols + ") VALUES (" + marks + ")";
            try (PreparedStatement insert = target.prepareStatement(insertSql)) {
                while(rs.next()){ for(int i=1;i<=count;i++) insert.setObject(i, rs.getObject(i)); insert.addBatch(); }
                insert.executeBatch();
            }
        }
    }

    private void mergeAutoMessages(Connection source, Connection target, String targetType) throws SQLException {
        java.util.Map<Long, Long> messageIds = new java.util.HashMap<>();
        try (Statement read = source.createStatement(); ResultSet messages = read.executeQuery("SELECT id,name,description,item_base64 FROM automessages ORDER BY id")) {
            while (messages.next()) {
                long sourceId = messages.getLong("id");
                String name = messages.getString("name");
                String description = messages.getString("description");
                String item = messages.getString("item_base64");
                Long targetId = findMatchingAutoMessage(target, name, description);
                if (targetId == null) {
                    String uniqueName = uniqueAutoMessageName(target, name);
                    try (PreparedStatement insert = target.prepareStatement("INSERT INTO automessages (name,description,item_base64) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                        insert.setString(1, uniqueName); insert.setString(2, description); insert.setString(3, item); insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Could not retrieve imported AutoMessage ID for '" + uniqueName + "'.");
                            targetId = keys.getLong(1);
                        }
                    }
                }
                messageIds.put(sourceId, targetId);
            }
        }
        try (Statement readServers = source.createStatement(); ResultSet servers = readServers.executeQuery("SELECT servername FROM servers")) {
            try (PreparedStatement insertServer = target.prepareStatement("INSERT INTO servers (servername) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM servers WHERE servername=?)")) {
                while (servers.next()) {
                    String name = servers.getString(1);
                    insertServer.setString(1, name); insertServer.setString(2, name); insertServer.addBatch();
                }
                insertServer.executeBatch();
            }
        }
        String sql = "SELECT x.automessage_id, ss.servername FROM automessage_servers x "
                + "JOIN servers ss ON ss.id=x.server_id";
        try (Statement readLinks = source.createStatement(); ResultSet links = readLinks.executeQuery(sql)) {
            String insertSql = "sqlite".equalsIgnoreCase(targetType)
                    ? "INSERT OR IGNORE INTO automessage_servers (automessage_id,server_id) VALUES (?,?)"
                    : "INSERT IGNORE INTO automessage_servers (automessage_id,server_id) VALUES (?,?)";
            try (PreparedStatement findServer = target.prepareStatement("SELECT id FROM servers WHERE servername=?");
                 PreparedStatement insertLink = target.prepareStatement(insertSql)) {
                while (links.next()) {
                    Long targetMessageId = messageIds.get(links.getLong(1));
                    if (targetMessageId == null) continue;
                    findServer.setString(1, links.getString(2));
                    try (ResultSet targetServer = findServer.executeQuery()) {
                        if (!targetServer.next()) continue;
                        insertLink.setLong(1, targetMessageId); insertLink.setLong(2, targetServer.getLong(1)); insertLink.addBatch();
                    }
                }
                insertLink.executeBatch();
            }
        }
    }

    private Long findMatchingAutoMessage(Connection target, String name, String description) throws SQLException {
        try (PreparedStatement query = target.prepareStatement("SELECT id FROM automessages WHERE name=? AND ((description IS NULL AND ? IS NULL) OR description=?) ORDER BY id LIMIT 1")) {
            query.setString(1, name); query.setString(2, description); query.setString(3, description);
            try (ResultSet result = query.executeQuery()) { return result.next() ? result.getLong(1) : null; }
        }
    }

    private String uniqueAutoMessageName(Connection target, String originalName) throws SQLException {
        String base = originalName == null || originalName.isBlank() ? "Imported message" : originalName;
        if (base.length() > 105) base = base.substring(0, 105);
        String candidate = base;
        int suffix = 2;
        while (autoMessageNameExists(target, candidate)) {
            String ending = " (" + suffix++ + ")";
            candidate = base.substring(0, Math.min(base.length(), 128 - ending.length())) + ending;
        }
        return candidate;
    }

    private boolean autoMessageNameExists(Connection target, String name) throws SQLException {
        try (PreparedStatement query = target.prepareStatement("SELECT 1 FROM automessages WHERE name=?")) {
            query.setString(1, name);
            try (ResultSet result = query.executeQuery()) { return result.next(); }
        }
    }

    private FileConfiguration loadConfig() {
        return new HoloPanelConfig(plugin).loadDatabaseConfig();
    }

    private FileConfiguration loadAutoMessageConfig() {
        File file = new File(plugin.getDataFolder(), "Addons/AutoMesssage/automessage.yml");
        return YamlDefaults.loadAndUpdate(plugin, "Addons/AutoMesssage/automessage.yml", file);
    }

    private void loadDriver(String className) throws SQLException {
        try {
            Class.forName(className, true, getClass().getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Could not load database driver " + className + ".", exception);
        }
    }
}
