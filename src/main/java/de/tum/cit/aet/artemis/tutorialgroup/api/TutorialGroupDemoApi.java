package de.tum.cit.aet.artemis.tutorialgroup.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;
import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.TutorialGroupAssignedNotification;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.CreateOrUpdateTutorialGroupRequestDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupScheduleDTO;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupChannelManagementService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupFreePeriodService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupScheduleService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

/**
 * Creates the tutorial groups of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(TutorialGroupEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class TutorialGroupDemoApi extends AbstractTutorialGroupApi {

    /**
     * Titles of the demo tutorial groups. A title is the idempotency key of its group within the demo course, so none of them may change. Titles have at most 19 characters.
     */
    private static final String MONDAY_GROUP_TITLE = "Monday Tutorial";

    private static final String THURSDAY_GROUP_TITLE = "Thursday Online";

    private static final String MONDAY_GROUP_DESCRIPTION = """
            Weekly exercise session on campus: in small teams, we work through the current exercises on software architecture, algorithms and object-oriented modeling \
            and discuss your questions.""";

    private static final String THURSDAY_GROUP_DESCRIPTION = """
            The online counterpart of the Monday tutorial: we solve the current exercises on software architecture, algorithms and object-oriented modeling together on a \
            shared screen and discuss your questions.""";

    /**
     * Reason of the demo free period. Used as its idempotency key within the demo course, so it must stay stable.
     */
    private static final String FREE_PERIOD_REASON = "Reading Week";

    private static final String LANGUAGE = "English";

    private static final int CAPACITY = 15;

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupDemoApi.class);

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    private final UserRepository userRepository;

    private final TutorialGroupScheduleService tutorialGroupScheduleService;

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private final TutorialGroupService tutorialGroupService;

    private final TutorialGroupFreePeriodService tutorialGroupFreePeriodService;

    private final CourseNotificationService courseNotificationService;

    public TutorialGroupDemoApi(TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository, TutorialGroupRepository tutorialGroupRepository,
            TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository, UserRepository userRepository,
            TutorialGroupScheduleService tutorialGroupScheduleService, TutorialGroupChannelManagementService tutorialGroupChannelManagementService,
            TutorialGroupService tutorialGroupService, TutorialGroupFreePeriodService tutorialGroupFreePeriodService, CourseNotificationService courseNotificationService) {
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
        this.userRepository = userRepository;
        this.tutorialGroupScheduleService = tutorialGroupScheduleService;
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
        this.tutorialGroupService = tutorialGroupService;
        this.tutorialGroupFreePeriodService = tutorialGroupFreePeriodService;
        this.courseNotificationService = courseNotificationService;
    }

    /**
     * Creates the tutorial groups of the demo course in the given course, as far as they do not exist yet: the configuration of its tutorial groups, two weekly tutorial groups
     * led by the demo tutor and a free period that cancels their sessions for a week. The demo student and four classmates meet on campus on Mondays, the remaining six
     * classmates online on Thursdays.
     * <p>
     * The configuration exists once the tutorial groups of the course have been configured, a tutorial group is identified by its title within the course and the free period by
     * its reason. Each of them is created like the production creation paths create it, see the methods below, and only if it is missing, so that deleted demo content comes
     * back on the next startup without touching anything else.
     * <p>
     * The tutorial period spans the course, which started a month before it was created, so the groups show past sessions as well as upcoming ones. The tutor recorded the
     * attendance of the last session of each group that took place before the group was created. The free period lies in the week after next relative to the time it is
     * created. These dates are never revisited, so once the free period is over, the sessions it cancelled stay visible in the past.
     *
     * @param course   the demo course the tutorial groups belong to.
     * @param tutor    the demo tutor, who leads both tutorial groups.
     * @param students the demo student followed by their ten classmates, who are registered for the tutorial groups.
     */
    public void createDemo(Course course, User tutor, List<User> students) {
        if (course.getTimeZone() == null) {
            // Like TutorialGroupsConfigurationResource, which refuses to configure the tutorial groups of a course without the time zone their sessions take place in.
            log.warn("Skipping the demo tutorial groups, because the demo course has no time zone. Set one in the course settings to seed them on the next start.");
            return;
        }
        TutorialGroupsConfiguration configuration = findOrCreateConfiguration(course);
        // The acting user creates the groups and registers the students, as the user of the request does in the production paths.
        User creator = userRepository.getUser();
        var mondayGroup = new CreateOrUpdateTutorialGroupRequestDTO(MONDAY_GROUP_TITLE, tutor.getId(), LANGUAGE, false, "Main Campus", CAPACITY, MONDAY_GROUP_DESCRIPTION,
                weeklySchedule(configuration, DayOfWeek.MONDAY, LocalTime.of(10, 0), "Room 01.07.014"));
        var thursdayGroup = new CreateOrUpdateTutorialGroupRequestDTO(THURSDAY_GROUP_TITLE, tutor.getId(), LANGUAGE, true, null, CAPACITY, THURSDAY_GROUP_DESCRIPTION,
                weeklySchedule(configuration, DayOfWeek.THURSDAY, LocalTime.of(16, 0), "Online via video call"));
        seedTutorialGroup(course, configuration, creator, mondayGroup, students.subList(0, 5));
        seedTutorialGroup(course, configuration, creator, thursdayGroup, students.subList(5, students.size()));
        seedFreePeriod(course, configuration);
    }

    /**
     * Returns the tutorial groups configuration of the course, or configures the tutorial groups of the course like {@code TutorialGroupsConfigurationResource#create} does if
     * they have not been configured yet. Every course owns its settings row from the start, so configuring means activating it: the tutorial period spans the course from its
     * start to its end date in the time zone of the course, and every tutorial group gets a public channel.
     */
    private TutorialGroupsConfiguration findOrCreateConfiguration(Course course) {
        Optional<TutorialGroupsConfiguration> existingConfiguration = tutorialGroupsConfigurationRepository.findByCourseId(course.getId());
        if (existingConfiguration.isPresent()) {
            log.debug("Tutorial groups configuration of the demo course already exists, skipping creation");
            return existingConfiguration.get();
        }

        ZoneId timeZone = ZoneId.of(course.getTimeZone());
        tutorialGroupsConfigurationRepository.activateSettings(course.getId(), course.getStartDate().withZoneSameInstant(timeZone).toLocalDate().toString(),
                course.getEndDate().withZoneSameInstant(timeZone).toLocalDate().toString(), true, true);
        TutorialGroupsConfiguration configuration = tutorialGroupsConfigurationRepository.findByCourseId(course.getId())
                .orElseThrow(() -> new EntityNotFoundException("TutorialGroupsConfiguration", course.getId()));
        tutorialGroupChannelManagementService.createTutorialGroupsChannelsForAllTutorialGroupsOfCourse(course);
        log.info("Created the tutorial groups configuration of the demo course with id {}", configuration.getId());
        return configuration;
    }

    /**
     * The weekly schedule of a demo tutorial group as the client sends it: two hours from the given time on the given day, from the first such day of the tutorial period to
     * its end.
     */
    private static TutorialGroupScheduleDTO weeklySchedule(TutorialGroupsConfiguration configuration, DayOfWeek day, LocalTime start, String location) {
        LocalDate firstSession = LocalDate.parse(configuration.getTutorialPeriodStartInclusive()).with(TemporalAdjusters.nextOrSame(day));
        return new TutorialGroupScheduleDTO(firstSession.atTime(start), firstSession.atTime(start.plusHours(2)), 1, LocalDate.parse(configuration.getTutorialPeriodEndInclusive()),
                location);
    }

    /**
     * Creates the tutorial group the request describes, unless the course already has a tutorial group with its title, like {@code TutorialGroupResource#createTutorialGroup}
     * does: the group is saved with its schedule and the sessions the schedule generates, which a free period cancels where they overlap with it, gets its channel if the
     * configuration asks for one, and its tutor is notified. The students are then registered like {@code TutorialGroupResource#batchRegisterStudents} registers them, which
     * notifies them and adds them to the channel, and all but one of them attended the last session that has already taken place.
     */
    private void seedTutorialGroup(Course course, TutorialGroupsConfiguration configuration, User creator, CreateOrUpdateTutorialGroupRequestDTO request, List<User> students) {
        if (tutorialGroupRepository.existsByTitleAndCourse(request.title(), course)) {
            log.debug("Demo tutorial group '{}' already exists, skipping creation", request.title());
            return;
        }
        request.tutorialGroupSchedule().validateMaximumTutorialPeriodLength();

        User tutor = userRepository.findByIdElseThrow(request.tutorId());
        TutorialGroup tutorialGroup = tutorialGroupRepository.save(new TutorialGroup(course, request.title(), request.additionalInformation(), request.capacity(),
                request.isOnline(), request.campus(), request.language(), tutor, new HashSet<>()));
        TutorialGroupSchedule schedule = TutorialGroupScheduleDTO.toTutorialGroupSchedule(request.tutorialGroupSchedule());
        tutorialGroupScheduleService.saveScheduleAndGenerateScheduledSessions(course, tutorialGroup, schedule);
        // Saving the group again when its channel is created would otherwise remove the schedule as an orphan.
        tutorialGroup.setTutorialGroupSchedule(schedule);
        if (configuration.getUseTutorialGroupChannels()) {
            tutorialGroupChannelManagementService.createChannelForTutorialGroup(tutorialGroup);
        }
        if (!creator.equals(tutor)) {
            courseNotificationService.sendCourseNotification(new TutorialGroupAssignedNotification(course.getId(), course.getTitle(), course.getCourseIcon(),
                    tutorialGroup.getTitle(), tutorialGroup.getId(), creator.getName()), List.of(tutor));
        }

        tutorialGroupService.registerMultipleStudentsViaLogin(tutorialGroup, students.stream().map(User::getLogin).toList(), TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION,
                creator);
        recordAttendanceOfLastSession(tutorialGroup, students.size() - 1);
        log.info("Created demo tutorial group '{}' with id {}", tutorialGroup.getTitle(), tutorialGroup.getId());
    }

    /**
     * Records the attendance of the last session of the group that has already taken place, if there is one, like the tutor records it through
     * {@code TutorialGroupSessionResource#updateSession}: a session that was edited no longer follows the schedule of its group, so that changing the schedule keeps it. The
     * session keeps its time and location and remains active, so the checks for overlapping sessions and free periods that the update runs leave it as it is.
     */
    private void recordAttendanceOfLastSession(TutorialGroup tutorialGroup, int attendance) {
        ZonedDateTime now = ZonedDateTime.now();
        tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId()).stream()
                .filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE && session.getEnd().isBefore(now))
                .max(Comparator.comparing(TutorialGroupSession::getStart)).ifPresent(session -> {
                    session.setAttendanceCount(attendance);
                    session.setTutorialGroupSchedule(null);
                    tutorialGroupSessionRepository.save(session);
                });
    }

    /**
     * Creates the demo free period, unless the course already has a free period with its reason, like {@code TutorialGroupFreePeriodResource#create} does: the free period is
     * saved and cancels the sessions that overlap with it. It spans the week after next from Monday 00:00 to Sunday 23:59 in the time zone of the course, like a free period
     * over whole days that an instructor enters in the client, so that a cancellation shows up among the upcoming sessions.
     * <p>
     * Free periods must not overlap, because a cancelled session names a single free period as the reason, so the demo free period is skipped if another free period already
     * covers part of that week.
     */
    private void seedFreePeriod(Course course, TutorialGroupsConfiguration configuration) {
        if (tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(course.getId()).stream()
                .anyMatch(freePeriod -> FREE_PERIOD_REASON.equals(freePeriod.getReason()))) {
            log.debug("Demo free period '{}' already exists, skipping creation", FREE_PERIOD_REASON);
            return;
        }

        LocalDate weekAfterNext = LocalDate.now(ZoneId.of(course.getTimeZone())).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
        TutorialGroupFreePeriod freePeriod = new TutorialGroupFreePeriod();
        freePeriod.setTutorialGroupsConfiguration(configuration);
        freePeriod.setReason(FREE_PERIOD_REASON);
        freePeriod.setStart(interpretInTimeZone(weekAfterNext, LocalTime.MIDNIGHT, course.getTimeZone()));
        freePeriod.setEnd(interpretInTimeZone(weekAfterNext.plusDays(6), LocalTime.of(23, 59), course.getTimeZone()));
        if (!tutorialGroupFreePeriodRepository.findOverlappingInSameCourseExclusive(course, freePeriod.getStart(), freePeriod.getEnd()).isEmpty()) {
            log.info("Skipping the demo free period '{}', because another free period of the demo course overlaps with it", FREE_PERIOD_REASON);
            return;
        }

        TutorialGroupFreePeriod savedFreePeriod = tutorialGroupFreePeriodRepository.save(freePeriod);
        tutorialGroupFreePeriodService.cancelOverlappingSessions(course, savedFreePeriod);
        log.info("Created demo free period '{}' with id {}", FREE_PERIOD_REASON, savedFreePeriod.getId());
    }
}
