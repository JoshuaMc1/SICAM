package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.*;
import com.joshuamc1.sicam.models.*;
import com.joshuamc1.sicam.repositories.AuditRepository;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class PagedDatabaseOrderTest {
    private Connection connection;
    private final List<Integer> ids=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        connection=DriverManager.getConnection("jdbc:sqlite::memory:");
        try(Statement s=connection.createStatement()) {
            s.execute("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT,name TEXT,status TEXT,rol_id INTEGER)");
            s.execute("INSERT INTO users VALUES(1,'root','Root','active',1)");
            s.execute("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,status TEXT,activo INTEGER)");
            s.execute("INSERT INTO roles VALUES(1,'ROOT','active',1)");
            s.execute("CREATE TABLE backups(id INTEGER PRIMARY KEY,filename TEXT,size_bytes INTEGER,usuario_id INTEGER,error_message TEXT,created_at TEXT,status TEXT)");
            s.execute("CREATE TABLE audit(id INTEGER PRIMARY KEY,user_id INTEGER,action TEXT,table_name TEXT,record_id INTEGER,details TEXT,created_at TEXT)");
        }
        for(int id=1;id<=35;id++) ids.add(id);
        ids.addAll(List.of(100,11200));
        try(PreparedStatement b=connection.prepareStatement("INSERT INTO backups VALUES(?,?,?,1,'',?,'SUCCESS')");
            PreparedStatement a=connection.prepareStatement("INSERT INTO audit VALUES(?,1,'update','products',?,?,?)")) {
            for(int id:ids) {
                String date=String.format("2026-10-%02d 12:00:00",1+id%28);
                b.setInt(1,id); b.setString(2,"backup_"+id); b.setLong(3,size(id)); b.setString(4,date); b.executeUpdate();
                a.setInt(1,id); a.setInt(2,12000-id); a.setString(3,id%2==0 ? "QA-filter" : "other"); a.setString(4,date); a.executeUpdate();
            }
        }
        Base.attach(connection); Session.setCurrentUser(User.findById(1));
    }
    @AfterEach void cleanup() throws Exception { Session.setCurrentUser(null); Base.detach(); connection.close(); }
    private long size(int id) { return (id%7)*1000000L+id; }
    private PageSort sort(String field,boolean descending) { return new PageSort(List.of(new PageSort.Key(field,descending))); }
    @Test void backupIdSortIsGlobalAcrossFourPagesInBothDirections() {
        var service=new BackupService();
        for(boolean descending:List.of(false,true)) {
            List<Integer> actual=new ArrayList<>();
            for(int offset=0;offset<ids.size();offset+=10) actual.addAll(service.listBackupsPaged(offset,10,sort("id",descending)).stream().map(b->b.getInteger("id")).collect(Collectors.toList()));
            List<Integer> expected=new ArrayList<>(ids); expected.sort(descending ? Comparator.reverseOrder() : Comparator.naturalOrder());
            assertEquals(expected,actual); assertEquals(ids.size(),new HashSet<>(actual).size());
        }
    }
    @Test void backupSizeAndDateSortUseStoredValuesBeforeLimit() {
        var service=new BackupService();
        List<Integer> expected=new ArrayList<>(ids);
        expected.sort(Comparator.comparingLong(this::size).thenComparing(Comparator.reverseOrder()));
        assertEquals(expected.subList(10,20),service.listBackupsPaged(10,10,sort("size",false)).stream().map(b->b.getInteger("id")).collect(Collectors.toList()));
        expected.sort(Comparator.comparingInt((Integer id)->1+id%28).thenComparing(Comparator.reverseOrder()));
        assertEquals(expected.subList(0,10),service.listBackupsPaged(0,10,sort("date",false)).stream().map(b->b.getInteger("id")).collect(Collectors.toList()));
    }
    @Test void auditIdAndRecordSortApplyBeforePaginationAndKeepSearch() {
        var repository=new AuditRepository();
        List<Integer> actual=new ArrayList<>();
        for(int offset=0;offset<ids.size();offset+=10) actual.addAll(repository.findPageWithUser(offset,10,null,sort("id",false)).stream().map(r->r.getId()).collect(Collectors.toList()));
        assertEquals(ids,actual);
        List<Integer> expected=ids.stream().filter(id->id%2==0).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        assertEquals(expected.subList(0,10),repository.findPageWithUser(0,10,"QA-filter",sort("record",false)).stream().map(r->r.getId()).collect(Collectors.toList()));
        assertEquals(expected.size(),repository.countWithFilter("QA-filter"));
    }
    @Test void sqlSortRejectsUnknownColumnsAndUsesStableDefault() {
        assertThrows(IllegalArgumentException.class,()->new BackupService().listBackupsPaged(0,10,sort("id; DROP TABLE backups",false)));
        assertThrows(IllegalArgumentException.class,()->new AuditRepository().findPageWithUser(0,10,null,sort("not_a_column",false)));
        assertEquals(11200,new BackupService().listBackupsPaged(0,10).get(0).getInteger("id"));
        assertEquals(37,new AuditRepository().countWithFilter(null));
    }
}
