package com.joshuamc1.sicam.export;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.models.dto.AuditDTO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

/** Auditoría horizontal con detalles íntegros y encabezados repetidos en cada página. */
public final class PdfAuditExporter {
    private PdfAuditExporter() {}
    public static void export(List<AuditDTO> data, File outFile, Consumer<Double> progress,
                              Supplier<Boolean> cancelled, String currentUser) throws IOException {
        export(data, outFile, progress, cancelled, currentUser, "Todos los registros");
    }

    public static void export(List<AuditDTO> data, File outFile, Consumer<Double> progress,
                              Supplier<Boolean> cancelled, String currentUser, String scope) throws IOException {
        if (data == null) data = List.of();
        try (PDDocument doc = new PDDocument()) {
            PDFont regular = loadFont(doc, "DejaVuSans.ttf"), bold = loadFont(doc, "DejaVuSans-Bold.ttf");
            PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            float margin = 40, tableWidth = size.getWidth() - margin * 2, lineHeight = 11;
            float[] weights = {0.045f,0.18f,0.135f,0.105f,0.065f,0.145f,0.325f};
            float[] widths = new float[weights.length];
            for(int i=0;i<widths.length;i++) widths[i]=tableWidth*weights[i];
            PDPage page = new PDPage(size); doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc,page);
            try {
                float y = drawLogo(doc,cs,size.getWidth(),size.getHeight()-margin);
                y = drawCenteredText(cs,bold,16,"Reporte de auditoría",size.getWidth(),y)-8;
                cs.setFont(regular,9);
                drawCell(cs,"Generado por: "+(currentUser==null?"-":currentUser),margin,y);
                String date="Generado: "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                drawCell(cs,date,size.getWidth()-margin-regular.getStringWidth(date)/1000*9,y);
                y-=16;
                drawCell(cs,scope+" | Registros: "+data.size(),margin,y); y-=24;
                y=drawHeader(cs,bold,margin,y,widths);
                for(int index=0;index<data.size();index++) {
                    if(cancelled!=null && cancelled.get()) return;
                    AuditDTO row=data.get(index);
                    String actor=(row.getUsuarioId()==null?"":"ID: "+row.getUsuarioId()+"\n")
                        +safe(row.getUsuarioName())+(row.getUsername()==null?"":"\n@"+row.getUsername());
                    String[] values={safe(row.getId()),actor,safe(row.getAccion()),safe(row.getTablaAfectada()),
                        safe(row.getRegistroAfectadoId()),safe(row.getFecha()),safe(row.getDetalles())};
                    List<List<String>> cells=new ArrayList<>(); int maxLines=1;
                    for(int col=0;col<values.length;col++) {
                        var lines=wrap(values[col],regular,8,widths[col]-6);
                        cells.add(lines); maxLines=Math.max(maxLines,lines.size());
                    }
                    int offset=0;
                    while(offset<maxLines) {
                        if(cancelled!=null && cancelled.get()) return;
                        int available=(int)((y-margin-5)/lineHeight);
                        int fullPage=(int)((size.getHeight()-margin*2-24)/lineHeight);
                        if(available<=0 || (offset==0 && maxLines<=fullPage && maxLines>available)) {
                            cs.close(); page=new PDPage(size); doc.addPage(page);
                            cs=new PDPageContentStream(doc,page);
                            y=drawHeader(cs,bold,margin,size.getHeight()-margin,widths);
                            available=(int)((y-margin-5)/lineHeight);
                        }
                        int count=Math.min(available,maxLines-offset); float x=margin;
                        cs.setFont(regular,8);
                        for(int col=0;col<cells.size();col++) {
                            var lines=cells.get(col);
                            if(col==0 && offset>0) drawCell(cs,values[0],x,y);
                            for(int j=0;j<count && offset+j<lines.size();j++) drawCell(cs,lines.get(offset+j),x,y-j*lineHeight);
                            x+=widths[col];
                        }
                        y-=count*lineHeight+6; offset+=count;
                    }
                    if(progress!=null) progress.accept((index+1d)/data.size());
                }
                if(data.isEmpty()) { cs.setFont(regular,9); drawCell(cs,"No hay registros para los criterios seleccionados.",margin,y); }
            } finally { cs.close(); }
            doc.save(outFile);
        }
    }

    private static PDFont loadFont(PDDocument doc,String filename) throws IOException {
        try(InputStream stream=PdfAuditExporter.class.getResourceAsStream("/fonts/"+filename)) {
            if(stream==null) throw new IOException("Falta la fuente incluida para auditoría: "+filename);
            return PDType0Font.load(doc,stream,true);
        }
    }

    private static float drawLogo(PDDocument doc, PDPageContentStream cs, float pageWidth, float y) {
        try (InputStream is = PdfAuditExporter.class.getResourceAsStream("/images/logo.png")) {
            if (is != null) {
                byte[] img = is.readAllBytes();
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, img, "logo");
                float w = 70f, h = 70f, x = (pageWidth - w)/2f, imgY = y - h;
                cs.drawImage(pdImage, x, imgY, w, h);
                return imgY - 10f;
            }
        } catch (IOException e) {
            Logs.warn(PdfAuditExporter.class, "Logo no cargado: {}", e.getMessage());
        }
        return y;
    }

    private static float drawCenteredText(PDPageContentStream cs, PDFont font, int size, String text, float pageWidth, float y) throws IOException {
        float textWidth = font.getStringWidth(text)/1000*size;
        float x = (pageWidth - textWidth)/2f;
        cs.setFont(font,size);
        cs.beginText(); cs.newLineAtOffset(x, y); cs.showText(text); cs.endText();
        return y - size - 4f;
    }

    private static float drawHeader(PDPageContentStream cs, PDFont bold, float margin, float y, float[] widths) throws IOException {
        String[] labels={"ID","Usuario","Acción","Tabla","Reg. ID","Fecha y hora","Detalles"};
        cs.setFont(bold,9); float x=margin;
        for(int i=0;i<labels.length;i++) { drawCell(cs,labels[i],x,y); x+=widths[i]; }
        return y-19;
    }

    private static void drawCell(PDPageContentStream cs,String value,float x,float y) throws IOException {
        cs.beginText(); cs.newLineAtOffset(x+2,y); cs.showText(value); cs.endText();
    }

    private static String safe(Object value) { return value==null?"":value.toString(); }

    private static List<String> wrap(String value,PDFont font,int size,float width) throws IOException {
        List<String> lines=new ArrayList<>();
        for(String paragraph:value.replace("\r\n","\n").replace('\r','\n').replace('\t',' ').split("\n",-1)) {
            StringBuilder line=new StringBuilder();
            for(int offset=0;offset<paragraph.length();) {
                int cp=paragraph.codePointAt(offset); offset+=Character.charCount(cp);
                if(Character.isISOControl(cp)) cp=' ';
                String next=new String(Character.toChars(cp));
                if(line.length()>0 && font.getStringWidth(line+next)/1000*size>width) {
                    int space=line.lastIndexOf(" ");
                    if(space>0) { lines.add(line.substring(0,space)); String rest=line.substring(space+1); line.setLength(0); line.append(rest); }
                    else { lines.add(line.toString()); line.setLength(0); }
                }
                line.append(next);
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
