package cinetix;

import cinetix.Ui.Act;
import java.io.File;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/** Admin and superadmin share this screen. The superadmin also sees Accounts, the accounts archive and the per-cashier views. */
public class AdminController {
    @FXML ToggleGroup nav, range, tabs, arcTabs;
    @FXML ToggleButton navAccounts, rCustom, arcAccTab;
    @FXML Label who, role, dateLbl, stSales, stTix, stTx, stShows, movieSub, sTotal, sTix, sTx;
    @FXML VBox pDash, pMovies, pRes, pSales, pAcc, pArc, seatView, cashCard, sCashCard, arcMoviesBox, arcShowsBox, arcAccsBox;
    @FXML HBox customBar;
    @FXML DatePicker dFromDp, dToDp, fromDp, toDp, seatDp;
    @FXML ComboBox<String> genreCb, seatMovieCb;
    @FXML FlowPane seatCards;
    @FXML LineChart<String, Number> lineSales;
    @FXML BarChart<String, Number> barMovies, barCash;
    @FXML PieChart pieGenre;
    @FXML TableView<Map<String, Object>> dashT, sMovieT, sDayT, sCashT, sTxT, movieT, resT, accT, arcAccT, arcMovieT, arcShowT;

    static final DateTimeFormatter PARSE_HM = DateTimeFormatter.ofPattern("H:mm", Locale.US), DB_HM = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
    boolean sup, ready;
    Accounts acc;
    String from, to, genre;   // current dashboard filter

    @FXML void initialize() {
        sup = "superadmin".equals(App.role);
        who.setText(App.name); role.setText(sup ? "Superadmin" : "Admin");
        Ui.vis(navAccounts, sup); Ui.vis(cashCard, sup); Ui.vis(sCashCard, sup); Ui.vis(arcAccTab, sup);
        acc = new Accounts(accT, arcAccT);

        genreCb.getItems().add("All genres"); genreCb.getItems().addAll(Db.GENRES); genreCb.setValue("All genres");
        dFromDp.setValue(LocalDate.now()); dToDp.setValue(LocalDate.now());
        fromDp.setValue(LocalDate.now()); toDp.setValue(LocalDate.now());
        seatDp.setValue(LocalDate.now());

        // dashboard + sales report tables
        Ui.cols(dashT, "Time", "@show_time", "Movie", "title", "Cinema", "cinema");
        dashT.getColumns().add(filledCol());
        Ui.cols(sMovieT, "Movie", "title", "Tickets sold", "tickets", "Sales", "$total");
        Ui.cols(sDayT, "Date", "day_lbl", "Tickets sold", "tickets", "Sales", "$total");
        Ui.cols(sCashT, "Cashier", "cashier", "Transactions", "transactions", "Tickets", "tickets", "Sales", "$total");
        Ui.cols(sTxT, "Code", "code", "Movie", "title", "Showtime", "showtime", "Seats", "seats", "Total", "$total", "Sold by", "sold_by");
        for (var t : List.of(dashT, sMovieT, sDayT, sCashT, sTxT)) {
            t.setFixedCellSize(54); t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        }

        // movies (with their showtimes) + seat list
        Ui.cols(movieT, "Movie", "title", "Genre", "genre", "Rating", "rating", "Length (min)", "length", "Upcoming showtimes", "upcoming");
        Ui.posterCol(movieT, 130, 190); // Increased poster column dimensions
        movieT.setFixedCellSize(210);   // Matched row height to accommodate larger posters
        movieT.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movieT.setMaxWidth(Double.MAX_VALUE);

// Force columns to stretch and fill table width nicely without dead spots on the right
        movieT.getColumns().get(0).setPrefWidth(140); // Poster column
        movieT.getColumns().get(1).setPrefWidth(220); // Movie Title
        movieT.getColumns().get(2).setPrefWidth(130); // Genre
        movieT.getColumns().get(3).setPrefWidth(100); // Rating
        movieT.getColumns().get(4).setPrefWidth(130); // Length (min)
        movieT.getColumns().get(5).setPrefWidth(180); // Upcoming showtimes

        Ui.actionList(movieT, new Act("Edit", this::movieForm), new Act("Archive", this::archiveMovie));
        Ui.cols(resT, "Code", "code", "Movie", "title", "Showtime", "showtime", "Seat", "seats", "Total", "$total", "Sold by", "sold_by");

        // archive
        Ui.cols(arcMovieT, "Movie", "title", "Genre", "genre", "Rating", "rating", "Length (min)", "length");
        Ui.actionList(arcMovieT, new Act("Restore", m -> Ui.safe(() -> { Db.update("UPDATE movies SET archived=0 WHERE id=?", m.get("id")); loadArc(); })));
        Ui.cols(arcShowT, "Date", "show_date", "Time", "@show_time", "Movie", "title", "Cinema", "cinema", "Price", "$price");
        Ui.actionList(arcShowT, new Act("Restore", s -> Ui.safe(() -> { Db.update("UPDATE schedules SET archived=0 WHERE id=?", s.get("id")); loadArc(); })));

        ready = true;
        show("Dashboard");
    }
    @FXML void logout() { if (Ui.confirm("Are you sure you want to log out?")) App.logout(); }
    @FXML void navigate(ActionEvent e) { show(((ToggleButton) e.getSource()).getText()); }

    void show(String n) {
        nav.getToggles().forEach(t -> t.setSelected(((ToggleButton) t).getText().equals(n)));
        Ui.vis(pDash, n.equals("Dashboard")); Ui.vis(pMovies, n.equals("Movies")); Ui.vis(pRes, n.equals("Seat map"));
        Ui.vis(pSales, n.equals("Sales report")); Ui.vis(pAcc, n.equals("Accounts")); Ui.vis(pArc, n.equals("Archive"));
        switch (n) {
            case "Dashboard" -> loadDash(); case "Movies" -> loadMovies(); case "Seat map" -> loadRes();
            case "Sales report" -> loadSales(); case "Accounts" -> acc.load(); default -> loadArc();
        }
    }
    @FXML void addAccount() { acc.add(); }

    // ---- helpers ----
    /** Fills a table and sizes it to its rows (no empty striped rows). */
    void rows(TableView<Map<String, Object>> t, List<Map<String, Object>> data) {
        t.setItems(FXCollections.observableArrayList(data));
        double h = Math.max(1, data.size()) * 54 + 42;
        t.setPrefHeight(h); t.setMinHeight(h); t.setMaxHeight(h);
    }
    static String nz(String s) { return s == null ? "" : s; }

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

    // ---- dashboard (date range + genre filter, charts) ----
    @FXML void pickRange() {
        if (range.getSelectedToggle() == null) range.selectToggle(range.getToggles().get(0));
        boolean custom = rCustom.isSelected(); Ui.vis(customBar, custom);
        if (!custom) loadDash();
    }
    LocalDate[] dashRange() {
        LocalDate today = LocalDate.now();
        Toggle t = range.getSelectedToggle(); String n = t == null ? "Today" : ((ToggleButton) t).getText();
        switch (n) {
            case "Last 7 days": return new LocalDate[]{today.minusDays(6), today};
            case "Last 30 days": return new LocalDate[]{today.minusDays(29), today};
            case "Custom": {
                LocalDate f = dFromDp.getValue(), e = dToDp.getValue();
                if (f == null || e == null) { Ui.info("Pick both a From and a To date."); return null; }
                if (f.isAfter(e)) { Ui.info("The From date can't be after the To date."); return null; }
                if (f.plusDays(120).isBefore(e)) { Ui.info("Pick a range of 120 days or less."); return null; }
                return new LocalDate[]{f, e};
            }
            default: return new LocalDate[]{today, today};
        }
    }
    /** FROM/WHERE shared by every dashboard query: the chosen dates and (optionally) one genre. */
    String base() {
        return " FROM reservations r JOIN schedules s ON s.id=r.schedule_id JOIN movies m ON m.id=s.movie_id JOIN users u ON u.id=r.sold_by"
                + " WHERE DATE(r.created_at) BETWEEN ? AND ?" + (genre == null ? "" : " AND m.genre=?");
    }
    List<Map<String, Object>> dq(String select, String tail) throws SQLException {
        Object[] a = genre == null ? new Object[]{from, to} : new Object[]{from, to, genre};
        return Db.query(select + base() + " " + tail, a);
    }

    @FXML void loadDash() {
        if (!ready) return;
        LocalDate[] r = dashRange(); if (r == null) return;
        from = r[0].toString(); to = r[1].toString();
        String g = genreCb.getValue(); genre = g == null || g.equals("All genres") ? null : g;
        DateTimeFormatter df = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
        dateLbl.setText((r[0].equals(r[1]) ? r[0].format(df) : r[0].format(df) + "  to  " + r[1].format(df)) + (genre == null ? "" : "   ·   " + genre));
        Ui.safe(() -> {
            var sum = dq("SELECT COALESCE(SUM(r.price),0) total, COUNT(*) tickets, COUNT(DISTINCT r.code) tx", "").get(0);
            stSales.setText(Ui.peso(sum.get("total"))); stTix.setText("" + sum.get("tickets")); stTx.setText("" + sum.get("tx"));
            stShows.setText("" + Db.one("SELECT COUNT(*) FROM schedules s JOIN movies m ON m.id=s.movie_id"
                    + " WHERE s.show_date=CURDATE() AND s.archived=0 AND m.archived=0"));

            // sales over time (days with no sales show as 0)
            Map<String, Double> byDay = new HashMap<>();
            for (var x : dq("SELECT DATE(r.created_at) d, SUM(r.price) total", "GROUP BY DATE(r.created_at)"))
                byDay.put(String.valueOf(x.get("d")), ((Number) x.get("total")).doubleValue());
            XYChart.Series<String, Number> line = new XYChart.Series<>();
            DateTimeFormatter lf = DateTimeFormatter.ofPattern("MMM d", Locale.US);
            for (LocalDate d = r[0]; !d.isAfter(r[1]); d = d.plusDays(1))
                line.getData().add(new XYChart.Data<>(d.format(lf), byDay.getOrDefault(d.toString(), 0.0)));
            lineSales.getData().clear(); lineSales.getData().add(line);

            // tickets per movie (top 8)
            XYChart.Series<String, Number> bm = new XYChart.Series<>();
            for (var x : dq("SELECT m.title, COUNT(*) tickets", "GROUP BY m.id, m.title ORDER BY tickets DESC LIMIT 8"))
                bm.getData().add(new XYChart.Data<>(String.valueOf(x.get("title")), (Number) x.get("tickets")));
            barMovies.getData().clear(); barMovies.getData().add(bm);

            // sales by genre
            List<PieChart.Data> slices = new ArrayList<>();
            for (var x : dq("SELECT COALESCE(m.genre,'Other') genre, SUM(r.price) total", "GROUP BY COALESCE(m.genre,'Other') ORDER BY total DESC"))
                slices.add(new PieChart.Data(x.get("genre") + "  " + Ui.peso(x.get("total")), ((Number) x.get("total")).doubleValue()));
            pieGenre.setData(FXCollections.observableArrayList(slices));

            // superadmin only: sales per cashier
            if (sup) {
                XYChart.Series<String, Number> bc = new XYChart.Series<>();
                for (var x : dq("SELECT u.name cashier, SUM(r.price) total", "GROUP BY u.id, u.name ORDER BY total DESC"))
                    bc.getData().add(new XYChart.Data<>(String.valueOf(x.get("cashier")), (Number) x.get("total")));
                barCash.getData().clear(); barCash.getData().add(bc);
            }

            rows(dashT, Db.query("SELECT s.show_time, m.title, s.cinema,"
                    + " ROUND(100*(SELECT COUNT(*) FROM reservations r WHERE r.schedule_id=s.id)/" + Db.CAPACITY + ") filled"
                    + " FROM schedules s JOIN movies m ON m.id=s.movie_id WHERE s.show_date=CURDATE() AND s.archived=0 AND m.archived=0 ORDER BY s.show_time"));
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
            sTotal.setText(Ui.peso(sum.get("total"))); sTix.setText("" + sum.get("tickets")); sTx.setText("" + sum.get("tx"));

            rows(sMovieT, Db.query("SELECT m.title, COUNT(*) tickets, SUM(r.price) total FROM reservations r"
                    + " JOIN schedules s ON s.id=r.schedule_id JOIN movies m ON m.id=s.movie_id"
                    + " WHERE DATE(r.created_at) BETWEEN ? AND ? GROUP BY m.title ORDER BY total DESC", a, b));
            rows(sDayT, Db.query("SELECT DATE_FORMAT(created_at,'%a, %b %e') day_lbl, COUNT(*) tickets, SUM(price) total"
                    + " FROM reservations WHERE DATE(created_at) BETWEEN ? AND ?"
                    + " GROUP BY DATE(created_at), DATE_FORMAT(created_at,'%a, %b %e') ORDER BY DATE(created_at) DESC", a, b));
            if (sup) rows(sCashT, Db.query("SELECT u.name cashier, COUNT(DISTINCT r.code) transactions, COUNT(*) tickets, SUM(r.price) total"
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

    // ---- movies (each movie is created and edited together with its showtimes) ----
    void loadMovies() {
        Ui.safe(() -> {
            var movies = Db.query("SELECT m.*, (SELECT COUNT(*) FROM schedules s WHERE s.movie_id=m.id AND s.archived=0 AND s.show_date>=CURDATE()) upcoming"
                    + " FROM movies m WHERE m.archived=0 ORDER BY m.title");
            movieSub.setText(movies.size() + " movies in the system. Showtimes are set inside each movie.");
            movieT.setItems(FXCollections.observableArrayList(movies));
        });
    }
    void archiveMovie(Map<String, Object> m) {
        if (!Ui.confirm("Archive " + m.get("title") + "? It disappears from the cashier screen. Past sales stay in the reports, and you can restore it from the Archive tab.")) return;
        Ui.safe(() -> { Db.update("UPDATE movies SET archived=1 WHERE id=?", m.get("id")); loadMovies(); });
    }

    /** One editable showtime line inside the movie form. */
    static class Row {
        Object id; boolean removed; LocalTime tm; double pr;
        final DatePicker dp = new DatePicker(); final TextField time = new TextField(), price = new TextField();
        final ComboBox<String> cinema = new ComboBox<>(FXCollections.observableArrayList(Db.CINEMAS)); final HBox box = new HBox(6);
    }
    /** Everything the movie form edits. */
    static class MForm {
        Object id; final List<Row> rows = new ArrayList<>();
        final TextField title = new TextField(), length = new TextField(), poster = new TextField();
        final ComboBox<String> genre = new ComboBox<>(FXCollections.observableArrayList(Db.GENRES)), rating = new ComboBox<>(FXCollections.observableArrayList("G", "PG", "PG-13", "R"));
        final TextArea syn = new TextArea();
    }

    @FXML void addMovie() { movieForm(null); }

    void movieForm(Map<String, Object> m) {
        MForm f = new MForm(); f.id = m == null ? null : m.get("id");
        String head = m == null ? "Add movie" : "Edit movie";
        Dialog<Void> d = new Dialog<>(); d.setTitle(head); d.setHeaderText(head + (m == null ? ": details and showtimes" : ""));

        f.title.setText(nz(Ui.s(m, "title"))); Ui.limit(f.title, 100); f.title.setPrefWidth(380);
        f.genre.setPromptText("Select genre"); f.genre.setPrefWidth(150); if (Ui.s(m, "genre") != null) f.genre.setValue(Ui.s(m, "genre"));
        f.rating.setPrefWidth(100); f.rating.setValue(m == null ? "PG-13" : Ui.s(m, "rating"));
        f.length.setText(nz(Ui.s(m, "length"))); Ui.limit(f.length, 3); f.length.setPrefWidth(90);
        f.syn.setText(nz(Ui.s(m, "synopsis"))); f.syn.setWrapText(true); f.syn.setPrefRowCount(3); Ui.limit(f.syn, 300);
        f.poster.setText(nz(Ui.s(m, "poster_path"))); f.poster.setEditable(false); f.poster.setPromptText("No image chosen."); f.poster.setPrefWidth(300);
        Button browse = new Button("Browse…"); browse.getStyleClass().addAll("sm", "edit");
        browse.setOnAction(e -> {
            FileChooser fc = new FileChooser(); fc.setTitle("Choose a poster image");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File picked = fc.showOpenDialog(App.stage);
            if (picked != null) f.poster.setText(picked.getAbsolutePath());
        });

        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10);
        g.addRow(0, new Label("Title"), new VBox(2, f.title, counter(f.title, 100)));
        g.addRow(1, new Label("Details"), new HBox(14, field("Genre", f.genre), field("Rating", f.rating), field("Length (min)", f.length)));
        g.addRow(2, new Label("Synopsis"), new VBox(2, f.syn, counter(f.syn, 300)));
        g.addRow(3, new Label("Poster"), new HBox(6, f.poster, browse));

        // showtimes section
        VBox rowsBox = new VBox(6);
        ScrollPane sp = new ScrollPane(rowsBox); sp.setFitToWidth(true); sp.setPrefHeight(170); sp.setMinHeight(110); sp.getStyleClass().add("clear");
        Button addRow = new Button("+ Add showtime"); addRow.getStyleClass().addAll("sm", "ok");
        addRow.setOnAction(e -> newRow(null, f.rows.stream().filter(x -> !x.removed).reduce((x, y) -> y).orElse(null), rowsBox, f.rows));
        HBox cols = new HBox(6, colLbl("Date", 140), colLbl("Time (HH:mm)", 70), colLbl("Cinema", 120), colLbl("Price", 80));
        HBox showHead = new HBox(12, Ui.lbl("Showtimes", "h2"), addRow); showHead.setAlignment(Pos.CENTER_LEFT);
        if (m != null) Ui.safe(() -> {
            for (var s : Db.query("SELECT * FROM schedules WHERE movie_id=? AND archived=0 AND show_date>=CURDATE() ORDER BY show_date, show_time", f.id))
                newRow(s, null, rowsBox, f.rows);
        });
        else newRow(null, null, rowsBox, f.rows);

        Label err = Ui.lbl("", "error"); err.setWrapText(true); err.setMaxWidth(580);
        d.getDialogPane().setContent(new VBox(12, g, showHead, cols, sp, err));
        d.getDialogPane().setPrefWidth(640); Ui.style(d.getDialogPane());
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        boolean[] saved = {false};
        ((Button) d.getDialogPane().lookupButton(save)).addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                String problem = check(f);
                if (problem != null) { err.setText(problem); ev.consume(); return; }
                persist(f); saved[0] = true;
            } catch (Exception ex) { err.setText(ex.getMessage() == null ? "Something went wrong." : ex.getMessage()); ev.consume(); }
        });
        d.showAndWait();
        if (saved[0]) loadMovies();
    }

    static Label counter(TextInputControl t, int max) {
        Label l = Ui.lbl("", "muted");
        Runnable u = () -> l.setText(t.getText().length() + "/" + max);
        t.textProperty().addListener((o, a, b) -> u.run()); u.run();
        return l;
    }
    static VBox field(String label, Node n) { return new VBox(4, Ui.lbl(label, "muted"), n); }
    static Label colLbl(String t, double w) { Label l = Ui.lbl(t, "muted"); l.setPrefWidth(w); l.setMinWidth(w); return l; }

    /** Adds one showtime line (existing showtime s, or a new one that starts from the previous line's values). */
    void newRow(Map<String, Object> s, Row prev, VBox rowsBox, List<Row> rows) {
        Row r = new Row(); r.id = s == null ? null : s.get("id");
        r.dp.setPrefWidth(140); r.dp.setEditable(false);
        r.dp.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item.isBefore(LocalDate.now())) setDisable(true);
            }
        });
        r.time.setPrefWidth(70); r.time.setPromptText("13:30"); Ui.limit(r.time, 5);
        r.cinema.setPrefWidth(120); r.price.setPrefWidth(80); Ui.limit(r.price, 7);
        if (s != null) {
            r.dp.setValue(LocalDate.parse(s.get("show_date").toString()));
            r.time.setText(s.get("show_time").toString().substring(0, 5));
            r.cinema.setValue(Ui.s(s, "cinema"));
            r.price.setText(new BigDecimal(s.get("price").toString()).stripTrailingZeros().toPlainString());
        } else {
            r.dp.setValue(prev == null ? LocalDate.now() : prev.dp.getValue());
            r.cinema.setValue(prev == null ? Db.CINEMAS[0] : prev.cinema.getValue());
            r.price.setText(prev == null ? "250" : prev.price.getText());
        }
        Button x = new Button("✕"); x.getStyleClass().addAll("sm", "warn");
        x.setOnAction(e -> { r.removed = true; Ui.vis(r.box, false); });   // an existing showtime is archived when you press Save
        r.box.setAlignment(Pos.CENTER_LEFT); r.box.getChildren().addAll(r.dp, r.time, r.cinema, r.price, x);
        rows.add(r); rowsBox.getChildren().add(r.box);
    }

    static boolean overlaps(LocalTime s1, int l1, LocalTime s2, int l2) {
        int a1 = s1.toSecondOfDay() / 60, b1 = a1 + l1, a2 = s2.toSecondOfDay() / 60, b2 = a2 + l2;
        return a1 < b2 && a2 < b1;
    }

    /** null when the form is fine, otherwise the first problem found (the dialog stays open). */
    String check(MForm f) throws SQLException {
        if (f.title.getText().trim().isEmpty()) return "Enter a title.";
        if (f.genre.getValue() == null) return "Choose a genre.";
        if (f.rating.getValue() == null) return "Choose a rating.";
        int len;
        try { len = Integer.parseInt(f.length.getText().trim()); } catch (NumberFormatException e) { return "Length must be a number of minutes."; }
        if (len < 1 || len > 600) return "Length must be between 1 and 600 minutes.";
        int n = 0;
        for (Row r : f.rows) {
            if (r.removed) continue; n++;
            if (r.dp.getValue() == null) return "Showtime " + n + ": pick a date.";
            try { r.tm = LocalTime.parse(r.time.getText().trim(), PARSE_HM); } catch (Exception e) { return "Showtime " + n + ": the time should look like 13:30."; }
            try { r.pr = Double.parseDouble(r.price.getText().trim()); } catch (NumberFormatException e) { return "Showtime " + n + ": the price must be a number."; }
            if (!Double.isFinite(r.pr) || r.pr <= 0) return "Showtime " + n + ": the price must be more than 0.";
            if (r.cinema.getValue() == null) return "Showtime " + n + ": choose a cinema.";
            if (r.id == null && r.dp.getValue().isBefore(LocalDate.now())) return "Showtime " + n + ": the date can't be in the past.";
        }
        List<Row> live = f.rows.stream().filter(r -> !r.removed).toList();
        for (int i = 0; i < live.size(); i++) {
            Row a = live.get(i);
            for (int j = i + 1; j < live.size(); j++) {
                Row b = live.get(j);
                if (a.cinema.getValue().equals(b.cinema.getValue()) && a.dp.getValue().equals(b.dp.getValue()) && overlaps(a.tm, len, b.tm, len))
                    return "Two showtimes overlap in " + a.cinema.getValue() + " on " + a.dp.getValue() + ".";
            }
            for (var o : Db.query("SELECT s.show_time, m.length, m.title FROM schedules s JOIN movies m ON m.id=s.movie_id"
                            + " WHERE s.cinema=? AND s.show_date=? AND s.archived=0 AND m.archived=0 AND s.movie_id<>?",
                    a.cinema.getValue(), a.dp.getValue().toString(), f.id == null ? 0 : f.id)) {
                int ol = o.get("length") == null ? 120 : ((Number) o.get("length")).intValue();
                if (overlaps(a.tm, len, LocalTime.parse(o.get("show_time").toString()), ol))
                    return a.cinema.getValue() + " is already showing " + o.get("title") + " at " + Ui.t12(o.get("show_time")) + " on " + a.dp.getValue() + ".";
            }
        }
        return null;
    }

    /** Saves the movie and all its showtimes together (all or nothing). */
    void persist(MForm f) throws Exception {
        String poster = f.poster.getText().isBlank() ? null : f.poster.getText();
        int len = Integer.parseInt(f.length.getText().trim());
        Db.tx(c -> {
            Object id = f.id;
            if (id == null) id = Db.exec(c, "INSERT INTO movies(title,genre,rating,length,synopsis,poster_path) VALUES(?,?,?,?,?,?)",
                    f.title.getText().trim(), f.genre.getValue(), f.rating.getValue(), len, f.syn.getText().trim(), poster);
            else Db.exec(c, "UPDATE movies SET title=?,genre=?,rating=?,length=?,synopsis=?,poster_path=? WHERE id=?",
                    f.title.getText().trim(), f.genre.getValue(), f.rating.getValue(), len, f.syn.getText().trim(), poster, id);
            for (Row r : f.rows) {
                if (r.removed) { if (r.id != null) Db.exec(c, "UPDATE schedules SET archived=1 WHERE id=?", r.id); continue; }
                String date = r.dp.getValue().toString(), time = r.tm.format(DB_HM);
                if (r.id == null) Db.exec(c, "INSERT INTO schedules(movie_id,show_date,show_time,cinema,price) VALUES(?,?,?,?,?)", id, date, time, r.cinema.getValue(), r.pr);
                else Db.exec(c, "UPDATE schedules SET show_date=?,show_time=?,cinema=?,price=? WHERE id=?", date, time, r.cinema.getValue(), r.pr, r.id);
            }
        });
    }

    // ---- seat map: every cinema showing the chosen movie on the chosen day ----
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
                String keep = seatMovieCb.getValue();
                var ms = Db.query("SELECT DISTINCT m.id, m.title FROM movies m JOIN schedules s ON s.movie_id=m.id WHERE m.archived=0 AND s.archived=0 ORDER BY m.title");
                seatMovieCb.getItems().setAll(ms.stream().map(x -> x.get("id") + " - " + x.get("title")).toList());
                if (keep != null && seatMovieCb.getItems().contains(keep)) seatMovieCb.setValue(keep);
                else if (!seatMovieCb.getItems().isEmpty()) seatMovieCb.getSelectionModel().selectFirst();
                seats();
            }
        });
    }
    @FXML void seats() {
        seatCards.getChildren().clear();
        String v = seatMovieCb.getValue(); LocalDate day = seatDp.getValue();
        if (v == null || day == null) return;
        Ui.safe(() -> {
            var sc = Db.query("SELECT s.id, s.show_time, s.cinema, (SELECT COUNT(*) FROM reservations r WHERE r.schedule_id=s.id) sold"
                    + " FROM schedules s WHERE s.movie_id=? AND s.show_date=? AND s.archived=0 ORDER BY s.cinema, s.show_time", v.split(" - ")[0], day.toString());
            if (sc.isEmpty()) { seatCards.getChildren().add(Ui.lbl("No showtimes for this movie on " + Ui.d(day) + ".", "muted")); return; }
            for (var s : sc) {
                Set<String> taken = new HashSet<>();
                for (var r : Db.query("SELECT seat FROM reservations WHERE schedule_id=?", s.get("id"))) taken.add((String) r.get("seat"));
                GridPane grid = new GridPane(); Ui.fillSeats(grid, taken, Set.of(), null);
                VBox card = new VBox(8, Ui.lbl(s.get("cinema") + "   " + Ui.t12(s.get("show_time")), "h2"),
                        Ui.lbl(s.get("sold") + " of " + Db.CAPACITY + " seats taken", "muted"), grid);
                card.getStyleClass().add("card"); seatCards.getChildren().add(card);
            }
        });
    }

    // ---- archive ----
    @FXML void arcTab() { if (arcTabs.getSelectedToggle() == null) arcTabs.getToggles().get(0).setSelected(true); loadArc(); }
    void loadArc() {
        if (arcTabs.getSelectedToggle() == null) arcTabs.getToggles().get(0).setSelected(true);
        String t = ((ToggleButton) arcTabs.getSelectedToggle()).getText();
        Ui.vis(arcMoviesBox, t.equals("Movies")); Ui.vis(arcShowsBox, t.equals("Showtimes")); Ui.vis(arcAccsBox, t.equals("Accounts"));
        Ui.safe(() -> {
            switch (t) {
                case "Movies" -> arcMovieT.setItems(FXCollections.observableArrayList(Db.query("SELECT * FROM movies WHERE archived=1 ORDER BY title")));
                case "Showtimes" -> arcShowT.setItems(FXCollections.observableArrayList(Db.query(
                        "SELECT s.*, m.title FROM schedules s JOIN movies m ON m.id=s.movie_id WHERE s.archived=1 ORDER BY s.show_date DESC, s.show_time")));
                default -> acc.loadArc();
            }
        });
    }
}