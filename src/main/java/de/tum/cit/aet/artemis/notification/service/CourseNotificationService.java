package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsageCollector;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.domain.CourseNotificationParameter;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationPageableDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationParameterDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;
import de.tum.cit.aet.artemis.notification.repository.CourseNotificationParameterRepository;
import de.tum.cit.aet.artemis.notification.repository.CourseNotificationRepository;

/**
 * Service that handles all course notification logic. Whenever you want to create a new notification use this service
 * to send it to the users.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationService.class);

    private static final String NOTIFICATION_MODULE = "notification";

    private final CourseNotificationRegistryService courseNotificationRegistryService;

    private final CourseNotificationSettingService courseNotificationSettingService;

    private final CourseNotificationRepository courseNotificationRepository;

    private final CourseNotificationParameterRepository courseNotificationParameterRepository;

    private final UserCourseNotificationStatusService userCourseNotificationStatusService;

    private final Map<NotificationChannelOption, CourseNotificationBroadcastService> serviceMap;

    private final FeatureUsageCollector featureUsageCollector;

    public CourseNotificationService(CourseNotificationRegistryService courseNotificationRegistryService, CourseNotificationSettingService courseNotificationSettingService,
            CourseNotificationRepository courseNotificationRepository, CourseNotificationParameterRepository courseNotificationParameterRepository,
            UserCourseNotificationStatusService userCourseNotificationStatusService, CourseNotificationWebappService webappService, CourseNotificationPushService pushService,
            CourseNotificationEmailService emailService, FeatureUsageCollector featureUsageCollector) {
        this.courseNotificationRegistryService = courseNotificationRegistryService;
        this.courseNotificationSettingService = courseNotificationSettingService;
        this.courseNotificationRepository = courseNotificationRepository;
        this.courseNotificationParameterRepository = courseNotificationParameterRepository;
        this.userCourseNotificationStatusService = userCourseNotificationStatusService;
        this.serviceMap = Map.of(NotificationChannelOption.WEBAPP, webappService, NotificationChannelOption.PUSH, pushService, NotificationChannelOption.EMAIL, emailService);
        this.featureUsageCollector = featureUsageCollector;
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

        courseNotification.notificationId = courseNotificationEntityId;

        for (var supportedChannel : supportedChannels) {
            var service = serviceMap.get(supportedChannel);
            if (service == null) {
                continue;
            }
            var filteredRecipients = courseNotificationSettingService.filterRecipientsBy(courseNotification, recipients, supportedChannel);
            var recipientDTOs = filteredRecipients.stream().map(CourseNotificationRecipientDTO::from).toList();
            // One count per notification that actually reached somebody on this channel, which is what answers whether a
            // channel is worth maintaining. Sends that every recipient has switched off are not usage.
            boolean anybodyToDeliverTo = !filteredRecipients.isEmpty();
            String feature = "course-notification/" + supportedChannel.name().toLowerCase(Locale.ROOT);
            long startNanos = System.nanoTime();
            try {
                var delivery = service.sendCourseNotification(convertToCourseNotificationDTO(courseNotification, UserCourseNotificationStatusType.UNSEEN), recipientDTOs);
                if (anybodyToDeliverTo) {
                    // Recorded when the channel finishes, not when it is handed the work. Two of the three channels are
                    // @Async, so at this point nothing has been delivered yet and a failure inside them could never
                    // reach this code: every send was reported as a success and the error rate of those two features
                    // was zero however badly delivery was going.
                    //
                    // What "finishes" means is not the same for every channel. Webapp and e-mail complete after doing
                    // the work, so their error rate is a delivery signal. Push completes on dispatch, because its relay
                    // pipeline swallows failures by design, so its count answers "is push used" and its error rate
                    // answers nothing. The admin documentation says this rather than leaving a zero to be misread.
                    delivery.whenComplete((ignored, failure) -> recordChannelUsage(feature, failure != null, elapsedMillis(startNanos)));
                }
            }
            catch (Exception e) {
                // A synchronous channel that throws never completes a future, so it would otherwise go uncounted.
                if (anybodyToDeliverTo) {
                    recordChannelUsage(feature, true, elapsedMillis(startNanos));
                }
                throw e;
            }

            // We keep track of the notified users so that we only create notification status entries for them
            setOfNotifiedUsers.addAll(filteredRecipients);
        }

        userCourseNotificationStatusService.batchCreateStatusForUsers(setOfNotifiedUsers, courseNotificationEntityId, courseNotification.courseId);
    }

    private void recordChannelUsage(String feature, boolean failed, long durationMs) {
        featureUsageCollector.recordUsage(FeatureKind.BACKGROUND, NOTIFICATION_MODULE, feature, Role.ANONYMOUS, failed, durationMs);
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
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
     * <p>
     * Since there may be some issues with serializing a Spring Boot {@link Page}, we created a wrapper class
     * {@link CourseNotificationPageableDTO} which lets us cache the paging result without any issues.
     * </p>
     *
     * @param pageable The pagination information
     * @param courseId The ID of the course
     * @param userId   The ID of the user
     * @return A paginated list of {@link CourseNotificationDTO} objects
     */
    @Cacheable(cacheNames = CourseNotificationCacheService.USER_COURSE_NOTIFICATION_CACHE, key = "'user_course_notification_' + #userId + '_' " + "+ #courseId + '_' "
            + "+ (#pageable != null ? (#pageable.isPaged() ? #pageable.pageNumber : 'unpaged') : 'null') + '_' "
            + "+ (#pageable != null ? (#pageable.isPaged() ? #pageable.pageSize : 'unpaged') : 'null')", unless = "#result.totalElements() == 0")
    public CourseNotificationPageableDTO<CourseNotificationDTO> getCourseNotifications(Pageable pageable, long courseId, long userId) {
        var courseNotificationPage = courseNotificationRepository.findCourseNotificationsByUserIdAndCourseIdAndStatusNotArchived(userId, courseId, pageable);

        return CourseNotificationPageableDTO.from(courseNotificationPage.map((courseNotificationDTO) -> {
            var classType = courseNotificationRegistryService.getNotificationClass(courseNotificationDTO.notificationType());

            try {
                var parameters = courseNotificationParameterRepository.findByCourseNotificationIdEquals(courseNotificationDTO.notificationId());

                CourseNotification courseNotification = classType.getDeclaredConstructor(Long.class, Long.class, ZonedDateTime.class, Map.class)
                        .newInstance(courseNotificationDTO.notificationId(), courseNotificationDTO.courseId(), courseNotificationDTO.creationDate(), parametersToMap(parameters));

                return convertToCourseNotificationDTO(courseNotification, courseNotificationDTO.status());
            }
            catch (InstantiationException | IllegalAccessException | IllegalArgumentException | ExceptionInInitializerError | InvocationTargetException | SecurityException
                    | NoSuchMethodException e) {
                log.error("Failed to instantiate notification {}: {} - {}", classType.getName(), e.getClass(), e.getMessage());
                return null;
            }
        }));
    }

    /**
     * Converts a set of {@link CourseNotificationParameterDTO} records to a map of key-value pairs.
     *
     * @param parameterSet The set of CourseNotificationParameterDTO records to convert
     * @return A map containing parameter keys and their corresponding values
     */
    private Map<String, String> parametersToMap(Set<CourseNotificationParameterDTO> parameterSet) {
        var params = new HashMap<String, String>();

        for (CourseNotificationParameterDTO parameter : parameterSet) {
            params.put(parameter.key(), parameter.value());
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
    private CourseNotificationDTO convertToCourseNotificationDTO(CourseNotification notification, UserCourseNotificationStatusType status) {
        return new CourseNotificationDTO(notification.getReadableNotificationType(), notification.notificationId, notification.courseId, notification.creationDate,
                notification.getCourseNotificationCategory(), notification.courseTitle(), notification.courseIconUrl(), notification.payload(), status,
                notification.getRelativeWebAppUrl());
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
        var courseNotificationEntity = new de.tum.cit.aet.artemis.notification.domain.CourseNotification(course,
                courseNotificationRegistryService.getNotificationIdentifier(courseNotification.getClass()), courseNotification.creationDate,
                courseNotification.creationDate.plus(courseNotification.getCleanupDuration()));

        courseNotificationEntity = courseNotificationRepository.save(courseNotificationEntity);

        var parameters = courseNotification.getParameters();
        var parameterEntities = new ArrayList<CourseNotificationParameter>();

        for (var key : parameters.keySet()) {
            if (parameters.get(key) == null) {
                continue;
            }

            String paramValue = parameters.get(key).toString();
            if (paramValue.length() > 500) {
                paramValue = paramValue.substring(0, 499);
            }

            parameterEntities.add(new CourseNotificationParameter(courseNotificationEntity, key, paramValue));
        }

        if (!parameterEntities.isEmpty()) {
            courseNotificationParameterRepository.saveAll(parameterEntities);
        }

        return courseNotificationEntity.getId();
    }
}
