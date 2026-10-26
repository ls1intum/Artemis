package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.api.LectureCourseHomeApi;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@WithMockUser(username = LectureCourseHomeApiTest.PREFIX + "student1", roles = "USER")
class LectureCourseHomeApiTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homelectures";

    @Autowired
    private LectureCourseHomeApi home;

    @Autowired
    private LectureUtilService lectures;

    @Autowired
    private LectureUnitRepository units;

    private Course course;

    private ZonedDateTime base;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 1, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
        base = ZonedDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    @Test
    void aFutureUnitSchedulesARefreshWithoutExposingItsLecture() {
        var lecture = lectures.createLecture(course);
        var unit = lectures.createTextUnit(lecture);
        unit.setReleaseDate(base.plusMinutes(1));
        units.saveAndFlush(unit);
        var user = userUtilService.getUserByLogin(PREFIX + "student1");
        var initial = home.load(course.getId(), user, base.toInstant(), base.minusDays(7).toInstant());
        assertThat(initial.items()).isEmpty();
        assertThat(initial.nextRefreshAt()).isEqualTo(base.plusMinutes(1).toInstant());
        var released = home.load(course.getId(), user, initial.nextRefreshAt(), base.minusDays(7).toInstant());
        assertThat(released.items()).extracting(item -> item.target().id()).containsExactly(lecture.getId());
    }

    @Test
    void scheduledLectureWithoutPublishedMaterialIsNotNewContent() {
        lectures.createLecture(course, base.plusHours(1), base.plusHours(2));
        var result = home.load(course.getId(), userUtilService.getUserByLogin(PREFIX + "student1"), Instant.now(), base.minusDays(7).toInstant());
        assertThat(result.items()).isEmpty();
    }

    @Test
    void newlyReleasedUnitInAnOldLectureIsIncludedAndFutureUnitsAreExcludedBeforeLimiting() {
        var lecture = lectures.createLecture(course, base.minusDays(30), base.minusDays(30).plusHours(1));
        var unit = lectures.createTextUnit(lecture);
        unit.setReleaseDate(base.minusMinutes(1));
        units.saveAndFlush(unit);
        for (int i = 0; i < 5; i++) {
            var futureLecture = lectures.createLecture(course);
            var future = lectures.createTextUnit(futureLecture);
            future.setReleaseDate(base.plusDays(1));
            units.saveAndFlush(future);
        }
        var result = home.load(course.getId(), userUtilService.getUserByLogin(PREFIX + "student1"), Instant.now(), base.minusHours(1).toInstant());
        assertThat(result.items()).extracting(i -> i.target().id()).containsExactly(lecture.getId());
    }

    @Test
    void newlyAddedMaterialWithAnEarlierReleaseDateIsStillNew() {
        var lecture = lectures.createLecture(course);
        var unit = lectures.createTextUnit(lecture);
        unit.setReleaseDate(base.minusDays(30));
        units.saveAndFlush(unit);
        var result = home.load(course.getId(), userUtilService.getUserByLogin(PREFIX + "student1"), Instant.now(), base.minusHours(1).toInstant());
        assertThat(result.items()).anyMatch(i -> i.target().id() == lecture.getId() && i.reason().equals("NEW_LECTURE"));
    }

    @Test
    void changingExistingMaterialPreservesItsOriginalCreationTimestamp() {
        var lecture = lectures.createLecture(course);
        var unit = lectures.createTextUnit(lecture);
        var created = unit.getCreatedDate();
        assertThat(created).isNotNull();
        unit.setName("Updated material");
        assertThat(units.saveAndFlush(unit).getCreatedDate().toInstant()).isEqualTo(created.toInstant());
        unit.setCreatedDate(base.minusDays(30));
        var original = units.saveAndFlush(unit);
        original.setName("Still the same published material");
        assertThat(units.saveAndFlush(original).getCreatedDate().toInstant()).isEqualTo(base.minusDays(30).toInstant());
        original.setCreatedDate(null);
        assertThat(units.saveAndFlush(original).getCreatedDate()).isNull();
    }

    @Test
    void undatedNewlyPublishedMaterialIsIncluded() {
        var lecture = lectures.createLecture(course);
        var unit = lectures.createTextUnit(lecture);
        unit.setReleaseDate(null);
        units.saveAndFlush(unit);
        var result = home.load(course.getId(), userUtilService.getUserByLogin(PREFIX + "student1"), Instant.now(), base.minusHours(1).toInstant());
        assertThat(result.items()).anyMatch(i -> i.target().id() == lecture.getId() && i.reason().equals("NEW_LECTURE"));
    }
}
