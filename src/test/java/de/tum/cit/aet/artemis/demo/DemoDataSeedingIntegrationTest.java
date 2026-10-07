package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.api.UserApi;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.api.CompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.course.api.CourseApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.lecture.api.LectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the demo course seeding routine of the {@code demo} profile.
 * <p>
 * The {@link DemoDataSeedingService} bean itself only exists when the {@code demo} and {@code scheduling} profiles are active, which no test context activates: adding a profile
 * would create another Spring context, and CI caps the number of server starts. The service has a plain constructor and all of its collaborators exist in this context anyway, so
 * the tests construct it by hand and invoke the listener directly. That exercises the real APIs against the real database without any profile handling.
 * <p>
 * Seeding deliberately uses fixed identifiers, so these tests write a course named {@code demo} and the two demo users into the shared test database instead of prefixed test data.
 * That is safe precisely because seeding is idempotent, which is also why every test can seed first and still be correct regardless of what ran before it. The methods must not run
 * in parallel though, hence {@link ExecutionMode#SAME_THREAD}.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoDataSeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private CourseApi courseApi;

    @Autowired
    private UserApi userApi;

    @Autowired
    private LectureApi lectureApi;

    @Autowired
    private CompetencyApi competencyApi;

    @Autowired
    private UserCourseRoleRepository userCourseRoleRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private LectureUnitService lectureUnitService;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository;

    @Test
    void seedsDemoContent() {
        seed();

        Course course = demoCourse().orElseThrow();
        assertThat(courseRepository.ensureDefaultConfigurations(course.getId())).as("demo course owns all of its default settings rows").isZero();
        assertThat(course.getSemester()).as("demo course has a semester in the format the client expects").matches("SS\\d{2}|WS\\d{2}/\\d{2}");
        User student = userTestRepository.findOneByLogin(UserApi.DEMO_STUDENT_LOGIN).orElseThrow();
        User instructor = userTestRepository.findOneByLogin(UserApi.DEMO_INSTRUCTOR_LOGIN).orElseThrow();

        assertThat(student.getActivated()).as("demo student can log in").isTrue();
        assertThat(instructor.getActivated()).as("demo instructor can log in").isTrue();
        assertThat(userCourseRoleRepository.existsByUser_IdAndCourse_IdAndRole(student.getId(), course.getId(), CourseRole.STUDENT)).as("demo student is enrolled").isTrue();
        assertThat(userCourseRoleRepository.existsByUser_IdAndCourse_IdAndRole(instructor.getId(), course.getId(), CourseRole.INSTRUCTOR)).as("demo instructor is enrolled")
                .isTrue();

        List<LectureUnit> lectureUnits = demoLectureUnits(course.getId());
        assertThat(lectureUnits).as("demo lecture has its text unit").hasSize(1);

        Set<Competency> competencies = competencyRepository.findAllByCourseId(course.getId());
        assertThat(competencies).as("demo competency exists").hasSize(1);
        assertThat(linkedLectureUnitIds(competencies)).as("demo competency is linked to the text unit").containsExactly(lectureUnits.getFirst().getId());
    }

    @Test
    void seedingTwiceCreatesNothingNew() {
        seed();
        DemoDataSnapshot afterFirstRun = snapshotDemoData();

        seed();

        assertThat(snapshotDemoData()).as("seeding an already seeded database must neither create nor replace anything").isEqualTo(afterFirstRun);
    }

    @Test
    void recreatesOnlyMissingContent() {
        seed();
        Course course = demoCourse().orElseThrow();
        demoLectures(course.getId()).forEach(lecture -> competencyLectureUnitLinkRepository.deleteAllByLectureId(lecture.getId()));
        competencyRepository.deleteAllByCourseId(course.getId());

        DemoDataSnapshot withoutCompetency = snapshotDemoData();
        assertThat(withoutCompetency.competencyIds()).as("competency was removed for this test").isEmpty();

        seed();

        DemoDataSnapshot afterReseeding = snapshotDemoData();
        assertThat(afterReseeding.competencyIds()).as("missing competency is recreated").hasSize(1);
        assertThat(afterReseeding.linkedLectureUnitIds()).as("missing competency is linked again").isEqualTo(withoutCompetency.lectureUnitIds());
        assertThat(afterReseeding).as("everything that still existed is left alone").isEqualTo(new DemoDataSnapshot(withoutCompetency.courseIds(), withoutCompetency.userIds(),
                withoutCompetency.lectureIds(), withoutCompetency.lectureUnitIds(), afterReseeding.competencyIds(), afterReseeding.linkedLectureUnitIds()));
    }

    @Test
    void recreatesMissingTextUnitInExistingLecture() {
        seed();
        DemoDataSnapshot seeded = snapshotDemoData();
        lectureUnitService.removeLectureUnit(demoLectureUnits(demoCourse().orElseThrow().getId()).getFirst());
        assertThat(snapshotDemoData().lectureUnitIds()).as("text unit was removed for this test").isEmpty();

        seed();

        DemoDataSnapshot afterReseeding = snapshotDemoData();
        assertThat(afterReseeding.lectureIds()).as("the existing lecture is reused").isEqualTo(seeded.lectureIds());
        assertThat(afterReseeding.lectureUnitIds()).as("missing text unit is recreated").hasSize(1).doesNotContainAnyElementsOf(seeded.lectureUnitIds());
        assertThat(afterReseeding.competencyIds()).as("the existing competency is reused").isEqualTo(seeded.competencyIds());
        assertThat(afterReseeding.linkedLectureUnitIds()).as("the existing competency is linked to the recreated text unit").isEqualTo(afterReseeding.lectureUnitIds());
    }

    @Test
    void doesNotLinkLectureUnitsAddedByUsers() {
        seed();
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(demoLectures(demoCourse().orElseThrow().getId()).iterator().next().getId());
        lecture.addLectureUnit(LectureFactory.generateTextUnit("Unit added by an instructor", "Not part of the seeded demo content."));
        LectureUnit addedUnit = lectureRepository.saveAndFlush(lecture).getLectureUnits().getLast();
        try {
            seed();

            DemoDataSnapshot afterReseeding = snapshotDemoData();
            assertThat(afterReseeding.lectureUnitIds()).as("the added unit is kept").contains(addedUnit.getId()).hasSize(2);
            assertThat(afterReseeding.linkedLectureUnitIds()).as("only the seeded text unit is linked").hasSize(1).doesNotContain(addedUnit.getId());
        }
        finally {
            lectureUnitService.removeLectureUnit(addedUnit);
        }
    }

    @Test
    void seedsWithoutOptionalModules() {
        seed();
        DemoDataSnapshot beforeRun = snapshotDemoData();

        DemoDataSeedingService withoutOptionalModules = new DemoDataSeedingService(courseApi, userApi, Optional.empty(), Optional.empty());
        assertThatCode(() -> withoutOptionalModules.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent()))
                .as("seeding must work when the lecture and atlas modules are disabled").doesNotThrowAnyException();

        assertThat(snapshotDemoData()).as("disabled modules must not change existing demo data").isEqualTo(beforeRun);
    }

    private void seed() {
        new DemoDataSeedingService(courseApi, userApi, Optional.of(lectureApi), Optional.of(competencyApi)).seedDemoData(new DeferredEagerBeanInitializationCompletedEvent());
    }

    private Optional<Course> demoCourse() {
        return courseRepository.findAllByShortName(CourseApi.DEMO_COURSE_SHORT_NAME).stream().findFirst();
    }

    private Set<Lecture> demoLectures(long courseId) {
        return lectureRepository.findAllByCourseId(courseId);
    }

    private List<LectureUnit> demoLectureUnits(long courseId) {
        return demoLectures(courseId).stream().flatMap(lecture -> lectureRepository.findByIdWithLectureUnitsElseThrow(lecture.getId()).getLectureUnits().stream()).toList();
    }

    private Set<Long> linkedLectureUnitIds(Set<Competency> competencies) {
        if (competencies.isEmpty()) {
            return Set.of();
        }
        return competencyLectureUnitLinkRepository.findLectureUnitIdsByCompetencyIds(competencies.stream().map(Competency::getId).collect(Collectors.toSet()));
    }

    /**
     * Captures the identities of the demo data instead of naming it, so that the idempotency assertions detect replaced records and keep working as the seeded content grows.
     */
    private DemoDataSnapshot snapshotDemoData() {
        Set<Long> userIds = Stream.of(UserApi.DEMO_STUDENT_LOGIN, UserApi.DEMO_INSTRUCTOR_LOGIN).flatMap(login -> userTestRepository.findOneByLogin(login).stream())
                .map(User::getId).collect(Collectors.toSet());
        Set<Long> courseIds = courseRepository.findAllByShortName(CourseApi.DEMO_COURSE_SHORT_NAME).stream().map(Course::getId).collect(Collectors.toSet());
        Optional<Course> course = demoCourse();
        if (course.isEmpty()) {
            return new DemoDataSnapshot(courseIds, userIds, Set.of(), Set.of(), Set.of(), Set.of());
        }
        long courseId = course.get().getId();
        Set<Competency> competencies = competencyRepository.findAllByCourseId(courseId);
        return new DemoDataSnapshot(courseIds, userIds, demoLectures(courseId).stream().map(Lecture::getId).collect(Collectors.toSet()),
                demoLectureUnits(courseId).stream().map(LectureUnit::getId).collect(Collectors.toSet()), competencies.stream().map(Competency::getId).collect(Collectors.toSet()),
                linkedLectureUnitIds(competencies));
    }

    private record DemoDataSnapshot(Set<Long> courseIds, Set<Long> userIds, Set<Long> lectureIds, Set<Long> lectureUnitIds, Set<Long> competencyIds,
            Set<Long> linkedLectureUnitIds) {
    }
}
