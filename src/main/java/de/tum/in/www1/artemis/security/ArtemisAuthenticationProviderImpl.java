package de.tum.in.www1.artemis.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserService;

public abstract class ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    private final Logger log = LoggerFactory.getLogger(ArtemisAuthenticationProviderImpl.class);

    protected final UserRepository userRepository;

    protected UserService userService;

    public ArtemisAuthenticationProviderImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired
    // break the dependency cycle
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void addUserToGroup(User user, String group) {
        log.info("Add user " + user.getLogin() + " to group " + group);
        if (!user.getGroups().contains(group)) {
            user.getGroups().add(group);
            final var authorities = userService.buildAuthoritiesFromGroups(user.getGroups());
            user.setAuthorities(authorities);
            // we only save if this is needed
            userRepository.save(user);
        }
    }

    @Override
    public void removeUserFromGroup(User user, String group) {
        log.info("Remove user " + user.getLogin() + " from group " + group);
        if (user.getGroups().contains(group)) {
            user.getGroups().remove(group);
            final var authorities = userService.buildAuthoritiesFromGroups(user.getGroups());
            user.setAuthorities(authorities);
            // we only save if this is needed
            userRepository.save(user);
        }
    }
}
