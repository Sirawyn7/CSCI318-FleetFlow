package com.fleetflow.notification.application;

import com.fleetflow.notification.domain.NotificationKind;
import com.fleetflow.notification.infrastructure.persistence.NotificationRecord;
import com.fleetflow.notification.infrastructure.persistence.NotificationTemplate;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationSchemaUpgradeTest {
    @TempDir Path directory;

    @Test void updatesExistingNativeEnumsAndPreservesRowsAcrossRestart() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("notifications").toAbsolutePath().toString().replace('\\', '/');
        String legacyEnum = "ENUM('ASSIGNED','AT_RISK','DELIVERED','IN_TRANSIT','NEAR_DESTINATION')";
        UUID oldId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE notifications (id UUID PRIMARY KEY, kind " + legacyEnum + " NOT NULL, "
                    + "source_event_id UUID NOT NULL UNIQUE, order_id UUID, delivery_id UUID, recipient_id UUID, "
                    + "reason VARCHAR(2000), body VARCHAR(4000), correlation_id VARCHAR(1000), "
                    + "occurred_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE, "
                    + "sent_at TIMESTAMP WITH TIME ZONE, audit_published BOOLEAN NOT NULL, version BIGINT NOT NULL)");
            sql.execute("INSERT INTO notifications (id, kind, source_event_id, audit_published, version) VALUES ('"
                    + oldId + "', 'ASSIGNED', '" + UUID.randomUUID() + "', false, 0)");
            sql.execute("CREATE TABLE notification_templates (code " + legacyEnum + " PRIMARY KEY, body VARCHAR(2000) NOT NULL)");
            sql.execute("INSERT INTO notification_templates VALUES ('ASSIGNED', 'Existing template')");
        }
        for (int restart = 0; restart < 2; restart++) {
            var configuration = new Configuration()
                    .addAnnotatedClass(NotificationRecord.class).addAnnotatedClass(NotificationTemplate.class)
                    .setProperty("hibernate.connection.url", url)
                    .setProperty("hibernate.connection.username", "sa")
                    .setProperty("hibernate.connection.password", "")
                    .setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                    .setProperty("hibernate.hbm2ddl.auto", "update");
            try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
                assertEquals(NotificationKind.ASSIGNED, session.find(NotificationRecord.class, oldId).getKind());
                assertNotNull(session.find(NotificationTemplate.class, NotificationKind.ASSIGNED.name()));
                var transaction = session.beginTransaction();
                for (var kind : new NotificationKind[]{NotificationKind.REASSIGNED, NotificationKind.ASSIGNMENT_FAILED}) {
                    session.persist(new NotificationRecord(UUID.randomUUID(), UUID.randomUUID(), null,
                            kind, "Internal diagnostic", Instant.now(), "upgrade"));
                    if (restart == 0) session.persist(new NotificationTemplate(kind));
                    else assertNotNull(session.find(NotificationTemplate.class, kind.name()));
                }
                transaction.commit();
            }
        }
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement();
             var columns = sql.executeQuery("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE "
                     + "(TABLE_NAME = 'NOTIFICATIONS' AND COLUMN_NAME = 'KIND') OR "
                     + "(TABLE_NAME = 'NOTIFICATION_TEMPLATES' AND COLUMN_NAME = 'CODE')")) {
            int count = 0;
            while (columns.next()) { assertEquals("CHARACTER VARYING", columns.getString(1)); count++; }
            assertEquals(2, count);
        }
    }

    /** A CHECK constraint listing today's kinds would make adding a kind a schema migration. */
    @Test void freshSchemaAcceptsNotificationKindsAddedLater() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("fresh").toAbsolutePath().toString().replace('\\', '/');
        var configuration = new Configuration()
                .addAnnotatedClass(NotificationRecord.class).addAnnotatedClass(NotificationTemplate.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .setProperty("hibernate.hbm2ddl.auto", "update");
        try (var factory = configuration.buildSessionFactory()) {
            assertNotNull(factory);
        }
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO notification_templates (code, body) VALUES ('FUTURE_KIND', 'x')");
            sql.execute("INSERT INTO notifications (id, kind, source_event_id, audit_published, version) VALUES ('"
                    + UUID.randomUUID() + "', 'FUTURE_KIND', '" + UUID.randomUUID() + "', false, 0)");
        }
    }
}
