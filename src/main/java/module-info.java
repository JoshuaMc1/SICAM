module com.joshuamc1.sicam {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires activejdbc;
    requires org.jetbrains.annotations;
    requires java.sql;
    requires java.desktop;
    requires jbcrypt;
    requires org.slf4j;
    requires org.apache.pdfbox;
    requires org.apache.fontbox;
    requires com.google.zxing;
    requires com.google.zxing.javase;
    requires escpos.coffee;
    requires io;
    requires kernel;
    requires layout;
    requires org.apache.poi.ooxml;
    requires org.apache.poi.poi;

    opens com.joshuamc1.sicam.controllers to javafx.fxml;
    opens com.joshuamc1.sicam.controllers.dashboard to javafx.fxml;
    opens com.joshuamc1.sicam.models to javafx.fxml, activejdbc;

    exports com.joshuamc1.sicam;
    opens com.joshuamc1.sicam.models.dto to activejdbc, javafx.fxml;
}