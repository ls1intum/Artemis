package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PERSONAL_ACCESS_TOKEN_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.config.AgentAccessEnabled;
import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.PersonalAccessToken;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.CreatePersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.dto.CreatedPersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.dto.PersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.security.allowedScopes.PersonalAccessTokenAuthentication;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

/**
 * Creates, lists, revokes and authenticates personal access tokens.
 * <p>
 * Present only when {@code artemis.agent-access.enabled} is true. A token authenticates as its owner, never as an
 * administrator: accounts with an admin authority can neither create a token nor authenticate with one, because the
 * scopes restrict which endpoints a token reaches but an administrator's role check inside an endpoint would not be
 * narrowed by them.
 */
@Profile(PROFILE_CORE)
@Conditional(AgentAccessEnabled.class)
@Lazy
@Service
public class PersonalAccessTokenService {

    private static final String ENTITY_NAME = "personalAccessToken";

    private static final int MAX_USABLE_TOKENS_PER_USER = 25;

    /** How stale a token's last-used time may get before a request updates it, which keeps the write off the hot path. */
    private static final Duration LAST_USED_UPDATE_INTERVAL = Duration.ofMinutes(5);

    private static final int DISPLAYED_SECRET_CHARACTERS = 4;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PersonalAccessTokenRepository personalAccessTokenRepository;

    private final UserRepository userRepository;

    private final UserActivityService userActivityService;

    private final UserCourseRoleRepository userCourseRoleRepository;

    private final int maxLifetimeInDays;

    public PersonalAccessTokenService(PersonalAccessTokenRepository personalAccessTokenRepository, UserRepository userRepository, UserActivityService userActivityService,
            UserCourseRoleRepository userCourseRoleRepository, @Value("${artemis.agent-access.max-lifetime-in-days:90}") int maxLifetimeInDays) {
        this.personalAccessTokenRepository = personalAccessTokenRepository;
        this.userRepository = userRepository;
        this.userActivityService = userActivityService;
        this.userCourseRoleRepository = userCourseRoleRepository;
        this.maxLifetimeInDays = maxLifetimeInDays;
    }

    /**
     * Creates a token for a user.
     *
     * @param user             the owner
     * @param request          the requested name, scopes and lifetime
     * @param sessionStartedAt when the session that asks for the token was established, or {@code null} if it is not known
     * @return the stored token together with its secret, which is not retrievable later
     * @throws AccessForbiddenException if the user is an administrator or neither an instructor nor an editor, or the session started before the credentials of the user last
     *                                      changed
     */
    public CreatedPersonalAccessTokenDTO create(User user, CreatePersonalAccessTokenDTO request, @Nullable Instant sessionStartedAt) {
        requireMayCreateTokens(user.getId());
        // A password reset ends the tokens created before it, and it has to end the sessions that could still create one as well: otherwise a stolen session
        // outlives the reset by creating a token that does not.
        Instant credentialsChangedDate = userActivityService.findCredentialsChangedDate(user.getId());
        if (sessionStartedAt != null && credentialsChangedDate != null && sessionStartedAt.isBefore(credentialsChangedDate)) {
            throw new AccessForbiddenException("Your session started before your credentials last changed. Sign in again to create a token.");
        }

        Set<AgentScope> scopes = request.scopes().stream().map(this::parseScope).collect(Collectors.toCollection(LinkedHashSet::new));
        int lifetimeInDays = request.expiresInDays() != null ? request.expiresInDays() : maxLifetimeInDays;
        if (lifetimeInDays > maxLifetimeInDays) {
            throw new BadRequestAlertException("The lifetime of a token is at most " + maxLifetimeInDays + " days", ENTITY_NAME, "lifetimeTooLong");
        }
        Instant now = Instant.now();
        if (personalAccessTokenRepository.countUsableByUserId(user.getId(), now) >= MAX_USABLE_TOKENS_PER_USER) {
            throw new BadRequestAlertException("A user can have at most " + MAX_USABLE_TOKENS_PER_USER + " tokens", ENTITY_NAME, "tooManyTokens");
        }

        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String secret = PERSONAL_ACCESS_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String prefix = secret.substring(0, PERSONAL_ACCESS_TOKEN_PREFIX.length() + DISPLAYED_SECRET_CHARACTERS);
        String joinedScopes = scopes.stream().map(AgentScope::getValue).collect(Collectors.joining(","));

        var token = new PersonalAccessToken(user.getId(), request.name().strip(), prefix, hash(secret), joinedScopes, now, now.plus(lifetimeInDays, ChronoUnit.DAYS));
        token = personalAccessTokenRepository.save(token);
        return new CreatedPersonalAccessTokenDTO(toDTO(token), secret);
    }

    /**
     * Lists the tokens of a user that have not been revoked, newest first.
     *
     * @param user the owner
     * @return the tokens, without secrets
     */
    public List<PersonalAccessTokenDTO> list(User user) {
        return personalAccessTokenRepository.findAllByUserIdAndRevokedDateIsNullOrderByCreatedDateDesc(user.getId()).stream().map(PersonalAccessTokenService::toDTO).toList();
    }

    /**
     * Revokes a token of a user.
     *
     * @param user    the owner
     * @param tokenId the token to revoke
     * @throws EntityNotFoundException if the user has no such token that is still active
     */
    public void revoke(User user, long tokenId) {
        if (personalAccessTokenRepository.revoke(tokenId, user.getId(), Instant.now()) == 0) {
            throw new EntityNotFoundException("PersonalAccessToken", tokenId);
        }
    }

    /**
     * Authenticates a request that presented a token secret.
     *
     * @param secret the secret from the Authorization header
     * @return the authentication, or empty if the secret is unknown, revoked or expired, was created before the owner's credentials last changed, or its owner may not use tokens
     */
    public Optional<PersonalAccessTokenAuthentication> authenticate(String secret) {
        Instant now = Instant.now();
        Optional<PersonalAccessToken> token = personalAccessTokenRepository.findByTokenHash(hash(secret)).filter(candidate -> candidate.isUsableAt(now));
        if (token.isEmpty()) {
            return Optional.empty();
        }
        Optional<User> owner = userRepository.findOneWithAuthoritiesById(token.get().getUserId()).filter(user -> user.getActivated() && !user.isDeleted());
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        Set<String> authorityNames = owner.get().getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet());
        if (AuthorizationCheckService.isAdminByAuthorityName(authorityNames)) {
            return Optional.empty();
        }
        // A password reset or a changed credential ends the sessions issued before it, and ends the tokens created before it as well.
        Instant credentialsChangedDate = userActivityService.findCredentialsChangedDate(owner.get().getId());
        if (credentialsChangedDate != null && token.get().getCreatedDate().isBefore(credentialsChangedDate)) {
            return Optional.empty();
        }

        personalAccessTokenRepository.touchLastUsed(token.get().getId(), now, now.minus(LAST_USED_UPDATE_INTERVAL));
        var authorities = authorityNames.stream().map(SimpleGrantedAuthority::new).toList();
        return Optional.of(new PersonalAccessTokenAuthentication(owner.get().getLogin(), authorities, token.get().getId(), parseScopes(token.get().getScopes())));
    }

    private void requireMayCreateTokens(long userId) {
        Set<String> authorityNames = userRepository.findOneWithAuthoritiesById(userId).map(User::getAuthorities).orElseThrow(() -> new EntityNotFoundException("User", userId))
                .stream().map(Authority::getName).collect(Collectors.toSet());
        if (AuthorizationCheckService.isAdminByAuthorityName(authorityNames)) {
            throw new AccessForbiddenException("Administrators cannot use personal access tokens");
        }
        if (!userCourseRoleRepository.existsByUser_IdAndRoleIn(userId, List.of(CourseRole.EDITOR, CourseRole.INSTRUCTOR))) {
            throw new AccessForbiddenException("Only instructors and editors can create personal access tokens");
        }
    }

    /**
     * Lists the tokens of a user that have not been revoked, for an administrator.
     *
     * @param login the login of the owner
     * @return the tokens, without secrets
     * @throws EntityNotFoundException if there is no user with the login
     */
    public List<PersonalAccessTokenDTO> listForLogin(String login) {
        return list(userRepository.findOneByLogin(login).orElseThrow(() -> new EntityNotFoundException("User", login)));
    }

    /**
     * Revokes every token of a user that is still active, for example when the account of the user has been deprovisioned in an external system.
     *
     * @param login the login of the owner
     * @return the number of tokens revoked
     * @throws EntityNotFoundException if there is no user with the login
     */
    public int revokeAllForLogin(String login) {
        User owner = userRepository.findOneByLogin(login).orElseThrow(() -> new EntityNotFoundException("User", login));
        return personalAccessTokenRepository.revokeAllOfUser(owner.getId(), Instant.now());
    }

    private AgentScope parseScope(String value) {
        return AgentScope.fromValue(value).orElseThrow(() -> new BadRequestAlertException("Unknown scope: " + value, ENTITY_NAME, "unknownScope"));
    }

    private static Set<AgentScope> parseScopes(String joinedScopes) {
        // Unknown names are skipped rather than rejected, so a token created by a newer version keeps working after a rollback.
        return Arrays.stream(joinedScopes.split(",")).map(AgentScope::fromValue).flatMap(Optional::stream).collect(Collectors.toSet());
    }

    private static PersonalAccessTokenDTO toDTO(PersonalAccessToken token) {
        return new PersonalAccessTokenDTO(token.getId(), token.getName(), token.getTokenPrefix(), List.of(token.getScopes().split(",")), token.getCreatedDate(),
                token.getExpiryDate(), token.getLastUsedDate());
    }

    private static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JRE, so this cannot happen.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
