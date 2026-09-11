package com.writingassistant.ui;

import com.writingassistant.config.ConfigManager;
import com.writingassistant.logging.AppLogger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX Application entry point for Writing Assistant desktop UI styled with BootstrapFX.
 * Manages window lifecycle, icon assets, and clean resource shutdown on exit.
 */
public class WritingAssistantApp extends Application {

    private static final Logger LOGGER = AppLogger.getLogger(WritingAssistantApp.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            LOGGER.info("Starting Writing Assistant JavaFX Application with BootstrapFX styling...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/writingassistant/ui/main.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1100, 750);
            scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());

            primaryStage.setTitle("Writing Assistant - AI Rewriting & Citation-Safe Paraphrasing");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(850);
            primaryStage.setMinHeight(580);

            // Load Application Window Icon if present
            try (InputStream iconStream = getClass().getResourceAsStream("/com/writingassistant/ui/icons/app-icon.png")) {
                if (iconStream != null) {
                    primaryStage.getIcons().add(new Image(iconStream));
                }
            } catch (Exception ignored) {
            }

            // Clean resource shutdown on window close
            primaryStage.setOnCloseRequest(event -> {
                LOGGER.info("Window close requested. Cleaning up application resources...");
                AppLogger.shutdown();
                Platform.exit();
            });

            primaryStage.show();
            LOGGER.info("Writing Assistant UI window displayed successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize JavaFX UI: " + e.getMessage(), e);
            throw new RuntimeException("UI initialization failed", e);
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Application stopping. Releasing background resources...");
        AppLogger.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
