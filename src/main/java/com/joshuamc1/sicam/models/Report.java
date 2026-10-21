package com.joshuamc1.sicam.models;

import java.util.ArrayList;
import java.util.List;

public class Report {
    private final String title;
    private String groupByColumn;
    private boolean landscape;
    private final List<String> headers = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    public Report(String title) {
        this.title = title;
    }

    public boolean isLandscape() { return landscape; }

    public void setLandscape(boolean landscape) { this.landscape = landscape; }

    public String getTitle() {
        return title;
    }

    /** Columna que identifica las tablas del PDF; se conserva en exportaciones tabulares. */
    public String getGroupByColumn() { return groupByColumn; }

    public void setGroupByColumn(String column) {
        if (!headers.contains(column)) throw new IllegalArgumentException("Columna de agrupación inexistente");
        groupByColumn = column;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void addHeader(String h) {
        this.headers.add(h);
    }

    public void addRow(List<String> row) {
        this.rows.add(row);
    }
}

