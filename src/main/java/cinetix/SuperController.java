package cinetix;

import cinetix.Ui.Act;
import cinetix.Ui.F;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Superadmin: overall sales overview, creates/edits/archives accounts, restores archived ones. */
public class SuperController {
    @FXML ToggleGroup nav;
    @FXML Label who, role, sTotal, sTix, sTx;
    @FXML VBox pOver, pAcc, pArc;
    @FXML DatePicker fromDp, toDp;
    @FXML TableView<Map<String, Object>> accT, arcT, sMovieT, sDayT, sCashT, sTxT;

    @FXML void initialize() {
        who.setText(App.name); role.setText("Superadmin");

        // overview tables
        Ui.cols(sMovieT, "Movie", "title", "Tickets sold", "tickets", "Sales", "$total");
        Ui.cols(sDayT, "Date", "day_lbl", "Tickets sold", "tickets", "Sales", "$total");
        Ui.cols(sCashT, "Cashier", "cashier", "Transactions", "transactions", "Tickets", "tickets", "Sales", "$total");
        Ui.cols(sTxT, "Code", "code", "Movie", "title", "Showtime", "showtime", "Seats", "seats", "Total", "$total", "Sold by", "sold_by");
        for (var t : List.of(sMovieT, sDayT, sCashT, sTxT)) {
            t.setFixedCellSize(54); t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        }
        fromDp.setValue(LocalDate.now()); toDp.setValue(LocalDate.now());

        // accounts + archive
        Ui.cols(accT, "Name", "name", "Username", "username", "Role", "role");
        Ui.actionList(accT, new Act("Edit", this::edit), new Act("Reset password", this::reset), new Act("Archive", this::archive));
        Ui.cols(arcT, "Name", "name", "Username", "username", "Role", "role");
        Ui.actionList(arcT, new Act("Restore", this::restore));
        show("Overview");
    }
    @FXML void logout() { if (Ui.confirm("Are you sure you want to log out?")) App.logout(); }
    @FXML void navigate(ActionEvent e) { show(((ToggleButton) e.getSource()).getText()); }

    void show(String n) {
        Ui.vis(pOver, n.equals("Overview")); Ui.vis(pAcc, n.equals("Accounts")); Ui.vis(pArc, n.equals("Archive"));
        switch (n) { case "Overview" -> loadOver(); case "Accounts" -> load(); default -> loadArc(); }
    }

    // ---- overview ----
    void rows(TableView<Map<String, Object>> t, List<Map<String, Object>> data) {
        t.setItems(FXCollections.observableArrayList(data));
        double h = Math.max(1, data.size()) * 54 + 42;
        t.setPrefHeight(h); t.setMinHeight(h); t.setMaxHeight(h);
    }
    @FXML void loadOver() {
        LocalDate f = fromDp.getValue(), t = toDp.getValue();
        if (f == null || t == null) { Ui.info("Pick both a From and a To date."); return; }
        if (f.isAfter(t)) { Ui.info("The From date can't be after the To date."); return; }
        String a = f.toString(), b = t.toString();
        Ui.safe(() -> {
            var sum = Db.query("SELECT COALESCE(SUM(price),0) total, COUNT(*) tickets, COUNT(DISTINCT code) tx"
                    + " FROM reservations WHERE DATE(created_at) BETWEEN ? AND ?", a, b).get(0);
            sTotal.setText(Ui.peso(sum.get("total")));
            sTix.setText("" + sum.get("tickets"));
            sTx.setText("" + sum.get("tx"));

            rows(sMovieT, Db.query("SELECT m.title, COUNT(*) tickets, SUM(r.price) total FROM reservations r"
                    + " JOIN schedules s ON s.id=r.schedule_id JOIN movies m ON m.id=s.movie_id"
                    + " WHERE DATE(r.created_at) BETWEEN ? AND ? GROUP BY m.title ORDER BY total DESC", a, b));

            rows(sDayT, Db.query("SELECT DATE_FORMAT(created_at,'%a, %b %e') day_lbl, COUNT(*) tickets, SUM(price) total"
                    + " FROM reservations WHERE DATE(created_at) BETWEEN ? AND ?"
                    + " GROUP BY DATE(created_at), DATE_FORMAT(created_at,'%a, %b %e') ORDER BY DATE(created_at) DESC", a, b));

            rows(sCashT, Db.query("SELECT u.name cashier, COUNT(DISTINCT r.code) transactions, COUNT(*) tickets, SUM(r.price) total"
                    + " FROM reservations r JOIN users u ON u.id=r.sold_by"
                    + " WHERE DATE(r.created_at) BETWEEN ? AND ? GROUP BY u.id, u.name ORDER BY total DESC", a, b));

            rows(sTxT, Db.query("SELECT r.code, m.title,"
                    + " CONCAT(DATE_FORMAT(s.show_date,'%a, %b %e'),', ',TIME_FORMAT(s.show_time,'%l:%i %p'),' · ',s.cinema) showtime,"
                    + " GROUP_CONCAT(r.seat ORDER BY r.seat SEPARATOR ', ') seats, SUM(r.price) total, u.name sold_by"
                    + " FROM reservations r JOIN schedules s ON s.id=r.schedule_id JOIN movies m ON m.id=s.movie_id JOIN users u ON u.id=r.sold_by"
                    + " WHERE DATE(r.created_at) BETWEEN ? AND ?"
                    + " GROUP BY r.code, m.title, s.show_date, s.show_time, s.cinema, u.name ORDER BY MIN(r.created_at) DESC", a, b));
        });
    }

    // ---- accounts ----
    void load() {
        Ui.safe(() -> accT.setItems(FXCollections.observableArrayList(
                Db.query("SELECT id, name, username, role, sec_question FROM users WHERE archived=0 ORDER BY id"))));
    }
    void loadArc() {
        Ui.safe(() -> arcT.setItems(FXCollections.observableArrayList(
                Db.query("SELECT id, name, username, role FROM users WHERE archived=1 ORDER BY name"))));
    }

    /** Hint shown under the username box while typing. */
    static String liveUser(String t, Object selfId) {
        if (t.isEmpty()) return null;
        String p = Db.usernameProblem(t); if (p != null) return p;
        return Db.usernameTaken(t, selfId) ? "That username is already taken." : "✓ Username is available.";
    }
    static String livePassword(String t) {
        if (t.isEmpty()) return null;
        String p = Db.passwordProblem(t);
        return p == null ? "✓ Password meets the rules." : p;
    }
    /** Checks shared by Add and Edit. */
    static String basics(Map<String, String> m, Object selfId) {
        if (m.get("name").length() < 2) return "Enter the full name (at least 2 characters).";
        String e = Db.usernameProblem(m.get("username")); if (e != null) return e;
        if (Db.usernameTaken(m.get("username"), selfId)) return "That username is already taken.";
        return null;
    }

    @FXML void add() {
        Map<String, Function<String, String>> live = new HashMap<>();
        live.put("username", t -> liveUser(t, null));
        live.put("password", SuperController::livePassword);
        Ui.form("Add account", m -> {
                            String p = basics(m, null); if (p != null) return p;
                            p = Db.passwordProblem(m.get("password")); if (p != null) return p;
                            if (!m.get("password").equals(m.get("password2"))) return "The two passwords don't match.";
                            if (m.get("answer").length() < 2) return "Enter a security answer (at least 2 characters).";
                            return null;
                        }, live,
                        new F("name", "Full name", null), new F("username", "Username", null),
                        new F("role", "Role", "cashier", "cashier", "admin", "superadmin"),
                        new F("password", "Temporary password", null), new F("password2", "Confirm password", null),
                        new F("question", "Security question", null, Db.QUESTIONS), new F("answer", "Security answer", null))
                .ifPresent(m -> Ui.safe(() -> {
                    Db.update("INSERT INTO users(name,username,password_hash,role,sec_question,sec_answer_hash) VALUES(?,?,?,?,?,?)",
                            m.get("name"), m.get("username"), Db.hash(m.get("password")), m.get("role"), m.get("question"), Db.hashAnswer(m.get("answer")));
                    load();
                }));
    }

    void edit(Map<String, Object> u) {
        Object id = u.get("id"); boolean self = ((Number) id).intValue() == App.uid;
        Map<String, Function<String, String>> live = new HashMap<>();
        live.put("username", t -> liveUser(t, id));
        Ui.form("Edit account", m -> {
                            String p = basics(m, id); if (p != null) return p;
                            if (self && !m.get("role").equals(u.get("role"))) return "You can't change your own role.";
                            if (!m.get("answer").isEmpty() && m.get("answer").length() < 2) return "The security answer needs at least 2 characters.";
                            return null;
                        }, live,
                        new F("name", "Full name", Ui.s(u, "name")), new F("username", "Username", Ui.s(u, "username")),
                        new F("role", "Role", Ui.s(u, "role"), "cashier", "admin", "superadmin"),
                        new F("question", "Security question", Ui.s(u, "sec_question"), Db.QUESTIONS),
                        new F("answer", "New security answer (blank keeps the current one)", null))
                .ifPresent(m -> Ui.safe(() -> {
                    Db.update("UPDATE users SET name=?, username=?, role=? WHERE id=?", m.get("name"), m.get("username"), m.get("role"), id);
                    if (!m.get("answer").isEmpty())
                        Db.update("UPDATE users SET sec_question=?, sec_answer_hash=? WHERE id=?", m.get("question"), Db.hashAnswer(m.get("answer")), id);
                    if (self) App.name = m.get("name");
                    load();
                }));
    }

    void reset(Map<String, Object> u) {
        Map<String, Function<String, String>> live = new HashMap<>();
        live.put("password", SuperController::livePassword);
        Ui.form("Reset password for " + u.get("username"), m -> {
                            String p = Db.passwordProblem(m.get("password")); if (p != null) return p;
                            return m.get("password").equals(m.get("password2")) ? null : "The two passwords don't match.";
                        }, live,
                        new F("password", "New password", null), new F("password2", "Confirm password", null))
                .ifPresent(m -> Ui.safe(() -> {
                    Db.update("UPDATE users SET password_hash=? WHERE id=?", Db.hash(m.get("password")), u.get("id"));
                    Ui.info("Password updated.");
                }));
    }

    /** Accounts are never deleted: archiving blocks the login and moves the account to the Archive tab. */
    void archive(Map<String, Object> u) {
        if (((Number) u.get("id")).intValue() == App.uid) { Ui.error("You can't archive your own account."); return; }
        if (!Ui.confirm("Archive " + u.get("username") + "? They won't be able to log in. You can restore them from the Archive tab.")) return;
        Ui.safe(() -> { Db.update("UPDATE users SET archived=1, active=0 WHERE id=?", u.get("id")); load(); });
    }
    void restore(Map<String, Object> u) {
        Ui.safe(() -> { Db.update("UPDATE users SET archived=0, active=1 WHERE id=?", u.get("id")); loadArc(); });
    }
}