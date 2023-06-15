package de.tum.in.www1.artemis.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.*;

/**
 * Service responsible for initializing the database with specific testdata related to tutorial groups for use in integration tests.
 */
@Service
public class TutorialGroupUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-editors:#{null}}")
    private Optional<String> tutorialGroupEditors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    private TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    private TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    private TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    @Autowired
    private TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    public void addTutorialCourse() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), tutorialGroupStudents.get(), tutorialGroupTutors.get(),
                tutorialGroupEditors.get(), tutorialGroupInstructors.get());
        courseRepo.save(course);
        assertThat(courseRepo.findById(course.getId())).as("tutorial course is initialized").isPresent();
    }

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

    public TutorialGroupFreePeriod addTutorialGroupFreeDay(Long tutorialGroupsConfigurationId, LocalDate date, String reason) {
        var tutorialGroupsConfiguration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(tutorialGroupsConfigurationId);
        var course = tutorialGroupsConfiguration.getCourse();

        TutorialGroupFreePeriod newTutorialGroupFreePeriod = new TutorialGroupFreePeriod();
        newTutorialGroupFreePeriod.setTutorialGroupsConfiguration(tutorialGroupsConfiguration);
        newTutorialGroupFreePeriod.setReason(reason);

        newTutorialGroupFreePeriod.setStart(interpretInTimeZone(date, START_OF_DAY, course.getTimeZone()));
        newTutorialGroupFreePeriod.setEnd(interpretInTimeZone(date, END_OF_DAY, course.getTimeZone()));

        return tutorialGroupFreePeriodRepository.save(newTutorialGroupFreePeriod);
    }

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
}
