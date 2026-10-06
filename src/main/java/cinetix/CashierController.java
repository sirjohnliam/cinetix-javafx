package cinetix;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Cashier: transactions only. Now showing -> Showtime -> Seats -> Review -> Receipt. */
public class CashierController {
    @FXML Label who, step1, step2, step3, dateLbl, tTitle, tMeta, tSynopsis, sTitle, sWhen, sCinema, sPrice, sSeats, sTotal;
    @FXML StackPane tPicBox;
    @FXML VBox pMovies, pTimes, pSeats;
    @FXML FlowPane posters, times;
    @FXML GridPane grid;
    Map<String, Object> movie, show;
    final Set<String> sel = new TreeSet<>(), taken = new HashSet<>();

    @FXML void initialize() {
        who.setText(App.name + ", Cashier");
        dateLbl.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)) + ". Pick a movie to sell tickets.");
        backMovies();
    }
    @FXML void logout() { if (Ui.confirm("Are you sure you want to log out?")) App.logout(); }

    void go(int n) {
        VBox[] p = {pMovies, pTimes, pSeats}; Label[] s = {step1, step2, step3}; String[] t = {"Now showing", "Showtime", "Seats"};
        for (int i = 0; i < 3; i++) {
            Ui.vis(p[i], i == n - 1);
            s[i].setText((i < n - 1 ? "✓" : String.valueOf(i + 1)) + "  " + t[i]);
            s[i].getStyleClass().remove("step-on"); if (i == n - 1) s[i].getStyleClass().add("step-on");
        }
    }
    double price() { return ((Number) show.get("price")).doubleValue(); }

    @FXML void backMovies() {
        Ui.safe(() -> {
            posters.getChildren().clear();
            for (var m : Db.query("SELECT m.*, COUNT(s.id) n FROM movies m JOIN schedules s ON s.movie_id=m.id AND s.show_date=CURDATE() GROUP BY m.id ORDER BY m.title")) {
                Node pic = Ui.moviePoster(m, 130, 190);
                Label t = Ui.lbl("" + m.get("title")); t.setStyle("-fx-font-weight:bold");
                Label meta = Ui.lbl(m.get("genre") + ", " + m.get("rating") + ", " + m.get("length") + " min\n" + m.get("n") + " showtimes today", "muted");
                VBox b = new VBox(4, pic, t, meta); b.setPrefWidth(130); b.setStyle("-fx-cursor:hand");
                b.setOnMouseClicked(e -> openMovie(m)); posters.getChildren().add(b);
            }
        });
        go(1);
    }

    void openMovie(Map<String, Object> m) {
        movie = m; show = null;
        tPicBox.getChildren().setAll(Ui.moviePoster(m, 150, 200));
        tTitle.setText("" + m.get("title"));
        tMeta.setText(m.get("genre") + ", " + m.get("rating") + ", " + m.get("length") + " min");
        Object syn = m.get("synopsis");
        tSynopsis.setText(syn == null || String.valueOf(syn).isBlank() ? "No synopsis yet." : String.valueOf(syn));
        Ui.safe(() -> {
            times.getChildren().clear(); ToggleGroup g = new ToggleGroup();
            for (var s : Db.query("SELECT s.*, " + Db.CAPACITY + "-(SELECT COUNT(*) FROM reservations r WHERE r.schedule_id=s.id) seats_left"
                    + " FROM schedules s WHERE s.movie_id=? AND s.show_date=CURDATE() ORDER BY s.show_time", m.get("id"))) {
                long left = ((Number) s.get("seats_left")).longValue();
                ToggleButton tb = new ToggleButton(Ui.t12(s.get("show_time")) + "\n" + s.get("cinema") + ", " + Ui.peso(s.get("price")) + "\n" + left + " seats left");
                tb.getStyleClass().add("time"); tb.setToggleGroup(g); tb.setDisable(left == 0);
                tb.setOnAction(e -> show = tb.isSelected() ? s : null); times.getChildren().add(tb);
            }
        });
        go(2);
    }
    @FXML void backTimes() { openMovie(movie); }

    @FXML void chooseSeats() {
        if (show == null) { Ui.info("Pick a showtime first."); return; }
        sel.clear(); taken.clear();
        Ui.safe(() -> { for (var r : Db.query("SELECT seat FROM reservations WHERE schedule_id=?", show.get("id"))) taken.add((String) r.get("seat")); });
        sTitle.setText("" + movie.get("title")); sWhen.setText(Ui.d(show.get("show_date")) + " " + Ui.t12(show.get("show_time")));
        sCinema.setText("Cinema:  " + show.get("cinema")); sPrice.setText("Standard seat:  " + Ui.peso(show.get("price")));
        draw(); go(3);
    }
    void draw() {
        Ui.fillSeats(grid, taken, sel, id -> { if (!sel.remove(id)) sel.add(id); draw(); });
        sSeats.setText(sel.isEmpty() ? "No seats selected yet." : "Seats: " + String.join(", ", sel));
        sTotal.setText("Total  " + Ui.peso(price() * sel.size()));
    }

    @FXML void review() {
        if (sel.isEmpty()) { Ui.info("Select at least one seat."); return; }
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
                if (e.getSQLState() != null && e.getSQLState().startsWith("23")) { Ui.info("One of those seats was just taken. Pick again."); chooseSeats(); }
                else throw e;
            }
        } catch (Exception e) { Ui.error(e.getMessage()); }
    }
}