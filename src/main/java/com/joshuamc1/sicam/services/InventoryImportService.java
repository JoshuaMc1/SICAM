package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.Authorization;
import com.joshuamc1.sicam.core.BarcodeGenerator;
import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.Product;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.javalite.activejdbc.Base;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Importación validada y atómica. La vista previa no escribe en la base de datos. */
public class InventoryImportService {
    public enum Mode {
        CREATE_ONLY("Solo crear"), UPDATE_ONLY("Solo actualizar"), UPSERT("Crear y actualizar");
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public static final class InputRow {
        public final int row;
        public final String code, name, description, unit;
        public final BigDecimal stock, minimum;
        public final List<String> errors;
        private InputRow(int row, String code, String name, String description, String unit,
                         BigDecimal stock, BigDecimal minimum, List<String> errors) {
            this.row=row; this.code=code; this.name=name; this.description=description; this.unit=unit;
            this.stock=stock; this.minimum=minimum; this.errors=List.copyOf(errors);
        }
    }
    public static final class PreviewRow {
        public final InputRow input;
        public final String action, detail;
        private final List<String> snapshot;
        private final Integer id;
        public boolean hasError() { return action.equals("Error"); }
        private PreviewRow(InputRow input, String action, String detail, Integer id, List<String> snapshot) {
            this.input=input; this.action=action; this.detail=detail; this.id=id; this.snapshot=snapshot;
        }
    }
    public static final class Plan {
        public final Mode mode;
        public final List<PreviewRow> rows;
        private final java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
        private Plan(Mode mode, List<PreviewRow> rows) { this.mode=mode; this.rows=List.copyOf(rows); }
        public boolean hasErrors() { return rows.stream().anyMatch(PreviewRow::hasError); }
        public long count(String action) { return rows.stream().filter(r -> r.action.equals(action)).count(); }
        public String summary() { return "Crear: " + count("Crear") + " | Actualizar: " + count("Actualizar")
            + " | Omitir: " + count("Omitir") + " | Errores: " + count("Error"); }
        public String report() {
            StringBuilder result=new StringBuilder(summary());
            for (PreviewRow r : rows) result.append("\nFila ").append(r.input.row).append(" — ")
                .append(r.input.name).append(": ").append(r.action).append(". ").append(r.detail);
            return result.toString();
        }
    }

    public List<InputRow> readFile(File file) throws Exception {
        String name=file.getName().toLowerCase(Locale.ROOT);
        List<List<String>> records=new ArrayList<>();
        List<Integer> numbers=new ArrayList<>();
        if (name.endsWith(".csv")) {
            String text=Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text=text.substring(1);
            char delimiter=detectDelimiter(text);
            readCsv(text, delimiter, records, numbers);
        } else if (name.endsWith(".xlsx")) {
            try (InputStream in=new FileInputStream(file); Workbook wb=new XSSFWorkbook(in)) {
                if (wb.getNumberOfSheets()==0) throw new IllegalArgumentException("Excel sin hojas");
                DataFormatter formatter=new DataFormatter(Locale.ROOT);
                int width = -1;
                for (Row row : wb.getSheetAt(0)) {
                    List<String> values=new ArrayList<>();
                    for (int c=0; c<row.getLastCellNum(); c++) {
                        Cell cell=row.getCell(c);
                        // No evaluar fórmulas ni permitir fórmulas en códigos o cantidades.
                        values.add(cell != null && cell.getCellType()==CellType.FORMULA ? "[FÓRMULA]" : formatter.formatCellValue(cell));
                    }
                    if (values.stream().allMatch(String::isBlank)) continue;
                    if (width < 0) width = values.size();
                    while (values.size() < width) values.add("");
                    records.add(values); numbers.add(row.getRowNum()+1);
                }
            }
        } else throw new IllegalArgumentException("Formato no soportado: " + name);
        if (records.isEmpty()) throw new IllegalArgumentException("Archivo vacío");
        Map<String,Integer> columns=new HashMap<>();
        List<String> header=records.get(0);
        for (int i=0; i<header.size(); i++) {
            String key=header.get(i).trim().toLowerCase(Locale.ROOT);
            if (columns.putIfAbsent(key,i)!=null) throw new IllegalArgumentException("Encabezado repetido: " + key);
        }
        for (String required : List.of("name","unit","current_stock","minimum_stock"))
            if (!columns.containsKey(required)) throw new IllegalArgumentException("Falta la columna requerida: " + required);
        List<InputRow> result=new ArrayList<>();
        Map<String,Integer> seen=new HashMap<>();
        for (int i=1; i<records.size(); i++) {
            List<String> values=records.get(i);
            List<String> errors=new ArrayList<>();
            if (values.size()!=header.size()) errors.add("Cantidad de columnas distinta al encabezado");
            String code=value(values,columns,"code"), productName=value(values,columns,"name"),
                unit=value(values,columns,"unit"), desc=value(values,columns,"description"), status=value(values,columns,"status");
            if (productName.isEmpty() || productName.length()>100) errors.add("Nombre requerido, máximo 100 caracteres");
            if (unit.isEmpty() || unit.length()>50) errors.add("Unidad requerida, máximo 50 caracteres");
            if (!code.isEmpty() && !BarcodeGenerator.isValidCode(code)) errors.add("Código inválido: use de 3 a 100 letras, números, guiones o guiones bajos");
            if (!code.isEmpty()) {
                Integer first=seen.putIfAbsent(code.toLowerCase(Locale.ROOT),numbers.get(i));
                if (first!=null) errors.add("Código repetido '"+code+"' en las filas "+first+" y "+numbers.get(i));
            }
            if (!status.isEmpty() && !status.equalsIgnoreCase("active")) errors.add("Solo se importan productos activos; el estado no se modifica por importación");
            BigDecimal stock=quantity(value(values,columns,"current_stock"),"Stock actual",errors);
            BigDecimal min=quantity(value(values,columns,"minimum_stock"),"Stock mínimo",errors);
            result.add(new InputRow(numbers.get(i),code,productName,desc,unit,stock,min,errors));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("El archivo no contiene productos");
        return List.copyOf(result);
    }
    private String value(List<String> values, Map<String,Integer> columns, String key) {
        Integer i=columns.get(key); return i==null || i>=values.size() ? "" : values.get(i).trim();
    }
    private BigDecimal quantity(String text,String field,List<String> errors) {
        try {
            // Coma decimal permitida; no se aceptan separadores de miles ni notación científica.
            if (!text.matches("[0-9]+([.,][0-9]{1,2})?")) throw new NumberFormatException();
            BigDecimal value=new BigDecimal(text.replace(',','.'));
            if (value.compareTo(new BigDecimal("99999999.99"))>0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { errors.add(field+": indique un número entre 0 y 99999999.99 con hasta dos decimales"); return null; }
    }
    private char detectDelimiter(String text) {
        boolean quoted=false; int commas=0, semicolons=0;
        for (int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if (c=='"') quoted=!quoted;
            if (!quoted && (c=='\n'||c=='\r')) break;
            if (!quoted && c==',') commas++;
            if (!quoted && c==';') semicolons++;
        }
        return semicolons>commas ? ';' : ',';
    }
    private void readCsv(String text,char delimiter,List<List<String>> records,List<Integer> numbers) {
        List<String> fields=new ArrayList<>(); StringBuilder field=new StringBuilder();
        boolean quoted=false, closed=false; int line=1, start=1;
        for (int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if (quoted) {
                if (c=='"') {
                    if (i+1<text.length() && text.charAt(i+1)=='"') { field.append('"'); i++; }
                    else { quoted=false; closed=true; }
                } else { field.append(c); if(c=='\n') line++; }
            } else if (c==delimiter) {
                fields.add(field.toString()); field.setLength(0); closed=false;
            } else if (c=='\n'||c=='\r') {
                fields.add(field.toString());
                if (fields.stream().anyMatch(v -> !v.isBlank())) { records.add(fields); numbers.add(start); }
                fields=new ArrayList<>(); field.setLength(0); closed=false;
                if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;
                line++; start=line;
            } else if (c=='"' && field.length()==0 && !closed) quoted=true;
            else if (c=='"' || (closed && !Character.isWhitespace(c))) throw new IllegalArgumentException("CSV mal formado en fila "+line);
            else if (!closed) field.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Comillas sin cerrar en fila "+start);
        fields.add(field.toString());
        if (fields.stream().anyMatch(v -> !v.isBlank())) { records.add(fields); numbers.add(start); }
    }

    public Plan preview(List<InputRow> inputs,Mode mode) {
        Authorization.require("SISTEMA","DESTRUCTIVE_OPERATIONS");
        return DatabaseManager.callInConnection(() -> {
            try { return buildPlan(inputs,mode,false); }
            catch (SQLException ex) { throw new IllegalStateException("No se pudo consultar el inventario",ex); }
        });
    }
    private Plan buildPlan(List<InputRow> inputs, Mode mode, boolean lock) throws SQLException {
        Objects.requireNonNull(mode,"Seleccione el modo de importación");
        if (inputs.isEmpty()) throw new IllegalArgumentException("No hay productos");
        List<PreviewRow> result=new ArrayList<>();
        String suffix=lock && Base.connection().getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL") ? " FOR UPDATE" : "";
        Set<String> codes = new HashSet<>();
        for (InputRow row : inputs) {
            String error=String.join("; ",row.errors);
            if (!row.code.isEmpty() && !codes.add(row.code.toLowerCase(Locale.ROOT)) && error.isEmpty()) error="Código repetido en la carga: "+row.code;
            Integer id=null; List<String> snapshot=null;
            if (error.isEmpty() && !row.code.isEmpty()) {
                try (PreparedStatement query=Base.connection().prepareStatement("SELECT id,name,description,unit,current_stock,minimum_stock,status FROM products WHERE LOWER(code)=LOWER(?)"+suffix)) {
                    query.setString(1,row.code);
                    try (ResultSet rs=query.executeQuery()) {
                        if (rs.next()) {
                            id=rs.getInt(1); snapshot=new ArrayList<>();
                            for (int c=2;c<=7;c++) snapshot.add(rs.getString(c));
                            if (!"active".equals(rs.getString("status"))) error="El código pertenece a un producto inactivo o eliminado. Debe gestionarlo desde Inventario";
                            if (rs.next()) error="El código identifica varios productos en la base de datos";
                        }
                    }
                }
            }
            String action=error.isEmpty() ? id==null ? "Crear" : "Actualizar" : "Error";
            String detail=error;
            if (action.equals("Crear") && mode==Mode.UPDATE_ONLY) { action="Omitir"; detail="No existe un producto con este código"; }
            if (action.equals("Actualizar") && mode==Mode.CREATE_ONLY) { action="Omitir"; detail="El código ya existe"; }
            if (action.equals("Crear")) detail=row.code.isEmpty() ? "Se generará un código único" : "Producto nuevo";
            if (action.equals("Actualizar")) detail="Nombre: "+snapshot.get(0)+" → "+row.name
                +" | Descripción: "+Objects.toString(snapshot.get(1),"")+" → "+(row.description.isEmpty()?Objects.toString(snapshot.get(1),""):row.description)
                +" | Unidad: "+snapshot.get(2)+" → "+row.unit+" | Stock: "+snapshot.get(3)+" → "+row.stock
                +" | Mínimo: "+snapshot.get(4)+" → "+row.minimum;
            result.add(new PreviewRow(row,action,detail,id,snapshot));
        }
        return new Plan(mode,result);
    }

    public Plan execute(Plan approved, BiConsumer<Integer,Integer> progress) {
        Authorization.require("SISTEMA","DESTRUCTIVE_OPERATIONS");
        if (approved.hasErrors()) throw new IllegalArgumentException("Corrija los errores del archivo antes de importar");
        if (approved.count("Crear")>0) Authorization.require("INVENTARIO","CREATE");
        if (approved.count("Actualizar")>0) Authorization.require("INVENTARIO","UPDATE");
        Objects.requireNonNull(progress, "Falta el indicador de progreso");
        Integer userId = Session.getCurrentUserId();
        if (!approved.executed.compareAndSet(false, true)) throw new IllegalStateException("Esta vista previa ya fue ejecutada. Genere una nueva");
        return DatabaseManager.callInConnection(() -> {
            Connection connection=Base.connection();
            boolean started=false;
            int rowNumber=0;
            try {
                if (!connection.getAutoCommit()) throw new IllegalStateException("Ya existe otra transacción en esta conexión");
                connection.setAutoCommit(false); started=true;
                List<InputRow> inputs=new ArrayList<>();
                for (PreviewRow r : approved.rows) inputs.add(r.input);
                Plan fresh=buildPlan(inputs,approved.mode,true);
                for (int i=0;i<fresh.rows.size();i++) {
                    PreviewRow before=approved.rows.get(i), now=fresh.rows.get(i);
                    if (!before.action.equals(now.action) || !Objects.equals(before.id,now.id) || !Objects.equals(before.snapshot,now.snapshot))
                        throw new IllegalStateException("Fila "+now.input.row+": el inventario cambió desde la vista previa. Revise nuevamente el archivo");
                }
                for (int i=0;i<fresh.rows.size();i++) {
                    PreviewRow row=fresh.rows.get(i); rowNumber=row.input.row;
                    if (row.action.equals("Crear") || row.action.equals("Actualizar")) writeRow(connection,row,userId);
                    progress.accept(i+1,fresh.rows.size());
                }
                if (!Objects.equals(userId, Session.getCurrentUserId())) throw new SecurityException("La sesión cambió durante la importación");
                Authorization.require("SISTEMA","DESTRUCTIVE_OPERATIONS");
                connection.commit();
                return fresh;
            } catch (Exception ex) {
                if (started) try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw new IllegalStateException("Importación cancelada"+(rowNumber>0?" en fila "+rowNumber:"")+". No se guardó ningún cambio. "+ex.getMessage(),ex);
            } finally {
                if (started) try { connection.setAutoCommit(true); } catch (SQLException ex) { throw new IllegalStateException("No se pudo restablecer la conexión",ex); }
            }
        });
    }
    private void writeRow(Connection connection, PreviewRow row, Integer userId) throws SQLException {
        InputRow input=row.input;
        Integer id=row.id;
        String code=input.code;
        if (id==null) {
            if (code.isEmpty()) {
                int attempts = 0;
                do {
                    if (++attempts > 50) throw new SQLException("No se pudo generar un código único");
                    code=BarcodeGenerator.generateRandomCode();
                } while (codeExists(connection,code));
            }
            try (PreparedStatement insert=connection.prepareStatement("INSERT INTO products(code,name,description,unit,current_stock,minimum_stock,status) VALUES(?,?,?,?,?,?,'active')",Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1,code); insert.setString(2,input.name); insert.setString(3,input.description); insert.setString(4,input.unit);
                insert.setBigDecimal(5,input.stock); insert.setBigDecimal(6,input.minimum); insert.executeUpdate();
                try (ResultSet keys=insert.getGeneratedKeys()) { if(!keys.next()) throw new SQLException("No se obtuvo el ID del producto"); id=keys.getInt(1); }
            }
        } else {
            try (PreparedStatement update=connection.prepareStatement("UPDATE products SET name=?,description=?,unit=?,current_stock=?,minimum_stock=? WHERE id=? AND status='active'")) {
                update.setString(1,input.name); update.setString(2,input.description.isEmpty()?row.snapshot.get(1):input.description);
                update.setString(3,input.unit); update.setBigDecimal(4,input.stock); update.setBigDecimal(5,input.minimum); update.setInt(6,id);
                if (update.executeUpdate()!=1) throw new SQLException("El producto dejó de estar disponible");
            }
        }
        BigDecimal old=row.id==null ? BigDecimal.ZERO : new BigDecimal(row.snapshot.get(3));
        String details="Importación; fila="+input.row+"; código="+code+"; nombre="+input.name+"; unidad="+input.unit+"; mínimo="+input.minimum+"; "+row.detail
            +"; stock_anterior="+old+"; stock_nuevo="+input.stock+"; diferencia="+input.stock.subtract(old);
        try (PreparedStatement audit=connection.prepareStatement("INSERT INTO audit(user_id,action,table_name,record_id,details) VALUES(?,?,'products',?,?)")) {
            audit.setInt(1,userId); audit.setString(2,row.id==null?"create":"update"); audit.setInt(3,id); audit.setString(4,details); audit.executeUpdate();
        }
    }
    private boolean codeExists(Connection connection,String code) throws SQLException {
        try (PreparedStatement query=connection.prepareStatement("SELECT id FROM products WHERE code=?")) {
            query.setString(1,code); try(ResultSet result=query.executeQuery()) { return result.next(); }
        }
    }

    // Compatibilidad con llamadas existentes: valida el archivo completo antes de construir modelos.
    public List<Product> parseFileForPreview(File file) throws Exception {
        List<InputRow> rows=readFile(file); List<Product> products=new ArrayList<>();
        for (InputRow row : rows) if(!row.errors.isEmpty()) throw new IllegalArgumentException("Fila "+row.row+": "+String.join("; ",row.errors));
        for (InputRow row : rows) {
            Product product=new Product(); product.set("code",row.code.isEmpty()?null:row.code); product.set("name",row.name);
            product.set("description",row.description); product.set("unit",row.unit); product.set("current_stock",row.stock); product.set("minimum_stock",row.minimum); products.add(product);
        }
        return products;
    }
    public void importItems(List<Product> items) {
        Authorization.require("SISTEMA","DESTRUCTIVE_OPERATIONS");
        List<InputRow> rows=new ArrayList<>(); Map<String,Integer> seen=new HashMap<>();
        for (int i=0;i<items.size();i++) {
            Product p=items.get(i); List<String> errors=new ArrayList<>();
            String code=Objects.toString(p.getString("code"),"").trim();
            String name=Objects.toString(p.getString("name"),"").trim(), unit=Objects.toString(p.getString("unit"),"").trim();
            if(name.isEmpty()||name.length()>100||unit.isEmpty()||unit.length()>50) errors.add("Nombre o unidad inválidos");
            if(!code.isEmpty() && (!BarcodeGenerator.isValidCode(code)||seen.putIfAbsent(code.toLowerCase(Locale.ROOT),i)!=null)) errors.add("Código inválido o repetido");
            BigDecimal stock=quantity(Objects.toString(p.get("current_stock"),""),"Stock actual",errors);
            BigDecimal min=quantity(Objects.toString(p.get("minimum_stock"),""),"Stock mínimo",errors);
            rows.add(new InputRow(i+2,code,name,Objects.toString(p.getString("description"),""),unit,stock,min,errors));
        }
        execute(preview(rows,Mode.UPSERT),(done,total)->{});
    }
}
