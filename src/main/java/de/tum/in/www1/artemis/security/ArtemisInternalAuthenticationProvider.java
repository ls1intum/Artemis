package de.tum.in.www1.artemis.security;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;

@Component
@ConditionalOnProperty(value = "artemis.user-management.use-external", havingValue = "false")
public class ArtemisInternalAuthenticationProvider extends ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(ArtemisInternalAuthenticationProvider.class);

    private final AuditEventRepository auditEventRepository;

    public ArtemisInternalAuthenticationProvider(UserRepository userRepository, AuditEventRepository auditEventRepository) {
        super(userRepository);
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final var user = userService.getUserWithAuthoritiesByLogin(authentication.getName());
        if (user.isEmpty()) {
            throw new AuthenticationServiceException(String.format("User %s does not exist in the Artemis database!", authentication.getName()));
        }
        final var storedPassword = userService.decryptPassword(user.get());
        if (!authentication.getCredentials().toString().equals(storedPassword)) {
            throw new AuthenticationServiceException("Invalid password for user " + user.get().getLogin());
        }
        final var grantedAuthorities = user.get().getAuthorities().stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).collect(Collectors.toList());

        return new UsernamePasswordAuthenticationToken(user.get().getLogin(), user.get().getPassword(), grantedAuthorities);
    }

    @Override
    public User getOrCreateUser(Authentication authentication, String firstName, String lastName, String email, boolean skipPasswordCheck) {
        final var password = authentication.getCredentials().toString();
        final var optionalUser = userService.getUserByLogin(authentication.getName().toLowerCase());
        final User user;
        if (optionalUser.isEmpty()) {
            user = userService.createUser(authentication.getName(), password, firstName, lastName, email, null, null, "en");
        }
        else {
            user = optionalUser.get();
            if (!skipPasswordCheck) {
                final var storedPassword = userService.decryptPassword(user);
                if (!password.equals(storedPassword)) {
                    throw new InternalAuthenticationServiceException("Authentication failed for user " + user.getLogin());
                }
            }
        }

        return user;
    }

    @Override
    public void addUserToGroups(User user, Set<String> groups) {
        if (groups == null) {
            return;
        }
        boolean userChanged = false;
        for (String group : groups) {
            if (!user.getGroups().contains(group)) {
                userChanged = true;
                user.getGroups().add(group);
            }
        }

        if (userChanged) {
            // we only save if this is needed
            userRepository.save(user);
        }
    }

    @Override
    public void createUserInExternalUserManagement(User user) {
        // This should not be invoked. As we only use internal management, nothing needs to be done in case it is invoked.
    }

    @Override
    public Optional<String> getUsernameForEmail(String email) {
        return userRepository.findOneByEmailIgnoreCase(email).flatMap(user -> Optional.of(user.getLogin()));
    }

    @Override
    public boolean isGroupAvailable(String group) {
        // Not needed since we don't have any externally specified groups. If we only use the Artemis DB (which is the case
        // for this service), adding a user to a group will always work since we don't have any predefined groups.
        return true;
    }

    @Override
    public void createGroup(String groupName) {
        // Not needed since we don't have any externally specified groups. If we only use the Artemis DB (which is the case
        // for this service), creating a group is not necessary, since groups are just referenced as strings when connected to users.
    }

    @Override
    public void deleteGroup(String groupName) {
        userService.removeGroupFromUsers(groupName);
    }

    @Override
    public void registerUserForCourse(User user, Course course) {
        addUserToGroup(user, course.getStudentGroupName());
        final var auditEvent = new AuditEvent(user.getLogin(), "REGISTER_FOR_COURSE", "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " has successfully registered for course " + course.getTitle());
    }

    @Override
    public ConnectorHealth health() {
        // the internal authentication provider is always running when Artemis is running
        return new ConnectorHealth(true);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }
}
