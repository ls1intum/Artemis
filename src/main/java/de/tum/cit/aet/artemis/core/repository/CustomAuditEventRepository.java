package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConverter;
import de.tum.cit.aet.artemis.core.domain.PersistentAuditEvent;

/**
 * An implementation of Spring Boot's {@link AuditEventRepository}.
 */
@Profile(PROFILE_CORE)
@Repository
public class CustomAuditEventRepository implements AuditEventRepository {

    private final boolean isSaml2Active;

    private static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";

    private static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";

    /**
     * Should be the same as in Liquibase migration.
     */
    protected static final int EVENT_DATA_COLUMN_MAX_LENGTH = 255;

    private final PersistenceAuditEventRepository persistenceAuditEventRepository;

    private final AuditEventConverter auditEventConverter;

    private static final Logger log = LoggerFactory.getLogger(CustomAuditEventRepository.class);

    public CustomAuditEventRepository(Environment environment, PersistenceAuditEventRepository persistenceAuditEventRepository, AuditEventConverter auditEventConverter) {
        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
        this.auditEventConverter = auditEventConverter;
        this.isSaml2Active = Set.of(environment.getActiveProfiles()).contains(Constants.PROFILE_SAML2);
    }

    @Override
    public List<AuditEvent> find(String principal, Instant after, String type) {
        Iterable<PersistentAuditEvent> persistentAuditEvents = persistenceAuditEventRepository.findByPrincipalAndAuditEventDateAfterAndAuditEventType(principal, after, type);
        return auditEventConverter.convertToAuditEvent(persistentAuditEvents);
    }

    @Override
    public void add(AuditEvent event) {
        if (!AUTHORIZATION_FAILURE.equals(event.getType())) {
            if (isSaml2Active && AUTHENTICATION_SUCCESS.equals(event.getType()) && SecurityContextHolder.getContext().getAuthentication() == null) {
                // If authentication is null, Auth is success, and SAML2 profile is active => SAML2 authentication is running.
                // Logging is handled manually.
                return;
            }

            PersistentAuditEvent persistentAuditEvent = new PersistentAuditEvent();
            persistentAuditEvent.setPrincipal(event.getPrincipal());
            persistentAuditEvent.setAuditEventType(event.getType());
            persistentAuditEvent.setAuditEventDate(event.getTimestamp());
            Map<String, String> eventData = auditEventConverter.convertDataToStrings(event.getData());
            persistentAuditEvent.setData(truncate(eventData));
            persistenceAuditEventRepository.save(persistentAuditEvent);
        }
    }

    /**
     * Truncate event data that might exceed column length.
     */
    private Map<String, String> truncate(Map<String, String> data) {
        Map<String, String> results = new HashMap<>();

        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    int length = value.length();
                    if (length > EVENT_DATA_COLUMN_MAX_LENGTH) {
                        value = value.substring(0, EVENT_DATA_COLUMN_MAX_LENGTH);
                        log.warn("Event data for {} too long ({}) has been truncated to {}. Consider increasing column width.", entry.getKey(), length,
                                EVENT_DATA_COLUMN_MAX_LENGTH);
                    }
                }
                results.put(entry.getKey(), value);
            }
        }
        return results;
    }
}
