package com.joshuamc1.sicam.export;

import com.joshuamc1.sicam.models.dto.AuditDTO;
import com.joshuamc1.sicam.repositories.AuditRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class AuditExportTest {
    @TempDir public Path temp;

    @Test public void allRecordsHaveNoThousandRowLimitAndDateRangeIncludesEntireDay() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try(var s=c.createStatement()) {
                s.execute("CREATE TABLE users(id INTEGER,name TEXT,username TEXT)");
                s.execute("CREATE TABLE audit(id INTEGER,user_id INTEGER,action TEXT,table_name TEXT,record_id INTEGER,details TEXT,created_at TEXT)");
                s.execute("INSERT INTO users VALUES(1,'Nombre compartido','admin2')");
            }
            try(var ps=c.prepareStatement("INSERT INTO audit VALUES(?,1,'update','products',42,?,?)")) {
                for(int i=1;i<=1005;i++) {
                    ps.setInt(1,i); ps.setString(2,"Detalle "+i);
                    ps.setString(3,i==1005?"2026-10-22 00:00:00":"2026-10-21 23:59:59"); ps.addBatch();
                }
                ps.executeBatch();
            }
            // LEFT JOIN debe conservar eventos aunque no encuentre al usuario.
            try(var s=c.createStatement()) { s.execute("INSERT INTO audit VALUES(1006,99,'delete','people',5,'Sin usuario','2026-10-21 00:00:00')"); }
            Base.attach(c);
            try {
                var repo=new AuditRepository(); var all=repo.findAllWithUser();
                assertEquals(1006,all.size()); assertEquals(1006,all.get(0).getId());
                assertEquals("admin2",all.get(1).getUsername()); assertEquals(99,all.get(0).getUsuarioId());
                var range=repo.findAllWithUserByDateRange(LocalDate.parse("2026-10-21"),LocalDate.parse("2026-10-21"));
                assertEquals(1005,range.size()); assertTrue(range.stream().noneMatch(r->r.getId()==1005));
                assertEquals("admin2",range.get(1).getUsername());
                assertEquals(10,repo.findPageWithUser(0,10,null).size());
            } finally { Base.detach(); }
        }
    }

    @Test public void csvPreservesQuotedAndMultilineDetailsAndAllIdentifiers() throws Exception {
        var rows=List.of(new AuditDTO(8,4,"Persona \"QA\"","update","products",42,"Solo \"comillas\"","2026-10-21 23:59:59","admin2"),
            new AuditDTO(null,null,null,"create",null,null,"Línea uno, con coma\nLínea dos",null,null));
        Path file=temp.resolve("auditoria.csv"); CsvAuditExporter.export(rows,file.toFile());
        String text=Files.readString(file);
        assertTrue(text.startsWith("id,usuario_id,usuario_name,accion,tabla_afectada,registro_afectado_id,detalles,fecha,usuario\r\n"));
        assertTrue(text.contains("8,4,\"Persona \"\"QA\"\"\",update,products,42,\"Solo \"\"comillas\"\"\",2026-10-21 23:59:59,admin2"));
        assertTrue(text.contains("\"Línea uno, con coma\nLínea dos\""));
        assertFalse(text.contains("null")); assertTrue(text.contains("\r\n,,,create,,,"));
    }

    @Test public void horizontalPdfPreservesHugeDetailsAndRepeatsHeadersWithEmbeddedFonts() throws Exception {
        StringBuilder details=new StringBuilder("Primer párrafo\n");
        for(int i=0;i<700;i++) details.append("CAMBIO_").append(i).append(": valor anterior, valor nuevo. ");
        details.append("\n").append("TOKEN".repeat(100)).append("FIN_TOKEN\nULTIMO_DETALLE");
        String name="Usuario con nombres largos y apellidos completos ApellidoFinal";
        var rows=List.of(new AuditDTO(8,4,name,"invalidate_multiple_transaction_items_with_stock_adjustment","transaction_items",42,details.toString(),"2026-10-21 23:59:59","admin2"),
            new AuditDTO(9,3,"Operador","create","products",77,"REGISTRO_FINAL","2026-10-22 00:00:00","operador"));
        Path file=temp.resolve("auditoria-larga.pdf");
        PdfAuditExporter.export(rows,file.toFile(),p->{},()->false,"root","Del 2026-10-21 al 2026-10-22");
        try(var doc=PDDocument.load(file.toFile())) {
            assertTrue(doc.getNumberOfPages()>2);
            var stripper=new PDFTextStripper();
            String text=stripper.getText(doc).replaceAll("\\s+","");
            assertTrue(text.contains(name.replaceAll("\\s+",""))); assertTrue(text.contains("@admin2"));
            assertTrue(text.contains("ID:4")); assertTrue(text.contains("23:59:59"));
            assertTrue(text.contains("invalidate_multiple_transaction_items_with_stock_adjustment"));
            for(int i=0;i<700;i++) assertTrue(text.contains("CAMBIO_"+i+":"));
            assertTrue(text.contains("TOKEN".repeat(100)+"FIN_TOKEN"));
            assertTrue(text.contains("ULTIMO_DETALLE")); assertTrue(text.contains("REGISTRO_FINAL"));
            for(int i=0;i<doc.getNumberOfPages();i++) {
                var page=doc.getPage(i); assertTrue(page.getMediaBox().getWidth()>page.getMediaBox().getHeight());
                stripper.setStartPage(i+1); stripper.setEndPage(i+1);
                String pageText=stripper.getText(doc); assertTrue(pageText.contains("Detalles")); assertTrue(pageText.contains("Fecha y hora"));
                for(var key:page.getResources().getFontNames()) assertTrue(page.getResources().getFont(key).isEmbedded());
            }
        }
        Path preview=Path.of("target","report-previews","auditoria.pdf"); Files.createDirectories(preview.getParent());
        var compact=List.of(new AuditDTO(8,4,name,"invalidate","transactions",42,
            "Transacción invalidada.\nMotivo: registro duplicado.\nProducto: Cemento; cantidad: 3 sacos.\nStock anterior: 10.00; stock final: 13.00.","2026-10-21 23:59:59","admin2"));
        PdfAuditExporter.export(compact,preview.toFile(),null,()->false,"root");
        try(var doc=PDDocument.load(preview.toFile())) {
            javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,110),"png",preview.resolveSibling("auditoria.png").toFile());
        }
    }

    @Test public void cancellationDoesNotWriteAPartialPdf() throws Exception {
        Path file=temp.resolve("cancelado.pdf"); Files.writeString(file,"contenido previo");
        PdfAuditExporter.export(List.of(new AuditDTO(1,1,"Root","update","users",1,"Detalle","2026-10-21")),
            file.toFile(),null,()->true,"root");
        assertEquals("contenido previo",Files.readString(file));
    }
}
