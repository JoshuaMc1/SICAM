package com.joshuamc1.sicam.core;

import java.util.*;

/** Orden solicitado por la interfaz, traducido solo mediante columnas SQL permitidas. */
public final class PageSort {
    public static final PageSort DEFAULT = new PageSort(List.of());
    public static final class Key {
        final String name;
        final boolean descending;
        public Key(String name, boolean descending) { this.name=Objects.requireNonNull(name); this.descending=descending; }
    }
    private final List<Key> keys;
    public PageSort(List<Key> keys) { this.keys=List.copyOf(keys); }
    public String toSql(Map<String,String> allowed, String fallback) {
        List<String> parts=new ArrayList<>();
        for(Key key:keys) {
            String column=allowed.get(key.name);
            if(column==null) throw new IllegalArgumentException("Columna de orden no permitida: " + key.name);
            parts.add(column + (key.descending ? " DESC" : " ASC"));
        }
        parts.add(fallback);
        return String.join(", ",parts);
    }
}
