package cinetix;

import cinetix.Ui.F;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Superadmin: creates and manages cashier/admin accounts, and approves or rejects sign-up requests. */
public class SuperController {
    @FXML ToggleGroup nav;
    @FXML ToggleButton reqNav;
    @FXML Label who;
    @FXML VBox pAcc, pReq;
    @FXML TableView<Map<String, Object>> accT, reqT;

    @FXML void initialize() {
        who.setText(App.name + "\nSuperadmin");
        Ui.cols(accT, "Name", "name", "Username", "username", "Role", "role", "Status", "status");
        Ui.actions(accT, "Reset password", this::reset, "Disable/Enable", this::toggle);
        Ui.cols(reqT, "Name", "name", "Username", "username", "Role requested", "role", "Requested", "created_at");
        Ui.actions(reqT, "Approve", this::approve, "Reject", this::reject);
        show("Accounts");
    }
    @FXML void logout() { if (Ui.confirm("Are you sure you want to log out?")) App.logout(); }
    @FXML void navigate(ActionEvent e) { show(((ToggleButton) e.getSource()).getText().startsWith("Account requests") ? "Requests" : "Accounts"); }

    void show(String n) {
        Ui.vis(pAcc, n.equals("Accounts")); Ui.vis(pReq, n.equals("Requests"));
        if (n.equals("Accounts")) load(); else loadReq();
    }

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