package ru.dmitry.bookingbot;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Database {

    private final HikariDataSource ds;

    /** postgresql://user:pass@host[:port]/db[?params]  или готовый jdbc:postgresql://... */
    private static final Pattern PG_URL = Pattern.compile(
            "^(?:postgresql|postgres)://([^:/@]+):([^@]+)@([^:/?]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?$"
    );

    public Database(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalStateException("DATABASE_URL пустой");
        }

        // чистим кавычки, пробелы, переносы строк, обёртку psql '...'
        String url = rawUrl.trim()
                .replaceAll("^psql\\s+", "")
                .replaceAll("^['\"]|['\"]$", "")
                .replaceAll("\\s+", "");

        HikariConfig cfg = new HikariConfig();

        if (url.startsWith("jdbc:")) {
            cfg.setJdbcUrl(url);
        } else {
            Matcher m = PG_URL.matcher(url);
            if (!m.matches()) {
                throw new IllegalStateException(
                        "DATABASE_URL не распознан. Нужен формат "
                                + "postgresql://ПОЛЬЗОВАТЕЛЬ:ПАРОЛЬ@хост/база?sslmode=require\n"
                                + "Пришло: " + mask(url));
            }

            String user = m.group(1);
            String pass = m.group(2);
            String host = m.group(3);
            String port = m.group(4) == null ? "5432" : m.group(4);
            String dbName = m.group(5);
            String query = (m.group(6) == null || m.group(6).isBlank())
                    ? "sslmode=require" : m.group(6);

            String jdbc = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?" + query;
            System.out.println("Подключаюсь: " + jdbc + " (пользователь " + user + ")");

            cfg.setJdbcUrl(jdbc);
            cfg.setUsername(user);
            cfg.setPassword(pass);
        }

        cfg.setDriverClassName("org.postgresql.Driver");
        cfg.setMaximumPoolSize(3);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(30000);
        cfg.setIdleTimeout(60000);
        cfg.setMaxLifetime(300000);

        this.ds = new HikariDataSource(cfg);
    }

    private static String mask(String url) {
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:***@");
    }

    private Connection conn() throws SQLException {
        return ds.getConnection();
    }

    public void init() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS booking (
                    id BIGSERIAL PRIMARY KEY,
                    chat_id BIGINT NOT NULL,
                    user_name TEXT,
                    phone TEXT,
                    service TEXT NOT NULL,
                    master TEXT NOT NULL,
                    day TEXT NOT NULL,
                    time TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    status TEXT NOT NULL DEFAULT 'active'
                )
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_booking_slot ON booking (master, day, status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_booking_chat ON booking (chat_id, status)");
        }
        System.out.println("База готова");
    }

    public long save(Booking b) throws SQLException {
        String sql = """
            INSERT INTO booking (chat_id, user_name, phone, service, master, day, time)
            VALUES (?,?,?,?,?,?,?)
            RETURNING id
        """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, b.chatId);
            ps.setString(2, b.userName);
            ps.setString(3, b.phone);
            ps.setString(4, b.service);
            ps.setString(5, b.master);
            ps.setString(6, b.day);
            ps.setString(7, b.time);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public List<String> busySlots(String master, String day) throws SQLException {
        List<String> out = new ArrayList<>();
        String sql = "SELECT time FROM booking WHERE master=? AND day=? AND status='active'";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, master);
            ps.setString(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    public List<Booking> activeByChat(long chatId) throws SQLException {
        List<Booking> out = new ArrayList<>();
        String sql = "SELECT * FROM booking WHERE chat_id=? AND status='active' ORDER BY id DESC";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public List<Booking> allActive() throws SQLException {
        List<Booking> out = new ArrayList<>();
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM booking WHERE status='active' ORDER BY day, time")) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    public boolean cancel(long id, long chatId) throws SQLException {
        String sql = "UPDATE booking SET status='cancelled' WHERE id=? AND chat_id=?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, chatId);
            return ps.executeUpdate() > 0;
        }
    }

    private Booking map(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.id = rs.getLong("id");
        b.chatId = rs.getLong("chat_id");
        b.userName = rs.getString("user_name");
        b.phone = rs.getString("phone");
        b.service = rs.getString("service");
        b.master = rs.getString("master");
        b.day = rs.getString("day");
        b.time = rs.getString("time");
        return b;
    }
}