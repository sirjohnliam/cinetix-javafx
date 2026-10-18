package cinetix;

import java.io.IOException;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
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

        // Set the global window/taskbar application icon using your logo asset
        try {
            s.getIcons().add(new Image(App.class.getResourceAsStream("/cinetix/images/1.png")));
        } catch (Exception ignored) {}

        s.setTitle("CineTix");
        s.initStyle(StageStyle.UNDECORATED); // Removes the Windows title bar and close/minimize buttons completely
        s.setFullScreenExitHint("Press F11 to leave full screen.");
        fillScreen();
        show("login");
        s.show();
        fillScreen();
    }

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

            // Ensure every new scene loaded also updates or re-applies the stage icon
            try {
                stage.getIcons().setAll(new Image(App.class.getResourceAsStream("/cinetix/images/1.png")));
            } catch (Exception ignored) {}

            stage.show();
            fillScreen();
            Ui.animateHover(p);
            Ui.addLogos(p);
            FadeTransition ft = new FadeTransition(Duration.millis(220), p);
            ft.setFromValue(0); ft.setToValue(1); ft.play();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    static void logout() { uid = 0; name = role = null; show("login"); }
}