package com.joshuamc1.sicam.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class AutoBackupManagerTest {
    @TempDir Path temp;
    private Path legacy() { return temp.resolve(".inventario_municipal/.cache/backup_control.properties"); }
    private Path current(Path folder) { return folder.resolve(".cache/backup_control.properties"); }
    private Properties read(Path path) throws Exception {
        var values=new Properties(); try(var input=Files.newInputStream(path)) { values.load(input); } return values;
    }
    @Test void newConfigurationIsCreatedUnderSicamAndDefaultsToDisabled() throws Exception {
        Path folder=temp.resolve(".sicam"); var manager=new AutoBackupManager(folder,legacy());
        assertTrue(Files.exists(current(folder))); assertFalse(manager.isAutoBackupEnabled());
        manager.setAutoBackupEnabled(true);
        assertEquals("true",read(current(folder)).getProperty("auto_backup_enabled"));
        assertFalse(Files.exists(legacy()));
    }
    @Test void migrationPreservesEnabledSettingDatesAndOriginalFile() throws Exception {
        Files.createDirectories(legacy().getParent());
        String original="auto_backup_enabled=true\nlast_startup_backup_date=2026-10-21\nlast_startup_backup_time=2026-10-21 08:00:00\nlast_shutdown_backup_date=2026-10-20\nlast_shutdown_backup_time=2026-10-20 17:00:00\n";
        Files.writeString(legacy(),original); Path folder=temp.resolve(".sicam");
        var manager=new AutoBackupManager(folder,legacy());
        assertTrue(manager.isAutoBackupEnabled());
        assertEquals("2026-10-21 08:00:00",manager.getLastStartupBackupInfo());
        assertEquals("2026-10-20 17:00:00",manager.getLastShutdownBackupInfo());
        assertEquals(original,Files.readString(legacy()));
        manager.setAutoBackupEnabled(false);
        assertEquals("false",read(current(folder)).getProperty("auto_backup_enabled"));
        assertEquals(original,Files.readString(legacy()));
    }
    @Test void existingNewConfigurationWinsAndResetDoesNotModifyLegacy() throws Exception {
        Files.createDirectories(legacy().getParent()); Files.writeString(legacy(),"auto_backup_enabled=true\n");
        Path folder=temp.resolve("custom/secure"); Files.createDirectories(current(folder).getParent());
        Files.writeString(current(folder),"auto_backup_enabled=false\nlast_startup_backup_date=2026-10-22\n");
        var manager=new AutoBackupManager(folder,legacy());
        assertFalse(manager.isAutoBackupEnabled()); assertEquals("2026-10-22",manager.getLastStartupBackupInfo());
        manager.resetCache(); assertEquals("",read(current(folder)).getProperty("last_startup_backup_date"));
        assertEquals("true",read(legacy()).getProperty("auto_backup_enabled"));
    }
}
