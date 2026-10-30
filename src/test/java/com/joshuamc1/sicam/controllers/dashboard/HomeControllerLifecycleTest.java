package com.joshuamc1.sicam.controllers.dashboard;

import javafx.application.Platform;
import javafx.scene.chart.*;
import org.junit.jupiter.api.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.User;
import javafx.concurrent.Task;
import org.javalite.activejdbc.Base;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

public class HomeControllerLifecycleTest {
    @BeforeAll public static void startToolkit() throws Exception {
        CountDownLatch ready=new CountDownLatch(1); Platform.startup(ready::countDown);
        assertTrue(ready.await(15,TimeUnit.SECONDS));
    }
    @AfterAll public static void stopToolkit() { Platform.exit(); }
    private static void renderEmptyDashboard() throws Exception {
        HomeController controller=new HomeController();
        for(String name:new String[]{"barStock","barMasMovidos","barActividadSemanal","barTendenciaTrimestral"}) {
            Field f=HomeController.class.getDeclaredField(name); f.setAccessible(true); f.set(controller,new BarChart<String,Number>(new CategoryAxis(),new NumberAxis()));
        }
        Field line=HomeController.class.getDeclaredField("lineMovimientos"); line.setAccessible(true); line.set(controller,new LineChart<String,Number>(new CategoryAxis(),new NumberAxis()));
        for(String name:new String[]{"pieStock","pieTipoTransaccion"}) {
            Field f=HomeController.class.getDeclaredField(name); f.setAccessible(true); f.set(controller,new PieChart());
        }
        Class<?> dataType=Class.forName(HomeController.class.getName()+"$DashboardData");
        Constructor<?> constructor=dataType.getDeclaredConstructor(); constructor.setAccessible(true);
        Object data=constructor.newInstance();
        Method update=HomeController.class.getDeclaredMethod("updateCharts",dataType); update.setAccessible(true); update.invoke(controller,data);
    }
    @Test public void emptyDashboardCollectionsRenderWithoutNullPointer() throws Exception {
        FutureTask<Void> task=new FutureTask<>(()->{ renderEmptyDashboard(); return null; });
        Platform.runLater(task); task.get(15,TimeUnit.SECONDS);
    }
    @FunctionalInterface interface FxOperation { void run() throws Exception; }
    private void withSession(FxOperation operation) throws Exception {
        FutureTask<Void> task=new FutureTask<>(()->{
            try(Connection connection=DriverManager.getConnection("jdbc:sqlite::memory:")) {
                try(Statement statement=connection.createStatement()) {
                    statement.execute("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT,status TEXT,rol_id INTEGER,session_token TEXT,session_expires_at TEXT)");
                    statement.execute("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,status TEXT,activo INTEGER)");
                    statement.execute("INSERT INTO roles VALUES(1,'ROOT','active',1),(2,'QA1_LECTOR','active',1)");
                    statement.execute("CREATE TABLE permisos(id INTEGER PRIMARY KEY,modulo TEXT,accion TEXT)");
                    statement.execute("INSERT INTO permisos VALUES(1,'DASHBOARD','READ'),(2,'INVENTARIO','READ'),(3,'INGRESOS','READ')");
                    statement.execute("CREATE TABLE rol_permisos(rol_id INTEGER,permiso_id INTEGER)");
                    statement.execute("INSERT INTO rol_permisos VALUES(2,1),(2,2)");
                    statement.execute("CREATE TABLE transactions(id INTEGER PRIMARY KEY,type TEXT,status TEXT,total_items INTEGER)");
                    statement.execute("INSERT INTO transactions VALUES(1,'in','active',1),(2,'out','active',1),(3,'loan','active',1),(4,'in','invalidated',1)");
                    statement.execute("INSERT INTO users(id,username,status,rol_id) VALUES(1,'primero','active',1),(2,'segundo','active',2)");
                }
                Base.attach(connection);
                try { Session.setCurrentUser(User.findById(1)); operation.run(); }
                finally { Session.setCurrentUser(null); Base.detach(); }
            }
            return null;
        });
        Platform.runLater(task); task.get(20,TimeUnit.SECONDS);
    }
    private Object get(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private void set(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
    private boolean current(HomeController controller) throws Exception {
        Method method=HomeController.class.getDeclaredMethod("isSessionCurrent"); method.setAccessible(true); return (boolean)method.invoke(controller);
    }
    @Test public void logoutAndReloginCannotReactivateOldDashboardEvenForSameUserObject() throws Exception {
        withSession(()->{
            User original=Session.getCurrentUser(); HomeController controller=new HomeController();
            assertTrue(current(controller));
            Session.setCurrentUser(original); assertTrue(current(controller));
            Session.setCurrentUser(null); assertFalse(current(controller));
            Session.setCurrentUser(original); assertFalse(current(controller));
        });
    }
    @Test public void anotherUserCannotReceiveThePreviousDashboardsResults() throws Exception {
        withSession(()->{
            HomeController controller=new HomeController();
            Session.setCurrentUser(User.findById(2)); assertFalse(current(controller));
            Method load=HomeController.class.getDeclaredMethod("loadDashboardAsync"); load.setAccessible(true); load.invoke(controller);
            assertFalse(controller.isLoading()); assertNull(get(controller,"activeTask"));
        });
    }
    @Test public void shutdownCancelsTaskSchedulerAndCompletionCallback() throws Exception {
        withSession(()->{
            HomeController controller=new HomeController(); AtomicBoolean callback=new AtomicBoolean();
            controller.setOnLoaded(()->callback.set(true));
            CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
            Task<Object> worker=new Task<>() { @Override protected Object call() throws Exception { started.countDown(); release.await(); return new Object(); } };
            ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
            set(controller,"activeTask",worker); set(controller,"refreshScheduler",scheduler);
            ((AtomicBoolean)get(controller,"loading")).set(true);
            Thread thread=new Thread(worker,"dashboard-cancellation-test"); thread.setDaemon(true); thread.start();
            try {
                assertTrue(started.await(5,TimeUnit.SECONDS)); controller.shutdown(); controller.shutdown();
                assertTrue(worker.isCancelled()); assertTrue(scheduler.isShutdown()); assertFalse(controller.isLoading()); assertFalse(current(controller));
                Method notify=HomeController.class.getDeclaredMethod("notifyLoaded"); notify.setAccessible(true); notify.invoke(controller); assertFalse(callback.get());
            } finally { release.countDown(); scheduler.shutdownNow(); }
        });
    }
    @Test public void mainControllerDiscardsOldViewsOnNavigationAndLogout() throws Exception {
        withSession(()->{
            MainController main=new MainController(); HomeController home=new HomeController();
            set(main,"currentController",home);
            Method canDisplay=MainController.class.getDeclaredMethod("canDisplayView",Object.class); canDisplay.setAccessible(true);
            assertTrue((boolean)canDisplay.invoke(main,home));
            Object anotherView=new Object(); set(main,"currentController",anotherView);
            assertFalse((boolean)canDisplay.invoke(main,home));
            set(main,"currentController",home); main.shutdown();
            assertFalse((boolean)canDisplay.invoke(main,home)); assertFalse(current(home));
        });
    }
    @Test public void dashboardReaderWithoutTransactionPermissionsDoesNotRenderOrFetchDetails() throws Exception {
        withSession(()->{
            Session.setCurrentUser(User.findById(2));
            HomeController controller=new HomeController();
            javafx.scene.control.TableView<com.joshuamc1.sicam.models.Transaction> table=new javafx.scene.control.TableView<>();
            set(controller,"tblTransacciones",table);
            var transactions=new java.util.ArrayList<>(com.joshuamc1.sicam.models.Transaction.<com.joshuamc1.sicam.models.Transaction>findAll());
            Method update=HomeController.class.getDeclaredMethod("updateTable",java.util.List.class); update.setAccessible(true);
            update.invoke(controller,transactions);
            assertTrue(table.getItems().isEmpty());
            assertTrue(((javafx.scene.control.Label)table.getPlaceholder()).getText().contains("permisos suficientes"));
            Method items=HomeController.class.getDeclaredMethod("getTransactionItemsCached",com.joshuamc1.sicam.models.Transaction.class); items.setAccessible(true);
            for(var transaction:transactions) assertTrue(((java.util.List<?>)items.invoke(controller,transaction)).isEmpty());
        });
    }
    @Test public void dashboardFiltersTypesAndInvalidatedRowsAndRespectsPermissionRevocation() throws Exception {
        withSession(()->{
            Session.setCurrentUser(User.findById(2));
            Base.exec("INSERT INTO rol_permisos VALUES(2,3)");
            HomeController controller=new HomeController();
            javafx.scene.control.TableView<com.joshuamc1.sicam.models.Transaction> table=new javafx.scene.control.TableView<>();
            set(controller,"tblTransacciones",table);
            var transactions=new java.util.ArrayList<>(com.joshuamc1.sicam.models.Transaction.<com.joshuamc1.sicam.models.Transaction>findAll());
            Method update=HomeController.class.getDeclaredMethod("updateTable",java.util.List.class); update.setAccessible(true);
            update.invoke(controller,transactions);
            assertEquals(java.util.List.of(1),table.getItems().stream().map(t->t.getInteger("id")).collect(java.util.stream.Collectors.toList()));
            Base.exec("DELETE FROM rol_permisos WHERE rol_id=2 AND permiso_id=3");
            Method items=HomeController.class.getDeclaredMethod("getTransactionItemsCached",com.joshuamc1.sicam.models.Transaction.class); items.setAccessible(true);
            assertTrue(((java.util.List<?>)items.invoke(controller,table.getItems().get(0))).isEmpty());
            update.invoke(controller,transactions); assertTrue(table.getItems().isEmpty());
        });
    }

    @Test public void initialViewUsesFirstAuthorizedModuleAndFallsBackToProfile() throws Exception {
        withSession(()->{
            Session.setCurrentUser(User.findById(2));
            MainController controller=new MainController();
            Method initial=MainController.class.getDeclaredMethod("initialView"); initial.setAccessible(true);
            assertEquals("/fxml/dashboard/home.fxml",initial.invoke(controller));
            Base.exec("DELETE FROM rol_permisos WHERE rol_id=2 AND permiso_id=1");
            assertEquals("/fxml/dashboard/inventory.fxml",initial.invoke(controller));
            Base.exec("DELETE FROM rol_permisos WHERE rol_id=2");
            assertEquals("/fxml/dashboard/profile.fxml",initial.invoke(controller));
        });
    }

}
