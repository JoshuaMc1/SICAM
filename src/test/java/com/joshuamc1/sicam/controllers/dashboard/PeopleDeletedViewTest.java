package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.models.Person;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PeopleDeletedViewTest {
    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch started=new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(15,TimeUnit.SECONDS));
    }
    @AfterAll static void stopToolkit() { Platform.exit(); }
    private void set(Object target,String name,Object value) throws Exception {
        var field=PeopleController.class.getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
    private void onFx(CheckedRunnable operation) throws Exception {
        FutureTask<Void> task=new FutureTask<>(()->{
            try(Connection connection=DriverManager.getConnection("jdbc:sqlite::memory:")) {
                try(Statement s=connection.createStatement()) {
                    s.execute("CREATE TABLE people(id INTEGER PRIMARY KEY,dni TEXT,first_name TEXT,last_name TEXT,internal_code TEXT,status TEXT,deleted_at TIMESTAMP)");
                    s.execute("INSERT INTO people VALUES(1,'ACT','Activo',NULL,NULL,'active',NULL),(10,'INA','Inactivo',NULL,NULL,'inactive',NULL),(1130,'DEL','Eliminado',NULL,'QA1','deleted','2026-10-20 12:00:00')");
                }
                Base.attach(connection);
                try { operation.run(); } finally { Base.detach(); }
            }
            return null;
        });
        Platform.runLater(task); task.get(20,TimeUnit.SECONDS);
    }
    @FunctionalInterface interface CheckedRunnable { void run() throws Exception; }
    @Test void separateDeletedPeopleAndKeepNumericOrderAndSearch() throws Exception {
        onFx(()->{
            List<Person> people=new ArrayList<>(Person.findAll());
            assertEquals(List.of(10,1),PeopleController.filterPeople(people,false,"").stream().map(p->p.getInteger("id")).collect(java.util.stream.Collectors.toList()));
            assertEquals(List.of(1130),PeopleController.filterPeople(people,true,"qa1").stream().map(p->p.getInteger("id")).collect(java.util.stream.Collectors.toList()));
            assertTrue(PeopleController.filterPeople(people,false,"qa1").isEmpty());
        });
    }
    @Test void deletedBadgeIsDistinctFromInactiveAndActive() throws Exception {
        onFx(()->{
            var controller=new PeopleController(); TableView<Person> table=new TableView<>();
            set(controller,"tblPeople",table);
            TableColumn<Person,String> status=new TableColumn<>(); set(controller,"colStatus",status);
            var setup=PeopleController.class.getDeclaredMethod("configureStatusColumn"); setup.setAccessible(true); setup.invoke(controller);
            TableCell<Person,String> cell=status.getCellFactory().call(status);
            cell.updateTableView(table); cell.updateTableColumn(status); cell.updateIndex(0);
            var update=cell.getClass().getDeclaredMethod("updateItem",String.class,boolean.class); update.setAccessible(true);
            String[] labels={"Activo","Inactivo","Eliminado"}, colors={"#4CAF50","#9E9E9E","#D32F2F"};
            for(int i=0;i<3;i++) {
                Person person=Person.findById(new int[]{1,10,1130}[i]); table.getItems().setAll(person);
                update.invoke(cell,person.getString("status"),false);
                Label badge=(Label)cell.getGraphic(); assertEquals(labels[i],badge.getText()); assertTrue(badge.getStyle().contains(colors[i]));
            }
        });
    }
}
