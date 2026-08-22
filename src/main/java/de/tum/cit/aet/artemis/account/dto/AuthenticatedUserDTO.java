package de.tum.cit.aet.artemis.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The identity of the user making the current request.
 * <p>
 * Most code that asks for "the current user" only needs to know who they are, in order to compare an owner, write an
 * author, or name someone in a log line. Loading the {@code User} entity for that reads a row of roughly sixty columns,
 * hydrates it, and leaves it in the persistence context to be dirty-checked, none of which is used. On the exam paths
 * this happens several times within one request.
 * <p>
 * This carries only identity. Where a role or course-membership decision is needed, use
 * {@code AuthorizationCheckService}, which resolves those in the database rather than in Java.
 *
 * @param id    the user's database id
 * @param login the user's login
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AuthenticatedUserDTO(long id, String login) {
}
