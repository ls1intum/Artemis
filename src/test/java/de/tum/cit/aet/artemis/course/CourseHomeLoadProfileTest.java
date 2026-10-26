package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

@Isolated("Measures SessionFactory-wide Hibernate statistics without concurrent test queries.")
@WithMockUser(username = CourseHomeLoadProfileTest.PREFIX + "student1", roles = "USER")
class CourseHomeLoadProfileTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homeprofile";

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private TextExerciseUtilService exercises;

    private Course course;

    private boolean statisticsEnabledBefore;

    @BeforeEach
    void setup() {
        statisticsEnabledBefore = emf.unwrap(SessionFactory.class).getStatistics().isStatisticsEnabled();
        userUtilService.addUsers(PREFIX, 1, 1, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
    }

    @AfterEach
    void restoreStatistics() {
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(statisticsEnabledBefore);
    }

    @Test
    void aLargeCourseKeepsTheSameBoundedPayloadAndConstantQueryCount() throws Exception {
        for (int i = 0; i < 10; i++)
            addExercise();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        get();
        stats.clear();
        queryInterceptor.startQueryCount();
        get();
        long smallQueries = queryInterceptor.getQueryCount();
        for (int i = 0; i < 990; i++)
            addExercise();
        get();
        stats.clear();
        // Scheduled jobs can query on other threads even when the test class is isolated.
        queryInterceptor.startQueryCount();
        var large = get();
        long largeQueries = queryInterceptor.getQueryCount();
        assertThat(large.progress().releasedExercises()).isEqualTo(1000);
        assertThat(large.updates()).hasSizeLessThanOrEqualTo(6);
        assertThat(large.teachingTeam().items()).hasSizeLessThanOrEqualTo(3);
        assertThat(smallQueries).isPositive();
        assertThat(largeQueries).isPositive().isLessThanOrEqualTo(smallQueries + 3).isLessThanOrEqualTo(40);
        for (Class<?> entity : java.util.List.of(Exercise.class, Submission.class, LectureUnit.class, IrisMessage.class, Course.class))
            assertThat(stats.getEntityStatistics(entity.getName()).getLoadCount()).as(entity.getSimpleName()).isZero();
        String wire = request.getObjectMapper().writeValueAsString(large);
        assertThat(wire.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeLessThan(20000);
        assertThat(wire).doesNotContain("problemStatement", "participations", "sourceCode", "secret solution");
    }

    private void addExercise() {
        var base = ZonedDateTime.now();
        var e = exercises.createIndividualTextExercise(course, base.minusDays(1), base.plusDays(5), base.plusDays(6));
        e.setProblemStatement("secret solution " + "x".repeat(1000));
        exerciseRepository.saveAndFlush(e);
    }

    private CourseHomeDTO get() throws Exception {
        return request.get("/api/course/courses/" + course.getId() + "/home", HttpStatus.OK, CourseHomeDTO.class);
    }
}
