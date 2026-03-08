package de.tum.cit.aet.artemis.iris.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.filter.BotApiKeyFilter;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;

/**
 * Seeds the Iris bot account on application startup.
 * Creates a User with bot fields if it does not already exist.
 */
@Service
public class BotAccountStartupService {

    private static final Logger log = LoggerFactory.getLogger(BotAccountStartupService.class);

    private static final String IRIS_BOT_LOGIN = "iris-bot";

    private static final String API_KEY_PREFIX = "artbot_";

    private final UserRepository userRepository;

    private final AuthorityRepository authorityRepository;

    private final PasswordService passwordService;

    @Value("${artemis.iris.bot-api-key:}")
    private String configuredApiKey;

    public BotAccountStartupService(UserRepository userRepository, AuthorityRepository authorityRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordService = passwordService;
    }

    /**
     * Seeds the Iris bot account on application startup if it does not already exist.
     * Creates a User entity with bot fields and an API key, logging the key at WARN level.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedIrisBotAccount() {
        var existingBot = userRepository.findBotByLogin(IRIS_BOT_LOGIN);
        if (existingBot.isPresent()) {
            log.info("Iris bot account already exists, skipping seeding");
            return;
        }

        // Determine the API key
        String rawApiKey;
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            rawApiKey = configuredApiKey;
        }
        else {
            rawApiKey = generateApiKey();
        }

        // Create the User entity for the bot
        User botUser = new User();
        botUser.setLogin(IRIS_BOT_LOGIN);
        botUser.setFirstName("Iris");
        botUser.setLastName("Bot");
        botUser.setEmail(null);
        botUser.setActivated(true);
        botUser.setInternal(true);
        botUser.setLangKey("en");
        botUser.setPassword(passwordService.hashPassword(generateRandomString(64)));
        botUser.setIsBot(true);
        botUser.setApiKeyHash(BotApiKeyFilter.sha256Hex(rawApiKey));

        Authority userAuthority = authorityRepository.findById(Role.STUDENT.getAuthority()).orElseThrow();
        botUser.setAuthorities(new HashSet<>(Set.of(userAuthority)));
        botUser.setGroups(new HashSet<>());
        userRepository.save(botUser);

        log.warn("Iris bot account created with login '{}'. API key: {}", IRIS_BOT_LOGIN, rawApiKey);
        log.warn("Store this API key securely -- it cannot be recovered. Set 'artemis.iris.bot-api-key' in application.yml for multi-node deployments.");
    }

    private static String generateApiKey() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String generateRandomString(int length) {
        byte[] randomBytes = new byte[length];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
