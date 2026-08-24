package de.tum.cit.aet.artemis.admin.config;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Retention for the three audit logs, bound from {@code artemis.audit-events}.
 * <p>
 * The periods differ by orders of magnitude because the logs hold records with opposite characteristics: one row is
 * written per login attempt, which is the bulk of the data and tells you little a few weeks later, while security and
 * application records are rare deliberate actions on accounts, courses, exercises and exams, and are what has to be
 * reconstructed when a question is raised long after the fact, an exam dispute in particular.
 * <p>
 * All three are validated as positive, and the binding is {@code @Validated}, so an invalid value fails startup rather
 * than being applied. That matters because this job needs no opt-in and its deletions are irreversible: {@code 0} would
 * make almost every existing event eligible, and a negative value would move the cutoff into the future and delete
 * records written minutes ago.
 *
 * @param generalRetentionPeriod     Days before the login record (successful, failed and passkey logins, and logouts) is
 *                                       deleted.
 * @param securityRetentionPeriod    Days before a security event, i.e. a change to an account's credentials or identity,
 *                                       is deleted.
 * @param applicationRetentionPeriod Days before an application event is deleted, including types this version does not
 *                                       recognise, which are routed to that log precisely so they are over-retained
 *                                       rather than lost.
 */
@Validated
@ConfigurationProperties(prefix = "artemis.audit-events")
public record AuditEventRetentionProperties(@DefaultValue("365") @Positive int generalRetentionPeriod, @DefaultValue("1825") @Positive int securityRetentionPeriod,
        @DefaultValue("1825") @Positive int applicationRetentionPeriod) {
}
