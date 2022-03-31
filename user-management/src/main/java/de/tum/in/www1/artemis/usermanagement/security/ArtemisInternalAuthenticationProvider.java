package de.tum.in.www1.artemis.usermanagement.security;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.usermanagement.service.user.UserCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(value = "artemis.user-management.use-external", havingValue = "false")
public class ArtemisInternalAuthenticationProvider extends ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(ArtemisInternalAuthenticationProvider.class);

    public ArtemisInternalAuthenticationProvider(UserRepository userRepository, PasswordService passwordService, UserCreationService userCreationService) {
        super(userRepository, passwordService, userCreationService);
    }

    @Override
    public User getOrCreateUser(Authentication authentication, String firstName, String lastName, String email, boolean skipPasswordCheck) {
        final var password = authentication.getCredentials().toString();
        final var optionalUser = userRepository.findOneByLogin(authentication.getName().toLowerCase());
        final User user;
        if (optionalUser.isEmpty()) {
            user = userCreationService.createInternalUser(authentication.getName(), password, null, firstName, lastName, email, null, null, "en");
        }
        else {
            user = optionalUser.get();
            if (!skipPasswordCheck && !passwordService.checkPasswordMatch(password, user.getPassword())) {
                throw new InternalAuthenticationServiceException("Authentication failed for user " + user.getLogin());
            }
        }

        return user;
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
        throw new UnsupportedOperationException("Cannot create user in external user management when internal authentication provider is configured.");
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
}
