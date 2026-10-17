package cinetix;

import java.io.File;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.animation.ScaleTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.*;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/** Small helpers shared by all screens. */
public class Ui {
    interface Task { void run() throws Exception; }
    record F(String k, String label, String v, String... opts) {}                      // form field
    record Tk(String title, String date, String time, String cinema, String seats, double total) {}

    static void safe(Task t) {
        try { t.run(); }
        catch (NumberFormatException e) { error("Enter a valid number."); }
        catch (DateTimeParseException e) { error("Use yyyy-mm-dd for dates and HH:mm for times, for example 13:30."); }
        catch (SQLException e) { error("23000".equals(e.getSQLState()) ? "That conflicts with existing data: it is a duplicate or still in use." : e.getMessage()); }
        catch (Exception e) { error(e.getMessage()); }
    }
    static String css() { return Ui.class.getResource("/cinetix/style.css").toExternalForm(); }
    static void style(DialogPane p) { p.getStylesheets().add(css()); }
    static void alert(Alert.AlertType t, String m) { Alert a = new Alert(t, m); a.setHeaderText(null); style(a.getDialogPane()); a.showAndWait(); }
    static void info(String m) { alert(Alert.AlertType.INFORMATION, m); }
    static void error(String m) { alert(Alert.AlertType.ERROR, m == null ? "Something went wrong." : m); }
    /** Yes/No confirmation used for logout and for anything that deletes or approves data. */
    static boolean confirm(String m) {
        ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES), no = new ButtonType("No", ButtonBar.ButtonData.NO);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, m, yes, no); a.setHeaderText(null); style(a.getDialogPane());
        return a.showAndWait().filter(b -> b == yes).isPresent();
    }
    static void vis(Region r, boolean b) { r.setVisible(b); r.setManaged(b); }
    static String peso(Object n) { return "₱" + new DecimalFormat("#,##0").format(((Number) n).doubleValue()); }
    static String t12(Object t) { return LocalTime.parse(t.toString()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)); }
    static String d(Object d) { return LocalDate.parse(d.toString()).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)); }
    static String s(Map<String, Object> m, String k) { if (m == null) return null; Object v = m.get(k); return v == null ? null : String.valueOf(v); }
    static Label lbl(String t, String... cls) { Label l = new Label(t); l.getStyleClass().addAll(cls); return l; }

    /** Maximum characters per form field, enforced while typing and shown as a live counter. */
    static final Map<String, Integer> LIMITS = Map.ofEntries(
            Map.entry("name", 50), Map.entry("username", 20), Map.entry("password", 32), Map.entry("password2", 32),
            Map.entry("answer", 50), Map.entry("title", 100), Map.entry("genre", 50), Map.entry("length", 3),
            Map.entry("synopsis", 300), Map.entry("cinema", 30), Map.entry("price", 7), Map.entry("date", 10), Map.entry("time", 5));
    /** Stops typing and pasting past max characters (text already longer than max can still be shortened). */
    static void limit(TextInputControl c, int max) {
        c.setTextFormatter(new TextFormatter<String>(ch -> ch.getControlNewText().length() <= max
                || ch.getControlNewText().length() < ch.getControlText().length() ? ch : null));
    }
    record Act(String name, Consumer<Map<String, Object>> fn) {}

    /** A short lift on hover for every button/toggle button already on screen. Called once per screen after it loads. */
    static void animateHover(Parent root) {
        for (Node n : root.lookupAll(".button, .toggle-button")) {
            n.setOnMouseEntered(e -> scale(n, 1.045));
            n.setOnMouseExited(e -> scale(n, 1.0));
        }
    }
    static void scale(Node n, double to) {
        ScaleTransition t = new ScaleTransition(Duration.millis(120), n); t.setToX(to); t.setToY(to); t.play();
    }

    /** The movie's poster image if one was chosen and the file still exists, otherwise a plain colour block.
     * A small thumbnail (as in a table row) shows the movie's initials; a bigger poster shows its full title. */
    static Node moviePoster(Map<String, Object> m, double w, double h) {
        Object p = m == null ? null : m.get("poster_path");
        if (p != null && !String.valueOf(p).isBlank()) {
            try {
                Image img = new Image(new File(String.valueOf(p)).toURI().toString(), w * 2, h * 2, false, true);
                if (!img.isError()) {
                    ImageView iv = new ImageView(img); iv.setFitWidth(w); iv.setFitHeight(h); iv.setPreserveRatio(false);
                    Rectangle clip = new Rectangle(w, h); clip.setArcWidth(8); clip.setArcHeight(8); iv.setClip(clip);
                    return iv;
                }
            } catch (Exception ignored) { /* fall through to the placeholder below */ }
        }
        String title = m == null ? "" : String.valueOf(m.get("title"));
        boolean tiny = w < 80;
        Label l = lbl(tiny ? initials(title) : title, "pic");
        l.setPrefSize(w, h); l.setMinSize(w, h); l.setMaxSize(w, h);
        if (tiny) l.setStyle("-fx-font-size:" + Math.max(10, (int) (Math.min(w, h) * 0.45)) + "px;");
        return l;
    }
    /** Up to two capital letters for a thumbnail placeholder, e.g. "Resident Evil" -> "RE". */
    static String initials(String title) {
        if (title == null || title.isBlank()) return "?";
        StringBuilder sb = new StringBuilder();
        for (String w : title.trim().split("\\s+")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }
    /** A small poster thumbnail as the first column of a movies table. */
    static void posterCol(TableView<Map<String, Object>> t) { posterCol(t, 34, 48); }
    /** Poster column with a custom poster size (w x h). */
    static void posterCol(TableView<Map<String, Object>> t, double w, double h) {
        TableColumn<Map<String, Object>, Void> c = new TableColumn<>("");
        c.setMinWidth(w + 24); c.setPrefWidth(w + 24); c.setMaxWidth(w + 24); c.setSortable(false);
        c.setCellFactory(x -> new TableCell<>() {
            { setAlignment(Pos.CENTER); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : moviePoster(getTableView().getItems().get(getIndex()), w, h));
            }
        });
        t.getColumns().add(0, c);
    }

    /** Columns as (title, key) pairs. Key prefixes: $ = peso, @ = 12-hour time, % = percent. */
    static void cols(TableView<Map<String, Object>> t, String... p) {
        t.getColumns().clear();
        for (int i = 0; i < p.length; i += 2) {
            char f = p[i + 1].charAt(0); String k = "$@%".indexOf(f) >= 0 ? p[i + 1].substring(1) : p[i + 1];
            TableColumn<Map<String, Object>, String> c = new TableColumn<>(p[i]);
            c.setCellValueFactory(cd -> {
                Object v = cd.getValue().get(k);
                String s = v == null ? "" : switch (f) { case '$' -> peso(v); case '@' -> t12(v); case '%' -> v + "%"; default -> String.valueOf(v); };
                return new SimpleStringProperty(s);
            });
            t.getColumns().add(c);
        }
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("Nothing here yet."));
        t.setFixedCellSize(52);   // every row the same height, so a poster thumbnail can't push a row out of line with its buttons
    }
    static void actions(TableView<Map<String, Object>> t, String n1, Consumer<Map<String, Object>> f1, String n2, Consumer<Map<String, Object>> f2) {
        actionList(t, new Act(n1, f1), new Act(n2, f2));
    }
    /** A column of row buttons. Edit = blue, Archive/Delete = orange, Restore/Approve = green, others stay plain. */
    static void actionList(TableView<Map<String, Object>> t, Act... acts) {
        TableColumn<Map<String, Object>, Void> c = new TableColumn<>(""); c.setMinWidth(Math.max(240, 125 * acts.length));
        c.setCellFactory(x -> new TableCell<>() {
            final HBox box = new HBox(6);
            { setAlignment(Pos.CENTER_LEFT); box.setAlignment(Pos.CENTER_LEFT); for (Act a : acts) box.getChildren().add(btn(a)); }
            private Button btn(Act a) {
                Button z = new Button(a.name()); z.getStyleClass().add("sm");
                String n = a.name().toLowerCase();
                if (n.startsWith("edit")) z.getStyleClass().add("edit");
                else if (n.startsWith("archive") || n.startsWith("delete")) z.getStyleClass().add("warn");
                else if (n.startsWith("restore") || n.startsWith("approve")) z.getStyleClass().add("ok");
                z.setOnAction(e -> a.fn().accept(getTableView().getItems().get(getIndex()))); return z;
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : box); }
        });
        t.getColumns().add(c);
    }

    /** Reads whatever control a field produced, whatever its type. */
    static String readValue(Node n) {
        if (n instanceof ComboBox<?> cb) return String.valueOf(cb.getValue());
        if (n instanceof PasswordField pf) return pf.getText();   // passwords are not trimmed
        if (n instanceof TextArea ta) return ta.getText().trim();
        if (n instanceof HBox box && !box.getChildren().isEmpty() && box.getChildren().get(0) instanceof TextField tf) return tf.getText().trim();
        if (n instanceof TextInputControl ti) return ti.getText().trim();
        return "";
    }

    /** A pop-up form. Field key "synopsis" gets a multi-line box; key "poster" gets a file picker.
     * Text boxes stop at their LIMITS length and show a live counter. check (optional) runs when OK is pressed and returns
     * an error message (the dialog stays open) or null. live (optional) maps a field key to a hint shown while typing;
     * a hint starting with a tick is shown green, any other hint red. */
    static Optional<Map<String, String>> form(String title, F... fs) { return formImpl(title, null, null, fs); }
    static Optional<Map<String, String>> form(String title, Function<Map<String, String>, String> check, F... fs) { return formImpl(title, check, null, fs); }
    static Optional<Map<String, String>> form(String title, Function<Map<String, String>, String> check,
                                              Map<String, Function<String, String>> live, F... fs) { return formImpl(title, check, live, fs); }

    private static Optional<Map<String, String>> formImpl(String title, Function<Map<String, String>, String> check,
                                                          Map<String, Function<String, String>> live, F... fs) {
        Dialog<Map<String, String>> d = new Dialog<>(); d.setTitle(title); d.setHeaderText(title);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(10));
        Map<String, Node> ctl = new LinkedHashMap<>(); int i = 0;
        for (F f : fs) {
            Node c;
            if (f.k().equals("synopsis")) {
                TextArea ta = new TextArea(f.v() == null ? "" : f.v()); ta.setWrapText(true); ta.setPrefRowCount(4); ta.setPrefWidth(280); c = ta;
            } else if (f.k().equals("poster")) {
                TextField path = new TextField(f.v() == null ? "" : f.v()); path.setEditable(false); path.setPrefWidth(190); path.setPromptText("No image chosen.");
                Button browse = new Button("Browse…"); browse.getStyleClass().add("sm");
                browse.setOnAction(e -> {
                    FileChooser fc = new FileChooser(); fc.setTitle("Choose a poster image");
                    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
                    File picked = fc.showOpenDialog(App.stage);
                    if (picked != null) path.setText(picked.getAbsolutePath());
                });
                HBox box = new HBox(6, path, browse); c = box;
            } else if (f.opts().length > 0) {
                ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(f.opts())); cb.setValue(f.v() == null ? f.opts()[0] : f.v()); c = cb;
            } else {
                TextField tf = f.k().startsWith("password") ? new PasswordField() : new TextField(); tf.setText(f.v() == null ? "" : f.v()); tf.setPrefWidth(260); c = tf;
            }
            Node cell = c;
            if (c instanceof TextInputControl ti) {
                Integer max = LIMITS.get(f.k()); if (max != null) limit(ti, max);
                Function<String, String> lv = live == null ? null : live.get(f.k());
                Label hint = lbl("", "muted"); hint.setWrapText(true); hint.setMaxWidth(280);
                Runnable upd = () -> {
                    String txt = ti.getText() == null ? "" : ti.getText();
                    String msg = lv == null ? null : lv.apply(ti instanceof PasswordField ? txt : txt.trim());
                    hint.getStyleClass().removeAll("error", "ok-text");
                    if (msg != null) { hint.setText(msg); hint.getStyleClass().add(msg.startsWith("✓") ? "ok-text" : "error"); }
                    else hint.setText(max == null ? "" : txt.length() + "/" + max);
                };
                ti.textProperty().addListener((o, x, y) -> upd.run()); upd.run();
                cell = new VBox(2, c, hint);
            }
            g.addRow(i++, new Label(f.label()), cell); ctl.put(f.k(), c);
        }
        Label err = lbl("", "error"); err.setWrapText(true); err.setMaxWidth(430);
        d.getDialogPane().setContent(new VBox(8, g, err)); style(d.getDialogPane());
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Supplier<Map<String, String>> read = () -> {
            Map<String, String> m = new HashMap<>();
            ctl.forEach((k, c) -> m.put(k, readValue(c)));
            return m;
        };
        if (check != null) ((Button) d.getDialogPane().lookupButton(ButtonType.OK)).addEventFilter(ActionEvent.ACTION, ev -> {
            String msg = check.apply(read.get());
            if (msg != null) { err.setText(msg); ev.consume(); }
        });
        d.setResultConverter(b -> b == ButtonType.OK ? read.get() : null);
        return d.showAndWait();
    }

    /** Draws the seat map (A-F x 1-12). click == null makes it read-only. */
    static void fillSeats(GridPane g, Set<String> taken, Set<String> sel, Consumer<String> click) {
        g.getChildren().clear(); g.setHgap(6); g.setVgap(8); g.setAlignment(Pos.CENTER);
        Region gap = new Region(); gap.setMinWidth(40); g.add(gap, 7, 0);
        for (int r = 0; r < 6; r++) {
            char row = (char) ('A' + r);
            g.add(lbl("" + row, "muted"), 0, r); g.add(lbl("" + row, "muted"), 14, r);
            for (int n = 1; n <= 12; n++) {
                String id = "" + row + n; Button b = new Button("" + n); b.getStyleClass().add("seat");
                if (taken.contains(id)) { b.getStyleClass().add("taken"); b.setMouseTransparent(true); }
                else if (sel.contains(id)) b.getStyleClass().add("sel");
                if (click == null) b.setMouseTransparent(true); else b.setOnAction(e -> click.accept(id));
                g.add(b, n <= 6 ? n : n + 1, r);
            }
        }
    }

    static HBox ticket(Tk k, String code) {
        HBox bars = new HBox(2); Random r = new Random(code.hashCode());
        for (int i = 0; i < 26; i++) bars.getChildren().add(new Rectangle(1 + r.nextInt(4), 60, Color.WHITE));
        Label det = lbl(d(k.date()) + "   " + t12(k.time()) + "\n" + k.cinema() + "   Seats: " + k.seats()); det.setWrapText(true); det.setMaxWidth(320);
        VBox left = new VBox(6, lbl("CineTix", "gold"), lbl(k.title(), "h1"), det, lbl("Total  " + peso(k.total()), "gold", "h2"));
        VBox right = new VBox(6, bars, lbl(code, "muted")); right.setAlignment(Pos.CENTER);
        HBox t = new HBox(24, left, right); t.getStyleClass().add("ticket"); return t;
    }
    static void receipt(Tk k, String code) {
        HBox t = ticket(k, code); Button pr = new Button("Print"); pr.getStyleClass().add("sm");
        pr.setOnAction(e -> { PrinterJob j = PrinterJob.createPrinterJob(); if (j != null && j.showPrintDialog(App.stage) && j.printPage(t)) j.endJob(); });
        Dialog<Void> dl = new Dialog<>(); dl.setTitle("Order receipt"); dl.setHeaderText("Order receipt. Give this ticket to the customer.");
        dl.getDialogPane().setContent(new VBox(12, t, pr)); style(dl.getDialogPane());
        dl.getDialogPane().getButtonTypes().add(new ButtonType("Done", ButtonBar.ButtonData.OK_DONE)); dl.showAndWait();
    }
}