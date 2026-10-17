package cinetix;

import cinetix.Ui.Act;
import cinetix.Ui.F;
import java.util.*;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.scene.control.TableView;

/** Account management (superadmin only): add, edit, reset password, archive, restore. */
class Accounts {
    final TableView<Map<String, Object>> accT, arcT;

    Accounts(TableView<Map<String, Object>> accT, TableView<Map<String, Object>> arcT) {
        this.accT = accT; this.arcT = arcT;
        Ui.cols(accT, "Name", "name", "Username", "username", "Role", "role");
        Ui.actionList(accT, new Act("Edit", this::edit), new Act("Reset password", this::reset), new Act("Archive", this::archive));
        Ui.cols(arcT, "Name", "name", "Username", "username", "Role", "role");
        Ui.actionList(arcT, new Act("Restore", this::restore));
    }

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

    void add() {
        Map<String, Function<String, String>> live = new HashMap<>();
        live.put("username", t -> liveUser(t, null));
        live.put("password", Accounts::livePassword);
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
        live.put("password", Accounts::livePassword);
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