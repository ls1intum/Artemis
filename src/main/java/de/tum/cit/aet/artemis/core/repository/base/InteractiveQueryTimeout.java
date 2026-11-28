package de.tum.cit.aet.artemis.core.repository.base;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.QueryHints;

/**
 * Cancels a read query of an interactive page after the time a person is willing to wait for it.
 * <p>
 * It sets the JPA hint {@code jakarta.persistence.query.timeout}, which Hibernate hands to the JDBC driver as the
 * timeout of the statement. When it expires the driver asks the database to cancel the statement, so the connection
 * is free again, and the request fails with a {@code QueryTimeoutException} instead of holding
 * the connection until the database is done with it. That matters when many sessions ask the same slow question: the
 * limit keeps each of them from occupying a connection for minutes, and a client that has given up does not cancel
 * the statement it started.
 * <p>
 * Put it on queries that serve students on a page that is bounded by design, and on nothing that an instructor or an
 * administrator runs for an export or a report: those legitimately take longer, and the database's own
 * {@code statement_timeout} is the limit for them.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = InteractiveQueryTimeout.MILLISECONDS))
public @interface InteractiveQueryTimeout {

    /**
     * The limit in milliseconds, the unit of the JPA hint. A page that takes longer than this is not one a student waits
     * for, and it stays below the 30 seconds after which a request that waits for a free connection gives up.
     */
    String MILLISECONDS = "15000";
}
