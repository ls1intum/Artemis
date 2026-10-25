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
        base = ZonedDateTime.now();
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
    void undatedNewlyPublishedMaterialIsIncluded() {
        var lecture = lectures.createLecture(course);
        var unit = lectures.createTextUnit(lecture);
        unit.setReleaseDate(null);
        units.saveAndFlush(unit);
        var result = home.load(course.getId(), userUtilService.getUserByLogin(PREFIX + "student1"), Instant.now(), base.minusHours(1).toInstant());
        assertThat(result.items()).anyMatch(i -> i.target().id() == lecture.getId() && i.reason().equals("NEW_LECTURE"));
    }
}
