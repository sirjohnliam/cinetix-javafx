package cinetix;

import java.security.*;
import java.sql.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** XAMPP defaults: MySQL on 3306, user root, no password. Change here if yours differs. */
public class Db {
    static final String HOST = "jdbc:mariadb://localhost:3306/", NAME = "cinetix_app", USER = "root", PASS = "";
    static final int CAPACITY = 72;   // 6 rows x 12 seats

    static Connection conn() throws SQLException { return DriverManager.getConnection(HOST + NAME, USER, PASS); }

    /** Creates the database, tables and demo data on first run. */
    static void init() throws Exception {
        try (Connection c = DriverManager.getConnection(HOST, USER, PASS); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS " + NAME + " CHARACTER SET utf8mb4");
        }
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS users(id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,"
                    + " username VARCHAR(50) NOT NULL UNIQUE, password_hash VARCHAR(200) NOT NULL,"
                    + " role ENUM('cashier','admin','superadmin') NOT NULL, active INT NOT NULL DEFAULT 1)");
            s.execute("CREATE TABLE IF NOT EXISTS movies(id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(150) NOT NULL,"
                    + " genre VARCHAR(50), rating VARCHAR(10), length INT, synopsis TEXT, poster_path VARCHAR(400))");
            s.execute("CREATE TABLE IF NOT EXISTS schedules(id INT AUTO_INCREMENT PRIMARY KEY, movie_id INT NOT NULL,"
                    + " show_date DATE NOT NULL, show_time TIME NOT NULL, cinema VARCHAR(30) NOT NULL, price DECIMAL(8,2) NOT NULL,"
                    + " FOREIGN KEY(movie_id) REFERENCES movies(id))");
            s.execute("CREATE TABLE IF NOT EXISTS reservations(id INT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(10) NOT NULL,"
                    + " schedule_id INT NOT NULL, seat VARCHAR(4) NOT NULL, price DECIMAL(8,2) NOT NULL, sold_by INT NOT NULL,"
                    + " created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(schedule_id, seat),"
                    + " FOREIGN KEY(schedule_id) REFERENCES schedules(id), FOREIGN KEY(sold_by) REFERENCES users(id))");
            s.execute("CREATE TABLE IF NOT EXISTS account_requests(id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,"
                    + " username VARCHAR(50) NOT NULL, password_hash VARCHAR(200) NOT NULL, role ENUM('cashier','admin') NOT NULL,"
                    + " status ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',"
                    + " created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            // Lets an older database (created before this update) catch up without the user deleting it.
            tryExec(s, "ALTER TABLE movies ADD COLUMN IF NOT EXISTS synopsis TEXT");
            tryExec(s, "ALTER TABLE movies ADD COLUMN IF NOT EXISTS poster_path VARCHAR(400)");
        }
        if (one("SELECT 1 FROM users") == null) {
            String[][] u = {{"Clowee Aganap","clowee5","super123","superadmin"},{"Admin User","admin1","admin123","admin"},{"Liam Serrano","liams1","cashier123","cashier"}};
            for (String[] x : u) update("INSERT INTO users(name,username,password_hash,role) VALUES(?,?,?,?)", x[0], x[1], hash(x[2]), x[3]);
            Object[][] m = {{"Resident Evil","Horror","R",120},{"Obsession","Horror","R",109},{"Project Hail Mary","Sci-Fi","PG-13",156},{"The Wind Rises","Drama","PG-13",126}};
            String[] syn = {"A detective and a small team confront a zombie outbreak at a research facility.",
                    "A woman becomes consumed by a dangerous, all-consuming infatuation.",
                    "An astronaut wakes up alone on a mission to save humanity from a dying sun.",
                    "A young man pursues his dream of designing aircraft in pre-war Japan."};
            String[] t = {"10:30","10:30","13:00","16:00"};
            for (int i = 0; i < 4; i++) {
                update("INSERT INTO movies(title,genre,rating,length,synopsis) VALUES(?,?,?,?,?)", m[i][0], m[i][1], m[i][2], m[i][3], syn[i]);
                update("INSERT INTO schedules(movie_id,show_date,show_time,cinema,price) VALUES(?,CURDATE(),?,?,250)", i + 1, t[i], "Cinema " + (i + 1));
            }
        }
    }

    /** Runs a migration statement and ignores "already exists" style errors on a database that was set up before this change. */
    static void tryExec(Statement s, String sql) { try { s.execute(sql); } catch (SQLException ignored) {} }

    static List<Map<String, Object>> query(String sql, Object... a) throws SQLException {
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
            try (ResultSet rs = p.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData(); List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object o = rs.getObject(i);
                        if (o instanceof java.util.Date) o = o.toString();   // DATE / TIME / DATETIME -> text
                        m.put(md.getColumnLabel(i), o);
                    }
                    out.add(m);
                }
                return out;
            }
        }
    }
    static Object one(String sql, Object... a) throws SQLException {
        var r = query(sql, a); return r.isEmpty() ? null : r.get(0).values().iterator().next();
    }
    static int update(String sql, Object... a) throws SQLException {
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
            return p.executeUpdate();
        }
    }
    static String newCode() throws SQLException {
        String c; do { c = String.format("CT-%04d", new Random().nextInt(10000)); } while (one("SELECT 1 FROM reservations WHERE code=?", c) != null);
        return c;
    }

    // ---- passwords: PBKDF2 (built into Java, no extra library) ----
    static byte[] pbkdf2(String pw, byte[] salt) {
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(pw.toCharArray(), salt, 100_000, 256)).getEncoded(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    static String hash(String pw) {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf2(pw, salt));
    }
    static boolean check(String pw, String stored) {
        String[] p = stored.split(":");
        return MessageDigest.isEqual(pbkdf2(pw, Base64.getDecoder().decode(p[0])), Base64.getDecoder().decode(p[1]));
    }
}