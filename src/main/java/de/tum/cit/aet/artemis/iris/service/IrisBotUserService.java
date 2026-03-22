package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.domain.User.IRIS_BOT_LOGIN;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import tech.jhipster.security.RandomUtil;

/**
 * Service responsible for managing the Iris bot user account.
 * The bot user is created at startup when Iris is enabled and is used
 * by the autonomous tutor pipeline to post replies in communication channels.
 */
@Service
@Lazy
@Conditional(IrisEnabled.class)
public class IrisBotUserService {

    private static final Logger log = LoggerFactory.getLogger(IrisBotUserService.class);

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final ChannelRepository channelRepository;

    private final ConversationService conversationService;

    public IrisBotUserService(UserRepository userRepository, PasswordService passwordService, ChannelRepository channelRepository, ConversationService conversationService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.channelRepository = channelRepository;
        this.conversationService = conversationService;
    }

    /**
     * Ensures the Iris bot user exists in the database at application startup.
     * Runs asynchronously so that bcrypt password hashing does not block the main startup thread.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        ensureIrisBotUserExists();
    }

    /**
     * Ensures the Iris bot user exists in the database.
     * The bot user is always created when Iris is enabled, regardless of feature toggle state,
     * so it is ready when the AutonomousTutor feature is turned on later.
     */
    public void ensureIrisBotUserExists() {
        try {
            SecurityUtils.setAuthorizationObject();
            Optional<User> existingBot = userRepository.findOneWithGroupsAndAuthoritiesByLogin(IRIS_BOT_LOGIN);
            if (existingBot.isPresent()) {
                log.info("Iris bot user already exists, ensuring it is activated and internal");
                User bot = existingBot.get();
                bot.setActivated(true);
                bot.setInternal(true);
                userRepository.save(bot);
            }
            else {
                log.info("Creating Iris bot user");
                User bot = new User();
                bot.setLogin(IRIS_BOT_LOGIN);
                bot.setFirstName("Iris");
                bot.setLastName("Bot");
                bot.setEmail("iris-bot@localhost");
                bot.setActivated(true);
                bot.setInternal(true);
                bot.setPassword(passwordService.hashPassword(RandomUtil.generatePassword()));
                bot.setAuthorities(new HashSet<>(Set.of(new Authority("ROLE_USER"))));
                bot.setGroups(new HashSet<>());
                bot.setLangKey("en");
                userRepository.save(bot);
                log.info("Iris bot user created successfully");
            }
        }
        catch (Exception e) {
            log.error("Failed to ensure Iris bot user exists", e);
        }
    }

    /**
     * Retrieves the Iris bot user from the database.
     *
     * @return the Iris bot user
     * @throws IllegalStateException if the bot user does not exist
     */
    public User getIrisBotUser() {
        return userRepository.findOneWithGroupsAndAuthoritiesByLogin(IRIS_BOT_LOGIN)
                .orElseThrow(() -> new IllegalStateException("Iris bot user does not exist. Ensure Iris is enabled and the application has started."));
    }

    /**
     * Enrolls the Iris bot user as a participant in all channels of the given course.
     * Skips channels where the bot is already a participant (handled by ConversationService).
     *
     * @param course the course whose channels the bot should join
     */
    public void enrollBotInCourseChannels(Course course) {
        var botUser = getIrisBotUser();
        var channels = channelRepository.findChannelsByCourseId(course.getId());
        for (var channel : channels) {
            conversationService.registerUsersToConversation(course, Set.of(botUser), channel, Optional.empty());
        }
        log.info("Enrolled Iris bot in {} channels of course {}", channels.size(), course.getId());
    }

    /**
     * Removes the Iris bot user from all channels of the given course.
     *
     * @param course the course whose channels the bot should leave
     */
    public void removeBotFromCourseChannels(Course course) {
        var botUser = getIrisBotUser();
        var channels = channelRepository.findChannelsByCourseId(course.getId());
        for (var channel : channels) {
            conversationService.deregisterUsersFromAConversation(course, Set.of(botUser), channel);
        }
        log.info("Removed Iris bot from {} channels of course {}", channels.size(), course.getId());
    }
}
