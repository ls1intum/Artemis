package de.tum.in.www1.artemis.security;

import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import de.tum.in.www1.artemis.service.UserService;

@Component
@ConditionalOnProperty(value = "artemis.user-management.use-external", havingValue = "false")
public class ArtemisInternalAuthenticationProvider implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(ArtemisInternalAuthenticationProvider.class);

    private UserService userService;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    public ArtemisInternalAuthenticationProvider(UserRepository userRepository, AuditEventRepository auditEventRepository) {
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final var user = userService.getUserWithAuthoritiesByLogin(authentication.getName());
        if (user.isEmpty()) {
            throw new AuthenticationServiceException(String.format("User %s does not exist in the Artemis database!", authentication.getName()));
        }
        final var storedPassword = userService.decryptPasswordByLogin(user.get().getLogin()).get();
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
            user = userService.createUser(authentication.getName(), password, firstName, lastName, email, null, "en");
        }
        else {
            user = optionalUser.get();
            if (!skipPasswordCheck) {
                final var storedPassword = userService.decryptPasswordByLogin(user.getLogin()).get();
                if (!password.equals(storedPassword)) {
                    throw new InternalAuthenticationServiceException("Authentication failed for user " + user.getLogin());
                }
            }
        }

        return user;
    }

    @Override
    public void addUserToGroup(String username, String group) {
        final var user = userService.getUserWithGroupsByLogin(username).get();
        addUserToGroup(user, group);
    }

    @Override
    public void removeUserFromGroup(String username, String group) {
        final var user = userService.getUserWithGroupsByLogin(username).get();
        user.getGroups().remove(group);
        userRepository.save(user);
    }

    private void addUserToGroup(User user, String group) {
        log.info("Add user " + user.getLogin() + " to group " + group);
        user.getGroups().add(group);
        userRepository.save(user);
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
    public void registerUserForCourse(User user, Course course) {
        addUserToGroup(user, course.getStudentGroupName());
        final var auditEvent = new AuditEvent(user.getLogin(), "REGISTER_FOR_COURSE", "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " has successfully registered for course " + course.getTitle());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }
}
