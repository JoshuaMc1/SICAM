package com.joshuamc1.sicam.core;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

public class PermissionHelperUiTest {
    @BeforeAll public static void startToolkit() throws Exception {
        CountDownLatch started=new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch(IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(15,TimeUnit.SECONDS));
    }
    @AfterAll public static void stopToolkit() { Platform.exit(); }
    private void onFx(Runnable assertion) throws Exception {
        FutureTask<Void> task=new FutureTask<>(assertion,null);
        Platform.runLater(task); task.get(15,TimeUnit.SECONDS);
    }
    @Test public void visibleDeniedActionIsDisabledAndShowsRedWarning() throws Exception {
        onFx(()->{
            Button action=new Button("Exportar"); HBox parent=new HBox(action);
            PermissionHelper.configureVisibleAction(action,false);
            assertTrue(action.isVisible()); assertTrue(action.isDisabled());
            Label label=(Label)parent.getChildren().get(1);
            assertEquals("No cuenta con permisos suficientes",label.getText());
            assertTrue(label.getStyle().contains("#D32F2F"));
            PermissionHelper.configureVisibleAction(action,true);
            assertFalse(action.isDisabled()); assertFalse(label.isVisible()); assertFalse(label.isManaged());
        });
    }
    @Test public void siblingActionsShareOneWarningUntilAllAreAuthorized() throws Exception {
        onFx(()->{
            Button csv=new Button("CSV"), pdf=new Button("PDF"); HBox parent=new HBox(csv,pdf);
            PermissionHelper.configureVisibleAction(csv,false); PermissionHelper.configureVisibleAction(pdf,false);
            assertEquals(3,parent.getChildren().size());
            Label label=(Label)parent.getChildren().get(2);
            PermissionHelper.configureVisibleAction(csv,true);
            assertTrue(label.isVisible()); assertTrue(pdf.isDisabled());
            PermissionHelper.configureVisibleAction(pdf,true);
            assertFalse(label.isVisible());
        });
    }
}
