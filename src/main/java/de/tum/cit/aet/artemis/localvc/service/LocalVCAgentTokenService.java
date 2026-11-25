package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PERSONAL_ACCESS_TOKEN_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.PersonalAccessTokenService;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.security.allowedScopes.PersonalAccessTokenAuthentication;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDraftGuardService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Lets a coding agent use git with a personal access token, within the limits that every other use of a token has.
 * <p>
 * The token is the password of the HTTPS credentials, and the user name is the login of its owner, so that git clients need nothing but their
 * standard credential handling. What the token opens is narrower than what its owner may open:
 * <ul>
 * <li>only if it holds the scope {@code programming:repositories}</li>
 * <li>only the template, solution, tests and auxiliary repositories, never the repository of a student</li>
 * <li>never those of an exam exercise, which are confidential until the exam starts</li>
 * <li>a push only while the exercise is a draft without participations, the rule that applies to changing the exercise through the REST API</li>
 * <li>the owner is at least an editor in the course of the exercise, for reading as well, because only editors and instructors may hold a token</li>
 * </ul>
 * The roles of the owner still apply on top: they are checked by the usual authorization of the repository afterwards.
 */
@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class LocalVCAgentTokenService {

    /** The logger of the audit trail of token requests, shared with the REST API so that an operator routes both together. */
    private static final Logger log = LoggerFactory.getLogger("de.tum.cit.aet.artemis.agentaccess.audit");

    /** Present only when agent access is enabled. */
    private final Optional<PersonalAccessTokenService> personalAccessTokenService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final ProgrammingExerciseDraftGuardService programmingExerciseDraftGuardService;

    private final AuthorizationCheckService authorizationCheckService;

    public LocalVCAgentTokenService(Optional<PersonalAccessTokenService> personalAccessTokenService, AuxiliaryRepositoryService auxiliaryRepositoryService,
            ProgrammingExerciseDraftGuardService programmingExerciseDraftGuardService, AuthorizationCheckService authorizationCheckService) {
        this.personalAccessTokenService = personalAccessTokenService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.programmingExerciseDraftGuardService = programmingExerciseDraftGuardService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Whether the password of a git request is a personal access token rather than a password or a VCS access token.
     *
     * @param passwordOrToken the password of the credentials
     * @return true if it has the prefix of personal access tokens
     */
    public static boolean isAgentToken(String passwordOrToken) {
        return passwordOrToken.startsWith(PERSONAL_ACCESS_TOKEN_PREFIX);
    }

    /**
     * Authenticates a git request that presented a personal access token as its password. Such a secret is never tried as a password, so that a
     * wrong token cannot lead to a login attempt.
     *
     * @param user   the user named in the credentials
     * @param secret the token
     * @return the authentication of the token, with its id and scopes
     * @throws LocalVCAuthException if the token is unknown, expired or revoked, its owner may not use tokens, or it belongs to another user
     */
    public PersonalAccessTokenAuthentication authenticate(User user, String secret) throws LocalVCAuthException {
        Optional<PersonalAccessTokenAuthentication> authentication = personalAccessTokenService.flatMap(service -> service.authenticate(secret));
        if (authentication.isEmpty()) {
            throw new LocalVCAuthException("The token is unknown, expired or revoked, or its owner may not use tokens.");
        }
        if (!authentication.get().getName().equals(user.getLogin())) {
            throw new LocalVCAuthException("The token belongs to another user. Use the login of its owner as the user name.");
        }
        return authentication.get();
    }

    /**
     * Decides whether a token may reach a repository, and writes the decision to the audit log.
     *
     * @param token                    the authenticated token
     * @param user                     the owner of the token
     * @param exercise                 the projected exercise the repository belongs to
     * @param repositoryTypeOrUserName the repository type or the user name taken from the repository URI
     * @param repositoryAction         whether the request reads or writes
     * @throws LocalVCForbiddenException if the token may not do this
     */
    public void authorize(PersonalAccessTokenAuthentication token, User user, GitRepositoryAccessDTO exercise, String repositoryTypeOrUserName,
            RepositoryActionType repositoryAction) throws LocalVCForbiddenException {
        try {
            requireAllowed(token, user, exercise, repositoryTypeOrUserName, repositoryAction);
        }
        catch (LocalVCForbiddenException e) {
            log.info("token={} user={} git {} exercise={} repository={} -> refused by the token rules: {}", token.getTokenId(), token.getName(), repositoryAction,
                    exercise.exerciseId(), repositoryTypeOrUserName, e.getMessage());
            throw e;
        }
        log.info("token={} user={} git {} exercise={} repository={} -> passed the token rules, the roles of the owner are checked next", token.getTokenId(), token.getName(),
                repositoryAction, exercise.exerciseId(), repositoryTypeOrUserName);
    }

    private void requireAllowed(PersonalAccessTokenAuthentication token, User user, GitRepositoryAccessDTO exercise, String repositoryTypeOrUserName,
            RepositoryActionType repositoryAction) throws LocalVCForbiddenException {
        if (!token.getScopes().contains(AgentScope.PROGRAMMING_REPOSITORIES)) {
            throw new LocalVCForbiddenException("The token does not have the scope " + AgentScope.PROGRAMMING_REPOSITORIES.getValue() + ".");
        }
        if (!authorizationCheckService.isAtLeastEditorInCourse(exercise.courseId(), user)) {
            throw new LocalVCForbiddenException("A personal access token opens repositories only in courses where its owner is at least an editor.");
        }
        if (exercise.examId() != null) {
            throw new LocalVCForbiddenException("Exam exercises cannot be accessed with a personal access token.");
        }
        if (!isStaffRepository(exercise, repositoryTypeOrUserName)) {
            throw new LocalVCForbiddenException(
                    "A personal access token opens only the template, solution, tests and auxiliary repositories of an exercise, not those of students.");
        }
        if (repositoryAction == RepositoryActionType.WRITE) {
            Optional<String> blocked = programmingExerciseDraftGuardService.repositoryPushBlockedReason(exercise.exerciseId(), exercise.releaseDate(), exercise.startDate());
            if (blocked.isPresent()) {
                throw new LocalVCForbiddenException(blocked.get());
            }
        }
    }

    private boolean isStaffRepository(GitRepositoryAccessDTO exercise, String repositoryTypeOrUserName) {
        return repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString()) || repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())
                || repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())
                || auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exercise.exerciseId());
    }
}
