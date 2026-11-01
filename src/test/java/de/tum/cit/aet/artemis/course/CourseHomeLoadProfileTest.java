package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEventListener;
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

@Isolated("Installs a shared Hibernate load observer while profiling synchronous requests.")
@WithMockUser(username = CourseHomeLoadProfileTest.PREFIX + "student1", roles = "USER")
class CourseHomeLoadProfileTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homeprofile";

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private TextExerciseUtilService exercises;

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 1, 1, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
    }

    @Test
    void aLargeCourseKeepsTheSameBoundedPayloadAndConstantQueryCount() throws Exception {
        for (int i = 0; i < 10; i++)
            addExercise();
        get();
        queryInterceptor.startQueryCount();
        get();
        long smallQueries = queryInterceptor.getQueryCount();
        for (int i = 0; i < 990; i++)
            addExercise();
        get();
        try (var entityLoads = new RequestEntityLoads(emf)) {
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
                assertThat(entityLoads.count(entity)).as(entity.getSimpleName()).isZero();
            String wire = request.getObjectMapper().writeValueAsString(large);
            assertThat(wire.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeLessThan(20000);
            assertThat(wire).doesNotContain("problemStatement", "participations", "sourceCode", "secret solution");
        }
    }

    @Test
    void backgroundCourseLoadsDoNotAffectTheHomeRequestProfile() throws Exception {
        get();
        try (var entityLoads = new RequestEntityLoads(emf); var worker = Executors.newSingleThreadExecutor()) {
            var backgroundCourse = worker.submit(() -> courseRepository.findByIdElseThrow(course.getId())).get();
            assertThat(backgroundCourse.getId()).isEqualTo(course.getId());
            get();
            assertThat(entityLoads.count(Course.class)).as("Home request Course loads").isZero();
        }
    }

    @Test
    void requestThreadCourseLoadsAreCountedOnlyUntilTheMeasurementCloses() {
        var entityLoads = new RequestEntityLoads(emf);
        try (entityLoads) {
            courseRepository.findByIdElseThrow(course.getId());
            assertThat(entityLoads.count(Course.class)).isEqualTo(1);
        }
        courseRepository.findByIdElseThrow(course.getId());
        assertThat(entityLoads.count(Course.class)).as("closed measurement must not keep counting").isEqualTo(1);
    }

    @Test
    void requestThreadExerciseLoadsIncludeConcreteSubtypes() {
        var exercise = addExercise();
        try (var entityLoads = new RequestEntityLoads(emf)) {
            exerciseRepository.findByIdElseThrow(exercise.getId());
            assertThat(entityLoads.count(Exercise.class)).isEqualTo(1);
        }
    }

    private Exercise addExercise() {
        var base = ZonedDateTime.now();
        var e = exercises.createIndividualTextExercise(course, base.minusDays(1), base.plusDays(5), base.plusDays(6));
        e.setProblemStatement("secret solution " + "x".repeat(1000));
        return exerciseRepository.saveAndFlush(e);
    }

    /** Counts real hydration only within the request thread's measurement scope. */
    private static final class RequestEntityLoads implements AutoCloseable {

        private static final ThreadLocal<RequestEntityLoads> ACTIVE = new ThreadLocal<>();

        private static final PostLoadEventListener OBSERVER = event -> {
            var measurement = ACTIVE.get();
            if (measurement != null) {
                measurement.loadedTypes.merge(event.getEntity().getClass(), 1L, Long::sum);
            }
        };

        private final Map<Class<?>, Long> loadedTypes = new HashMap<>();

        private RequestEntityLoads(EntityManagerFactory emf) {
            var listeners = emf.unwrap(SessionFactoryImplementor.class).getEventEngine().getListenerRegistry().getEventListenerGroup(EventType.POST_LOAD);
            synchronized (OBSERVER) {
                List<PostLoadEventListener> registered = new ArrayList<>();
                listeners.fireEventOnEachListener(registered, (listener, existing) -> existing.add(listener));
                if (!registered.contains(OBSERVER)) {
                    listeners.appendListener(OBSERVER);
                }
            }
            ACTIVE.set(this);
        }

        private long count(Class<?> entityType) {
            return loadedTypes.entrySet().stream().filter(entry -> entityType.isAssignableFrom(entry.getKey())).mapToLong(Map.Entry::getValue).sum();
        }

        @Override
        public void close() {
            // Keep the inert shared observer installed: replacing listener groups could interrupt background callbacks.
            ACTIVE.remove();
        }
    }

    private CourseHomeDTO get() throws Exception {
        return request.get("/api/course/courses/" + course.getId() + "/home", HttpStatus.OK, CourseHomeDTO.class);
    }
}
