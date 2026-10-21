package com.joshuamc1.sicam.core;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import java.util.*;

/** Evita ordenar solo las filas visibles; el cargador aplica el orden antes de paginar. */
public final class PagedTableSupport<T> {
    private final TableView<T> table;
    private boolean publishing=true;
    public PagedTableSupport(TableView<T> table, Runnable reloadFirstPage) {
        this.table=table;
        table.setSortPolicy(view -> {
            if(!publishing) reloadFirstPage.run();
            return true;
        });
        publishing=false;
    }
    public Comparator<T> comparator(Comparator<T> fallback) {
        Comparator<T> order=table.getComparator();
        return order==null ? fallback : order.thenComparing(fallback);
    }
    public boolean hasOrder() { return !table.getSortOrder().isEmpty(); }
    public PageSort sqlOrder(Map<TableColumn<T,?>,String> fields) {
        List<PageSort.Key> keys=new ArrayList<>();
        for(TableColumn<T,?> column:table.getSortOrder()) {
            String field=fields.get(column);
            if(field!=null) keys.add(new PageSort.Key(field,column.getSortType()==TableColumn.SortType.DESCENDING));
        }
        return new PageSort(keys);
    }
    public void publish(ObservableList<T> target, Collection<T> rows) {
        publishing=true;
        try {
            List<TableColumn<T,?>> columns=new ArrayList<>(table.getSortOrder());
            target.setAll(rows);
            if(table.getItems()!=target) {
                table.setItems(target);
                if(!table.getSortOrder().equals(columns)) table.getSortOrder().setAll(columns);
            }
        } finally { publishing=false; }
    }
}
