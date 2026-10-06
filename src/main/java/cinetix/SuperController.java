package cinetix;

import cinetix.Ui.F;
import java.time.LocalDate;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Superadmin: overall sales overview, creates and manages cashier/admin accounts, and approves or rejects sign-up requests. */
public class SuperController {
    @FXML ToggleGroup nav;
    @FXML ToggleButton reqNav;
    @FXML Label who, role, sTotal, sTix, sTx;
    @FXML VBox pOver, pAcc, pReq;
    @FXML DatePicker fromDp, toDp;
    @FXML TableView<Map<String, Object>> accT, reqT, sMovieT, sDayT, sCashT, sTxT;

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

        // account tables
        Ui.cols(accT, "Name", "name", "Username", "username", "Role", "role", "Status", "status");
        Ui.actions(accT, "Reset password", this::reset, "Disable/Enable", this::toggle);
        Ui.cols(reqT, "Name", "name", "Username", "username", "Role requested", "role", "Requested", "created_at");
        Ui.actions(reqT, "Approve", this::approve, "Reject", this::reject);
        show("Overview");
    }
    @FXML void logout() { if (Ui.confirm("Are you sure you want to log out?")) App.logout(); }
    @FXML void navigate(ActionEvent e) {
        String t = ((ToggleButton) e.getSource()).getText();
        show(t.startsWith("Account requests") ? "Requests" : t.equals("Overview") ? "Overview" : "Accounts");
    }

    void show(String n) {
        Ui.vis(pOver, n.equals("Overview")); Ui.vis(pAcc, n.equals("Accounts")); Ui.vis(pReq, n.equals("Requests"));
        switch (n) { case "Overview" -> loadOver(); case "Accounts" -> load(); default -> loadReq(); }
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
                Db.query("SELECT id, name, username, role, IF(active=1,'Active','Disabled') status FROM users ORDER BY id"))));
    }
    @FXML void add() {
        Ui.form("Add account", new F("name", "Full name", null), new F("username", "Username", null),
                        new F("role", "Role", "cashier", "cashier", "admin", "superadmin"), new F("password", "Temporary password", null))
                .ifPresent(d -> Ui.safe(() -> {
                    if (d.get("name").isEmpty() || d.get("username").isEmpty()) throw new Exception("Enter a name and username.");
                    if (d.get("password").length() < 6) throw new Exception("Password needs 6 or more characters.");
                    Db.update("INSERT INTO users(name,username,password_hash,role) VALUES(?,?,?,?)", d.get("name"), d.get("username"), Db.hash(d.get("password")), d.get("role"));
                    load();
                }));
    }
    void reset(Map<String, Object> u) {
        Ui.form("Reset password for " + u.get("username"), new F("password", "New password", null)).ifPresent(d -> Ui.safe(() -> {
            if (d.get("password").length() < 6) throw new Exception("Password needs 6 or more characters.");
            Db.update("UPDATE users SET password_hash=? WHERE id=?", Db.hash(d.get("password")), u.get("id")); Ui.info("Password updated.");
        }));
    }
    void toggle(Map<String, Object> u) {
        if (((Number) u.get("id")).intValue() == App.uid) { Ui.error("You can't disable your own account."); return; }
        Ui.safe(() -> { Db.update("UPDATE users SET active=1-active WHERE id=?", u.get("id")); load(); });
    }

    // ---- sign-up requests ----
    void loadReq() {
        Ui.safe(() -> {
            var rows = Db.query("SELECT * FROM account_requests WHERE status='pending' ORDER BY created_at");
            reqT.setItems(FXCollections.observableArrayList(rows));
            reqNav.setText(rows.isEmpty() ? "Account requests" : "Account requests (" + rows.size() + ")");
        });
    }
    void approve(Map<String, Object> r) {
        if (!Ui.confirm("Approve " + r.get("username") + " as " + r.get("role") + "?")) return;
        Ui.safe(() -> {
            if (Db.one("SELECT 1 FROM users WHERE username=?", r.get("username")) != null)
                throw new Exception("That username was taken by someone else since the request was sent. Reject it and ask them to request again.");
            Db.update("INSERT INTO users(name,username,password_hash,role) VALUES(?,?,?,?)", r.get("name"), r.get("username"), r.get("password_hash"), r.get("role"));
            Db.update("UPDATE account_requests SET status='approved' WHERE id=?", r.get("id"));
            loadReq(); load();
        });
    }
    void reject(Map<String, Object> r) {
        if (!Ui.confirm("Reject the request from " + r.get("username") + "?")) return;
        Ui.safe(() -> { Db.update("UPDATE account_requests SET status='rejected' WHERE id=?", r.get("id")); loadReq(); });
    }
}