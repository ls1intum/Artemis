package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.admin.domain.AbstractPersistedAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.ApplicationAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.PersistentAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.SecurityAuditEvent;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class PersistedAuditEventContractTest {

    @ParameterizedTest
    @MethodSource("eventTypes")
    void retainsIndependentTableMappings(Class<? extends AbstractPersistedAuditEvent> type, String tableName, String dataTableName) {
        try (StandardServiceRegistry registry = new StandardServiceRegistryBuilder().applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false).build()) {
            Metadata metadata = new MetadataSources(registry).addAnnotatedClass(type).buildMetadata();
            var entity = metadata.getEntityBinding(type.getName());
            assertThat(entity.getTable().getName()).isEqualTo(tableName);
            assertThat(entity.getIdentifier().getColumns()).extracting(org.hibernate.mapping.Column::getName).containsExactly("event_id");
            assertThat(entity.getProperty("principal").getColumns()).extracting(org.hibernate.mapping.Column::getName).containsExactly("principal");
            assertThat(entity.getProperty("auditEventDate").getColumns()).extracting(org.hibernate.mapping.Column::getName).containsExactly("event_date");
            assertThat(entity.getProperty("auditEventType").getColumns()).extracting(org.hibernate.mapping.Column::getName).containsExactly("event_type");
            var data = metadata.getCollectionBinding(type.getName() + ".data");
            assertThat(data.getCollectionTable().getName()).isEqualTo(dataTableName);
            assertThat(data.getKey().getColumns()).extracting(org.hibernate.mapping.Column::getName).containsExactly("event_id");
            assertThat(data.getElement().getColumns()).extracting(org.hibernate.mapping.Column::getName).containsExactly("event_data");
        }
    }

    @ParameterizedTest
    @MethodSource("eventTypes")
    void preservesIdentityAndJson(Class<? extends AbstractPersistedAuditEvent> type, String tableName, String dataTableName) throws Exception {
        var event = type.getConstructor().newInstance();
        var other = type.getConstructor().newInstance();
        assertThat(event).isEqualTo(event).isNotEqualTo(other);
        event.setId(12L);
        other.setId(12L);
        assertThat(event).isEqualTo(other).hasSameHashCodeAs(other);
        event.setPrincipal("alice");
        event.setAuditEventDate(Instant.parse("2026-01-01T00:00:00Z"));
        event.setAuditEventType("EXERCISE_DELETED");
        event.getData().put("exerciseId", "42");
        var mapper = JsonObjectMapper.get();
        var json = mapper.readTree(mapper.writeValueAsString(event));
        assertThat(json.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("id", "principal", "auditEventDate", "auditEventType", "data");
        assertThat(json.get("id").asLong()).isEqualTo(12L);
        assertThat(json.get("principal").asString()).isEqualTo("alice");
        assertThat(json.get("data").get("exerciseId").asString()).isEqualTo("42");
        assertThat(event.toString()).isEqualTo(type.getSimpleName() + "{principal='alice', auditEventDate=2026-01-01T00:00:00Z, auditEventType='EXERCISE_DELETED'}");
    }

    @Test
    void subclassInstancesKeepTheirAuditLogLabel() {
        class ProxyApplicationAuditEvent extends ApplicationAuditEvent {
        }
        assertThat(new ProxyApplicationAuditEvent().toString()).startsWith("ApplicationAuditEvent{");
    }

    @Test
    void identitiesDoNotCrossAuditTables() {
        List<AbstractPersistedAuditEvent> events = List.of(new PersistentAuditEvent(), new ApplicationAuditEvent(), new SecurityAuditEvent());
        events.forEach(event -> event.setId(1L));
        assertThat(events).doesNotHaveDuplicates();
    }

    private static Stream<Arguments> eventTypes() {
        return Stream.of(Arguments.of(PersistentAuditEvent.class, "jhi_persistent_audit_event", "jhi_persistent_audit_evt_data"),
                Arguments.of(ApplicationAuditEvent.class, "application_audit_event", "application_audit_evt_data"),
                Arguments.of(SecurityAuditEvent.class, "security_audit_event", "security_audit_evt_data"));
    }
}
