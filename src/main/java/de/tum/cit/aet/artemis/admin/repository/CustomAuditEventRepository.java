package de.tum.cit.aet.artemis.admin.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import de.tum.cit.aet.artemis.account.repository.UserActivityRepository;
import de.tum.cit.aet.artemis.admin.domain.ApplicationAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.PersistedAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.PersistentAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.SecurityAuditEvent;
import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConverter;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventTypeClassifier;

/**
 * An implementation of Spring Boot's {@link AuditEventRepository}.
 * <p>
 * This is the single write path for audit events, and therefore the place where each event is routed to one of the three
 * audit logs: authentication events stay in {@code jhi_persistent_audit_event}, account credential/identity changes go to
 * {@code security_audit_event}, and everything else (domain actions, and any unrecognised type) goes to
 * {@code application_audit_event}. See {@link AuditEventTypeClassifier}.
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

    private final SecurityAuditEventRepository securityAuditEventRepository;

    private final ApplicationAuditEventRepository applicationAuditEventRepository;

    private final AuditEventConverter auditEventConverter;

    private final UserActivityRepository userActivityRepository;

    private static final Logger log = LoggerFactory.getLogger(CustomAuditEventRepository.class);

    public CustomAuditEventRepository(Environment environment, PersistenceAuditEventRepository persistenceAuditEventRepository,
            SecurityAuditEventRepository securityAuditEventRepository, ApplicationAuditEventRepository applicationAuditEventRepository, AuditEventConverter auditEventConverter,
            UserActivityRepository userActivityRepository) {
        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
        this.securityAuditEventRepository = securityAuditEventRepository;
        this.applicationAuditEventRepository = applicationAuditEventRepository;
        this.auditEventConverter = auditEventConverter;
        this.userActivityRepository = userActivityRepository;
        this.isSaml2Active = new ArtemisConfigHelper().isSaml2Enabled(environment);
    }

    /**
     * Finds events across the three audit logs, mirroring how {@link #add(AuditEvent)} routes them.
     * <p>
     * A given event type lives in exactly one log, so a type filter is answered from the log the classifier assigns it
     * to. Without a type filter every log has to be searched, because the caller cannot know which one holds the event.
     * The only caller is Spring Boot's actuator {@code auditevents} endpoint; the admin audit view reads one specific log
     * through {@code AuditEventService} instead, and every other collaborator only writes through {@link #add(AuditEvent)}.
     */
    @Override
    public List<AuditEvent> find(String principal, Instant after, String type) {
        if (type == null) {
            List<AuditEvent> events = new ArrayList<>();
            events.addAll(auditEventConverter.convertToAuditEvent(persistenceAuditEventRepository.findByPrincipalAndAuditEventDateAfter(principal, after)));
            events.addAll(auditEventConverter.convertToAuditEvent(securityAuditEventRepository.findByPrincipalAndAuditEventDateAfter(principal, after)));
            events.addAll(auditEventConverter.convertToAuditEvent(applicationAuditEventRepository.findByPrincipalAndAuditEventDateAfter(principal, after)));
            // Concatenating three logs would otherwise order by log rather than by time, which is not what a caller
            // asking for "everything this principal did" expects to read.
            events.sort(Comparator.comparing(AuditEvent::getTimestamp));
            return events;
        }

        Iterable<? extends PersistedAuditEvent> events = switch (AuditEventTypeClassifier.classify(type)) {
            case GENERAL -> persistenceAuditEventRepository.findByPrincipalAndAuditEventDateAfterAndAuditEventType(principal, after, type);
            case SECURITY -> securityAuditEventRepository.findByPrincipalAndAuditEventDateAfterAndAuditEventType(principal, after, type);
            case APPLICATION -> applicationAuditEventRepository.findByPrincipalAndAuditEventDateAfterAndAuditEventType(principal, after, type);
        };
        return auditEventConverter.convertToAuditEvent(events);
    }

    @Override
    public void add(AuditEvent event) {
        String eventType = event.getType();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (AuditEventConstants.AUTHORIZATION_FAILURE.equals(eventType)) {
            // Not persisted: Spring Security emits one of these for every denied request, which is noise rather than an audit trail.
            return;
        }

        if (isSaml2Active && AuditEventConstants.AUTHENTICATION_SUCCESS.equals(eventType) && authentication == null) {
            // If authentication is null, Auth is a success, and SAML2 profile is active => SAML2 authentication is running.
            // Logging is handled manually.
            return;
        }

        if (authentication instanceof WebAuthnAuthentication) {
            eventType = AuditEventConstants.AUTHENTICATION_PASSKEY_SUCCESS;
        }

        Map<String, String> eventData = truncate(auditEventConverter.convertDataToStrings(event.getData()));
        persist(eventType, event.getPrincipal(), event.getTimestamp(), eventData);

        if (isLoginSuccess(eventType)) {
            recordLastLogin(event.getPrincipal(), event.getTimestamp());
        }
    }

    /**
     * Writes the event into the audit log its type belongs to.
     */
    private void persist(String eventType, String principal, Instant timestamp, Map<String, String> eventData) {
        switch (AuditEventTypeClassifier.classify(eventType)) {
            case SECURITY -> {
                SecurityAuditEvent securityEvent = new SecurityAuditEvent();
                securityEvent.setPrincipal(principal);
                securityEvent.setAuditEventType(eventType);
                securityEvent.setAuditEventDate(timestamp);
                securityEvent.setData(eventData);
                securityAuditEventRepository.save(securityEvent);
            }
            case APPLICATION -> {
                ApplicationAuditEvent applicationEvent = new ApplicationAuditEvent();
                applicationEvent.setPrincipal(principal);
                applicationEvent.setAuditEventType(eventType);
                applicationEvent.setAuditEventDate(timestamp);
                applicationEvent.setData(eventData);
                applicationAuditEventRepository.save(applicationEvent);
            }
            case GENERAL -> {
                PersistentAuditEvent generalEvent = new PersistentAuditEvent();
                generalEvent.setPrincipal(principal);
                generalEvent.setAuditEventType(eventType);
                generalEvent.setAuditEventDate(timestamp);
                generalEvent.setData(eventData);
                persistenceAuditEventRepository.save(generalEvent);
            }
        }
    }

    /**
     * @return whether the given audit event type represents a successful login (internal/LDAP, passkey, or SAML2)
     */
    private static boolean isLoginSuccess(String eventType) {
        return AuditEventConstants.AUTHENTICATION_SUCCESS.equals(eventType) || AuditEventConstants.AUTHENTICATION_PASSKEY_SUCCESS.equals(eventType)
                || AuditEventConstants.SAML2_AUTHENTICATION_SUCCESS.equals(eventType);
    }

    /**
     * Records the user's last login (used as the activity signal for the data-privacy not-enrolled-user cleanup) on a
     * best-effort basis: a failure here must never break authentication or audit logging.
     *
     * @param principal the login of the authenticated user
     * @param timestamp the login timestamp
     */
    private void recordLastLogin(String principal, Instant timestamp) {
        try {
            userActivityRepository.recordLoginCreatingRowIfMissing(principal, timestamp);
        }
        catch (Exception e) {
            log.warn("Could not record last login date for principal {}", principal, e);
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
