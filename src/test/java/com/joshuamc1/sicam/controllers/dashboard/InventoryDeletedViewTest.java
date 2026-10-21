package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.models.Product;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryDeletedViewTest {
    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch started=new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(15,TimeUnit.SECONDS));
    }
    @AfterAll static void stopToolkit() { Platform.exit(); }
    private void set(Object target,String name,Object value) throws Exception {
        var field=InventoryController.class.getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
    private void apply(InventoryController controller) throws Exception {
        var method=InventoryController.class.getDeclaredMethod("applyFiltersAndPagination"); method.setAccessible(true); method.invoke(controller);
    }
    private void onFx(CheckedRunnable operation) throws Exception {
        FutureTask<Void> task=new FutureTask<>(()->{
            try(Connection connection=DriverManager.getConnection("jdbc:sqlite::memory:")) {
                try(Statement s=connection.createStatement()) {
                    s.execute("CREATE TABLE products(id INTEGER PRIMARY KEY,code TEXT,name TEXT,description TEXT,unit TEXT,current_stock REAL,minimum_stock REAL,status TEXT,deleted_at TIMESTAMP)");
                    s.execute("INSERT INTO products VALUES(1,'ACT','Activo','','unidad',10,2,'active',NULL),(2,'INA','Inactivo','','unidad',0,2,'inactive',NULL),(3,'DEL','Eliminado','','unidad',0,2,'deleted','2026-10-20 12:00:00')");
                }
                Base.attach(connection);
                try { operation.run(); } finally { Base.detach(); }
            }
            return null;
        });
        Platform.runLater(task); task.get(20,TimeUnit.SECONDS);
    }
    @FunctionalInterface interface CheckedRunnable { void run() throws Exception; }
    @Test void deletedProductsAreSeparateAndRequireViewPermission() throws Exception {
        onFx(()->{
            var controller=new InventoryController();
            TableView<Product> table=new TableView<>();
            set(controller,"tblProducts",table); set(controller,"txtSearch",new TextField());
            List<Product> products=new ArrayList<>(Product.findAll());
            set(controller,"allProducts",products); set(controller,"canViewDeleted",true);
            apply(controller);
            assertEquals(List.of(2,1),table.getItems().stream().map(p->p.getInteger("id")).collect(java.util.stream.Collectors.toList()));
            set(controller,"showDeleted",true); apply(controller);
            assertEquals(1,table.getItems().size()); assertTrue(table.getItems().get(0).isDeleted());
            set(controller,"canViewDeleted",false); apply(controller);
            assertTrue(table.getItems().isEmpty());
        });
    }
    @Test void deletedBadgeIsDistinctFromInactiveAndActive() throws Exception {
        onFx(()->{
            var controller=new InventoryController(); TableView<Product> table=new TableView<>();
            set(controller,"tblProducts",table); set(controller,"lblBadge",new Label()); set(controller,"txtSearch",new TextField());
            TableColumn<Product,String> status=new TableColumn<>(); set(controller,"colStatus",status);
            for(String name:List.of("colId","colCode","colName","colDescription","colUnit","colStock","colMinStock","colStockLevel","colActions")) set(controller,name,new TableColumn<>());
            var setup=InventoryController.class.getDeclaredMethod("setupTable"); setup.setAccessible(true); setup.invoke(controller);
            TableCell<Product,String> cell=status.getCellFactory().call(status);
            cell.updateTableView(table); cell.updateTableColumn(status); cell.updateIndex(0);
            var update=cell.getClass().getDeclaredMethod("updateItem",String.class,boolean.class); update.setAccessible(true);
            String[] labels={"Activo","Inactivo","Eliminado"}, colors={"#4CAF50","#9E9E9E","#D32F2F"};
            for(int i=0;i<3;i++) {
                Product product=Product.findById(i+1); table.getItems().setAll(product);
                update.invoke(cell,product.getString("status"),false);
                Label badge=(Label)cell.getGraphic(); assertEquals(labels[i],badge.getText()); assertTrue(badge.getStyle().contains(colors[i]));
            }
        });
    }
    @Test void idSortAppliesToAllPagesAndSearchInsteadOfOnlyVisibleRows() throws Exception {
        onFx(()->{
            for(int id:new int[]{10,20,100,11200}) Base.exec("INSERT INTO products(id,code,name,status,current_stock,minimum_stock) VALUES(?,?,?,'active',0,0)",id,"QA-"+id,"Producto "+id);
            var controller=new InventoryController(); TableView<Product> table=new TableView<>();
            set(controller,"tblProducts",table); set(controller,"lblBadge",new Label()); set(controller,"txtSearch",new TextField());
            TextField search=new TextField(); set(controller,"txtSearch",search);
            TableColumn<Product,Integer> idColumn=new TableColumn<>(); set(controller,"colId",idColumn); table.getColumns().add(idColumn);
            for(String name:List.of("colStatus","colCode","colName","colDescription","colUnit","colStock","colMinStock","colStockLevel","colActions")) set(controller,name,new TableColumn<>());
            var setup=InventoryController.class.getDeclaredMethod("setupTable"); setup.setAccessible(true); setup.invoke(controller);
            set(controller,"allProducts",new ArrayList<>(Product.findAll())); set(controller,"pageSize",2);
            apply(controller);
            assertEquals(List.of(11200,100),ids(table));
            idColumn.setSortType(TableColumn.SortType.ASCENDING); table.getSortOrder().setAll(idColumn);
            assertEquals(List.of(1,2),ids(table));
            set(controller,"currentPage",2); apply(controller); assertEquals(List.of(10,20),ids(table));
            set(controller,"currentPage",3); apply(controller); assertEquals(List.of(100,11200),ids(table));
            idColumn.setSortType(TableColumn.SortType.DESCENDING);
            assertEquals(List.of(11200,100),ids(table));
            set(controller,"currentPage",3); apply(controller); assertEquals(List.of(2,1),ids(table));
            search.setText("QA-"); set(controller,"currentPage",1); apply(controller); assertEquals(List.of(11200,100),ids(table));
            set(controller,"currentPage",2); apply(controller); assertEquals(List.of(20,10),ids(table));
        });
    }
    private List<Integer> ids(TableView<Product> table) {
        return table.getItems().stream().map(p->p.getInteger("id")).collect(java.util.stream.Collectors.toList());
    }

}
