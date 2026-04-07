package de.tum.cit.aet.artemis.tutorialgroup.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.DateUtil;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupRegistrationTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupScheduleTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to tutorial groups for use in integration tests.
 */
@Lazy
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
    protected TutorialGroupScheduleTestRepository tutorialGroupScheduleTestRepository;

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

        var tutorialGroup = TutorialGroupFactory.generateTutorialGroup(course, title, additionalInformation, capacity, isOnline, campus, language, teachingAssistant, null);

        var persistedTutorialGroup = tutorialGroupRepository.saveAndFlush(tutorialGroup);

        var registrations = new HashSet<TutorialGroupRegistration>();
        for (var student : registeredStudents) {
            registrations.add(new TutorialGroupRegistration(student, persistedTutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION));
        }
        tutorialGroupRegistrationRepository.saveAllAndFlush(registrations);
        return persistedTutorialGroup;
    }

    public TutorialGroupSchedule createAndSaveTutorialGroupSchedule(TutorialGroup tutorialGroup, int dayOfWeek, String startTime, String endTime, int repetitionFrequency,
            String validFromInclusive, String validToInclusive, String location) {
        TutorialGroupSchedule tutorialGroupSchedule = new TutorialGroupSchedule();
        tutorialGroupSchedule.setTutorialGroup(tutorialGroup);
        tutorialGroupSchedule.setDayOfWeek(dayOfWeek);
        tutorialGroupSchedule.setStartTime(startTime);
        tutorialGroupSchedule.setEndTime(endTime);
        tutorialGroupSchedule.setRepetitionFrequency(repetitionFrequency);
        tutorialGroupSchedule.setValidFromInclusive(validFromInclusive);
        tutorialGroupSchedule.setValidToInclusive(validToInclusive);
        tutorialGroupSchedule.setLocation(location);
        tutorialGroupScheduleTestRepository.saveAndFlush(tutorialGroupSchedule);

        tutorialGroup.setTutorialGroupSchedule(tutorialGroupSchedule);
        tutorialGroupRepository.saveAndFlush(tutorialGroup);

        return tutorialGroupSchedule;
    }

    public List<TutorialGroupSession> createAndSaveRegularSessionsFromTutorialGroupSchedule(Course course, TutorialGroup tutorialGroup,
            TutorialGroupSchedule tutorialGroupSchedule) {
        List<TutorialGroupSession> sessions = new ArrayList<>();
        ZonedDateTime sessionStart = ZonedDateTime.of(LocalDate.parse(tutorialGroupSchedule.getValidFromInclusive()), LocalTime.parse(tutorialGroupSchedule.getStartTime()),
                ZoneId.of(course.getTimeZone()));
        ZonedDateTime sessionEnd = ZonedDateTime.of(LocalDate.parse(tutorialGroupSchedule.getValidFromInclusive()), LocalTime.parse(tutorialGroupSchedule.getEndTime()),
                ZoneId.of(course.getTimeZone()));
        ZonedDateTime periodEnd = ZonedDateTime.of(LocalDate.parse(tutorialGroupSchedule.getValidToInclusive()), DateUtil.END_OF_DAY, ZoneId.of(course.getTimeZone()));

        while (sessionEnd.isBefore(periodEnd) || sessionEnd.isEqual(periodEnd)) {
            TutorialGroupSession session = createTutorialGroupSession(sessionStart, sessionEnd, tutorialGroupSchedule.getLocation(), null, TutorialGroupSessionStatus.ACTIVE,
                    tutorialGroupSchedule, tutorialGroup);
            sessions.add(session);
            sessionStart = sessionStart.plusWeeks(tutorialGroupSchedule.getRepetitionFrequency());
            sessionEnd = sessionEnd.plusWeeks(tutorialGroupSchedule.getRepetitionFrequency());
        }

        tutorialGroupSessionRepository.saveAllAndFlush(sessions);

        return sessions;
    }

    public TutorialGroupSession createTutorialGroupSession(ZonedDateTime start, ZonedDateTime end, String location, Integer attendanceCount, TutorialGroupSessionStatus status,
            TutorialGroupSchedule tutorialGroupSchedule, TutorialGroup tutorialGroup) {
        TutorialGroupSession session = new TutorialGroupSession();
        session.setStart(start);
        session.setEnd(end);
        session.setLocation(location);
        session.setAttendanceCount(attendanceCount);
        session.setStatus(status);
        session.setTutorialGroupSchedule(tutorialGroupSchedule);
        session.setTutorialGroup(tutorialGroup);
        return session;
    }

    public TutorialGroup createAndSaveTutorialGroup(Long courseId, String title, String additionalInformation, Integer capacity, Boolean isOnline, String campus, String language,
            User teachingAssistant, Set<User> studentsToRegister) {
        var course = courseRepo.findByIdElseThrow(courseId);

        TutorialGroup tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle(title);
        tutorialGroup.setAdditionalInformation(additionalInformation);
        tutorialGroup.setCapacity(capacity);
        tutorialGroup.setIsOnline(isOnline);
        tutorialGroup.setLanguage(language);
        tutorialGroup.setCampus(campus);
        tutorialGroup.setTeachingAssistant(teachingAssistant);
        tutorialGroup = tutorialGroupRepository.saveAndFlush(tutorialGroup);

        var registrations = new HashSet<TutorialGroupRegistration>();
        for (var student : studentsToRegister) {
            registrations.add(new TutorialGroupRegistration(student, tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION));
        }
        tutorialGroupRegistrationRepository.saveAllAndFlush(registrations);

        return tutorialGroup;
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

    public TutorialGroup createAndSaveTutorialGroup(Course course, String title, User teachingAssistant, int capacity, String campus) {
        TutorialGroup group = createTutorialGroup(course, title, teachingAssistant, capacity, campus);
        group.setCourse(course);
        tutorialGroupRepository.saveAndFlush(group);
        return group;
    }

    TutorialGroup createTutorialGroup(Course course, String title, User teachingAssistant, int capacity, String campus) {
        var tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle(title);
        tutorialGroup.setTeachingAssistant(teachingAssistant);
        tutorialGroup.setCapacity(capacity);
        tutorialGroup.setCampus(campus);
        return tutorialGroup;
    }

}
