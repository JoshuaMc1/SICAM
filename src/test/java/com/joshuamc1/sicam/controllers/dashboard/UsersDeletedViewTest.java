package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.models.User;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class UsersDeletedViewTest {
    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch started=new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(15,TimeUnit.SECONDS));
    }
    @AfterAll static void stopToolkit() { Platform.exit(); }
    private void set(Object target,String name,Object value) throws Exception {
        var field=UsersController.class.getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
    private void onFx(CheckedRunnable operation) throws Exception {
        FutureTask<Void> task=new FutureTask<>(()->{
            try(Connection connection=DriverManager.getConnection("jdbc:sqlite::memory:")) {
                try(Statement s=connection.createStatement()) {
                    s.execute("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT,name TEXT,status TEXT,deleted_at TIMESTAMP,session_token TEXT,session_expires_at TEXT)");
                    s.execute("INSERT INTO users(id,username,name,status,deleted_at) VALUES(1,'ACT','Activo','active',NULL),(10,'INA','Inactivo','inactive',NULL),(1130,'QA1','Eliminado','deleted','2026-10-20 12:00:00'),(2000,'SYSTEM','Sistema','active',NULL),(2001,'SYSTEM','Sistema eliminado','deleted','2026-10-20 12:00:00')");
                }
                Base.attach(connection);
                try { operation.run(); } finally { Base.detach(); }
            }
            return null;
        });
        Platform.runLater(task); task.get(20,TimeUnit.SECONDS);
    }
    @FunctionalInterface interface CheckedRunnable { void run() throws Exception; }
    @Test void separateDeletedUsersAndKeepNumericOrderAndSearch() throws Exception {
        onFx(()->{
            List<User> users=new ArrayList<>(User.findAll());
            assertEquals(List.of(10,1),UsersController.filterUsers(users,false,"").stream().map(p->p.getInteger("id")).collect(java.util.stream.Collectors.toList()));
            assertEquals(List.of(1130),UsersController.filterUsers(users,true,"qa1").stream().map(p->p.getInteger("id")).collect(java.util.stream.Collectors.toList()));
            assertTrue(UsersController.filterUsers(users,false,"qa1").isEmpty());
        });
    }
    @Test void deletedBadgeIsDistinctFromInactiveAndActive() throws Exception {
        onFx(()->{
            var controller=new UsersController(); TableView<User> table=new TableView<>();
            set(controller,"tblUsers",table);
            TableColumn<User,String> status=new TableColumn<>(); set(controller,"colStatus",status);
            var setup=UsersController.class.getDeclaredMethod("configureStatusColumn"); setup.setAccessible(true); setup.invoke(controller);
            TableCell<User,String> cell=status.getCellFactory().call(status);
            cell.updateTableView(table); cell.updateTableColumn(status); cell.updateIndex(0);
            var update=cell.getClass().getDeclaredMethod("updateItem",String.class,boolean.class); update.setAccessible(true);
            String[] labels={"Activo","Inactivo","Eliminado"}, colors={"#4CAF50","#9E9E9E","#D32F2F"};
            for(int i=0;i<3;i++) {
                User user=User.findById(new int[]{1,10,1130}[i]); table.getItems().setAll(user);
                update.invoke(cell,user.getString("status"),false);
                Label badge=(Label)cell.getGraphic(); assertEquals(labels[i],badge.getText()); assertTrue(badge.getStyle().contains(colors[i]));
            }
        });
    }
    @Test void rootAccountsAreExcludedForOtherRolesInBothListsAndSearch() throws Exception {
        onFx(()->{
            Base.exec("ALTER TABLE users ADD COLUMN rol_id INTEGER");
            Base.exec("UPDATE users SET rol_id=2");
            Base.exec("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,status TEXT,activo INTEGER)");
            Base.exec("INSERT INTO roles VALUES(1,'ROOT','active',1),(2,'AUDITOR','active',1)");
            Base.exec("CREATE TABLE permisos(id INTEGER PRIMARY KEY,modulo TEXT,accion TEXT)");
            Base.exec("INSERT INTO permisos VALUES(1,'USUARIOS','READ'),(2,'SISTEMA','VIEW_DELETED')");
            Base.exec("CREATE TABLE rol_permisos(rol_id INTEGER,permiso_id INTEGER)");
            Base.exec("INSERT INTO rol_permisos VALUES(2,1),(2,2)");
            Base.exec("INSERT INTO users(id,username,name,status,rol_id) VALUES(3000,'root_test','Root','active',1),(3001,'root_deleted','Root eliminado','deleted',1)");
            var service=new com.joshuamc1.sicam.services.UserService();
            try {
                com.joshuamc1.sicam.core.Session.setCurrentUser(User.findById(1));
                assertFalse(service.findAll().stream().anyMatch(User::isRootRole));
                assertFalse(service.findDeleted().stream().anyMatch(User::isRootRole));
                assertFalse(service.findAllIncludingDeleted().stream().anyMatch(User::isRootRole));
                assertTrue(UsersController.filterUsers(service.findAll(),false,"root_test").isEmpty());
                com.joshuamc1.sicam.core.Session.setCurrentUser(User.findById(3000));
                assertTrue(service.findAll().stream().anyMatch(User::isRootRole));
                assertTrue(service.findDeleted().stream().anyMatch(User::isRootRole));
            } finally { com.joshuamc1.sicam.core.Session.setCurrentUser(null); }
        });
    }

}
