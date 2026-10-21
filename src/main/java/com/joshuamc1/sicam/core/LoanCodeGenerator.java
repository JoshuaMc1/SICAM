package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.models.Person;
import com.joshuamc1.sicam.models.Transaction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generador de códigos únicos para préstamos
 * Formato: P-{INICIALES}{YYYYMMDD}-{SECUENCIA}
 * Ejemplo: P-JA20251127-001
 */
public class LoanCodeGenerator {

    /**
     * Genera un código único de préstamo basado en la persona y fecha
     *
     * @param person Persona a la que se le hace el préstamo
     * @param date Fecha del préstamo
     * @return Código único del préstamo (ej: P-JA20251127-001)
     */
    public static String generateLoanCode(Person person, LocalDate date) {
        if (person == null || date == null) {
            throw new IllegalArgumentException("Persona y fecha no pueden ser null");
        }

        // 1. Prefijo fijo
        String prefix = "P";

        // 2. Obtener iniciales de la persona (primer letra nombre + primer letra apellido)
        String initials = getInitials(person);

        // 3. Formatear fecha como YYYYMMDD
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 4. Obtener secuencia del día para esta persona
        int sequence = getNextSequence(person, date);

        // 5. Construir código final
        return String.format("%s-%s%s-%03d", prefix, initials, dateStr, sequence);
    }

    /**
     * Obtiene las iniciales de la persona (primera letra nombre + primera letra apellido)
     *
     * @param person Persona
     * @return Iniciales en mayúsculas (ej: "JA" para Juan Almendares)
     */
    private static String getInitials(Person person) {
        // El modelo Person tiene first_name y last_name, no name
        String firstName = person.getString("first_name");
        String lastName = person.getString("last_name");

        if (firstName == null || firstName.trim().isEmpty()) {
            return "XX"; // Valor por defecto
        }

        // Primera letra del nombre
        String firstInitial = firstName.trim().substring(0, 1).toUpperCase();

        // Primera letra del apellido (si existe)
        String lastInitial;
        if (lastName != null && !lastName.trim().isEmpty()) {
            lastInitial = lastName.trim().substring(0, 1).toUpperCase();
        } else {
            // Si no hay apellido, usar segunda letra del nombre o repetir la primera
            if (firstName.trim().length() > 1) {
                lastInitial = firstName.trim().substring(1, 2).toUpperCase();
            } else {
                lastInitial = firstInitial; // Repetir la inicial del nombre
            }
        }

        return firstInitial + lastInitial;
    }

    /**
     * Obtiene el siguiente número de secuencia para préstamos de esta persona en esta fecha
     *
     * @param person Persona
     * @param date Fecha
     * @return Número de secuencia (empezando en 1)
     */
    private static int getNextSequence(Person person, LocalDate date) {
        String codePrefix = "P-" + getInitials(person) + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        String suffix;
        try { suffix = org.javalite.activejdbc.Base.connection().getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL") ? " FOR UPDATE" : ""; }
        catch (java.sql.SQLException ex) { throw new IllegalStateException("No se pudo generar el código del préstamo", ex); }
        java.util.List<Transaction> loans = Transaction.findBySQL("SELECT * FROM transactions WHERE type = 'loan' AND loan_code LIKE ?" + suffix, codePrefix + "%");
        int maximum = 0;
        for (Transaction loan : loans) {
            String existing = loan.getString("loan_code");
            try { maximum = Math.max(maximum, Integer.parseInt(existing.substring(codePrefix.length()))); }
            catch (NumberFormatException ex) { throw new IllegalStateException("Código de préstamo existente inválido: " + existing, ex); }
        }
        return Math.addExact(maximum, 1);
    }

    /**
     * Valida si un código de préstamo tiene el formato correcto
     *
     * @param loanCode Código a validar
     * @return true si el formato es válido
     */
    public static boolean isValidLoanCode(String loanCode) {
        if (loanCode == null || loanCode.isEmpty()) {
            return false;
        }

        // Formato esperado: P-XX20251127-001
        // P: prefijo fijo
        // XX: dos letras (iniciales)
        // YYYYMMDD: fecha (8 dígitos)
        // 001: secuencia (3 dígitos)
        return loanCode.matches("^P-[A-Z]{2}\\d{8}-\\d{3}$");
    }

    /**
     * Extrae la fecha del código de préstamo
     *
     * @param loanCode Código del préstamo
     * @return Fecha extraída del código
     */
    public static LocalDate extractDateFromCode(String loanCode) {
        if (!isValidLoanCode(loanCode)) {
            throw new IllegalArgumentException("Código de préstamo inválido: " + loanCode);
        }

        // Extraer YYYYMMDD del código: P-XX20251127-001
        // Posición 5-12
        String dateStr = loanCode.substring(5, 13);
        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /**
     * Extrae las iniciales del código de préstamo
     *
     * @param loanCode Código del préstamo
     * @return Iniciales extraídas
     */
    public static String extractInitialsFromCode(String loanCode) {
        if (!isValidLoanCode(loanCode)) {
            throw new IllegalArgumentException("Código de préstamo inválido: " + loanCode);
        }

        // Extraer XX del código: P-XX20251127-001
        // Posición 2-3
        return loanCode.substring(2, 4);
    }
}

