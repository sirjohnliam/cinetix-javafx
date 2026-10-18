package cinetix;

import java.util.*;
import java.util.function.Function;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController {
    @FXML TextField user, passText;
    @FXML PasswordField pass;
    @FXML Button eye;
    @FXML Label error;

    @FXML void initialize() {
        passText.textProperty().bindBidirectional(pass.textProperty());   // one password, two views (hidden / shown)
        Ui.limit(user, 20); Ui.limit(pass, 32); Ui.limit(passText, 32);
    }

    @FXML void togglePw() {
        boolean show = !passText.isVisible();
        Ui.vis(passText, show); Ui.vis(pass, !show);
        eye.setText(show ? "🔒" : "👁"); // Switches between visible and hidden icon indicators
        (show ? passText : pass).requestFocus();
    }

    /** The role comes from the account itself, so there is nothing to pick on this screen. */
    @FXML void login() {
        try {
            var rows = Db.query("SELECT * FROM users WHERE username=?", user.getText().trim());
            if (rows.isEmpty() || !Db.check(pass.getText(), (String) rows.get(0).get("password_hash"))) { error.setText("Wrong username or password."); return; }
            var u = rows.get(0);
            if (isArchived(u)) { error.setText("This account is archived. Ask a superadmin to restore it."); return; }
            String role = (String) u.get("role");
            App.uid = ((Number) u.get("id")).intValue(); App.name = (String) u.get("name"); App.role = role;
            Ui.info("Welcome, " + App.name + "! You're logged in as " + cap(role) + ".");
            App.show(role.equals("cashier") ? "cashier" : "admin");   // admin and superadmin share admin.fxml
        } catch (Exception e) { error.setText(e.getMessage()); }
    }

    static boolean isArchived(Map<String, Object> u) {
        return ((Number) u.get("archived")).intValue() == 1 || ((Number) u.get("active")).intValue() == 0;
    }

    /** Username -> security question -> new password. */
    @FXML void forgot() {
        Ui.form("Forgot password", new Ui.F("username", "Your username", user.getText().trim())).ifPresent(a -> Ui.safe(() -> {
            var rows = Db.query("SELECT * FROM users WHERE username=?", a.get("username"));
            if (rows.isEmpty()) throw new Exception("No account has that username.");
            var u = rows.get(0);
            if (isArchived(u)) throw new Exception("This account is archived. Ask a superadmin to restore it.");
            String question = (String) u.get("sec_question"), stored = (String) u.get("sec_answer_hash");
            if (question == null || question.isBlank() || stored == null || stored.isBlank())
                throw new Exception("This account has no security question yet. Ask a superadmin to reset your password.");

            Map<String, Function<String, String>> live = new HashMap<>();
            live.put("password", t -> t.isEmpty() ? null : (Db.passwordProblem(t) == null ? "✓ Password meets the rules." : Db.passwordProblem(t)));
            Ui.form("Reset password", m -> {
                                if (!Db.checkAnswer(m.get("answer"), stored)) return "That answer is not correct.";
                                String p = Db.passwordProblem(m.get("password")); if (p != null) return p;
                                return m.get("password").equals(m.get("password2")) ? null : "The two passwords don't match.";
                            }, live,
                            new Ui.F("answer", question, null), new Ui.F("password", "New password", null), new Ui.F("password2", "Confirm new password", null))
                    .ifPresent(r -> Ui.safe(() -> {
                        Db.update("UPDATE users SET password_hash=? WHERE id=?", Db.hash(r.get("password")), u.get("id"));
                        Ui.info("Password updated. You can log in now.");
                    }));
        }));
    }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}