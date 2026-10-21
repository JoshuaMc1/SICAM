import com.joshuamc1.sicam.config.MySQLConfigManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Captura de diagnóstico: solo SELECT, sin contraseñas ni hashes de usuarios. */
public class CaptureQaSnapshot {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Uso: CaptureQaSnapshot SQL OUTPUT CHECKPOINT");
        if (!MySQLConfigManager.hasConfig()) throw new IllegalStateException("Abra SICAM y configure MySQL antes de capturar");
        String query=Files.readString(Path.of(args[0]),StandardCharsets.UTF_8);
        // Evita que una edición accidental convierta esta herramienta en un ejecutor de escrituras.
        if (!query.stripLeading().startsWith("SELECT JSON_OBJECT(") || query.contains(";"))
            throw new IllegalArgumentException("La captura admite únicamente su SELECT JSON_OBJECT sin separadores de sentencias");
        Class.forName("com.mysql.cj.jdbc.Driver");
        Properties properties=new Properties();
        properties.setProperty("user",MySQLConfigManager.getUsername());
        properties.setProperty("password",MySQLConfigManager.getPassword());
        properties.setProperty("connectTimeout","10000");
        properties.setProperty("socketTimeout","60000");
        try (Connection connection=DriverManager.getConnection(MySQLConfigManager.getJdbcUrl(),properties)) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try (PreparedStatement statement=connection.prepareStatement(query)) {
                statement.setString(1,args[2]);
                statement.setQueryTimeout(60);
                try (ResultSet result=statement.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("La consulta no devolvió la captura");
                    Files.writeString(Path.of(args[1]),result.getString(1)+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                }
            } finally { connection.rollback(); }
        }
        System.out.println("Captura de solo lectura guardada: "+args[1]);
    }
}
