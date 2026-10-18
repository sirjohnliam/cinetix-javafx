package cinetix;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Cashier: Now showing (+ upcoming, genre filter) -> Showtime and seats on one page -> Review -> Receipt. */
public class CashierController {
    @FXML Label who, step1, step2, step3, dateLbl, myTix, mySales, tTitle, tMeta, tSynopsis, seatHint, sTitle, sWhen, sCinema, sPrice, sSeats, sTotal;
    @FXML StackPane tPicBox;
    @FXML VBox pMovies, pBook;
    @FXML FlowPane genreBar, posters, upcoming, times;
    @FXML GridPane grid;
    @FXML Button reviewBtn;
    Map<String, Object> movie, show;
    List<Map<String, Object>> showing = new ArrayList<>(), soon = new ArrayList<>();
    String genreFilter;   // null = all genres
    ToggleGroup genreGroup = new ToggleGroup(), timeGroup = new ToggleGroup();
    final Set<String> sel = new TreeSet<>(), taken = new HashSet<>();

    @FXML void initialize() {
        who.setText(App.name + ", Cashier");
        dateLbl.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)) + ". Pick a movie to sell tickets.");
        backMovies();
    }
    @FXML void logout() { if (Ui.confirm("Are you sure you want to log out?")) App.logout(); }

    void go(int n) {
        Ui.vis(pMovies, n == 1); Ui.vis(pBook, n == 2);
        Label[] s = {step1, step2, step3}; String[] t = {"Now showing", "Showtime & seats", "Review & pay"};
        for (int i = 0; i < 3; i++) {
            s[i].setText((i < n - 1 ? "✓" : String.valueOf(i + 1)) + "  " + t[i]);
            s[i].getStyleClass().remove("step-on"); if (i == n - 1) s[i].getStyleClass().add("step-on");
        }
    }
    double price() { return ((Number) show.get("price")).doubleValue(); }
    static String nz(Object o) { return o == null ? "" : String.valueOf(o); }

    // ---- step 1: now showing, upcoming, genre filter ----
    @FXML void backMovies() {
        Ui.safe(() -> {
            showing = Db.query("SELECT m.*, COUNT(s.id) n FROM movies m JOIN schedules s ON s.movie_id=m.id AND s.show_date=CURDATE() AND s.archived=0"
                    + " WHERE m.archived=0 GROUP BY m.id ORDER BY m.title");
            soon = Db.query("SELECT m.*, MIN(s.show_date) opens FROM movies m JOIN schedules s ON s.movie_id=m.id AND s.show_date>CURDATE() AND s.archived=0"
                    + " WHERE m.archived=0 AND NOT EXISTS (SELECT 1 FROM schedules t WHERE t.movie_id=m.id AND t.show_date=CURDATE() AND t.archived=0)"
                    + " GROUP BY m.id ORDER BY opens, m.title");
            var mine = Db.query("SELECT COUNT(*) t, COALESCE(SUM(price),0) s FROM reservations WHERE sold_by=? AND DATE(created_at)=CURDATE()", App.uid).get(0);
            myTix.setText("" + mine.get("t")); mySales.setText(Ui.peso(mine.get("s")));
            buildGenreBar(); renderMovies();
        });
        go(1);
    }

    void buildGenreBar() {
        TreeSet<String> gs = new TreeSet<>();
        for (var m : showing) if (m.get("genre") != null) gs.add(String.valueOf(m.get("genre")));
        for (var m : soon) if (m.get("genre") != null) gs.add(String.valueOf(m.get("genre")));
        if (genreFilter != null && !gs.contains(genreFilter)) genreFilter = null;
        genreBar.getChildren().clear(); genreGroup = new ToggleGroup();
        List<String> all = new ArrayList<>(); all.add("All"); all.addAll(gs);
        for (String g : all) {
            ToggleButton b = new ToggleButton(g); b.getStyleClass().add("pill"); b.setToggleGroup(genreGroup);
            b.setSelected(g.equals("All") ? genreFilter == null : g.equals(genreFilter));
            b.setOnAction(e -> {
                if (!b.isSelected()) b.setSelected(true);
                genreFilter = g.equals("All") ? null : g; renderMovies();
            });
            genreBar.getChildren().add(b);
        }
    }

    boolean matches(Map<String, Object> m) { return genreFilter == null || genreFilter.equals(String.valueOf(m.get("genre"))); }

    void renderMovies() {
        posters.getChildren().clear(); upcoming.getChildren().clear();
        for (var m : showing) if (matches(m)) posters.getChildren().add(card(m, false));
        for (var m : soon) if (matches(m)) upcoming.getChildren().add(card(m, true));
        if (posters.getChildren().isEmpty())
            posters.getChildren().add(Ui.lbl(genreFilter == null ? "No movies are showing today." : "No " + genreFilter + " movies are showing today.", "muted"));
        if (upcoming.getChildren().isEmpty())
            upcoming.getChildren().add(Ui.lbl(genreFilter == null ? "Nothing coming up yet." : "No upcoming " + genreFilter + " movies yet.", "muted"));
    }

    /** One poster card. Upcoming cards are for viewing only. */
    VBox card(Map<String, Object> m, boolean isSoon) {
        Label t = Ui.lbl("" + m.get("title")); t.setStyle("-fx-font-weight:bold"); t.setWrapText(true); t.setMaxWidth(150);
        Label meta = Ui.lbl(nz(m.get("rating")) + ", " + nz(m.get("length")) + " min", "muted");
        VBox b = new VBox(6, Ui.moviePoster(m, 150, 220), t);
        if (m.get("genre") != null) b.getChildren().add(new HBox(Ui.genreTag(String.valueOf(m.get("genre")))));
        b.getChildren().add(meta);
        if (isSoon) b.getChildren().add(new HBox(Ui.lbl("Opens " + Ui.d(m.get("opens")), "badge")));
        else {
            int n = ((Number) m.get("n")).intValue();
            b.getChildren().add(Ui.lbl(n + (n == 1 ? " showtime today" : " showtimes today"), "muted"));
        }
        b.getStyleClass().add("poster-card"); b.setPrefWidth(170);
        if (isSoon) b.getStyleClass().add("poster-soon"); else b.setOnMouseClicked(e -> openMovie(m));
        return b;
    }

    // ---- step 2: showtime and seats together ----
    void openMovie(Map<String, Object> m) {
        movie = m; show = null;
        tPicBox.getChildren().setAll(Ui.moviePoster(m, 130, 190));
        tTitle.setText("" + m.get("title"));
        tMeta.setText(nz(m.get("genre")) + "  ·  " + nz(m.get("rating")) + "  ·  " + nz(m.get("length")) + " min");
        Object syn = m.get("synopsis");
        tSynopsis.setText(syn == null || String.valueOf(syn).isBlank() ? "No synopsis yet." : String.valueOf(syn));
        go(2);
        Ui.safe(() -> {
            times.getChildren().clear(); timeGroup = new ToggleGroup(); ToggleButton first = null;
            for (var s : Db.query("SELECT s.*, " + Db.CAPACITY + "-(SELECT COUNT(*) FROM reservations r WHERE r.schedule_id=s.id) seats_left"
                    + " FROM schedules s WHERE s.movie_id=? AND s.show_date=CURDATE() AND s.archived=0 ORDER BY s.show_time", m.get("id"))) {
                long left = ((Number) s.get("seats_left")).longValue();
                ToggleButton tb = new ToggleButton(Ui.t12(s.get("show_time")) + "\n" + s.get("cinema") + ", " + Ui.peso(s.get("price")) + "\n" + left + " seats left");
                tb.getStyleClass().add("time"); tb.setToggleGroup(timeGroup); tb.setDisable(left == 0);
                tb.setOnAction(e -> { if (!tb.isSelected()) tb.setSelected(true); show = s; loadSeats(); });
                times.getChildren().add(tb);
                if (first == null && left > 0) first = tb;
            }
            if (first != null) first.fire();   // the first showtime with seats is picked for you, so the seat map is never empty
            else loadSeats();
        });
    }

    void loadSeats() {
        sel.clear(); taken.clear();
        boolean has = show != null;
        Ui.vis(seatHint, !has);
        seatHint.setText(has ? "" : "No seats to sell: every showtime today is sold out.");
        sTitle.setText("" + movie.get("title"));
        if (has) {
            Ui.safe(() -> { for (var r : Db.query("SELECT seat FROM reservations WHERE schedule_id=?", show.get("id"))) taken.add((String) r.get("seat")); });
            sWhen.setText(Ui.d(show.get("show_date")) + "   " + Ui.t12(show.get("show_time")));
            sCinema.setText("Cinema:  " + show.get("cinema")); sPrice.setText("Standard seat:  " + Ui.peso(show.get("price")));
        } else { sWhen.setText(""); sCinema.setText(""); sPrice.setText(""); }
        draw();
    }
    void draw() {
        if (show == null) {
            grid.getChildren().clear(); sSeats.setText("No seats selected yet."); sTotal.setText("Total  " + Ui.peso(0)); reviewBtn.setDisable(true); return;
        }
        Ui.fillSeats(grid, taken, sel, id -> { if (!sel.remove(id)) sel.add(id); draw(); });
        sSeats.setText(sel.isEmpty() ? "No seats selected yet." : "Seats: " + String.join(", ", sel));
        sTotal.setText("Total  " + Ui.peso(price() * sel.size()));
        reviewBtn.setDisable(sel.isEmpty());
    }

    // ---- review, pay, receipt ----
    @FXML void review() {
        if (show == null || sel.isEmpty()) { Ui.info("Select at least one seat."); return; }
        Ui.Tk tk = new Ui.Tk("" + movie.get("title"), "" + show.get("show_date"), "" + show.get("show_time"), "" + show.get("cinema"), String.join(", ", sel), price() * sel.size());
        ButtonType back = new ButtonType("Back to seats", ButtonBar.ButtonData.CANCEL_CLOSE), ok = new ButtonType("Confirm Payment", ButtonBar.ButtonData.OK_DONE);
        Alert a = new Alert(Alert.AlertType.NONE, "", back, ok); a.setTitle("Review order"); a.setHeaderText("Review order. Check the details, then confirm.");
        a.getDialogPane().setContent(Ui.ticket(tk, "Seat not taken yet.")); Ui.style(a.getDialogPane());
        if (a.showAndWait().filter(b -> b == ok).isPresent()) confirm(tk);
    }

    /** All seats are saved together or not at all; the UNIQUE(schedule_id, seat) key blocks double booking. */
    void confirm(Ui.Tk tk) {
        try (Connection c = Db.conn()) {
            c.setAutoCommit(false);
            try {
                String code = Db.newCode();
                try (PreparedStatement p = c.prepareStatement("INSERT INTO reservations(code,schedule_id,seat,price,sold_by) VALUES(?,?,?,?,?)")) {
                    for (String seat : sel) { p.setString(1, code); p.setObject(2, show.get("id")); p.setString(3, seat); p.setDouble(4, price()); p.setInt(5, App.uid); p.executeUpdate(); }
                }
                c.commit(); Ui.receipt(tk, code); backMovies();
            } catch (SQLException e) {
                c.rollback();
                if (e.getSQLState() != null && e.getSQLState().startsWith("23")) { Ui.info("One of those seats was just taken. Pick again."); loadSeats(); }
                else throw e;
            }
        } catch (Exception e) { Ui.error(e.getMessage()); }
    }
}