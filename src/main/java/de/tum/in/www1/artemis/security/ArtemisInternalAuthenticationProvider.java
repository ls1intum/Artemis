package de.tum.in.www1.artemis.security;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserCreationService;

@Profile(PROFILE_CORE)
@Component
@ConditionalOnProperty(value = "artemis.user-management.use-external", havingValue = "false")
public class ArtemisInternalAuthenticationProvider extends ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(ArtemisInternalAuthenticationProvider.class);

    public ArtemisInternalAuthenticationProvider(UserRepository userRepository, PasswordService passwordService, UserCreationService userCreationService) {
        super(userRepository, passwordService, userCreationService);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        log.info("Authenticating {}", authentication.getName());
        final var user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(authentication.getName());
        if (user.isEmpty()) {
            throw new AuthenticationServiceException(String.format("User %s does not exist in the Artemis database!", authentication.getName()));
        }
        if (!user.get().getActivated()) {
            throw new UserNotActivatedException("User " + user.get().getLogin() + " was not activated");
        }
        if (!passwordService.checkPasswordMatch(authentication.getCredentials().toString(), user.get().getPassword())) {
            throw new AuthenticationServiceException("Invalid password for user " + user.get().getLogin());
        }
        log.info("Authentication successful for {}", authentication.getName());
        return new UsernamePasswordAuthenticationToken(user.get().getLogin(), user.get().getPassword(), user.get().getGrantedAuthorities());
    }

    @Override
    public void addUserToGroup(User user, String group) {
        // nothing to do, this was already done by the UserService, this method is only needed when external management is active
    }

    @Override
    public void removeUserFromGroup(User user, String group) {
        // nothing to do, this was already done by the UserService, this method is only needed when external management is active
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
        // nothing to do here, because the user service already takes care about internal groups
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
