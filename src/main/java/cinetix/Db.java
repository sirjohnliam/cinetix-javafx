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
            // Lets an older database (created before this update) catch up without the user deleting it.
            tryExec(s, "ALTER TABLE movies ADD COLUMN IF NOT EXISTS synopsis TEXT");
            tryExec(s, "ALTER TABLE movies ADD COLUMN IF NOT EXISTS poster_path VARCHAR(400)");
            // Round 1: archive flags + security question for "Forgot password".
            tryExec(s, "ALTER TABLE users ADD COLUMN IF NOT EXISTS archived INT NOT NULL DEFAULT 0");
            tryExec(s, "ALTER TABLE users ADD COLUMN IF NOT EXISTS sec_question VARCHAR(150)");
            tryExec(s, "ALTER TABLE users ADD COLUMN IF NOT EXISTS sec_answer_hash VARCHAR(200)");
            tryExec(s, "ALTER TABLE movies ADD COLUMN IF NOT EXISTS archived INT NOT NULL DEFAULT 0");
            tryExec(s, "ALTER TABLE schedules ADD COLUMN IF NOT EXISTS archived INT NOT NULL DEFAULT 0");
            // Accounts that were "disabled" before this update are treated as archived (archived <=> active = 0).
            tryExec(s, "UPDATE users SET archived=1 WHERE active=0 AND archived=0");
        }
        if (one("SELECT 1 FROM users") == null) {
            String[][] u = {{"Clowee Aganap","clowee5","super123","superadmin"},{"Admin User","admin1","admin123","admin"},{"Liam Serrano","liams1","cashier123","cashier"}};
            for (String[] x : u) update("INSERT INTO users(name,username,password_hash,role,sec_question,sec_answer_hash) VALUES(?,?,?,?,?,?)",
                    x[0], x[1], hash(x[2]), x[3], QUESTIONS[4], hashAnswer("cinetix"));   // demo accounts: favorite movie = cinetix
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

    // ---- lists used by the movie form ----
    static final String[] GENRES = {"Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family",
            "Fantasy", "Horror", "Musical", "Mystery", "Romance", "Sci-Fi", "Thriller", "War"};
    static final String[] CINEMAS = {"Cinema 1", "Cinema 2", "Cinema 3", "Cinema 4", "Cinema 5", "Cinema 6"};

    // ---- several statements saved as one unit ----
    interface Work { void run(Connection c) throws Exception; }
    /** All statements run on one connection: they are all saved, or none are. */
    static void tx(Work w) throws Exception {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try { w.run(c); c.commit(); } catch (Exception e) { c.rollback(); throw e; }
        }
    }
    /** Runs one INSERT/UPDATE on an open connection; returns the new row's id (0 for an update). */
    static long exec(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
            p.executeUpdate();
            try (ResultSet k = p.getGeneratedKeys()) { return k.next() ? k.getLong(1) : 0; }
        }
    }

    // ---- account rules (used by the login, forgot-password and account forms) ----
    static final String[] QUESTIONS = {"What was the name of your first pet?", "What city were you born in?",
            "What is your mother's maiden name?", "What was the name of your first school?", "What is your favorite movie?"};

    /** null when the password is acceptable, otherwise what is missing. */
    static String passwordProblem(String pw) {
        if (pw == null || pw.length() < 8) return "Password needs at least 8 characters.";
        if (pw.length() > 32) return "Password can't be longer than 32 characters.";
        if (pw.chars().anyMatch(Character::isWhitespace)) return "Password can't contain spaces.";
        if (pw.chars().noneMatch(Character::isUpperCase)) return "Add an uppercase letter.";
        if (pw.chars().noneMatch(Character::isLowerCase)) return "Add a lowercase letter.";
        if (pw.chars().noneMatch(Character::isDigit)) return "Add a number.";
        if (pw.chars().allMatch(Character::isLetterOrDigit)) return "Add a special character, for example ! @ # $ %.";
        return null;
    }
    /** null when the username is acceptable: 4-20 characters, letters, numbers, _ and . only. */
    static String usernameProblem(String u) {
        if (u == null || u.length() < 4) return "Username needs at least 4 characters.";
        if (u.length() > 20) return "Username can't be longer than 20 characters.";
        if (!u.matches("[A-Za-z0-9_.]+")) return "Use only letters, numbers, _ and . in a username.";
        return null;
    }
    /** exceptId = the account being edited (so it doesn't clash with itself), or null when adding. */
    static boolean usernameTaken(String u, Object exceptId) {
        try { return one("SELECT 1 FROM users WHERE username=? AND id<>?", u, exceptId == null ? 0 : exceptId) != null; }
        catch (SQLException e) { return false; }
    }
    // The security answer is stored hashed, ignoring case and surrounding spaces.
    static String hashAnswer(String a) { return hash(a.trim().toLowerCase()); }
    static boolean checkAnswer(String a, String stored) { return a != null && stored != null && check(a.trim().toLowerCase(), stored); }

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