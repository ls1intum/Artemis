package de.tum.cit.aet.artemis.localvc.service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;

/**
 * Who accessed which repository, as the authorization of a git request or command found out. It is what the access log entry of the operation is written for.
 *
 * @param user            the authenticated user
 * @param participationId the id of the participation that owns the repository
 * @param mechanism       how the user authenticated
 * @param ipAddress       the address the request or command came from
 */
public record AccessLogSubject(User user, long participationId, AuthenticationMechanism mechanism, String ipAddress) {
}
