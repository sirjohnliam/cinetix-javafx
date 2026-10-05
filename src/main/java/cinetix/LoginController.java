package cinetix;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController {
    @FXML ToggleGroup roles;
    @FXML TextField user;
    @FXML PasswordField pass;
    @FXML Label error;

    @FXML void login() {
        try {
            var rows = Db.query("SELECT * FROM users WHERE username=?", user.getText().trim());
            if (rows.isEmpty() || !Db.check(pass.getText(), (String) rows.get(0).get("password_hash"))) { error.setText("Wrong username or password."); return; }
            var u = rows.get(0);
            if (((Number) u.get("active")).intValue() == 0) { error.setText("This account is disabled."); return; }
            Toggle sel = roles.getSelectedToggle();
            String want = sel == null ? "" : ((ToggleButton) sel).getText().toLowerCase();
            if (!want.equals(u.get("role"))) { error.setText("This account is not a " + (want.isEmpty() ? "selected role" : want) + "."); return; }
            App.uid = ((Number) u.get("id")).intValue(); App.name = (String) u.get("name"); App.role = want;
            Ui.info("Welcome, " + App.name + "! You're logged in as " + cap(want) + ".");
            App.show(want.equals("superadmin") ? "super" : want);   // cashier.fxml / admin.fxml / super.fxml
        } catch (Exception e) { error.setText(e.getMessage()); }
    }

    /** Doesn't create an account. It files a request that a superadmin must approve under Account requests. */
    @FXML void signup() {
        Toggle sel = roles.getSelectedToggle();
        String want = sel == null ? "cashier" : ((ToggleButton) sel).getText().toLowerCase();
        if (want.equals("superadmin")) { error.setText("Superadmin accounts can't be requested here. Ask an existing superadmin to create one."); return; }
        Ui.form("Request an account", new Ui.F("name", "Full name", null), new Ui.F("username", "Username", null),
                        new Ui.F("role", "Role you're requesting", want, "cashier", "admin"), new Ui.F("password", "Password", null))
                .ifPresent(d -> Ui.safe(() -> {
                    if (d.get("name").isEmpty() || d.get("username").isEmpty()) throw new Exception("Enter a name and username.");
                    if (d.get("password").length() < 6) throw new Exception("Password needs 6 or more characters.");
                    if (Db.one("SELECT 1 FROM users WHERE username=?", d.get("username")) != null) throw new Exception("That username is already taken.");
                    if (Db.one("SELECT 1 FROM account_requests WHERE username=? AND status='pending'", d.get("username")) != null)
                        throw new Exception("A request for that username is already waiting for approval.");
                    Db.update("INSERT INTO account_requests(name,username,password_hash,role) VALUES(?,?,?,?)",
                            d.get("name"), d.get("username"), Db.hash(d.get("password")), d.get("role"));
                    Ui.info("Your request was sent. A superadmin needs to approve it before you can log in.");
                }));
    }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}