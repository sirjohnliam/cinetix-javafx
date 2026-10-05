package cinetix;

import cinetix.Ui.F;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Admin: manages the system (dashboard, movies, schedules, read-only reservations). */
public class AdminController {
    @FXML ToggleGroup nav, tabs;
    @FXML Label who, dateLbl, stMovies, stShows, stSales, movieSub;
    @FXML VBox pDash, pMovies, pSched, pRes, pSales, seatView;
    @FXML HBox dayBar;
    @FXML TableView<Map<String, Object>> dashT, movieT, schedT, resT, salesT;
    @FXML ComboBox<String> showCb;
    @FXML GridPane grid;
    String day = LocalDate.now().toString();
    List<Map<String, Object>> movies = new ArrayList<>();

    @FXML void initialize() {
        who.setText(App.name + "\nAdmin");
        dateLbl.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)));
        Ui.cols(dashT, "Time", "@show_time", "Movie", "title", "Cinema", "cinema", "Seats filled", "%filled");
        Ui.cols(salesT, "Date", "sale_date", "Tickets sold", "tickets", "Total sales", "$total");
        Ui.cols(movieT, "Movie", "title", "Genre", "genre", "Rating", "rating", "Length (min)", "length");
        Ui.posterCol(movieT);
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
        Ui.vis(pSales, n.equals("Sales Report")); Ui.vis(pRes, n.equals("Seat Taken"));
        switch (n) { case "Dashboard" -> loadDash(); case "Movies" -> loadMovies(); case "Schedules" -> loadSched(); case "Sales Report" -> loadSales(); default -> loadRes(); }
    }

    // ---- dashboard ----
    void loadDash() {
        Ui.safe(() -> {
            stMovies.setText("" + Db.one("SELECT COUNT(DISTINCT movie_id) FROM schedules WHERE show_date=CURDATE()"));
            stShows.setText("" + Db.one("SELECT COUNT(*) FROM schedules WHERE show_date=CURDATE()"));
            stSales.setText(Ui.peso(Db.one("SELECT COALESCE(SUM(price),0) FROM reservations WHERE DATE(created_at)=CURDATE()")));
            dashT.setItems(FXCollections.observableArrayList(Db.query("SELECT s.show_time, m.title, s.cinema,"
                    + " ROUND(100*(SELECT COUNT(*) FROM reservations r WHERE r.schedule_id=s.id)/" + Db.CAPACITY + ") filled"
                    + " FROM schedules s JOIN movies m ON m.id=s.movie_id WHERE s.show_date=CURDATE() ORDER BY s.show_time")));
        });
    }

    // ---- sales report ----
    void loadSales() {
        Ui.safe(() -> salesT.setItems(FXCollections.observableArrayList(Db.query("SELECT DATE(created_at) sale_date,"
                + " COUNT(DISTINCT code) tickets, SUM(price) total FROM reservations GROUP BY DATE(created_at) ORDER BY sale_date DESC"))));
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

    // ---- reservations (read-only) ----
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