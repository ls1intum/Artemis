package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.course_notification.domain.CourseNotificationParameter;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.course_notification.repository.CourseNotificationParameterRepository;
import de.tum.cit.aet.artemis.course_notification.repository.CourseNotificationRepository;

/**
 * Service that handles all course notification logic. Whenever you want to create a new notification use this service
 * to send it to the users.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationService.class);

    private final CourseNotificationRegistry courseNotificationRegistry;

    private final CourseNotificationSettingService courseNotificationSettingService;

    private final CourseNotificationRepository courseNotificationRepository;

    private final CourseNotificationParameterRepository courseNotificationParameterRepository;

    private final UserCourseNotificationStatusService userCourseNotificationStatusService;

    private final Map<NotificationSettingOption, CourseNotificationBroadcastService> serviceMap;

    public CourseNotificationService(CourseNotificationRegistry courseNotificationRegistry, CourseNotificationSettingService courseNotificationSettingService,
            CourseNotificationRepository courseNotificationRepository, CourseNotificationParameterRepository courseNotificationParameterRepository,
            UserCourseNotificationStatusService userCourseNotificationStatusService, UserRepository userRepository, CourseNotificationWebappService webappService,
            CourseNotificationPushService pushService, CourseNotificationEmailService emailService) {
        this.courseNotificationRegistry = courseNotificationRegistry;
        this.courseNotificationSettingService = courseNotificationSettingService;
        this.courseNotificationRepository = courseNotificationRepository;
        this.courseNotificationParameterRepository = courseNotificationParameterRepository;
        this.userCourseNotificationStatusService = userCourseNotificationStatusService;
        this.serviceMap = Map.of(NotificationSettingOption.WEBAPP, webappService, NotificationSettingOption.PUSH, pushService, NotificationSettingOption.EMAIL, emailService);
    }

    /**
     * Sends a notification on all channels the notification supports (websocket, push, email, ...) to the list of
     * recipients if they have the notification type enabled.
     *
     * @param courseNotification to send.
     * @param recipients         list of recipients. Will be filtered by user settings.
     */
    public void sendCourseNotification(CourseNotification courseNotification, List<User> recipients) {
        var supportedChannels = courseNotification.getSupportedChannels();
        var setOfNotifiedUsers = new HashSet<User>();

        var courseNotificationEntityId = createCourseNotification(courseNotification);

        for (var supportedChannel : supportedChannels) {
            var service = serviceMap.get(supportedChannel);
            if (service == null) {
                continue;
            }
            var filteredRecipients = courseNotificationSettingService.filterRecipientsBy(courseNotification, recipients, supportedChannel);
            service.sendCourseNotification(convertToCourseNotificationDTO(courseNotification), filteredRecipients);

            // We keep track of the notified users so that we only create notification status entries for them
            setOfNotifiedUsers.addAll(filteredRecipients);
        }

        userCourseNotificationStatusService.batchCreateStatusForUsers(setOfNotifiedUsers, courseNotificationEntityId, courseNotification.courseId);
    }

    /**
     * Retrieves course notifications for a specific user and course.
     *
     * <p>
     * This method fetches non-archived course notifications from the repository,
     * converts each notification entity to its corresponding DTO using reflection,
     * and returns the results as a paginated list. Results are cached unless empty.
     * </p>
     *
     * @param pageable The pagination information
     * @param courseId The ID of the course
     * @param userId   The ID of the user
     * @return A paginated list of {@link CourseNotificationDTO} objects
     */
    @Cacheable(cacheNames = CourseNotificationCacheService.USER_COURSE_NOTIFICATION_CACHE, key = "'user_course_notification_' + #userId + '_' + #courseId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize", unless = "#result.getNumberOfElements() == 0")
    public Page<CourseNotificationDTO> getCourseNotifications(Pageable pageable, long courseId, long userId) {
        var courseNotificationsEntityPage = courseNotificationRepository.findCourseNotificationsByUserIdAndCourseIdAndStatusNotArchived(userId, courseId, pageable);

        return courseNotificationsEntityPage.map((courseNotificationEntity) -> {
            var classType = courseNotificationRegistry.getNotificationClass(courseNotificationEntity.getType());

            try {
                CourseNotification courseNotification = classType.getDeclaredConstructor(Long.class, ZonedDateTime.class, Map.class).newInstance(
                        courseNotificationEntity.getCourse().getId(), courseNotificationEntity.getCreationDate(), parametersToMap(courseNotificationEntity.getParameters()));

                return convertToCourseNotificationDTO(courseNotification);
            }
            catch (InstantiationException | IllegalAccessException | IllegalArgumentException | ExceptionInInitializerError | InvocationTargetException | SecurityException
                    | NoSuchMethodException e) {
                log.error("Failed to instantiate notification {}: {}", classType.getName(), e.getMessage());
                return null;
            }
        });
    }

    /**
     * Converts a set of {@link CourseNotificationParameter} entities to a map of key-value pairs.
     *
     * @param parameterSet The set of CourseNotificationParameter objects to convert
     * @return A map containing parameter keys and their corresponding values
     */
    private Map<String, String> parametersToMap(Set<CourseNotificationParameter> parameterSet) {
        Map<String, String> params = new HashMap<>();

        for (CourseNotificationParameter parameter : parameterSet) {
            params.put(parameter.getKey(), parameter.getValue());
        }

        return params;
    }

    /**
     * Initializes a new {@link CourseNotificationDTO} and returns it. This can be sent to clients.
     *
     * @param notification to be made into a record
     *
     * @return Returns the notification as a DTO.
     */
    private CourseNotificationDTO convertToCourseNotificationDTO(CourseNotification notification) {
        return new CourseNotificationDTO(notification.getReadableNotificationType(), notification.courseId, notification.creationDate, notification.getCourseNotificationCategory(),
                notification.getParameters());
    }

    /**
     * Creates a course notification in the system and persists it to the database.
     *
     * <p>
     * This method transforms a {@link CourseNotification} object into its entity representation,
     * saves it to the repository, and then stores all associated notification parameters.
     * </p>
     *
     * @param courseNotification The {@link CourseNotification} object containing notification data
     *                               including course ID, creation date and parameters
     * @return The ID of the newly created CourseNotification entity
     */
    private long createCourseNotification(CourseNotification courseNotification) {
        Course course = new Course();
        course.setId(courseNotification.courseId);

        // Package needed because of overlap in class name
        var courseNotificationEntity = new de.tum.cit.aet.artemis.course_notification.domain.CourseNotification(course,
                courseNotificationRegistry.getNotificationIdentifier(courseNotification.getClass()), courseNotification.creationDate,
                courseNotification.creationDate.plus(courseNotification.getCleanupDuration()));

        courseNotificationEntity = courseNotificationRepository.save(courseNotificationEntity);

        var parameters = courseNotification.getParameters();

        for (var key : parameters.keySet()) {
            courseNotificationParameterRepository.save(new CourseNotificationParameter(courseNotificationEntity, key, parameters.get(key)));
        }

        return courseNotificationEntity.getId();
    }
}
