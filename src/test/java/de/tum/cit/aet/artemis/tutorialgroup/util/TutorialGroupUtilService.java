package de.tum.cit.aet.artemis.tutorialgroup.util;

import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupRegistrationTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to tutorial groups for use in integration tests.
 */
@Service
@Profile(SPRING_PROFILE_TEST)
public class TutorialGroupUtilService {

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private TutorialGroupTestRepository tutorialGroupRepository;

    @Autowired
    private TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    private TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    private TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    @Autowired
    private TutorialGroupRegistrationTestRepository tutorialGroupRegistrationRepository;

    /**
     * Creates and saves a TutorialGroupSession for the TutorialGroup with the given ID.
     *
     * @param tutorialGroupId The ID of the TutorialGroup
     * @param start           The start date of the TutorialGroupSession
     * @param end             The end date of the TutorialGroupSession
     * @param attendanceCount The attendance count of the TutorialGroupSession
     * @return The created TutorialGroupSession
     */
    public TutorialGroupSession createIndividualTutorialGroupSession(Long tutorialGroupId, ZonedDateTime start, ZonedDateTime end, Integer attendanceCount) {
        var tutorialGroup = tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);

        TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
        tutorialGroupSession.setStart(start);
        tutorialGroupSession.setEnd(end);
        tutorialGroupSession.setTutorialGroup(tutorialGroup);
        tutorialGroupSession.setLocation("LoremIpsum");
        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.ACTIVE);
        tutorialGroupSession.setAttendanceCount(attendanceCount);
        tutorialGroupSession = tutorialGroupSessionRepository.save(tutorialGroupSession);
        return tutorialGroupSession;
    }

    /**
     * Creates and saves a {@link TutorialGroupSession} for the {@link TutorialGroup} with the given ID,
     * allowing customization of the session status and status explanation.
     *
     * @param tutorialGroupId   The ID of the TutorialGroup
     * @param start             The start date and time of the TutorialGroupSession
     * @param end               The end date and time of the TutorialGroupSession
     * @param attendanceCount   The attendance count of the TutorialGroupSession (can be null)
     * @param status            The status of the TutorialGroupSession (e.g., ACTIVE, CANCELLED)
     * @param statusExplanation An explanation for the session's status, used especially if cancelled
     * @return The created session
     */
    public TutorialGroupSession createIndividualTutorialGroupSession(Long tutorialGroupId, ZonedDateTime start, ZonedDateTime end, Integer attendanceCount,
            TutorialGroupSessionStatus status, String statusExplanation) {
        var tutorialGroup = tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);

        TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
        tutorialGroupSession.setStart(start);
        tutorialGroupSession.setEnd(end);
        tutorialGroupSession.setTutorialGroup(tutorialGroup);
        tutorialGroupSession.setLocation("LoremIpsum");
        tutorialGroupSession.setStatus(status);
        tutorialGroupSession.setStatusExplanation(status == TutorialGroupSessionStatus.CANCELLED ? statusExplanation : null);
        tutorialGroupSession.setAttendanceCount(attendanceCount);
        return tutorialGroupSessionRepository.save(tutorialGroupSession);
    }

    /**
     * Creates and saves a TutorialGroupFreePeriod for the TutorialGroupsConfiguration with the given ID.
     *
     * @param tutorialGroupsConfigurationId The ID of the TutorialGroupsConfiguration
     * @param startDate                     The startDate of the TutorialGroupFreePeriod
     * @param endDate                       The endDate of the TutorialGroupFreePeriod
     * @param reason                        The reason for the TutorialGroupFreePeriod
     * @return The created TutorialGroupFreePeriod
     */
    public TutorialGroupFreePeriod addTutorialGroupFreePeriod(Long tutorialGroupsConfigurationId, LocalDateTime startDate, LocalDateTime endDate, String reason) {
        var tutorialGroupsConfiguration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(tutorialGroupsConfigurationId);
        var course = tutorialGroupsConfiguration.getCourse();

        TutorialGroupFreePeriod newTutorialGroupFreePeriod = new TutorialGroupFreePeriod();
        newTutorialGroupFreePeriod.setTutorialGroupsConfiguration(tutorialGroupsConfiguration);
        newTutorialGroupFreePeriod.setReason(reason);

        newTutorialGroupFreePeriod.setStart(interpretInTimeZone(startDate.toLocalDate(), startDate.toLocalTime(), course.getTimeZone()));
        newTutorialGroupFreePeriod.setEnd(interpretInTimeZone(endDate.toLocalDate(), endDate.toLocalTime(), course.getTimeZone()));

        return tutorialGroupFreePeriodRepository.save(newTutorialGroupFreePeriod);
    }

    /**
     * Creates and saves a Tutorial Group for the Course with the given ID.
     *
     * @param courseId              The ID of the Course
     * @param title                 The title of the TutorialGroup
     * @param additionalInformation The additional information of the TutorialGroup
     * @param capacity              The capacity of the TutorialGroup
     * @param isOnline              True, if the TutorialGroup is online
     * @param campus                The campus of the TutorialGroup
     * @param language              The language of the TutorialGroup
     * @param teachingAssistant     The teaching assistant of the TutorialGroup
     * @param registeredStudents    The registered students of the TutorialGroup
     * @return The created TutorialGroup
     */
    public TutorialGroup createTutorialGroup(Long courseId, String title, String additionalInformation, Integer capacity, Boolean isOnline, String campus, String language,
            User teachingAssistant, Set<User> registeredStudents) {
        var course = courseRepo.findByIdElseThrow(courseId);

        var tutorialGroup = TutorialGroupFactory.generateTutorialGroup(title, additionalInformation, capacity, isOnline, language, campus);
        tutorialGroup.setCourse(course);
        tutorialGroup.setTeachingAssistant(teachingAssistant);

        var persistedTutorialGroup = tutorialGroupRepository.saveAndFlush(tutorialGroup);

        var registrations = new HashSet<TutorialGroupRegistration>();
        for (var student : registeredStudents) {
            registrations.add(new TutorialGroupRegistration(student, persistedTutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION));
        }
        tutorialGroupRegistrationRepository.saveAllAndFlush(registrations);
        return persistedTutorialGroup;
    }

    /**
     * Creates and saves a TutorialGroupsConfiguration for the Course with the given ID.
     *
     * @param courseId The ID of the Course
     * @param start    The start date of the TutorialGroupsConfiguration
     * @param end      The end date of the TutorialGroupsConfiguration
     * @return The created TutorialGroupsConfiguration
     */
    public TutorialGroupsConfiguration createTutorialGroupConfiguration(Long courseId, LocalDate start, LocalDate end) {
        var course = courseRepo.findByIdElseThrow(courseId);
        var tutorialGroupConfiguration = TutorialGroupFactory.generateTutorialGroupsConfiguration(start, end);
        tutorialGroupConfiguration.setCourse(course);
        var persistedConfiguration = tutorialGroupsConfigurationRepository.save(tutorialGroupConfiguration);
        course.setTutorialGroupsConfiguration(persistedConfiguration);
        course = courseRepo.save(course);
        persistedConfiguration.setCourse(course);
        return persistedConfiguration;
    }

    /**
     * Creates a list {@link TutorialGroupSession} instances for the given {@link TutorialGroup},
     * starting 5 days after the course's start date. All sessions span two hours each.
     * Repeats weekly until the course's end date.
     *
     * @param tutorialGroup            the tutorial group to which the sessions belong
     * @param course                   the course associated with the tutorial group
     * @param includeCancelledSessions indicates whether the first two sessions should be cancelled
     * @return the created sessions
     * @throws IllegalArgumentException if the course has no start or end date
     */
    public List<TutorialGroupSession> createTutorialGroupSessions(TutorialGroup tutorialGroup, Course course, boolean includeCancelledSessions) {
        if (course.getStartDate() == null || course.getEndDate() == null) {
            throw new IllegalArgumentException("Course must have a start and end date");
        }
        List<TutorialGroupSession> sessions = new ArrayList<>();
        ZonedDateTime start = course.getStartDate().plusDays(5).withHour(12).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime endDate = course.getEndDate();
        for (int i = 0; !start.plusWeeks(i).isAfter(endDate); i++) {
            ZonedDateTime sessionStart = start.plusWeeks(i);
            ZonedDateTime sessionEnd = sessionStart.plusHours(2);
            TutorialGroupSessionStatus sessionStatus;
            if (includeCancelledSessions) {
                sessionStatus = i < 2 ? TutorialGroupSessionStatus.CANCELLED : TutorialGroupSessionStatus.ACTIVE;
            }
            else {
                sessionStatus = TutorialGroupSessionStatus.ACTIVE;
            }
            TutorialGroupSession session = createIndividualTutorialGroupSession(tutorialGroup.getId(), sessionStart, sessionEnd, null, sessionStatus, null);
            sessions.add(session);
        }
        return sessions;
    }
}
