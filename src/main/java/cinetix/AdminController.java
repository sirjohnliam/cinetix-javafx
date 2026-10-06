package cinetix;

import cinetix.Ui.F;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Admin: manages the system (dashboard, movies, schedules, sales report, read-only seat map). */
public class AdminController {
    @FXML ToggleGroup nav, tabs;
    @FXML Label who, role, dateLbl, stMovies, stShows, stTix, stSales, movieSub, sTotal, sTix, sTx;
    @FXML VBox pDash, pMovies, pSched, pRes, pSales, seatView;
    @FXML HBox dayBar;
    @FXML DatePicker fromDp, toDp;
    @FXML TableView<Map<String, Object>> dashT, salesMovieT, movieT, schedT, resT, sMovieT, sDayT, sCashT, sTxT;
    @FXML ComboBox<String> showCb;
    @FXML GridPane grid;
    String day = LocalDate.now().toString();
    List<Map<String, Object>> movies = new ArrayList<>();

    @FXML void initialize() {
        who.setText(App.name); role.setText("Admin");
        dateLbl.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)));

        // dashboard tables
        Ui.cols(dashT, "Time", "@show_time", "Movie", "title", "Cinema", "cinema");
        dashT.getColumns().add(filledCol());
        Ui.cols(salesMovieT, "Movie", "title", "Tickets sold", "tickets", "Sales", "$total");

        // sales report tables
        Ui.cols(sMovieT, "Movie", "title", "Tickets sold", "tickets", "Sales", "$total");
        Ui.cols(sDayT, "Date", "day_lbl", "Tickets sold", "tickets", "Sales", "$total");
        Ui.cols(sCashT, "Cashier", "cashier", "Transactions", "transactions", "Tickets", "tickets", "Sales", "$total");
        Ui.cols(sTxT, "Code", "code", "Movie", "title", "Showtime", "showtime", "Seats", "seats", "Total", "$total", "Sold by", "sold_by");
        fromDp.setValue(LocalDate.now()); toDp.setValue(LocalDate.now());

        for (var t : List.of(dashT, salesMovieT, sMovieT, sDayT, sCashT, sTxT)) {
            t.setFixedCellSize(54); t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        }

        // other tables
        Ui.cols(movieT, "Movie", "title", "Genre", "genre", "Rating", "rating", "Length (min)", "length");
        Ui.posterCol(movieT, 90, 130);
        movieT.setFixedCellSize(150);
        Ui.cols(schedT, "Time", "@show_time", "Movie", "title", "Cinema", "cinema", "Price", "$price", "Seats left", "seats_left");
        Ui.cols(resT, "Code", "code", "Movie", "title", "Showtime", "showtime", "Seat", "seats", "Total", "$total", "Sold by", "sold_by");
        Ui.actions(movieT, "Edit", this::movieForm, "Delete", m -> {
            if (Ui.confirm("Delete " + m.get("title") + "?")) Ui.safe(() -> { Db.update("DELETE FROM movies WHERE id=?", m.get("id")); loadMovies(); });
        });
        Ui.actions(schedT, "Edit", this::schedForm, "Delete", x -> {
            if (Ui.confirm("Delete this showtime?")) Ui.safe(() -> { Db.update("DELETE FROM schedules WHERE id=?", x.get("id")); loadSched(); });
        });
        show("Dashboard");
    }
    @FXML void logout() { if (Ui.confirm("Are you sure you want to log out?")) App.logout(); }
    @FXML void navigate(ActionEvent e) { show(((ToggleButton) e.getSource()).getText()); }

    void show(String n) {
        nav.getToggles().forEach(t -> t.setSelected(((ToggleButton) t).getText().equals(n)));
        Ui.vis(pDash, n.equals("Dashboard")); Ui.vis(pMovies, n.equals("Movies")); Ui.vis(pSched, n.equals("Schedules"));
        Ui.vis(pSales, n.equals("Sales report")); Ui.vis(pRes, n.equals("Seat map"));
        switch (n) { case "Dashboard" -> loadDash(); case "Movies" -> loadMovies(); case "Schedules" -> loadSched(); case "Sales report" -> loadSales(); default -> loadRes(); }
    }

    // ---- helpers ----
    /** Fills a table and sizes it to its rows (no empty striped rows). */
    void rows(TableView<Map<String, Object>> t, List<Map<String, Object>> data) {
        t.setItems(FXCollections.observableArrayList(data));
        double h = Math.max(1, data.size()) * 54 + 42;
        t.setPrefHeight(h); t.setMinHeight(h); t.setMaxHeight(h);
    }

    /** "Seats filled" column with a progress bar + percent. */
    TableColumn<Map<String, Object>, Object> filledCol() {
        TableColumn<Map<String, Object>, Object> c = new TableColumn<>("Seats filled");
        c.setCellValueFactory(x -> new SimpleObjectProperty<>(x.getValue().get("filled")));
        c.setCellFactory(col -> new TableCell<>() {
            final ProgressBar pb = new ProgressBar(); final Label l = new Label(); final HBox box = new HBox(12, pb, l);
            { pb.getStyleClass().add("dash-bar"); pb.setPrefWidth(2000); pb.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(pb, Priority.ALWAYS); l.setMinWidth(36); box.setAlignment(Pos.CENTER_LEFT); }
            @Override protected void updateItem(Object v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                double p = ((Number) v).doubleValue(); pb.setProgress(p / 100); l.setText((int) p + "%"); setGraphic(box);
            }
        });
        c.setSortable(false);
        return c;
    }

    // ---- dashboard ----
    void loadDash() {
        Ui.safe(() -> {
            stMovies.setText("" + Db.one("SELECT COUNT(DISTINCT movie_id) FROM schedules WHERE show_date=CURDATE()"));
            stShows.setText("" + Db.one("SELECT COUNT(*) FROM schedules WHERE show_date=CURDATE()"));
            stTix.setText("" + Db.one("SELECT COUNT(*) FROM reservations WHERE DATE(created_at)=CURDATE()"));
            stSales.setText(Ui.peso(Db.one("SELECT COALESCE(SUM(price),0) FROM reservations WHERE DATE(created_at)=CURDATE()")));
            rows(salesMovieT, Db.query("SELECT m.title, COUNT(*) tickets, SUM(r.price) total FROM reservations r"
                    + " JOIN schedules s ON s.id=r.schedule_id JOIN movies m ON m.id=s.movie_id"
                    + " WHERE DATE(r.created_at)=CURDATE() GROUP BY m.title ORDER BY total DESC"));
            rows(dashT, Db.query("SELECT s.show_time, m.title, s.cinema,"
                    + " ROUND(100*(SELECT COUNT(*) FROM reservations r WHERE r.schedule_id=s.id)/" + Db.CAPACITY + ") filled"
                    + " FROM schedules s JOIN movies m ON m.id=s.movie_id WHERE s.show_date=CURDATE() ORDER BY s.show_time"));
        });
    }

    // ---- sales report ----
    @FXML void loadSales() {
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

    // ---- movies ----
    void loadMovies() {
        Ui.safe(() -> {
            movies = Db.query("SELECT * FROM movies ORDER BY title");
            movieSub.setText(movies.size() + " movies in the system"); movieT.setItems(FXCollections.observableArrayList(movies));
        });
    }
    @FXML void addMovie() { movieForm(null); }
    void movieForm(Map<String, Object> m) {
        Ui.form(m == null ? "Add movie" : "Edit movie", new F("title", "Title", Ui.s(m, "title")), new F("genre", "Genre", Ui.s(m, "genre")),
                        new F("rating", "Rating", Ui.s(m, "rating"), "G", "PG", "PG-13", "R"), new F("length", "Length (min)", Ui.s(m, "length")),
                        new F("synopsis", "Synopsis", Ui.s(m, "synopsis")), new F("poster", "Poster image", Ui.s(m, "poster_path")))
                .ifPresent(d -> Ui.safe(() -> {
                    int len = Integer.parseInt(d.get("length"));
                    if (d.get("title").isEmpty()) throw new Exception("Enter a title.");
                    String poster = d.get("poster").isBlank() ? null : d.get("poster");
                    if (m == null) Db.update("INSERT INTO movies(title,genre,rating,length,synopsis,poster_path) VALUES(?,?,?,?,?,?)",
                            d.get("title"), d.get("genre"), d.get("rating"), len, d.get("synopsis"), poster);
                    else Db.update("UPDATE movies SET title=?,genre=?,rating=?,length=?,synopsis=?,poster_path=? WHERE id=?",
                            d.get("title"), d.get("genre"), d.get("rating"), len, d.get("synopsis"), poster, m.get("id"));
                    loadMovies();
                }));
    }

    // ---- schedules ----
    void loadSched() {
        Ui.safe(() -> {
            movies = Db.query("SELECT * FROM movies ORDER BY title");
            dayBar.getChildren().clear(); ToggleGroup g = new ToggleGroup();
            for (int i = 0; i < 5; i++) {
                LocalDate d = LocalDate.now().plusDays(i);
                ToggleButton b = new ToggleButton(i == 0 ? "Today" : d.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)));
                b.getStyleClass().add("pill"); b.setToggleGroup(g); b.setSelected(d.toString().equals(day));
                b.setOnAction(e -> { day = d.toString(); loadSched(); }); dayBar.getChildren().add(b);
            }
            schedT.setItems(FXCollections.observableArrayList(Db.query("SELECT s.*, m.title, " + Db.CAPACITY
                    + "-(SELECT COUNT(*) FROM reservations r WHERE r.schedule_id=s.id) seats_left FROM schedules s JOIN movies m ON m.id=s.movie_id"
                    + " WHERE s.show_date=? ORDER BY s.show_time", day)));
        });
    }
    @FXML void addSched() { schedForm(null); }
    void schedForm(Map<String, Object> x) {
        if (movies.isEmpty()) { Ui.info("Add a movie first."); return; }
        String[] mo = movies.stream().map(m -> m.get("id") + " - " + m.get("title")).toArray(String[]::new);
        String cur = x == null ? null : x.get("movie_id") + " - " + x.get("title");
        Ui.form(x == null ? "Add schedule" : "Edit schedule", new F("movie", "Movie", cur, mo), new F("date", "Date (yyyy-mm-dd)", x == null ? day : Ui.s(x, "show_date")),
                        new F("time", "Time (HH:mm)", x == null ? null : Ui.s(x, "show_time").substring(0, 5)), new F("cinema", "Cinema", x == null ? "Cinema 1" : Ui.s(x, "cinema")),
                        new F("price", "Price", x == null ? "250" : Ui.s(x, "price")))
                .ifPresent(d -> Ui.safe(() -> {
                    int mid = Integer.parseInt(d.get("movie").split(" - ")[0]); double price = Double.parseDouble(d.get("price"));
                    LocalDate.parse(d.get("date")); LocalTime.parse(d.get("time"));
                    if (x == null) Db.update("INSERT INTO schedules(movie_id,show_date,show_time,cinema,price) VALUES(?,?,?,?,?)", mid, d.get("date"), d.get("time"), d.get("cinema"), price);
                    else Db.update("UPDATE schedules SET movie_id=?,show_date=?,show_time=?,cinema=?,price=? WHERE id=?", mid, d.get("date"), d.get("time"), d.get("cinema"), price, x.get("id"));
                    day = d.get("date"); loadSched();
                }));
    }

    // ---- seat map (read-only) ----
    @FXML void tab() { if (tabs.getSelectedToggle() == null) tabs.getToggles().get(0).setSelected(true); loadRes(); }
    void loadRes() {
        boolean list = tabs.getToggles().get(0).isSelected();
        Ui.vis(resT, list); Ui.vis(seatView, !list);
        Ui.safe(() -> {
            if (list) resT.setItems(FXCollections.observableArrayList(Db.query("SELECT r.code, m.title, CONCAT(s.show_date,' ',TIME_FORMAT(s.show_time,'%l:%i %p')) showtime,"
                    + " GROUP_CONCAT(r.seat ORDER BY r.seat SEPARATOR ', ') seats, SUM(r.price) total, u.name sold_by FROM reservations r"
                    + " JOIN schedules s ON s.id=r.schedule_id JOIN movies m ON m.id=s.movie_id JOIN users u ON u.id=r.sold_by"
                    + " GROUP BY r.code, m.title, s.show_date, s.show_time, u.name ORDER BY MIN(r.created_at) DESC")));
            else {
                var sc = Db.query("SELECT s.id, s.show_date, s.show_time, s.cinema, m.title FROM schedules s JOIN movies m ON m.id=s.movie_id ORDER BY s.show_date DESC, s.show_time");
                showCb.getItems().setAll(sc.stream().map(x -> x.get("id") + " - " + x.get("show_date") + " " + Ui.t12(x.get("show_time")) + ", " + x.get("title") + ", " + x.get("cinema")).toList());
                if (!showCb.getItems().isEmpty()) { showCb.getSelectionModel().selectFirst(); seats(); }
            }
        });
    }
    @FXML void seats() {
        String v = showCb.getValue(); if (v == null) return;
        Ui.safe(() -> {
            Set<String> t = new HashSet<>();
            for (var r : Db.query("SELECT seat FROM reservations WHERE schedule_id=?", v.split(" - ")[0])) t.add((String) r.get("seat"));
            Ui.fillSeats(grid, t, Set.of(), null);
        });
    }
}