package cinetix;

import java.io.IOException;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {
    static Stage stage;
    static int uid; static String name, role;   // logged-in user

    @Override public void start(Stage s) {
        stage = s;
        try { Db.init(); }
        catch (Exception e) {
            Ui.error("Can't reach the database.\nOpen XAMPP and start MySQL, then run again.\n\n" + e.getMessage());
            Platform.exit(); return;
        }
        s.setTitle("CineTix");
        s.setFullScreenExitHint("Press F11 to leave full screen.");
        fillScreen();
        show("login");
        s.show();
        fillScreen();   // re-applied once the window is actually on screen; some Windows setups need this second call
    }

    /** Sizes the window to the full screen. Done instead of setMaximized(true), which some Windows setups quietly
     * undo the moment a new Scene is attached to the Stage (e.g. after every screen change here). */
    static void fillScreen() {
        Rectangle2D b = Screen.getPrimary().getVisualBounds();
        stage.setX(b.getMinX()); stage.setY(b.getMinY());
        stage.setWidth(b.getWidth()); stage.setHeight(b.getHeight());
    }

    static void show(String fxml) {
        try {
            Parent p = FXMLLoader.load(App.class.getResource("/cinetix/" + fxml + ".fxml"));
            p.setOpacity(0);
            Scene sc = new Scene(p, 1100, 700);
            sc.getStylesheets().add(Ui.css());
            sc.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.F11) stage.setFullScreen(!stage.isFullScreen()); });
            stage.setScene(sc);
            fillScreen();
            Ui.animateHover(p);
            FadeTransition ft = new FadeTransition(Duration.millis(220), p);
            ft.setFromValue(0); ft.setToValue(1); ft.play();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    static void logout() { uid = 0; name = role = null; show("login"); }
}