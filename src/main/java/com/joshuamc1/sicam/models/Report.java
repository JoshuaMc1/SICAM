package com.joshuamc1.sicam.models;

import java.util.ArrayList;
import java.util.List;

public class Report {
    private final String title;
    private final List<String> headers = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    public Report(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
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

