package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.models.Product;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Servicio para importar inventario desde CSV o Excel y ofrecer vista previa.
 * Soporta columnas: code, name, description, unit, current_stock, minimum_stock
 */
public class InventoryImportService {
    private final ProductService productService = new ProductService();

    public List<Product> parseFileForPreview(File file) throws Exception {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return parseCsv(file);
        } else if (name.endsWith(".xlsx")) {
            return parseXlsx(file);
        } else {
            throw new IllegalArgumentException("Formato no soportado: " + file.getName());
        }
    }

    private List<Product> parseCsv(File file) throws Exception {
        List<Product> items = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) throw new IllegalArgumentException("CSV vacío");
            List<String> headerCols = parseCsvLine(header);
            Map<String, Integer> idx = this.mapColumns(headerCols.toArray(new String[0]));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> parts = parseCsvLine(line);
                Product p = new Product();
                String code = get(parts, idx.get("code"));
                if (code != null) code = code.trim();
                p.set("code", (code == null || code.isEmpty()) ? null : code);
                p.set("name", get(parts, idx.get("name")));
                p.set("description", get(parts, idx.get("description")));
                p.set("unit", get(parts, idx.get("unit")));
                p.set("current_stock", parseDouble(get(parts, idx.get("current_stock"))));
                p.set("minimum_stock", parseDouble(get(parts, idx.get("minimum_stock"))));
                items.add(p);
            }
        }
        return items;
    }

    private List<Product> parseXlsx(File file) throws Exception {
        List<Product> items = new ArrayList<>();
        try (InputStream in = new FileInputStream(file); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext()) throw new IllegalArgumentException("Excel vacío");
            Row header = it.next();
            Map<String, Integer> idx = mapColumns(header);
            while (it.hasNext()) {
                Row r = it.next();
                Product p = new Product();
                String code = getCell(r, idx.get("code"));
                if (code != null) code = code.trim();
                p.set("code", (code == null || code.isEmpty()) ? null : code);
                p.set("name", getCell(r, idx.get("name")));
                p.set("description", getCell(r, idx.get("description")));
                p.set("unit", getCell(r, idx.get("unit")));
                p.set("current_stock", parseDouble(getCell(r, idx.get("current_stock"))));
                p.set("minimum_stock", parseDouble(getCell(r, idx.get("minimum_stock"))));
                items.add(p);
            }
        }
        return items;
    }

    private Map<String, Integer> mapColumns(String[] headers) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i] == null ? null : headers[i].trim().toLowerCase(Locale.ROOT);
            if (h != null && !h.isEmpty()) idx.put(h, i);
        }
        requireColumns(idx, "name", "unit", "current_stock", "minimum_stock");
        // code y description opcionales
        idx.putIfAbsent("code", -1);
        idx.putIfAbsent("description", -1);
        return idx;
    }

    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String h = headerRow.getCell(i) != null ? headerRow.getCell(i).getStringCellValue() : null;
            if (h != null) idx.put(h.trim().toLowerCase(Locale.ROOT), i);
        }
        requireColumns(idx, "name", "unit", "current_stock", "minimum_stock");
        idx.putIfAbsent("code", -1);
        idx.putIfAbsent("description", -1);
        return idx;
    }

    private void requireColumns(Map<String, Integer> idx, String... required) {
        for (String r : required) {
            if (!idx.containsKey(r)) {
                throw new IllegalArgumentException("Faltan columnas requeridas: " + Arrays.toString(required));
            }
        }
    }

    private String get(String[] arr, Integer i) {
        if (i == null || i < 0 || i >= arr.length) return null;
        String v = arr[i];
        return v != null ? v.trim() : null;
    }

    private String get(List<String> arr, Integer i) {
        if (i == null || i < 0 || i >= arr.size()) return null;
        String v = arr.get(i);
        return v != null ? v.trim() : null;
    }

    private String getCell(Row r, Integer i) {
        if (i == null || i < 0 || r.getCell(i) == null) return null;
        switch (r.getCell(i).getCellType()) {
            case STRING: return r.getCell(i).getStringCellValue().trim();
            case NUMERIC: return String.valueOf(r.getCell(i).getNumericCellValue());
            case BOOLEAN: return String.valueOf(r.getCell(i).getBooleanCellValue());
            default: return null;
        }
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return 0d;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0d; }
    }

    public void importItems(List<Product> items) {
        int inserted = 0, updated = 0, skipped = 0;
        for (Product p : items) {
            try {
                String code = p.getString("code");
                if (code != null) code = code.trim();
                if (code != null && code.isEmpty()) code = null;

                String name = p.getString("name");
                if (name == null || name.isBlank()) { skipped++; continue; }
                Product existing = null;
                if (code != null && !code.isBlank()) {
                    existing = productService.findByCode(code);
                }
                if (existing != null) {
                    String newDesc = p.getString("description");
                    if (newDesc != null && !newDesc.isBlank()) {
                        existing.set("description", newDesc);
                    }
                    existing.set("unit", p.getString("unit"));
                    existing.set("minimum_stock", p.getDouble("minimum_stock"));
                    existing.set("current_stock", p.getDouble("current_stock"));
                    productService.save(existing);
                    updated++;
                } else {
                    Product created = productService.createProduct(
                            name,
                            p.getString("description"),
                            p.getString("unit"),
                            Optional.ofNullable(p.getDouble("current_stock")).orElse(0d),
                            Optional.ofNullable(p.getDouble("minimum_stock")).orElse(0d)
                    );
                    if (code != null && !code.isBlank()) { created.set("code", code); productService.save(created); }
                    inserted++;
                }
            } catch (Exception ex) {
                Logs.error(getClass(), "Error importando producto: {}", ex.getMessage());
                skipped++;
            }
        }
        Logs.info(getClass(), "Importación completada. Insertados: {} Actualizados: {} Omitidos: {}", inserted, updated, skipped);
    }

    /**
     * Parser CSV simple compatible con campos entrecomillados (dobles comillas) y comas internas.
     * Reglas básicas RFC 4180: "" dentro de campo representa comilla escapada.
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        if (line == null) return result;
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    // Doble comilla dentro de comillas => comilla escapada
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++; // saltar la siguiente
                    } else {
                        inQuotes = false; // fin de campo entrecomillado
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    result.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
        }
        result.add(cur.toString());
        return result;
    }
}
