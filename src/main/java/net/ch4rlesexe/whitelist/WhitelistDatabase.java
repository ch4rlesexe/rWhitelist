package net.ch4rlesexe.whitelist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class WhitelistDatabase {

    private final WhitelistConfig config;
    private final Logger logger;
    private HikariDataSource dataSource;


    private final Set<String> lockedServers = ConcurrentHashMap.newKeySet();


    private final Set<String> disabledTargets = ConcurrentHashMap.newKeySet();

    public WhitelistDatabase(WhitelistConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void init() {


        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.error("MySQL driver class not found!", e);
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://" + config.host + ":" + config.port + "/" + config.database
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true");
        hc.setUsername(config.username);
        hc.setPassword(config.password);
        hc.setMaximumPoolSize(5);
        hc.setPoolName("rWhitelist-HikariPool");
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "25");
        this.dataSource = new HikariDataSource(hc);

        createTables();
        logger.info("WhitelistDatabase connected to {}:{}/{}", config.host, config.port, config.database);
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void createTables() {
        String prefix = config.tablePrefix;
        String entriesSql = """
                CREATE TABLE IF NOT EXISTS %sentries (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    wl_group    VARCHAR(32)  NOT NULL,
                    uuid        VARCHAR(36)  NOT NULL,
                    username    VARCHAR(64)  NOT NULL,
                    added_by    VARCHAR(64)  NOT NULL DEFAULT 'console',
                    added_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_group_uuid (wl_group, uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(prefix);
        String locksSql = """
                CREATE TABLE IF NOT EXISTS %slocks (
                    server_name VARCHAR(64) NOT NULL PRIMARY KEY,
                    locked_by   VARCHAR(64) NOT NULL DEFAULT 'console',
                    locked_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(prefix);
        String disabledSql = """
                CREATE TABLE IF NOT EXISTS %sdisabled (
                    target      VARCHAR(64) NOT NULL PRIMARY KEY,
                    disabled_by VARCHAR(64) NOT NULL DEFAULT 'console',
                    disabled_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(prefix);
        String codesSql = """
                CREATE TABLE IF NOT EXISTS %scodes (
                    code        VARCHAR(32) NOT NULL PRIMARY KEY,
                    wl_group    VARCHAR(32) NOT NULL,
                    max_uses    INT         NOT NULL DEFAULT 1,
                    uses_count  INT         NOT NULL DEFAULT 0,
                    created_by  VARCHAR(64) NOT NULL DEFAULT 'console',
                    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(prefix);
        String redemptionsSql = """
                CREATE TABLE IF NOT EXISTS %scode_redemptions (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    code        VARCHAR(32) NOT NULL,
                    uuid        VARCHAR(36) NOT NULL,
                    username    VARCHAR(64) NOT NULL,
                    redeemed_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_code_uuid (code, uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(prefix);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(entriesSql);
            stmt.execute(locksSql);
            stmt.execute(disabledSql);
            stmt.execute(codesSql);
            stmt.execute(redemptionsSql);
        } catch (SQLException e) {
            logger.error("Failed to create whitelist tables", e);
        }


        reloadCache();
    }

    public void reloadCache() {
        loadLockedServers();
        loadDisabledTargets();
    }



    private void loadLockedServers() {
        String sql = "SELECT server_name FROM " + config.tablePrefix + "locks";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            lockedServers.clear();
            while (rs.next()) {
                lockedServers.add(rs.getString("server_name").toLowerCase());
            }
            if (!lockedServers.isEmpty()) {
                logger.info("Loaded admin locks for servers: {}", lockedServers);
            }
        } catch (SQLException e) {
            logger.error("Failed to load server locks", e);
        }
    }


    public boolean isServerLocked(String serverName) {
        return lockedServers.contains(serverName.toLowerCase());
    }


    public boolean lockServer(String serverName, String lockedBy) {
        String lower = serverName.toLowerCase();
        String sql = "INSERT IGNORE INTO " + config.tablePrefix + "locks (server_name, locked_by) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lower);
            ps.setString(2, lockedBy);
            boolean inserted = ps.executeUpdate() > 0;
            if (inserted) lockedServers.add(lower);
            return inserted;
        } catch (SQLException e) {
            logger.error("Failed to lock server {}", serverName, e);
            return false;
        }
    }


    public boolean unlockServer(String serverName) {
        String lower = serverName.toLowerCase();
        String sql = "DELETE FROM " + config.tablePrefix + "locks WHERE server_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lower);
            boolean deleted = ps.executeUpdate() > 0;
            if (deleted) lockedServers.remove(lower);
            return deleted;
        } catch (SQLException e) {
            logger.error("Failed to unlock server {}", serverName, e);
            return false;
        }
    }


    public Set<String> getLockedServers() {
        return Collections.unmodifiableSet(lockedServers);
    }



    private void loadDisabledTargets() {
        String sql = "SELECT target FROM " + config.tablePrefix + "disabled";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            disabledTargets.clear();
            while (rs.next()) {
                disabledTargets.add(rs.getString("target").toLowerCase());
            }
            if (!disabledTargets.isEmpty()) {
                logger.info("Loaded disabled whitelist targets: {}", disabledTargets);
            }
        } catch (SQLException e) {
            logger.error("Failed to load disabled whitelist targets", e);
        }
    }


    public boolean enableWhitelist(String target) {
        String lower = target.toLowerCase();
        String sql = "DELETE FROM " + config.tablePrefix + "disabled WHERE target = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lower);
            boolean removed = ps.executeUpdate() > 0;
            disabledTargets.remove(lower);
            return removed;
        } catch (SQLException e) {
            logger.error("Failed to enable whitelist for {}", target, e);
            return false;
        }
    }


    public boolean disableWhitelist(String target, String disabledBy) {
        String lower = target.toLowerCase();
        String sql = "INSERT IGNORE INTO " + config.tablePrefix + "disabled (target, disabled_by) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lower);
            ps.setString(2, disabledBy);
            boolean inserted = ps.executeUpdate() > 0;
            if (inserted) disabledTargets.add(lower);
            return inserted;
        } catch (SQLException e) {
            logger.error("Failed to disable whitelist for {}", target, e);
            return false;
        }
    }


    public boolean isWhitelistEnabled(String target) {
        return !disabledTargets.contains(target.toLowerCase());
    }

    public Set<String> getDisabledTargets() {
        return Collections.unmodifiableSet(disabledTargets);
    }




    public boolean isWhitelisted(String group, UUID uuid, String username) {
        String cleanName = username != null ? username : "";
        String dotName = cleanName.startsWith(".") ? cleanName : "." + cleanName;
        String noDotName = cleanName.startsWith(".") ? cleanName.substring(1) : cleanName;

        String sql = "SELECT uuid, username FROM " + config.tablePrefix
                + "entries WHERE wl_group = ? AND (uuid = ? OR LOWER(username) = LOWER(?) OR LOWER(username) = LOWER(?) OR LOWER(username) = LOWER(?)) LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group);
            ps.setString(2, uuid.toString());
            ps.setString(3, cleanName);
            ps.setString(4, dotName);
            ps.setString(5, noDotName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dbUuid = rs.getString("uuid");
                    String dbUsername = rs.getString("username");
                    if (username != null && (!uuid.toString().equalsIgnoreCase(dbUuid) || !username.equalsIgnoreCase(dbUsername))) {
                        updateUuidForUser(uuid, username, dbUsername);
                    }
                    return true;
                }
                return false;
            }
        } catch (SQLException e) {
            logger.error("Failed to check whitelist for {}/{}/{}", group, uuid, username, e);
            return false;
        }
    }


    public boolean isWhitelisted(String group, UUID uuid) {
        return isWhitelisted(group, uuid, null);
    }

    public void updateUuidForUser(UUID newUuid, String username, String oldUsername) {
        String sql = "UPDATE " + config.tablePrefix + "entries SET uuid = ?, username = ? WHERE LOWER(username) = LOWER(?) OR LOWER(username) = LOWER(?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newUuid.toString());
            ps.setString(2, username);
            ps.setString(3, username);
            ps.setString(4, oldUsername != null ? oldUsername : username);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update UUID for {}", username, e);
        }
    }

    public void updateUuidForUser(UUID newUuid, String username) {
        updateUuidForUser(newUuid, username, null);
    }


    public boolean addPlayer(String group, UUID uuid, String username, String addedBy) {
        String sql = "INSERT IGNORE INTO " + config.tablePrefix
                + "entries (wl_group, uuid, username, added_by) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group);
            ps.setString(2, uuid.toString());
            ps.setString(3, username);
            ps.setString(4, addedBy);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to add {} to {}", username, group, e);
            return false;
        }
    }


    public boolean removePlayer(String group, UUID uuid) {
        String sql = "DELETE FROM " + config.tablePrefix + "entries WHERE wl_group = ? AND uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to remove {} from {}", uuid, group, e);
            return false;
        }
    }


    public boolean removePlayerByName(String group, String username) {
        String dotName = username.startsWith(".") ? username : "." + username;
        String noDotName = username.startsWith(".") ? username.substring(1) : username;
        String sql = "DELETE FROM " + config.tablePrefix + "entries WHERE wl_group = ? AND (LOWER(username) = LOWER(?) OR LOWER(username) = LOWER(?) OR LOWER(username) = LOWER(?))";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group);
            ps.setString(2, username);
            ps.setString(3, dotName);
            ps.setString(4, noDotName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to remove {} from {}", username, group, e);
            return false;
        }
    }


    public int clearGroup(String group) {
        String sql = "DELETE FROM " + config.tablePrefix + "entries WHERE wl_group = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group);
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to clear group {}", group, e);
            return -1;
        }
    }


    public List<WhitelistEntry> listGroup(String group) {
        List<WhitelistEntry> entries = new ArrayList<>();
        String sql = "SELECT uuid, username, added_by, added_at FROM " + config.tablePrefix
                + "entries WHERE wl_group = ? ORDER BY added_at DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new WhitelistEntry(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            rs.getString("added_by"),
                            rs.getTimestamp("added_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to list whitelist group {}", group, e);
        }
        return entries;
    }


    public List<String> getPlayerGroups(UUID uuid) {
        List<String> groups = new ArrayList<>();
        String sql = "SELECT wl_group FROM " + config.tablePrefix + "entries WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groups.add(rs.getString("wl_group"));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get groups for {}", uuid, e);
        }
        return groups;
    }


    public UUID lookupUuid(String username) {
        String dotName = username.startsWith(".") ? username : "." + username;
        String noDotName = username.startsWith(".") ? username.substring(1) : username;
        String sql = "SELECT uuid FROM " + config.tablePrefix
                + "entries WHERE LOWER(username) = LOWER(?) OR LOWER(username) = LOWER(?) OR LOWER(username) = LOWER(?) LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, dotName);
            ps.setString(3, noDotName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to lookup UUID for {}", username, e);
        }
        return null;
    }


    public void updateUsername(UUID uuid, String newName) {
        String sql = "UPDATE " + config.tablePrefix + "entries SET username = ? WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update username for {}", uuid, e);
        }
    }



    public boolean createCode(String code, String group, int maxUses, String createdBy) {
        String sql = "INSERT INTO " + config.tablePrefix + "codes (code, wl_group, max_uses, created_by) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.toUpperCase());
            ps.setString(2, group.toLowerCase());
            ps.setInt(3, maxUses);
            ps.setString(4, createdBy);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to create whitelist code {}", code, e);
            return false;
        }
    }

    public boolean deleteCode(String code) {
        String sql = "DELETE FROM " + config.tablePrefix + "codes WHERE code = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.toUpperCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete whitelist code {}", code, e);
            return false;
        }
    }

    public List<WhitelistCode> listCodes() {
        List<WhitelistCode> list = new ArrayList<>();
        String sql = "SELECT code, wl_group, max_uses, uses_count, created_by, created_at FROM " + config.tablePrefix + "codes ORDER BY created_at DESC";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new WhitelistCode(
                        rs.getString("code"),
                        rs.getString("wl_group"),
                        rs.getInt("max_uses"),
                        rs.getInt("uses_count"),
                        rs.getString("created_by"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            logger.error("Failed to list whitelist codes", e);
        }
        return list;
    }

    public RedeemResult redeemCode(String codeInput, UUID uuid, String username) {
        String code = codeInput.trim().toUpperCase();
        String selectSql = "SELECT wl_group, max_uses, uses_count FROM " + config.tablePrefix + "codes WHERE code = ? FOR UPDATE";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {

                String checkSql = "SELECT 1 FROM " + config.tablePrefix + "code_redemptions WHERE code = ? AND uuid = ?";
                try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                    psCheck.setString(1, code);
                    psCheck.setString(2, uuid.toString());
                    try (ResultSet rsCheck = psCheck.executeQuery()) {
                        if (rsCheck.next()) {
                            conn.rollback();
                            return new RedeemResult(RedeemStatus.ALREADY_REDEEMED, null, 0);
                        }
                    }
                }

                String group;
                int maxUses;
                int usesCount;
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setString(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return new RedeemResult(RedeemStatus.NOT_FOUND, null, 0);
                        }
                        group = rs.getString("wl_group");
                        maxUses = rs.getInt("max_uses");
                        usesCount = rs.getInt("uses_count");
                    }
                }

                if (usesCount >= maxUses) {
                    conn.rollback();
                    return new RedeemResult(RedeemStatus.EXHAUSTED, group, 0);
                }


                String updateCodeSql = "UPDATE " + config.tablePrefix + "codes SET uses_count = uses_count + 1 WHERE code = ?";
                try (PreparedStatement psUpdate = conn.prepareStatement(updateCodeSql)) {
                    psUpdate.setString(1, code);
                    psUpdate.executeUpdate();
                }


                String insertRedemptionSql = "INSERT INTO " + config.tablePrefix + "code_redemptions (code, uuid, username) VALUES (?, ?, ?)";
                try (PreparedStatement psRedeem = conn.prepareStatement(insertRedemptionSql)) {
                    psRedeem.setString(1, code);
                    psRedeem.setString(2, uuid.toString());
                    psRedeem.setString(3, username);
                    psRedeem.executeUpdate();
                }


                String insertEntrySql = "INSERT IGNORE INTO " + config.tablePrefix + "entries (wl_group, uuid, username, added_by) VALUES (?, ?, ?, ?)";
                try (PreparedStatement psEntry = conn.prepareStatement(insertEntrySql)) {
                    psEntry.setString(1, group);
                    psEntry.setString(2, uuid.toString());
                    psEntry.setString(3, username);
                    psEntry.setString(4, "code:" + code);
                    psEntry.executeUpdate();

                    if (!group.equalsIgnoreCase("hub")) {
                        psEntry.setString(1, "hub");
                        psEntry.setString(2, uuid.toString());
                        psEntry.setString(3, username);
                        psEntry.setString(4, "code:" + code);
                        psEntry.executeUpdate();
                    }
                }

                conn.commit();
                int remaining = maxUses - (usesCount + 1);
                return new RedeemResult(RedeemStatus.SUCCESS, group, remaining);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Failed to redeem code {} for {}", code, username, e);
            return new RedeemResult(RedeemStatus.ERROR, null, 0);
        }
    }



    public enum RedeemStatus {
        SUCCESS,
        NOT_FOUND,
        EXHAUSTED,
        ALREADY_REDEEMED,
        ERROR
    }

    public record RedeemResult(RedeemStatus status, String group, int remainingUses) {}

    public record WhitelistCode(String code, String group, int maxUses, int usesCount, String createdBy, Timestamp createdAt) {}

    public record WhitelistEntry(UUID uuid, String username, String addedBy, Timestamp addedAt) {}
}
