package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.BelongsTo;
import org.javalite.activejdbc.annotations.Table;

/**
 * Modelo para registros de backups de la base de datos
 */
@Table("backups")
@BelongsTo(parent = User.class, foreignKeyName = "usuario_id")
public class Backup extends Model {

    /**
     * Obtiene el usuario que creó el backup
     */
    public User getUsuario() {
        return parent(User.class);
    }

    /**
     * Obtiene el nombre del usuario
     */
    public String getUsuarioNombre() {
        User user = getUsuario();
        return user != null ? user.getString("username") : "Desconocido";
    }

    /**
     * Verifica si el backup fue exitoso
     */
    public boolean isSuccessful() {
        return "SUCCESS".equals(getString("status"));
    }

    /**
     * Obtiene el tamaño en MB
     */
    public double getSizeMB() {
        Long sizeBytes = getLong("size_bytes");
        return sizeBytes != null ? sizeBytes / (1024.0 * 1024.0) : 0.0;
    }

    /**
     * Obtiene el tamaño formateado en la unidad apropiada (bytes, KB, MB o GB)
     */
    public String getSizeFormatted() {
        Long sizeBytes = getLong("size_bytes");
        if (sizeBytes == null || sizeBytes == 0) {
            return "0 bytes";
        }

        // Menos de 1 KB - mostrar en bytes
        if (sizeBytes < 1024) {
            return sizeBytes + " bytes";
        }

        // Menos de 1 MB - mostrar en KB
        if (sizeBytes < 1024 * 1024) {
            double sizeKB = sizeBytes / 1024.0;
            return String.format("%.2f KB", sizeKB);
        }

        // 1 MB o más - mostrar en MB
        if (sizeBytes < 1024 * 1024 * 1024) {
            double sizeMB = sizeBytes / (1024.0 * 1024.0);
            return String.format("%.2f MB", sizeMB);
        }

        // 1 GB o más - mostrar en GB
        double sizeGB = sizeBytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.2f GB", sizeGB);
    }
}

