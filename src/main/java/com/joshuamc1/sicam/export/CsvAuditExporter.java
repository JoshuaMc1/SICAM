package com.joshuamc1.sicam.export;

import com.joshuamc1.sicam.models.dto.AuditDTO;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** CSV de auditoría completo; conserva los detalles, comillas y saltos de línea. */
public final class CsvAuditExporter {
    private CsvAuditExporter() {}
    public static void export(List<AuditDTO> data, File file) throws IOException {
        try (var writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("id,usuario_id,usuario_name,accion,tabla_afectada,registro_afectado_id,detalles,fecha,usuario\r\n");
            for (AuditDTO row : data) {
                Object[] values = {row.getId(), row.getUsuarioId(), row.getUsuarioName(), row.getAccion(),
                    row.getTablaAfectada(), row.getRegistroAfectadoId(), row.getDetalles(), row.getFecha(), row.getUsername()};
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) writer.write(',');
                    String value = values[i] == null ? "" : values[i].toString();
                    if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r"))
                        value = "\"" + value.replace("\"", "\"\"") + "\"";
                    writer.write(value);
                }
                writer.write("\r\n");
            }
        }
    }
}
