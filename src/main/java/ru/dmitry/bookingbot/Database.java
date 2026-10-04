package ru.dmitry.bookingbot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private final String url;

    public Database(String file) {
        this.url = "jdbc:sqlite:" + file;
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void init() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS booking (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id INTEGER NOT NULL,
                    user_name TEXT,
                    phone TEXT,
                    service TEXT NOT NULL,
                    master TEXT NOT NULL,
                    day TEXT NOT NULL,
                    time TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'active'
                )
            """);
        }
    }

    public long save(Booking b) throws SQLException {
        String sql = """
            INSERT INTO booking (chat_id, user_name, phone, service, master, day, time, created_at, status)
            VALUES (?,?,?,?,?,?,?,datetime('now','localtime'),'active')
        """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, b.chatId);
            ps.setString(2, b.userName);
            ps.setString(3, b.phone);
            ps.setString(4, b.service);
            ps.setString(5, b.master);
            ps.setString(6, b.day);
            ps.setString(7, b.time);
            ps.executeUpdate();
        }
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    /** Занятые слоты у мастера на день — чтобы не записать двоих на одно время. */
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
        String sql = "SELECT * FROM booking WHERE status='active' ORDER BY day, time";
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
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
