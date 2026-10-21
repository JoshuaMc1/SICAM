package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.User;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryImportServiceTest {
    @TempDir Path directory;
    private Connection connection;
    private final InventoryImportService importer=new InventoryImportService();
    private final String header="code,name,description,unit,current_stock,minimum_stock\n";
    @BeforeEach void setup() throws Exception {
        connection=DriverManager.getConnection("jdbc:sqlite::memory:");
        sql("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,status TEXT,activo INTEGER)");
        sql("CREATE TABLE users(id INTEGER PRIMARY KEY,rol_id INTEGER,status TEXT,username TEXT)");
        sql("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT UNIQUE,name TEXT,description TEXT,unit TEXT,current_stock DECIMAL,minimum_stock DECIMAL,status TEXT)");
        sql("CREATE TABLE audit(id INTEGER PRIMARY KEY,user_id INTEGER,action TEXT,table_name TEXT,record_id INTEGER,details TEXT)");
        sql("INSERT INTO roles VALUES(1,'ROOT','active',1)");
        sql("INSERT INTO users VALUES(1,1,'active','root')");
        Base.attach(connection); Session.setCurrentUser(User.findById(1));
    }
    @AfterEach void close() throws Exception { Session.setCurrentUser(null); Base.detach(); connection.close(); }
    private void sql(String query) throws Exception { try(Statement statement=connection.createStatement()) { statement.execute(query); } }
    private String scalar(String query) throws Exception { try(Statement statement=connection.createStatement(); ResultSet rs=statement.executeQuery(query)) { assertTrue(rs.next()); return rs.getString(1); } }
    private List<InventoryImportService.InputRow> read(String content) throws Exception {
        Path file=directory.resolve("test.csv"); Files.writeString(file,content); return importer.readFile(file.toFile());
    }
    private InventoryImportService.Plan plan(String body,InventoryImportService.Mode mode) throws Exception { return importer.preview(read(header+body),mode); }
    private void existing(String status) throws Exception { sql("INSERT INTO products(code,name,description,unit,current_stock,minimum_stock,status) VALUES('ABC','Anterior','Conservar','unidad',10,2,'"+status+"')"); }
    @Test void readsBomSemicolonDecimalsAndMultilineQuotes() throws Exception {
        var rows=read("\uFEFFcode;name;description;unit;current_stock;minimum_stock\r\n001;Pico;\"Acero; fuerte\ncon mango \"\"largo\"\"\";unidad;12,50;2\r\n");
        assertEquals(1,rows.size()); assertEquals("001",rows.get(0).code);
        assertEquals("Acero; fuerte\ncon mango \"largo\"",rows.get(0).description);
        assertTrue(rows.get(0).errors.isEmpty()); assertEquals("12.50",rows.get(0).stock.toString());
    }
    @Test void rejectsInvalidNumbersAndCollectsErrors() throws Exception {
        var p=plan("AAA,,desc,,abc,-2\nBBB,B,desc,u,NaN,1.234\nCCC,C,desc,u,100000000,0\n",InventoryImportService.Mode.UPSERT);
        assertEquals(3,p.count("Error")); assertTrue(p.report().contains("Fila 2"));
        assertThrows(IllegalArgumentException.class,()->importer.execute(p,(d,t)->{}));
        assertEquals("0",scalar("SELECT COUNT(*) FROM products"));
    }
    @Test void rejectsDuplicateCodesBeforeWriting() throws Exception {
        var p=plan("ABC,Pico,,u,10,2\nabc,Machete,,u,20,3\n",InventoryImportService.Mode.UPSERT);
        assertTrue(p.hasErrors()); assertTrue(p.report().contains("2 y 3"));
        assertThrows(IllegalArgumentException.class,()->importer.execute(p,(d,t)->{}));
        assertEquals("0",scalar("SELECT COUNT(*) FROM products"));
    }
    @Test void rejectsMalformedCsvAndMissingHeaders() {
        assertThrows(IllegalArgumentException.class,()->read(header+"AAA,\"sin cerrar"));
        assertThrows(IllegalArgumentException.class,()->read("code,name\nABC,Pico"));
        assertThrows(IllegalArgumentException.class,()->read(header));
    }
    @Test void createsAllRowsAndAuditWithinOneCommit() throws Exception {
        var p=plan("ABC,Pico,,u,10,2\nDEF,Machete,,u,20,3\n",InventoryImportService.Mode.UPSERT);
        List<Integer> done=new ArrayList<>();
        var result=importer.execute(p,(d,t)->{ assertEquals(2,t); done.add(d); });
        assertEquals(2,result.count("Crear")); assertEquals(List.of(1,2),done);
        assertEquals("2",scalar("SELECT COUNT(*) FROM products")); assertEquals("2",scalar("SELECT COUNT(*) FROM audit"));
        assertTrue(connection.getAutoCommit());
    }
    @Test void updatesNameAndStockPreservingEmptyDescriptionAndLogsDifference() throws Exception {
        existing("active");
        var p=plan("ABC,Nuevo,,saco,15,3\n",InventoryImportService.Mode.UPSERT);
        assertTrue(p.report().contains("Anterior → Nuevo"));
        importer.execute(p,(d,t)->{});
        assertEquals("Nuevo",scalar("SELECT name FROM products")); assertEquals("Conservar",scalar("SELECT description FROM products"));
        String audit=scalar("SELECT details FROM audit"); assertTrue(audit.contains("stock_anterior=10")); assertTrue(audit.contains("diferencia=5"));
    }
    @Test void modesSkipExistingOrMissingProducts() throws Exception {
        existing("active"); String rows="ABC,Nuevo,,u,30,3\nDEF,Otro,,u,20,2\n";
        var create=plan(rows,InventoryImportService.Mode.CREATE_ONLY);
        assertEquals(1,create.count("Omitir")); assertEquals(1,create.count("Crear")); importer.execute(create,(d,t)->{});
        assertEquals("Anterior",scalar("SELECT name FROM products WHERE code='ABC'"));
        var update=plan("ABC,Nuevo,,u,30,3\nGHI,Otro,,u,20,2\n",InventoryImportService.Mode.UPDATE_ONLY);
        assertEquals(1,update.count("Omitir")); assertEquals(1,update.count("Actualizar")); importer.execute(update,(d,t)->{});
        assertEquals("2",scalar("SELECT COUNT(*) FROM products"));
    }
    @Test void protectsInactiveAndDeletedProducts() throws Exception {
        for(String status:List.of("inactive","deleted")) {
            sql("DELETE FROM products"); existing(status);
            var p=plan("ABC,Nuevo,,u,30,3\n",InventoryImportService.Mode.UPSERT);
            assertTrue(p.hasErrors()); assertThrows(IllegalArgumentException.class,()->importer.execute(p,(d,t)->{}));
            assertEquals(status,scalar("SELECT status FROM products"));
        }
    }
    @Test void rollsBackEarlierWritesAndAuditWhenLaterWriteFails() throws Exception {
        existing("active");
        sql("CREATE TRIGGER fail_insert BEFORE INSERT ON products WHEN NEW.code='DEF' BEGIN SELECT RAISE(ABORT,'fallo simulado'); END");
        var p=plan("ABC,Nuevo,,u,30,3\nDEF,Otro,,u,20,2\n",InventoryImportService.Mode.UPSERT);
        var error=assertThrows(IllegalStateException.class,()->importer.execute(p,(d,t)->{}));
        assertTrue(error.getMessage().contains("fila 3"));
        assertEquals("Anterior",scalar("SELECT name FROM products")); assertEquals("10",scalar("SELECT current_stock FROM products"));
        assertEquals("0",scalar("SELECT COUNT(*) FROM audit")); assertTrue(connection.getAutoCommit());
    }
    @Test void rollsBackProductsWhenAuditFails() throws Exception {
        sql("CREATE TRIGGER fail_audit BEFORE INSERT ON audit BEGIN SELECT RAISE(ABORT,'auditoria no disponible'); END");
        var p=plan("ABC,Pico,,u,10,2\n",InventoryImportService.Mode.UPSERT);
        assertThrows(IllegalStateException.class,()->importer.execute(p,(d,t)->{}));
        assertEquals("0",scalar("SELECT COUNT(*) FROM products"));
    }
    @Test void rejectsChangesSincePreview() throws Exception {
        existing("active"); var p=plan("ABC,Nuevo,,u,30,3\n",InventoryImportService.Mode.UPSERT);
        sql("UPDATE products SET current_stock=11 WHERE code='ABC'");
        var error=assertThrows(IllegalStateException.class,()->importer.execute(p,(d,t)->{}));
        assertTrue(error.getMessage().contains("inventario cambió")); assertEquals("11",scalar("SELECT current_stock FROM products"));
        assertEquals("0",scalar("SELECT COUNT(*) FROM audit"));
    }
    @Test void generatesUniqueCodesForBlankCodes() throws Exception {
        var p=plan(",Pico,,u,10,2\n,Machete,,u,20,3\n",InventoryImportService.Mode.UPSERT);
        importer.execute(p,(d,t)->{}); assertEquals("2",scalar("SELECT COUNT(DISTINCT code) FROM products"));
    }
    @Test void excelPreservesFormattedLeadingZerosAndRejectsFormulaValues() throws Exception {
        Path file=directory.resolve("test.xlsx");
        try(var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet=wb.createSheet(); var h=sheet.createRow(0);
            String[] headers={"code","name","unit","current_stock","minimum_stock"};
            for(int i=0;i<headers.length;i++) h.createCell(i).setCellValue(headers[i]);
            var row=sheet.createRow(1); row.createCell(0).setCellValue(1);
            var format=wb.createCellStyle(); format.setDataFormat(wb.createDataFormat().getFormat("00000")); row.getCell(0).setCellStyle(format);
            row.createCell(1).setCellValue("Pico"); row.createCell(2).setCellValue("unidad"); row.createCell(3).setCellValue(10); row.createCell(4).setCellFormula("1+1");
            try(var out=Files.newOutputStream(file)) { wb.write(out); }
        }
        var rows=importer.readFile(file.toFile()); assertEquals("00001",rows.get(0).code); assertFalse(rows.get(0).errors.isEmpty());
    }
    @Test void refusesExecutingSamePreviewTwice() throws Exception {
        var p=plan(",Pico,,u,10,2\n",InventoryImportService.Mode.UPSERT);
        importer.execute(p,(d,t)->{});
        assertThrows(IllegalStateException.class,()->importer.execute(p,(d,t)->{}));
        assertEquals("1",scalar("SELECT COUNT(*) FROM products"));
    }
    @Test void missingSessionCannotPreviewOrExecute() throws Exception {
        var p=plan("ABC,Pico,,u,10,2\n",InventoryImportService.Mode.UPSERT);
        Session.setCurrentUser(null);
        assertThrows(SecurityException.class,()->importer.preview(List.of(),InventoryImportService.Mode.UPSERT));
        assertThrows(SecurityException.class,()->importer.execute(p,(d,t)->{}));
        assertEquals("0",scalar("SELECT COUNT(*) FROM products"));
    }
}
