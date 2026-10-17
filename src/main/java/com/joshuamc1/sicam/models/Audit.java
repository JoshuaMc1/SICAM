package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.BelongsTo;
import org.javalite.activejdbc.annotations.Table;

@Table("audit")
@BelongsTo(parent = User.class, foreignKeyName = "user_id")
public class Audit extends Model {
    static {
        // validar campos con nombres reales de la tabla
        validatePresenceOf("user_id", "action");
    }

    public User getUser() {
        return parent(User.class);
    }

    public static void logAction(User user, String action, String tableName, Integer recordId, String details) {
        Audit audit = new Audit();
        if (user != null) {
            audit.set("user_id", user.getId());
        }
        audit.set("action", action);
        audit.set("table_name", tableName);
        audit.set("record_id", recordId);
        audit.set("details", details);
        audit.saveIt();
    }
}
