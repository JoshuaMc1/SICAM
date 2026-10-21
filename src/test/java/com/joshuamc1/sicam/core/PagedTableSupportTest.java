package com.joshuamc1.sicam.core;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.*;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PagedTableSupportTest {
    @BeforeAll static void start() throws Exception {
        CountDownLatch ready=new CountDownLatch(1); Platform.startup(ready::countDown);
        assertTrue(ready.await(15,TimeUnit.SECONDS));
    }
    @AfterAll static void stop() { Platform.exit(); }
    private void fx(Runnable operation) throws Exception {
        FutureTask<Void> task=new FutureTask<>(()->{ operation.run(); return null; });
        Platform.runLater(task); task.get(20,TimeUnit.SECONDS);
    }
    private static class Pager {
        final TableView<Integer> table=new TableView<>();
        final TableColumn<Integer,Integer> id=new TableColumn<>("ID");
        final ObservableList<Integer> visible=FXCollections.observableArrayList();
        final List<Integer> source=List.of(20,1,11200,2,100,10);
        final PagedTableSupport<Integer> order;
        int requests;
        int page=1;
        Pager() {
            id.setCellValueFactory(c->new SimpleObjectProperty<>(c.getValue())); table.getColumns().add(id);
            order=new PagedTableSupport<>(table,()->{ requests++; show(1,source); });
        }
        void show(int requested,List<Integer> filtered) {
            page=requested;
            List<Integer> all=new ArrayList<>(filtered); all.sort(order.comparator(Comparator.reverseOrder()));
            int offset=(page-1)*2;
            order.publish(visible,all.subList(Math.min(offset,all.size()),Math.min(offset+2,all.size())));
        }
    }
    @Test void sortingIsDelegatedAndPagePublishingNeverSortsOnlyVisibleRows() throws Exception {
        fx(()->{
            Pager pager=new Pager(); assertEquals(0,pager.requests);
            pager.show(1,pager.source); assertEquals(List.of(11200,100),pager.visible);
            pager.id.setSortType(TableColumn.SortType.ASCENDING); pager.table.getSortOrder().setAll(pager.id);
            assertEquals(1,pager.requests); assertEquals(1,pager.page); assertEquals(List.of(1,2),pager.visible);
            pager.show(2,pager.source); assertEquals(List.of(10,20),pager.visible);
            pager.show(3,pager.source); assertEquals(List.of(100,11200),pager.visible);
            int previous=pager.requests;
            pager.id.setSortType(TableColumn.SortType.DESCENDING);
            assertEquals(previous+1,pager.requests); assertEquals(1,pager.page);
            assertEquals(List.of(11200,100),pager.visible);
            pager.show(3,pager.source); assertEquals(List.of(2,1),pager.visible);
            assertEquals(previous+1,pager.requests,"Publicar una página no debe recargar ni volver a ordenar solo esa página");
        });
    }
    @Test void searchAndRefreshKeepOrderAndClearingSortRestoresNewestFirst() throws Exception {
        fx(()->{
            Pager pager=new Pager(); pager.id.setSortType(TableColumn.SortType.ASCENDING);
            pager.table.getSortOrder().setAll(pager.id);
            pager.show(1,List.of(11200,10,100)); assertEquals(List.of(10,100),pager.visible);
            pager.show(2,List.of(11200,10,100)); assertEquals(List.of(11200),pager.visible);
            pager.show(1,pager.source); assertEquals(List.of(1,2),pager.visible);
            pager.table.getSortOrder().clear(); assertEquals(List.of(11200,100),pager.visible);
        });
    }
    @Test void sqlSortSnapshotKeepsNumericDirectionAndSupportsMultipleColumns() throws Exception {
        fx(()->{
            Pager pager=new Pager();
            TableColumn<Integer,Integer> other=new TableColumn<>("Otro"); other.setCellValueFactory(c->new SimpleObjectProperty<>(c.getValue()));
            pager.table.getColumns().add(other);
            pager.id.setSortType(TableColumn.SortType.ASCENDING); other.setSortType(TableColumn.SortType.DESCENDING);
            pager.table.getSortOrder().setAll(pager.id,other);
            PageSort snapshot=pager.order.sqlOrder(Map.of(pager.id,"id",other,"other"));
            pager.table.getSortOrder().clear();
            assertEquals("t.id ASC, t.other DESC, t.id DESC",snapshot.toSql(Map.of("id","t.id","other","t.other"),"t.id DESC"));
        });
    }
}
