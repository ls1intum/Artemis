package de.tum.cit.aet.artemis.localvc.service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;

/**
 * Who accessed which repository, as the authorization of a git request or command found out. It is what the access log entry of the operation is written for.
 *
 * @param user      the authenticated user
 * @param target    the repository: the participation that owns it, or the exercise and the repository for the tests and auxiliary repositories
 * @param mechanism how the user authenticated
 * @param ipAddress the address the request or command came from
 */
public record AccessLogSubject(User user, AccessLogTarget target, AuthenticationMechanism mechanism, String ipAddress) {
}
