package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.*;
import com.joshuamc1.sicam.models.dto.TransactionItemDTO;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TransactionSafetyTest {
    private Connection connection;
    private final TransactionService service = new TransactionService();
    @BeforeEach void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        sql("PRAGMA foreign_keys=ON");
        sql("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,status TEXT,activo INTEGER)");
        sql("CREATE TABLE users(id INTEGER PRIMARY KEY,rol_id INTEGER,status TEXT,username TEXT,session_token TEXT,session_expires_at TEXT)");
        sql("CREATE TABLE people(id INTEGER PRIMARY KEY,first_name TEXT,last_name TEXT,status TEXT)");
        sql("CREATE TABLE products(id INTEGER PRIMARY KEY,code TEXT UNIQUE,name TEXT,description TEXT,unit TEXT,current_stock DECIMAL,minimum_stock DECIMAL,status TEXT)");
        sql("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT,date DATE,user_id INTEGER,person_id INTEGER,notes TEXT,total_items INTEGER,status TEXT DEFAULT 'active',invalidated_at TIMESTAMP,invalidated_by INTEGER,invalidation_reason TEXT,loan_code TEXT UNIQUE,loan_status TEXT DEFAULT 'pending',expected_return_date DATE,actual_return_date TIMESTAMP,related_transaction_id INTEGER,return_type TEXT,return_notes TEXT)");
        sql("CREATE TABLE transaction_items(id INTEGER PRIMARY KEY AUTOINCREMENT,transaction_id INTEGER REFERENCES transactions(id),product_id INTEGER REFERENCES products(id),quantity DECIMAL,notes TEXT,status TEXT DEFAULT 'active')");
        sql("CREATE TABLE audit(id INTEGER PRIMARY KEY,user_id INTEGER,action TEXT,table_name TEXT,record_id INTEGER,details TEXT)");
        sql("INSERT INTO roles VALUES(1,'ROOT','active',1)"); sql("INSERT INTO users(id,rol_id,status,username) VALUES(1,1,'active','root')");
        sql("INSERT INTO people VALUES(1,'Juan','Alvarez','active'),(2,'Jose','Aguilar','active')");
        sql("INSERT INTO products VALUES(1,'ABC','Producto','','unidad',10,0,'active'),(2,'DEF','Segundo','','unidad',10,0,'active')");
        Base.attach(connection); Session.setCurrentUser(User.findById(1));
    }
    @AfterEach void close() throws Exception {
        if (!connection.getAutoCommit()) connection.rollback();
        Session.setCurrentUser(null); Base.detach(); connection.close();
    }
    private void sql(String query) throws Exception { try (Statement statement = connection.createStatement()) { statement.execute(query); } }
    private double stock() throws Exception { return number("SELECT current_stock FROM products WHERE id=1"); }
    private double number(String query) throws Exception { try (Statement s=connection.createStatement(); ResultSet r=s.executeQuery(query)) { assertTrue(r.next()); return r.getDouble(1); } }
    private Transaction fresh(Transaction t) { return Transaction.findById(t.getId()); }
    private List<TransactionItemDTO> items(double quantity) { return List.of(new TransactionItemDTO(1,quantity,"")); }
    private Transaction create(String type,double quantity) { return service.createMultiProductTransaction(type,null,1,1,"",items(quantity)); }
    private Transaction loan() { return service.createLoan(null,1,1,"",LocalDate.now().plusDays(1),items(4)); }
    private Transaction returned(Transaction loan) { return service.createLoanReturn(loan.getInteger("id"),"same_product","",items(4)); }
    private void auditFailure() throws Exception { sql("CREATE TRIGGER fail_audit BEFORE INSERT ON audit BEGIN SELECT RAISE(ABORT,'fallo simulado'); END"); }

    @Test void normalFlowBalancesExactly() throws Exception {
        var income=create("in",5); assertEquals(15,stock());
        var out=create("out",4); assertEquals(11,stock());
        service.invalidate(out,"Error"); assertEquals(15,stock()); service.restore(out); assertEquals(11,stock());
        service.invalidate(income,"Error"); assertEquals(6,stock()); service.restore(income); assertEquals(11,stock());
        assertTrue(connection.getAutoCommit());
    }
    @Test void rejectsInsufficientStockIncludingDuplicateProductRows() throws Exception {
        assertThrows(IllegalStateException.class,()->create("out",15));
        assertThrows(IllegalStateException.class,()->service.createMultiProductTransaction("out",null,1,1,"",List.of(new TransactionItemDTO(1,6d,""),new TransactionItemDTO(1,6d,""))));
        assertEquals(10,stock()); assertEquals(0,number("SELECT COUNT(*) FROM transactions"));
    }
    @Test void rejectsInvalidQuantitiesBeforeWriting() throws Exception {
        for (double quantity : new double[]{-1,0,Double.NaN,Double.POSITIVE_INFINITY,1.001,100000000})
            assertThrows(IllegalArgumentException.class,()->create("out",quantity));
        assertEquals(10,stock()); assertEquals(0,number("SELECT COUNT(*) FROM transactions"));
    }
    @Test void spentIncomeCannotBeInvalidated() throws Exception {
        var income=create("in",5); create("out",14);
        assertThrows(IllegalStateException.class,()->service.invalidate(income,"Error"));
        assertEquals(1,stock()); assertTrue(fresh(income).isActive());
    }
    @Test void restoresCannotOverdrawStock() throws Exception {
        var out=create("out",8); service.invalidate(out,"Error"); create("out",7);
        assertThrows(IllegalStateException.class,()->service.restore(out)); assertEquals(3,stock()); assertTrue(fresh(out).isInvalidated());
    }
    @Test void staleCopiesCannotInvalidateOrRestoreTwice() throws Exception {
        var out=create("out",4); Transaction stale=fresh(out);
        Transaction originalCopy = stale;
        service.invalidate(out,"Error"); assertThrows(IllegalStateException.class,()->service.invalidate(originalCopy,"Error")); assertEquals(10,stock());
        stale=fresh(out); Transaction second=stale;
        service.restore(out); assertThrows(IllegalStateException.class,()->service.restore(second)); assertEquals(6,stock());
    }
    @Test void auditFailureRollsBackHeaderItemsAndStock() throws Exception {
        auditFailure(); assertThrows(RuntimeException.class,()->create("out",4));
        assertEquals(10,stock()); assertEquals(0,number("SELECT COUNT(*) FROM transactions")); assertEquals(0,number("SELECT COUNT(*) FROM transaction_items")); assertTrue(connection.getAutoCommit());
    }
    @Test void laterProductFailureRollsBackEarlierStock() throws Exception {
        assertThrows(IllegalStateException.class,()->service.createMultiProductTransaction("out",null,1,1,"",List.of(new TransactionItemDTO(1,2d,""),new TransactionItemDTO(2,11d,""))));
        assertEquals(10,stock()); assertEquals(10,number("SELECT current_stock FROM products WHERE id=2")); assertEquals(0,number("SELECT COUNT(*) FROM transactions"));
    }
    @Test void invalidationAndRestoreAuditFailureRollBackAllStates() throws Exception {
        var out=create("out",4); auditFailure();
        assertThrows(RuntimeException.class,()->service.invalidate(out,"Error")); assertEquals(6,stock()); assertTrue(fresh(out).isActive());
        assertEquals(1,number("SELECT COUNT(*) FROM transaction_items WHERE status='active'"));
        sql("DROP TRIGGER fail_audit"); service.invalidate(out,"Error"); auditFailure();
        assertThrows(RuntimeException.class,()->service.restore(out)); assertEquals(10,stock()); assertTrue(fresh(out).isInvalidated());
    }
    @Test void invalidatedLoanCannotBeReturned() throws Exception {
        var loan=loan(); service.invalidate(loan,"Error");
        assertThrows(IllegalStateException.class,()->returned(loan)); assertEquals(10,stock());
    }
    @Test void partialExcessAndWrongProductReturnsCannotCloseLoan() throws Exception {
        var loan=loan();
        for (double quantity : new double[]{1,5}) assertThrows(IllegalArgumentException.class,()->service.createLoanReturn(loan.getInteger("id"),"same_product","",items(quantity)));
        assertThrows(IllegalArgumentException.class,()->service.createLoanReturn(loan.getInteger("id"),"same_product","",List.of(new TransactionItemDTO(2,4d,""))));
        assertEquals("pending",fresh(loan).getString("loan_status")); assertEquals(6,stock());
    }
    @Test void overdueLoanAppearsInSearchAndCanBeReturned() throws Exception {
        var loan=loan(); sql("UPDATE transactions SET loan_status='overdue',loan_code='P-JA20260101-001',expected_return_date='2020-01-01' WHERE id="+loan.getInteger("id"));
        assertEquals(1,service.searchPendingLoansByCode("P-JA").size()); assertEquals(1,service.findPendingLoans().size());
        returned(loan); assertEquals("returned",fresh(loan).getString("loan_status")); assertEquals(10,stock());
    }
    @Test void invalidatingReturnReopensLoanAndRestoringClosesItAgain() throws Exception {
        var loan=loan(); var returned=returned(loan);
        assertThrows(IllegalStateException.class,()->service.invalidate(loan,"Error")); assertEquals(10,stock());
        service.invalidate(returned,"Error"); assertEquals(6,stock()); assertEquals("pending",fresh(loan).getString("loan_status")); assertNull(fresh(loan).getInteger("related_transaction_id"));
        service.restore(returned); assertEquals(10,stock()); assertEquals("returned",fresh(loan).getString("loan_status"));
    }
    @Test void invalidatingOverdueReturnReopensAsOverdue() throws Exception {
        var loan=loan(); sql("UPDATE transactions SET expected_return_date='2020-01-01' WHERE id="+loan.getInteger("id"));
        var returned=returned(loan); service.invalidate(returned,"Error"); assertEquals("overdue",fresh(loan).getString("loan_status"));
    }
    @Test void invalidatingReturnRequiresAvailableStockAndKeepsLoanClosedOnFailure() throws Exception {
        var loan=loan(); var returned=returned(loan); create("out",8);
        assertThrows(IllegalStateException.class,()->service.invalidate(returned,"Error")); assertEquals(2,stock()); assertEquals("returned",fresh(loan).getString("loan_status")); assertTrue(fresh(returned).isActive());
    }
    @Test void oldReturnCannotBeRestoredAfterAnotherReturnClosesLoan() throws Exception {
        var loan=loan(); var first=returned(loan); service.invalidate(first,"Error"); returned(loan);
        assertThrows(IllegalStateException.class,()->service.restore(first)); assertEquals(10,stock());
    }
    @Test void returnFailureRollsBackAndLoanStaysPending() throws Exception {
        var loan=loan(); auditFailure(); assertThrows(RuntimeException.class,()->returned(loan));
        assertEquals(6,stock()); assertEquals("pending",fresh(loan).getString("loan_status")); assertEquals(1,number("SELECT COUNT(*) FROM transactions"));
    }
    @Test void sameInitialsUseDifferentCodesAndBackdatedLoanUsesItsOwnDate() {
        var first=loan(); var second=service.createLoan(null,1,2,"",LocalDate.now().plusDays(1),items(1));
        assertNotEquals(first.getString("loan_code"),second.getString("loan_code"));
        var past=service.createLoan("2020-01-01",1,1,"",LocalDate.of(2020,1,2),items(1)); assertTrue(past.getString("loan_code").contains("20200101"));
    }
    @Test void inactiveProductsAndForgedActorsAreRejected() throws Exception {
        sql("UPDATE products SET status='inactive' WHERE id=1"); assertThrows(IllegalStateException.class,()->create("out",1));
        assertThrows(SecurityException.class,()->service.createLoan(null,2,1,"",LocalDate.now().plusDays(1),items(1))); assertEquals(10,stock());
    }
    @Test void legacyCreationUsesItemsAndCanBeInvalidated() throws Exception {
        var income=service.createTransaction(1,"in",2,1,1,""); assertEquals(12,stock());
        assertEquals(1,service.getTransactionItems(income.getInteger("id")).size()); service.invalidate(income,"Error"); assertEquals(10,stock());
    }
    @Test void manualProductUpdateAndAuditAreAtomicAndDetectStaleStock() throws Exception {
        Product product=Product.findById(1); product.set("current_stock",20); auditFailure();
        assertThrows(RuntimeException.class,()->new ProductService().save(product)); assertEquals(10,stock());
        sql("DROP TRIGGER fail_audit"); assertThrows(IllegalStateException.class,()->new ProductService().save(product,9d)); assertEquals(10,stock());
    }
    @Test void nestedTransactionDoesNotCommitItsOwnersChanges() throws Exception {
        connection.setAutoCommit(false); create("out",4); assertFalse(connection.getAutoCommit()); connection.rollback(); assertEquals(10,stock()); assertEquals(0,number("SELECT COUNT(*) FROM transactions"));
    }
    @Test void failedNestedOperationRollsBackOnlyItsSavepoint() throws Exception {
        connection.setAutoCommit(false); sql("UPDATE products SET name='Antes' WHERE id=1");
        assertThrows(IllegalStateException.class,()->create("out",11));
        assertEquals(10,stock()); assertFalse(connection.getAutoCommit());
        connection.commit(); assertEquals("Antes",((Product)Product.findById(1)).getString("name"));
    }
    @Test void decimalMovementsDoNotAccumulateFloatingPointErrors() throws Exception {
        sql("UPDATE products SET current_stock=0.3 WHERE id=1");
        var out=service.createMultiProductTransaction("out",null,1,1,"",List.of(new TransactionItemDTO(1,0.1,""),new TransactionItemDTO(1,0.2,"")));
        assertEquals(0,stock()); service.invalidate(out,"Error"); assertEquals(0.3,stock());
    }
    @Test void incomingStockCannotOverflowDatabasePrecision() throws Exception {
        sql("UPDATE products SET current_stock=99999999.99 WHERE id=1");
        assertThrows(IllegalStateException.class,()->create("in",0.01));
        assertEquals(99999999.99,stock()); assertEquals(0,number("SELECT COUNT(*) FROM transactions"));
    }
    @Test void creatingLoanAuditFailureRollsBackAllChanges() throws Exception {
        auditFailure(); assertThrows(RuntimeException.class,()->loan());
        assertEquals(10,stock()); assertEquals(0,number("SELECT COUNT(*) FROM transactions")); assertTrue(connection.getAutoCommit());
    }
    @Test void returnInvalidationFailureKeepsParentChildAndStockUnchanged() throws Exception {
        var loan=loan(); var returned=returned(loan); auditFailure();
        assertThrows(RuntimeException.class,()->service.invalidate(returned,"Error"));
        assertEquals(10,stock()); assertTrue(fresh(returned).isActive()); assertEquals("returned",fresh(loan).getString("loan_status"));
        assertEquals(returned.getInteger("id"),fresh(loan).getInteger("related_transaction_id"));
    }
    @Test void returnCannotBeRestoredWhileLoanIsInvalidated() throws Exception {
        var loan=loan(); var returned=returned(loan); service.invalidate(returned,"Error"); service.invalidate(loan,"Error");
        assertThrows(IllegalStateException.class,()->service.restore(returned)); assertEquals(10,stock());
        service.restore(loan); service.restore(returned); assertEquals(10,stock());
    }
    @Test void replacementRequiresExplanationOfFullSettlementAndPreservesItsProducts() throws Exception {
        var loan=loan();
        assertThrows(IllegalArgumentException.class,()->service.createLoanReturn(loan.getInteger("id"),"replaced","",List.of(new TransactionItemDTO(2,2d,""))));
        var replacement=service.createLoanReturn(loan.getInteger("id"),"replaced","Se liquida el préstamo completo con dos productos equivalentes",List.of(new TransactionItemDTO(2,2d,"")));
        assertEquals("replaced",fresh(loan).getString("loan_status")); assertEquals(6,stock()); assertEquals(12,number("SELECT current_stock FROM products WHERE id=2"));
        service.invalidate(replacement,"Error"); assertEquals("pending",fresh(loan).getString("loan_status")); assertEquals(10,number("SELECT current_stock FROM products WHERE id=2"));
    }
    @Test void duplicateProductCodesAreRejectedWithoutPartialCreationOrAudit() throws Exception {
        for (String status : new String[]{"active", "inactive", "deleted"}) {
            sql("UPDATE products SET status='"+status+"' WHERE id=1");
            var error=assertThrows(IllegalArgumentException.class,()->new ProductService().createProduct("Duplicado","","unidad",0,2,"abc"));
            assertTrue(error.getMessage().contains("Ya existe un producto"));
            assertFalse(error.getMessage().contains("SELECT"));
            assertEquals(2,number("SELECT COUNT(*) FROM products")); assertEquals(0,number("SELECT COUNT(*) FROM audit"));
            assertTrue(connection.getAutoCommit());
        }
    }
    @Test void editingAnotherProductsCodeFailsButKeepingOwnCodeWorks() throws Exception {
        Product product=Product.findById(2); product.set("code","ABC");
        var service=new ProductService();
        assertThrows(IllegalArgumentException.class,()->service.save(product));
        assertEquals("DEF",((Product)Product.findById(2)).getString("code")); assertEquals(0,number("SELECT COUNT(*) FROM audit"));
        product.set("code","DEF"); service.save(product);
        assertEquals(1,number("SELECT COUNT(*) FROM audit"));
    }
    @Test void concurrentDuplicateConstraintBecomesSafeMessageAndRollsBackEverything() throws Exception {
        sql("CREATE TRIGGER simulate_competing_code BEFORE INSERT ON products WHEN NEW.name='Simular carrera' BEGIN INSERT INTO products(id,code,name,unit,current_stock,minimum_stock,status) VALUES(99,'RACE','Competidor','unidad',0,0,'active'); END");
        var error=assertThrows(IllegalArgumentException.class,()->new ProductService().createProduct("Simular carrera","","unidad",0,2,"RACE"));
        assertTrue(error.getMessage().contains("Ya existe un producto")); assertFalse(error.getMessage().contains("UPDATE"));
        assertEquals(2,number("SELECT COUNT(*) FROM products")); assertEquals(0,number("SELECT COUNT(*) FROM audit")); assertTrue(connection.getAutoCommit());
    }
    @Test void selectedReturnDateIsStoredAsCivilDateAcrossTimeZones() throws Exception {
        java.util.TimeZone original=java.util.TimeZone.getDefault();
        try {
            for(String zone:List.of("Pacific/Kiritimati","America/Los_Angeles")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
                var loan=service.createLoan("2020-01-01",1,1,"",LocalDate.of(2020,2,1),items(4));
                var returned=service.createLoanReturn(loan.getInteger("id"),"2020-01-02","same_product","fecha seleccionada",items(4));
                assertEquals("2020-01-02",fresh(returned).getString("date"));
                assertEquals("2020-01-02",fresh(loan).getString("actual_return_date"));
                assertEquals(10,stock());
            }
        } finally { java.util.TimeZone.setDefault(original); }
    }
    @Test void restoringReturnPreservesItsOriginalOperationDate() throws Exception {
        var loan=service.createLoan("2020-01-01",1,1,"",LocalDate.of(2020,2,1),items(4));
        var returned=service.createLoanReturn(loan.getInteger("id"),"2020-01-02","same_product","histórico",items(4));
        service.invalidate(returned,"Prueba de restauración");
        assertNull(fresh(loan).getString("actual_return_date"));
        service.restore(returned);
        assertEquals("2020-01-02",fresh(returned).getString("date"));
        assertEquals("2020-01-02",fresh(loan).getString("actual_return_date"));
        assertEquals(10,stock());
    }
    @Test void returnDateBeforeLoanIsRejectedWithoutWriting() throws Exception {
        var loan=service.createLoan("2020-01-02",1,1,"",LocalDate.of(2020,2,1),items(4));
        assertThrows(IllegalArgumentException.class,()->service.createLoanReturn(loan.getInteger("id"),"2020-01-01","same_product","",items(4)));
        assertEquals(6,stock()); assertEquals(1,number("SELECT COUNT(*) FROM transactions"));
        assertNull(fresh(loan).getString("actual_return_date"));
        assertEquals("pending",fresh(loan).getString("loan_status"));
    }

}
