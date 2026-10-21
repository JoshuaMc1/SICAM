package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.models.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

public class ReportsExportTest {
    @TempDir public Path temp;
    @Test public void csvPreservesQuotesCommasAndNotes() throws Exception {
        Report report=new Report("Historial");
        report.addHeader("Estado");report.addHeader("Motivo");
        report.addRow(List.of("Invalidado","Texto con \"comillas\""));
        report.addRow(List.of("Invalidado","Texto, con coma y\nsegunda línea"));
        Path file=temp.resolve("historial.csv");
        var method=ReportsController.class.getDeclaredMethod("exportToCsv",Report.class,File.class);
        method.setAccessible(true);
        method.invoke(new ReportsController(),report,file.toFile());
        String text=Files.readString(file);
        assertTrue(text.contains("\"Texto con \"\"comillas\"\"\""));
        assertTrue(text.contains("\"Texto, con coma y\nsegunda línea\""));
    }
    @Test public void monthlyPdfUsesNewThreeColumnReport() throws Exception {
        Report report=new Report("Resumen mensual");
        report.addHeader("Sección");report.addHeader("Métrica");report.addHeader("Valor");
        report.addRow(List.of("Actividad del mes","Egresos invalidados","7"));
        report.addRow(List.of("Existencias actuales","Stock total (l)","12"));
        Path file=temp.resolve("mensual.pdf");
        var method=ReportsController.class.getDeclaredMethod("exportToPdfWithProgress",Report.class,File.class,String.class,Consumer.class,Supplier.class);
        method.setAccessible(true);
        method.invoke(new ReportsController(),report,file.toFile(),"Resumen",(Consumer<Double>)p->{},(Supplier<Boolean>)()->false);
        try(PDDocument doc=PDDocument.load(file.toFile())) {
            String text=new PDFTextStripper().getText(doc).replaceAll("\\s+","");
            assertTrue(doc.getPage(0).getMediaBox().getHeight() > doc.getPage(0).getMediaBox().getWidth());
            assertTrue(text.contains("Egresosinvalidados"));
            assertTrue(text.contains("Stocktotal(l)"));
            assertTrue(text.contains("7"));
            assertTrue(text.contains("12"));
        }
    }
    @Test public void monthlyDashboardRetainsCardsChartAndStateDetails() throws Exception {
        Report report=new Report("Resumen mensual");
        report.addHeader("Sección");report.addHeader("Métrica");report.addHeader("Valor");
        report.addRow(List.of("Existencias actuales","Productos no eliminados","4"));
        report.addRow(List.of("Existencias actuales","Bajo el mínimo","1"));
        report.addRow(List.of("Existencias actuales","Sin stock","1"));
        report.addRow(List.of("Actividad de 2026-10","Total de transacciones registradas","7"));
        report.addRow(List.of("Actividad de 2026-10","Ingresos — Activos","2"));
        report.addRow(List.of("Actividad de 2026-10","Egresos — Activos","3"));
        report.addRow(List.of("Actividad de 2026-10","Préstamos — Activos","1"));
        report.addRow(List.of("Actividad de 2026-10","Egresos — Invalidados","1"));
        report.addRow(List.of("Actividad de 2026-10","Usuarios con operaciones","2"));
        Path file=Path.of("target","report-previews","mensual.pdf");
        Files.createDirectories(file.getParent());
        var method=ReportsController.class.getDeclaredMethod("writeMonthlyDashboardRichPdf",Report.class,File.class,String.class);
        method.setAccessible(true);
        method.invoke(new ReportsController(),report,file.toFile(),"Octubre 2026");
        try(PDDocument doc=PDDocument.load(file.toFile())) {
            String text=new PDFTextStripper().getText(doc).replaceAll("\\s+","");
            assertTrue(text.contains("TotaldeProductosenSistema"));
            assertTrue(text.contains("Transaccionesvigentesdelmes"));
            assertTrue(text.contains("Egresos—Invalidados"));
            assertTrue(text.contains("Indicadorescomplementarios"));
            assertTrue(text.contains("14.3%"));
            var stripper=new PDFTextStripper(); stripper.setStartPage(2); stripper.setEndPage(2);
            String second=stripper.getText(doc).replaceAll("\\s+", "");
            assertTrue(second.contains("Transaccionesinvalidadas(%)"));
            assertFalse(second.contains("|"));
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,100),
                "png",file.resolveSibling("kpi_primera_pagina.png").toFile());
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(1,100),
                "png",file.resolveSibling("kpi_segunda_pagina.png").toFile());
            assertEquals(2,doc.getNumberOfPages());
        }
    }
    @Test public void wideReportStaysTabularAndPreservesFullBreakdown() throws Exception {
        Report report=new Report("Personas con Préstamos Activos");
        for(String header: List.of("ID préstamo","Código","DNI","Persona","Productos","Fecha de operación","Devolución prevista","Estado préstamo","Días de atraso")) report.addHeader(header);
        StringBuilder products=new StringBuilder("Pala: 4 unidad\nCemento: 3 saco");
        for(int i=0;i<100;i++) products.append("\nProducto ").append(i).append(": 2 unidad");
        products.append("\nULTIMO_PRODUCTO: 8 l");
        report.addRow(List.of("6","P-JM20261020-003","1807-2000-00124","Juan Pedro Martínez",products.toString(),"2026-10-20","2026-11-30","Pendiente","0"));
        Path file=temp.resolve("prestamos.pdf");
        var method=ReportsController.class.getDeclaredMethod("exportToPdfWithProgress",Report.class,File.class,String.class,Consumer.class,Supplier.class);
        method.setAccessible(true);
        method.invoke(new ReportsController(),report,file.toFile(),"Una transacción con desglose de productos",(Consumer<Double>)p->{},(Supplier<Boolean>)()->false);
        try(PDDocument doc=PDDocument.load(file.toFile())) {
            String text=new PDFTextStripper().getText(doc).replaceAll("\\s+","");
            assertTrue(doc.getNumberOfPages()>1);
            for (var page : doc.getPages()) {
                assertTrue(page.getMediaBox().getWidth() > page.getMediaBox().getHeight());
            }
            assertTrue(text.contains("ULTIMO_PRODUCTO:8l"));
            assertFalse(text.contains("Registro2"));
            assertEquals(1,text.split("JuanPedroMartínez",-1).length-1);
        }
        // Ejemplo compacto para revisar el diseño recuperado.
        report.getRows().set(0, new java.util.ArrayList<>(report.getRows().get(0)));
        report.getRows().get(0).set(4,"Pala: 4 unidad\nCemento: 3 saco");
        Path preview=Path.of("target","report-previews","prestamos.pdf");
        Files.createDirectories(preview.getParent());
        method.invoke(new ReportsController(),report,preview.toFile(),"Una transacción con desglose de productos",(Consumer<Double>)p->{},(Supplier<Boolean>)()->false);
        try(PDDocument doc=PDDocument.load(preview.toFile())) {
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,120), "png",
                preview.resolveSibling("prestamos.png").toFile());
        }
    }

    private void writePdf(Report report,Path file) throws Exception {
        var method=ReportsController.class.getDeclaredMethod("exportToPdfWithProgress",Report.class,File.class,String.class,Consumer.class,Supplier.class);
        method.setAccessible(true);
        method.invoke(new ReportsController(),report,file.toFile(),report.getGroupByColumn() == null
            ? "Usuarios del sistema, excluyendo las cuentas SYSTEM y las cuentas con rol ROOT."
            : "Reporte de usuarios organizados y agrupados por su rol asignado en el sistema.",(Consumer<Double>)p->{},(Supplier<Boolean>)()->false);
    }
    private Report groupedUsers() {
        Report report=new Report("Usuarios agrupados por rol");
        for(String h:List.of("ID","Nombre","Usuario","Rol","Estado","Fecha de registro")) report.addHeader(h);
        report.setGroupByColumn("Rol"); return report;
    }
    @Test public void groupedUsersPdfRepeatsSeparateTableHeadersAndKeepsOriginalDesign() throws Exception {
        Report report=groupedUsers();
        report.addRow(List.of("1","Consulta Uno","consulta1","CONSULTOR","Activo","2026-10-21 10:00:00"));
        report.addRow(List.of("2","Consulta Dos","consulta2","CONSULTOR","Activo","2026-10-21 10:00:00"));
        report.addRow(List.of("3","Operador Uno","operador1","OPERADOR","Inactivo","2026-10-21 10:00:00"));
        Path preview=Path.of("target","report-previews","usuarios_por_rol.pdf"); Files.createDirectories(preview.getParent());
        writePdf(report,preview);
        try(PDDocument doc=PDDocument.load(preview.toFile())) {
            assertEquals(1,doc.getNumberOfPages());
            assertTrue(doc.getPage(0).getMediaBox().getHeight()>doc.getPage(0).getMediaBox().getWidth());
            assertTrue(doc.getPage(0).getResources().getXObjectNames().iterator().hasNext(),"Debe conservar el logo");
            String text=new PDFTextStripper().getText(doc).replaceAll("\\s+","");
            assertTrue(text.contains("Usuarios—Consultor(2)"));
            assertTrue(text.contains("Usuarios—Operador(1)"));
            assertEquals(2,text.split("Fechaderegistro",-1).length-1);
            assertEquals(1,text.split("ConsultaUno",-1).length-1);
            assertEquals(1,text.split("OperadorUno",-1).length-1);
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,100),"png",preview.resolveSibling("usuarios_por_rol.png").toFile());
        }
        Report plain=new Report("Usuarios del sistema");
        for(String h:List.of("ID","Nombre","Usuario","Estado","Fecha de registro")) plain.addHeader(h);
        plain.addRow(List.of("1","Consulta Uno","consulta1","Activo","2026-10-21 10:00:00"));
        Path plainFile=preview.resolveSibling("usuarios.pdf"); writePdf(plain,plainFile);
        try(PDDocument doc=PDDocument.load(plainFile.toFile())) {
            String text=new PDFTextStripper().getText(doc);
            assertFalse(text.contains("Usuarios —"));
            assertTrue(text.contains("Consulta Uno"));
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,100),"png",preview.resolveSibling("usuarios.png").toFile());
        }
    }
    @Test public void groupedUsersContinueTheirOwnTableAcrossPagesAndCsvKeepsRole() throws Exception {
        Report report=groupedUsers();
        for(int i=0;i<90;i++) report.addRow(List.of(""+i,"Consulta_"+i,"usuario_"+i,"CONSULTOR","Activo","2026-10-21"));
        report.addRow(List.of("100","Operador final","operador_final","OPERADOR","Activo","2026-10-21"));
        Path file=temp.resolve("grupos-largos.pdf"); writePdf(report,file);
        try(PDDocument doc=PDDocument.load(file.toFile())) {
            assertTrue(doc.getNumberOfPages()>1);
            PDFTextStripper stripper=new PDFTextStripper();
            for(int i=1;i<doc.getNumberOfPages();i++) {
                stripper.setStartPage(i); stripper.setEndPage(i);
                String text=stripper.getText(doc).replaceAll("\\s+","");
                assertTrue(text.contains("Usuarios—Consultor(90)"));
                assertTrue(text.contains("Fechaderegistro"));
            }
            stripper.setStartPage(1); stripper.setEndPage(doc.getNumberOfPages());
            String text=stripper.getText(doc).replaceAll("\\s+","");
            assertTrue(text.contains("Consulta_89")); assertTrue(text.contains("Usuarios—Operador(1)"));
            assertEquals(1,text.split("operador_final",-1).length-1);
        }
        Path csv=temp.resolve("grupos.csv");
        var export=ReportsController.class.getDeclaredMethod("exportToCsv",Report.class,File.class); export.setAccessible(true);
        export.invoke(new ReportsController(),report,csv.toFile());
        String text=Files.readString(csv);
        assertTrue(text.startsWith("ID,Nombre,Usuario,Rol,Estado,Fecha de registro"));
        assertTrue(text.contains("CONSULTOR")); assertTrue(text.contains("OPERADOR"));
        assertEquals(6,report.getHeaders().size(),"Exportar PDF no debe modificar las columnas del CSV");
    }

    @Test public void transactionStatesUseBadgesAndFontsAreEmbeddedWithoutSystemScan() throws Exception {
        var messages=new java.util.ArrayList<java.util.logging.LogRecord>();
        var logger=java.util.logging.Logger.getLogger("org.apache.pdfbox.pdmodel.font.FileSystemFontProvider");
        var handler=new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) { messages.add(record); }
            public void flush() {} public void close() {}
        };
        logger.addHandler(handler);
        try {
            Report report=new Report("Historial de transacciones — Todos los estados"); report.setLandscape(true);
            for(String h:List.of("ID","Productos","Tipo","Estado","Fecha de operación","Usuario","Persona","Fecha de registro","Invalidación","Notas")) report.addHeader(h);
            report.addRow(List.of("1","Pala: 2 unidad","Ingreso","Activo","2026-10-21","operador","Persona A","2026-10-21","","Registro válido"));
            report.addRow(List.of("2","Cemento: 1 saco","Egreso","Invalidado","2026-10-21","operador","Persona B","2026-10-21","Motivo: Duplicado","Nota"));
            report.addRow(List.of("3","Agua: 1 l","Préstamo","Cancelado","2026-10-21","operador","Persona C","2026-10-21","","Cancelación"));
            Path file=Path.of("target","report-previews","estados_transacciones.pdf"); Files.createDirectories(file.getParent());
            writePdf(report,file);
            try(var doc=PDDocument.load(file.toFile())) {
                String text=new PDFTextStripper().getText(doc);
                for(String state:List.of("Activo","Invalidado","Cancelado")) assertTrue(text.contains(state));
                for(var page:doc.getPages()) for(var name:page.getResources().getFontNames()) {
                    var font=page.getResources().getFont(name);
                    assertTrue(font.isEmbedded()); assertTrue(font.getName().contains("DejaVuSans"));
                }
                var preview=new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,110);
                javax.imageio.ImageIO.write(preview,"png",file.resolveSibling("estados_transacciones.png").toFile());
                for(int color:new int[]{0x4CAF50,0xF44336,0x9E9E9E}) {
                    int pixels=0;
                    for(int y=0;y<preview.getHeight();y++) for(int x=0;x<preview.getWidth();x++)
                        if((preview.getRGB(x,y)&0xFFFFFF)==color) pixels++;
                    assertTrue(pixels>10,"Falta badge: "+Integer.toHexString(color));
                }
            }
            assertTrue(messages.isEmpty(),"La exportación no debe escanear fuentes del sistema");
            Path csv=temp.resolve("estados.csv");
            var export=ReportsController.class.getDeclaredMethod("exportToCsv",Report.class,File.class); export.setAccessible(true);
            export.invoke(new ReportsController(),report,csv.toFile());
            String text=Files.readString(csv);
            assertTrue(text.contains(",Activo,")); assertTrue(text.contains(",Invalidado,")); assertTrue(text.contains(",Cancelado,"));
        } finally { logger.removeHandler(handler); }
    }

}
