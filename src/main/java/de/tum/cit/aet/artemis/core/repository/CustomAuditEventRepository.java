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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConverter;
import de.tum.cit.aet.artemis.core.domain.PersistentAuditEvent;

/**
 * An implementation of Spring Boot's {@link AuditEventRepository}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public class CustomAuditEventRepository implements AuditEventRepository {

    private final boolean isSaml2Active;

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
        String eventType = event.getType();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!AuditEventConstants.AUTHORIZATION_FAILURE.equals(eventType)) {
            if (isSaml2Active && AuditEventConstants.AUTHENTICATION_SUCCESS.equals(eventType) && authentication == null) {
                // If authentication is null, Auth is a success, and SAML2 profile is active => SAML2 authentication is running.
                // Logging is handled manually.
                return;
            }

            if (authentication instanceof WebAuthnAuthentication) {
                eventType = AuditEventConstants.AUTHENTICATION_PASSKEY_SUCCESS;
            }

            PersistentAuditEvent persistentAuditEvent = new PersistentAuditEvent();
            persistentAuditEvent.setPrincipal(event.getPrincipal());
            persistentAuditEvent.setAuditEventType(eventType);
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
