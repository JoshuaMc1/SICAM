package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.models.Report;
import com.joshuamc1.sicam.core.enums.StockLevel;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ReportRepositoryTest {
    private Connection connection;
    private ReportRepository repo;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    public void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        repo = new ReportRepository(connection);
        sql("CREATE TABLE roles(id INTEGER, nombre TEXT)");
        sql("CREATE TABLE users(id INTEGER, name TEXT, username TEXT, rol_id INTEGER, status TEXT, created_at TEXT,session_token TEXT,session_expires_at TEXT)");
        sql("CREATE TABLE people(id INTEGER, dni TEXT, first_name TEXT, last_name TEXT, phone TEXT, internal_code TEXT, status TEXT, created_at TEXT)");
        sql("CREATE TABLE products(id INTEGER, name TEXT, description TEXT, unit TEXT, current_stock REAL, minimum_stock REAL, status TEXT, created_at TEXT, code TEXT)");
        sql("CREATE TABLE transactions(id INTEGER, type TEXT, date TEXT, created_at TEXT, status TEXT, user_id INTEGER, person_id INTEGER, loan_code TEXT, loan_status TEXT, expected_return_date TEXT, invalidated_at TEXT, invalidated_by INTEGER, invalidation_reason TEXT, notes TEXT)");
        sql("CREATE TABLE transaction_items(id INTEGER, transaction_id INTEGER, product_id INTEGER, quantity REAL, status TEXT)");
        sql("INSERT INTO roles VALUES(1,'ROOT'),(2,'OPERADOR')");
        sql("INSERT INTO users(id,name,username,rol_id,status,created_at) VALUES(1,'Root','root',1,'active',?), (2,'María','maria',2,'active',?), (3,'Eliminado','deleted',2,'deleted',?)", today, today, today);
        sql("INSERT INTO people VALUES(1,'123','Ana','López','','','active',?)", today);
        product(1, "Papel", "unidad", 5, 10, "active", today.minusDays(150));
        product(2, "Agua", "l", 12, 10, "active", today.minusDays(150));
        product(3, "Agotado", "unidad", 0, 10, "active", today.minusDays(150));
        product(4, "Eliminado", "unidad", 0, 10, "deleted", today.minusDays(150));
        product(5, "Nuevo", "unidad", 20, 10, "active", today.minusDays(2));
        tx(1,"out","active","pending",today,today);
        tx(2,"out","invalidated","pending",today,today);
        sql("UPDATE transactions SET invalidated_at=?,invalidated_by=2,invalidation_reason='Duplicado',notes='Nota general' WHERE id=2", today);
        item(1,1,1,2,"active"); item(2,1,2,3,"active");
        item(3,2,1,100,"cancelled");
        item(4,1,1,50,"cancelled");
    }
    @AfterEach public void close() throws Exception { connection.close(); }
    private void sql(String sql, Object... values) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i=0;i<values.length;i++) ps.setString(i+1, String.valueOf(values[i]));
            ps.execute();
        }
    }
    private void product(int id,String name,String unit,double current,double min,String state,LocalDate created) throws Exception {
        sql("INSERT INTO products VALUES(?,?,'',?,?,?,?,?,?)",id,name,unit,current,min,state,created,"COD-"+id);
    }
    private void tx(int id,String type,String status,String loanStatus,LocalDate due,LocalDate date) throws Exception {
        sql("INSERT INTO transactions VALUES(?,?,?,?,?,2,1,?,?,?,NULL,NULL,NULL,NULL)",id,type,date,date,status,"P-"+id,loanStatus,due);
    }
    private void item(int id,int tx,int product,double quantity,String status) throws Exception {
        sql("INSERT INTO transaction_items VALUES(?,?,?,?,?)",id,tx,product,quantity,status);
    }
    private String value(Report report, List<String> row, String column) {
        return row.get(report.getHeaders().indexOf(column));
    }
    private void rectangular(Report report) {
        for (List<String> row : report.getRows()) assertEquals(report.getHeaders().size(),row.size(),report.getTitle());
    }

    @Test public void historyPreservesInvalidationAndProductUnits() {
        Report invalid = repo.transactionDetails("Historial",null,null,null,null,null,"invalidated");
        assertEquals(1,invalid.getRows().size());
        List<String> row=invalid.getRows().get(0);
        assertEquals("Invalidado",value(invalid,row,"Estado"));
        assertTrue(value(invalid,row,"Invalidación").contains("Motivo: Duplicado"));
        assertTrue(value(invalid,row,"Invalidación").contains("Por: maria"));
        assertTrue(value(invalid,row,"Productos").contains("Cancelado"));
        Report all=repo.transactionsAll();
        assertEquals(2,all.getRows().size());
        assertTrue(all.getRows().stream().anyMatch(r -> value(all,r,"Productos").contains("Agua: 3.0 l")));
        assertTrue(all.getRows().stream().anyMatch(r -> value(all,r,"Productos").contains("Papel: 2.0 unidad")));
        rectangular(all);
        assertFalse(invalid.getTitle().contains("CASE"));
    }

    @Test public void operationalTotalsIgnoreInvalidatedTransactionsAndCancelledItems() {
        Report report=repo.productsMostMoved(today.toString(),today.toString());
        assertEquals(2,report.getRows().size());
        for(List<String> row: report.getRows()) {
            double expected=value(report,row,"ID").equals("1")?2:3;
            assertEquals(expected,Double.parseDouble(value(report,row,"Cantidad egresada")));
            assertEquals("1",value(report,row,"Movimientos"));
        }
        Report consumption=repo.monthlyConsumption(12);
        assertEquals(2,consumption.getRows().size());
        assertEquals(5,consumption.getRows().stream().mapToDouble(r->Double.parseDouble(value(consumption,r,"Cantidad consumida"))).sum());
        assertTrue(repo.productsMostMoved(today.minusYears(2).toString(),today.minusYears(1).toString()).getRows().isEmpty());
    }

    @Test public void loanReportExcludesClosedAndInvalidatedAndComputesOverdue() throws Exception {
        tx(10,"loan","active","pending",today.minusDays(3),today);
        tx(11,"loan","active","pending",today,today);
        tx(12,"loan","active","returned",today.minusDays(8),today);
        tx(13,"loan","invalidated","overdue",today.minusDays(8),today);
        tx(14,"loan","active","paid",today.minusDays(8),today);
        tx(15,"loan","active","replaced",today.minusDays(8),today);
        for(int id=10;id<=15;id++) item(id,id,1,1,"active");
        item(99,10,2,4,"active");
        Report report=repo.peopleWithActiveLoans();
        assertEquals(2,report.getRows().size());
        List<String> overdue=report.getRows().stream().filter(r->value(report,r,"ID préstamo").equals("10")).findFirst().orElseThrow();
        assertEquals("Vencido",value(report,overdue,"Estado préstamo"));
        assertEquals("3",value(report,overdue,"Días de atraso"));
        assertTrue(value(report,overdue,"Productos").contains("Papel: 1.0 unidad"));
        assertTrue(value(report,overdue,"Productos").contains("Agua: 4.0 l"));
        assertEquals(1,report.getRows().stream().filter(r->value(report,r,"ID préstamo").equals("10")).count());
        assertTrue(report.getRows().stream().anyMatch(r->value(report,r,"Estado préstamo").equals("Vence hoy")));
        rectangular(report);
    }

    @Test public void activeLoansPrioritizeDueDatesAndKeepContactAndAllProducts() throws Exception {
        sql("UPDATE people SET phone='9999-0000' WHERE id=1");
        tx(20,"loan","active","pending",today.plusDays(2),today);
        tx(21,"loan","active","pending",today.minusDays(4),today);
        tx(22,"loan","active","pending",today,today);
        tx(23,"loan","active","pending",today,today);
        sql("UPDATE transactions SET expected_return_date=NULL WHERE id=23");
        for(int id=20;id<=23;id++) item(id,id,1,1,"active");
        item(100,21,2,2,"active");
        item(101,21,3,99,"cancelled");
        Report report=repo.peopleWithActiveLoans();
        assertEquals(List.of("21","22","20","23"),report.getRows().stream().map(r->r.get(0)).collect(java.util.stream.Collectors.toList()));
        assertEquals("9999-0000",value(report,report.getRows().get(0),"Teléfono"));
        assertTrue(value(report,report.getRows().get(0),"Productos").contains("Agua: 2.0 l"));
        assertFalse(value(report,report.getRows().get(0),"Productos").contains("Agotado"));
        assertEquals("",value(report,report.getRows().get(3),"Días de atraso"));
        rectangular(report);
    }

    @Test public void consumptionCountsDistinctEgressesAndRespectsCalendarMonths() throws Exception {
        LocalDate start=today.withDayOfMonth(1).minusMonths(11);
        tx(30,"out","active","pending",start,start);
        tx(31,"out","active","pending",start.minusDays(1),start.minusDays(1));
        tx(32,"loan","active","pending",today,today);
        tx(33,"out","active","pending",today.plusDays(1),today.plusDays(1));
        item(30,30,1,4,"active"); item(31,31,1,100,"active");
        item(32,32,1,100,"active"); item(33,33,1,100,"active");
        item(34,1,1,1,"active"); // Dos detalles de un mismo egreso cuentan una operación.
        Report report=repo.monthlyConsumption(12);
        assertEquals(10,report.getRows().stream().mapToDouble(r->Double.parseDouble(value(report,r,"Cantidad consumida"))).sum());
        assertTrue(report.getRows().stream().allMatch(r->value(report,r,"Egresos").equals("1")));
        assertTrue(report.isLandscape());
        assertThrows(IllegalArgumentException.class,()->repo.monthlyConsumption(0));
    }

    @Test public void analyticalReportsExportSameDataInPdfAndCsv() throws Exception {
        tx(40,"loan","active","pending",today.minusDays(2),today);
        item(40,40,1,2,"active"); item(41,40,2,3,"active");
        List<Report> reports=List.of(repo.productsMostMoved(today.toString(),today.toString()),
            repo.peopleWithActiveLoans(),repo.monthlyConsumption(12),repo.productsWithoutMovement(90),repo.userActivity(2,null,null),repo.inventoryComplete(),
            repo.inventoryByDateRange(today.minusDays(200).toString(),today.toString()),
            repo.inventoryMovements(),repo.inventoryMovementsByDateRange(today.toString(),today.toString()));
        var csv=com.joshuamc1.sicam.controllers.dashboard.ReportsController.class
            .getDeclaredMethod("exportToCsv",Report.class,java.io.File.class);
        var pdf=com.joshuamc1.sicam.controllers.dashboard.ReportsController.class
            .getDeclaredMethod("exportToPdfWithProgress",Report.class,java.io.File.class,String.class,
                java.util.function.Consumer.class,java.util.function.Supplier.class);
        csv.setAccessible(true); pdf.setAccessible(true);
        var controller=new com.joshuamc1.sicam.controllers.dashboard.ReportsController();
        java.nio.file.Path dir=java.nio.file.Path.of("target","report-previews");
        java.nio.file.Files.createDirectories(dir);
        String[] names={"productos_con_mayor_movimiento","personas_con_prestamos_activos","consumo_mensual","productos_sin_movimientos","actividad_detallada_usuario","inventario_completo","productos_por_fecha","movimientos_30_dias","movimientos_por_fecha"};
        for(int i=0;i<reports.size();i++) {
            Report report=reports.get(i);
            var csvFile=dir.resolve(names[i]+".csv"); var pdfFile=dir.resolve(names[i]+".pdf");
            csv.invoke(controller,report,csvFile.toFile());
            pdf.invoke(controller,report,pdfFile.toFile(),"Operaciones vigentes, con detalle por producto y unidad.",
                (java.util.function.Consumer<Double>)p->{},(java.util.function.Supplier<Boolean>)()->false);
            String csvText=java.nio.file.Files.readString(csvFile);
            assertEquals(String.join(",",report.getHeaders()),csvText.lines().findFirst().orElseThrow());
            for(var row:report.getRows()) for(String cell:row)
                assertTrue(csvText.contains(cell),"Falta en CSV: "+cell);
            if(i==1) assertTrue(csvText.contains("\"Papel: 2.0 unidad\nAgua: 3.0 l\""));
            try(var document=org.apache.pdfbox.pdmodel.PDDocument.load(pdfFile.toFile())) {
                assertTrue(document.getPage(0).getMediaBox().getWidth()>document.getPage(0).getMediaBox().getHeight());
                String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+","");
                for(var row:report.getRows()) for(String cell:row)
                    assertTrue(text.contains(cell.replaceAll("\\s+","")),"Falta en PDF: "+cell);
                var preview=new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0,100);
                javax.imageio.ImageIO.write(preview,"png",dir.resolve(names[i]+".png").toFile());
                if(i>=5) assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight(),
                    document.getPage(0).getMediaBox().getWidth(),0.01);
                if(i==5) {
                    for(int color:new int[]{0x616161,0xD32F2F,0xF9A825,0x388E3C}) {
                        int pixels=0;
                        for(int y=0;y<preview.getHeight();y++) for(int x=0;x<preview.getWidth();x++)
                            if((preview.getRGB(x,y)&0xFFFFFF)==color) pixels++;
                        assertTrue(pixels>10,"Falta color de badge: "+Integer.toHexString(color));
                    }
                    assertTrue(csvText.startsWith("ID,Código,Producto,"));
                    assertTrue(csvText.contains("Stock suficiente"));
                }
            }
        }
    }

    @Test public void noMovementHonorsAgeAndIgnoresInvalidatedOperations() throws Exception {
        item(20,2,3,9,"active"); // Invalidadas no alteran la última actividad válida.
        Report report=repo.productsWithoutMovement(90);
        assertEquals(List.of("3"),report.getRows().stream().map(r->r.get(0)).collect(java.util.stream.Collectors.toList()));
        rectangular(report);
    }

    @Test public void inventoryMatchesBadgesAndExcludesDeletedByDefault() {
        Report complete=repo.inventoryComplete();
        assertEquals(4,complete.getRows().size());
        assertEquals("COD-5",value(complete,complete.getRows().get(0),"Código"));
        assertEquals("Stock suficiente",value(complete,complete.getRows().get(0),"Nivel de stock"));
        assertEquals("1",repo.inventoryLowStockReport().getRows().get(0).get(0));
        assertEquals("3",repo.inventoryOutOfStock().getRows().get(0).get(0));
        Report near=repo.inventory("Cerca",null,null,false,StockLevel.NEAR_MINIMUM);
        assertEquals("2",near.getRows().get(0).get(0));
        assertEquals(5,repo.inventory("Todos",null,null,true,null).getRows().size());
        rectangular(near);
    }

    @Test public void userActivityUsesRegistrationDateAndPreservesTransactionDetails() throws Exception {
        sql("UPDATE transactions SET date=? WHERE id=1",today.minusYears(1));
        Report report=repo.userActivity(2,today.toString(),today.toString());
        assertEquals(2,report.getRows().size());
        assertTrue(report.isLandscape());
        assertTrue(report.getRows().stream().allMatch(r->value(report,r,"Usuario").equals("maria")));
        assertTrue(report.getRows().stream().anyMatch(r->value(report,r,"Estado").equals("Activo")));
        assertTrue(report.getRows().stream().anyMatch(r->value(report,r,"Estado").equals("Invalidado")
            && value(report,r,"Invalidación").contains("Duplicado") && value(report,r,"Notas").equals("Nota general")));
        var active=report.getRows().stream().filter(r->value(report,r,"Estado").equals("Activo")).findFirst().orElseThrow();
        assertTrue(value(report,active,"Productos").contains("Papel: 2.0 unidad"));
        assertTrue(value(report,active,"Productos").contains("Agua: 3.0 l"));
        assertEquals(today.minusYears(1).toString(),value(report,active,"Fecha de operación"));
        sql("UPDATE transactions SET created_at=? WHERE id=1",today.minusYears(2));
        assertEquals(1,repo.userActivity(2,today.toString(),today.toString()).getRows().size());
        assertEquals(2,repo.userActivity(2,null,null).getRows().size());
        assertTrue(repo.userActivity(1,null,null).getRows().isEmpty());
        var activeOnly=repo.userActivity(2,null,null,"active");
        assertEquals(1,activeOnly.getRows().size());
        assertEquals("Activo",value(activeOnly,activeOnly.getRows().get(0),"Estado"));
        assertThrows(IllegalArgumentException.class,()->repo.userActivity(2,null,today.toString()));
        rectangular(report);
    }

    @Test public void serviceTransactionTitlesTranslateTypesAndFormatDateRange() throws Exception {
        sql("ALTER TABLE roles ADD COLUMN status TEXT DEFAULT 'active'");
        sql("ALTER TABLE roles ADD COLUMN activo INTEGER DEFAULT 1");
        org.javalite.activejdbc.Base.attach(connection);
        try {
            com.joshuamc1.sicam.core.Session.setCurrentUser(com.joshuamc1.sicam.models.User.findById(1));
            var service=new com.joshuamc1.sicam.services.ReportService();
            String[] types={"in","out","loan"}; String[] labels={"Ingresos","Egresos","Préstamos"};
            for(int n=0;n<types.length;n++) {
                var report=service.transactionReport("Transacciones por tipo",types[n],null,null,null,null,null);
                assertEquals("Transacciones por tipo — "+labels[n]+" — Todos los estados",report.getTitle());
                var dated=service.transactionReport("Transacciones por tipo y fecha de operación",types[n],
                    "2026-10-21","2026-10-19",null,null,null);
                assertEquals("Transacciones por tipo y fecha de operación — "+labels[n]
                    +" — del 19/10/2026 al 21/10/2026 — Todos los estados",dated.getTitle());
            }
        } finally {
            com.joshuamc1.sicam.core.Session.setCurrentUser(null);
            org.javalite.activejdbc.Base.detach();
        }
    }

    @Test public void movementRankingIncludesEveryProductInSelectedPeriod() throws Exception {
        for(int id=10;id<35;id++) {
            product(id,"Producto "+id,"unidad",5,1,"active",today);
            item(id,1,id,1,"active");
        }
        var report=repo.productsMostMoved(today.toString(),today.toString());
        assertEquals(27,report.getRows().size());
        assertEquals("Productos con mayor movimiento — operaciones vigentes — "+today+" a "+today,report.getTitle());
        tx(50,"in","active","pending",today,today); item(100,50,1,1,"active");
        report=repo.productsMostMoved(today.toString(),today.toString());
        assertEquals("1",value(report,report.getRows().get(0),"ID"));
        assertEquals("2",value(report,report.getRows().get(0),"Movimientos"));
    }

    @Test public void noMovementUsesInclusiveAgeBoundaryAndOnlyValidOperations() throws Exception {
        product(6,"Reciente","unidad",5,1,"active",today.minusDays(89));
        product(7,"Sin operaciones","unidad",5,1,"active",today.minusDays(90));
        product(8,"Con operación reciente","unidad",5,1,"active",today.minusDays(200));
        product(9,"En el límite","unidad",5,1,"active",today.minusDays(200));
        product(10,"Solo invalidada","unidad",5,1,"active",today.minusDays(200));
        product(11,"Solo cancelado","unidad",5,1,"active",today.minusDays(200));
        tx(20,"out","active","pending",today,today.minusDays(89)); item(20,20,8,1,"active");
        tx(21,"in","active","pending",today,today.minusDays(90)); item(21,21,9,1,"active");
        item(22,2,10,1,"active"); item(23,1,11,1,"cancelled");
        var report=repo.productsWithoutMovement(90);
        assertEquals(java.util.Set.of("3","7","9","10","11"),report.getRows().stream().map(r->r.get(0)).collect(java.util.stream.Collectors.toSet()));
        var boundary=report.getRows().stream().filter(r->r.get(0).equals("9")).findFirst().orElseThrow();
        assertEquals("90",value(report,boundary,"Días sin movimiento"));
        var never=report.getRows().stream().filter(r->r.get(0).equals("7")).findFirst().orElseThrow();
        assertEquals("Sin movimientos",value(report,never,"Último movimiento válido"));
        assertEquals("90",value(report,never,"Días sin movimiento"));
        assertThrows(IllegalArgumentException.class,()->repo.productsWithoutMovement(-1));
        rectangular(report);
    }

    @Test public void usersRespectRootGroupingAndDeletedFilter() {
        assertEquals(1,repo.users(true,true,false).getRows().size());
        assertEquals(1,repo.users(false,true,false).getRows().size());
        Report all=repo.users(true,true,true);
        assertEquals(2,all.getRows().size());
        assertEquals("OPERADOR",value(all,all.getRows().get(0),"Rol"));
        assertTrue(all.getRows().stream().noneMatch(row -> value(all,row,"Rol").equalsIgnoreCase("ROOT")));
        assertTrue(all.getRows().stream().anyMatch(r->value(all,r,"Estado").equals("Eliminado")));
    }

    @Test public void dashboardSeparatesCurrentStockAndMonthStatesAndUnits() {
        Report report=repo.monthlyDashboard(YearMonth.now().toString());
        assertTrue(report.getRows().stream().anyMatch(r->r.get(1).equals("Egresos — Invalidados") && r.get(2).equals("1")));
        assertTrue(report.getRows().stream().anyMatch(r->r.get(1).equals("Productos no eliminados") && r.get(2).equals("4")));
        assertTrue(report.getRows().stream().anyMatch(r->r.get(1).equals("Stock total (l)")));
        assertTrue(report.getRows().stream().anyMatch(r->r.get(1).equals("Stock total (unidad)")));
        rectangular(report);
    }

    @Test public void legacyEntryPointsUseSameConsistentReports() {
        for(Report report: List.of(repo.inventoryTotal(),repo.inventoryLowStock(),repo.inventoryByDateRange(today.minusYears(1).toString(),today.toString()),
            repo.transactions(today.toString(),today.toString()),repo.transactionsByType("out",today.toString(),today.toString()),
            repo.transactionsByPerson(1,today.toString(),today.toString()),repo.peopleRegistered(today.toString(),today.toString()),
            repo.usersWithRoles(),repo.usersExcludingRoot(),repo.usersGroupedByRole(),repo.inventoryMovements(),
            repo.transactionsByTypeReport("out"),repo.transactionsByUserReport(2),repo.transactionsByPersonReport(1))) rectangular(report);
    }
    @Test public void systemUsersNeverExposeRolesSystemOrRootEvenForRootExporter() throws Exception {
        sql("INSERT INTO users(id,name,username,rol_id,status,created_at) VALUES(4,'Sistema','SYSTEM',NULL,'active',?), (5,'Sistema duplicado','system',2,'active',?), (6,'Sin rol','sin_rol',NULL,'active',?)",today,today,today);
        sql("INSERT INTO roles VALUES(9,'ROOT')");
        sql("INSERT INTO users(id,name,username,rol_id,status,created_at) VALUES(7,'Otra cuenta root','root_extra',9,'active',?)",today);
        Report report=repo.users(true,false,false);
        assertEquals(List.of("ID","Nombre","Usuario","Estado","Fecha de registro"),report.getHeaders());
        assertNull(report.getGroupByColumn());
        assertEquals(java.util.Set.of("maria","sin_rol"),report.getRows().stream().map(row->value(report,row,"Usuario")).collect(java.util.stream.Collectors.toSet()));
        rectangular(report);
    }
    @Test public void includingDeletedDoesNotBringProtectedAccountsBackIntoSystemReport() throws Exception {
        sql("INSERT INTO users(id,name,username,rol_id,status,created_at) VALUES(4,'Sistema','SYSTEM',NULL,'deleted',?)",today);
        Report report=repo.users(true,false,true);
        assertEquals(2,report.getRows().size());
        assertTrue(report.getRows().stream().anyMatch(row->value(report,row,"Estado").equals("Eliminado")));
        assertFalse(report.getHeaders().contains("Rol"));
        assertTrue(report.getRows().stream().noneMatch(row->value(report,row,"Usuario").equalsIgnoreCase("SYSTEM") || value(report,row,"Usuario").equals("root")));
    }
    @Test public void groupedUsersCarrySectionMetadataAndRetainRoleInTabularData() throws Exception {
        sql("INSERT INTO users(id,name,username,rol_id,status,created_at) VALUES(4,'Sistema','SYSTEM',NULL,'active',?)",today);
        Report report=repo.users(false,true,false);
        assertEquals("Rol",report.getGroupByColumn());
        assertEquals(1,report.getRows().size());
        assertEquals("OPERADOR",value(report,report.getRows().get(0),"Rol"));
        rectangular(report);
    }

    @Test public void groupedUsersExcludeEveryRootAccountIncludingDeletedAndAlternateRoleIds() throws Exception {
        sql("INSERT INTO roles VALUES(9,'root')");
        sql("INSERT INTO users(id,name,username,rol_id,status,created_at) VALUES(5,'Otra cuenta privilegiada','cuenta_privilegiada',9,'active',?), (6,'Root eliminado','root_eliminado',9,'deleted',?)",today,today);
        Report report=repo.users(true,true,true);
        assertEquals("Rol",report.getGroupByColumn());
        assertEquals(java.util.Set.of("maria","deleted"),report.getRows().stream().map(row->value(report,row,"Usuario")).collect(java.util.stream.Collectors.toSet()));
        assertTrue(report.getRows().stream().noneMatch(row->value(report,row,"Rol").equalsIgnoreCase("ROOT")));
    }

    @Test public void systemUsersCsvExcludesProtectedAccountsAndRoleColumn(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        sql("INSERT INTO users(id,name,username,rol_id,status,created_at) VALUES(4,'Sistema','SYSTEM',NULL,'active',?), (5,'Privilegiado','admin_privilegiado',1,'active',?)",today,today);
        sql("UPDATE users SET name=? WHERE id=2", "María, \"QA\"");
        Report report=repo.users(true,false,true);
        java.nio.file.Path file=directory.resolve("usuarios.csv");
        var export=com.joshuamc1.sicam.controllers.dashboard.ReportsController.class.getDeclaredMethod("exportToCsv",Report.class,java.io.File.class);
        export.setAccessible(true);
        export.invoke(new com.joshuamc1.sicam.controllers.dashboard.ReportsController(),report,file.toFile());
        String text=java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("ID,Nombre,Usuario,Estado,Fecha de registro",text.lines().findFirst().orElseThrow());
        assertEquals(3,text.lines().count());
        assertTrue(text.contains("\"María, \"\"QA\"\"\""));
        assertTrue(text.contains(",maria,Activo,"));
        assertTrue(text.contains(",deleted,Eliminado,"));
        assertFalse(text.contains("SYSTEM"));
        assertFalse(text.contains(",root,"));
        assertFalse(text.contains("admin_privilegiado"));
    }

}
