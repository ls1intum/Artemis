package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDTO;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.lti.test_repository.OnlineCourseConfigurationTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseOnlineConfigurationUpdateIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseonlineupdate";

    @Autowired
    private LtiApi ltiApi;

    @Autowired
    private OnlineCourseConfigurationTestRepository configurationRepository;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        course.setEnrollmentEnabled(false);
        course = courseRepository.save(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseTogglesConfigurationAndPreservesExistingSettings() throws Exception {
        CourseManagementDTO enabled = updateCourse(true);
        assertThat(enabled.onlineCourse()).isTrue();
        assertThat(enabled.onlineCourseConfiguration()).isNotNull();
        var configuration = configurationRepository.findByCourseId(course.getId()).orElseThrow();
        long configurationId = configuration.getId();
        configuration.setUserPrefix("customprefix");
        configuration.setRequireExistingUser(true);
        configurationRepository.save(configuration);

        updateCourse(true);
        var preserved = configurationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(preserved.getId()).isEqualTo(configurationId);
        assertThat(preserved.getUserPrefix()).isEqualTo("customprefix");
        assertThat(preserved.isRequireExistingUser()).isTrue();

        CourseManagementDTO disabled = updateCourse(false);
        assertThat(disabled.onlineCourse()).isFalse();
        assertThat(disabled.onlineCourseConfiguration()).isNull();
        assertThat(configurationRepository.findByCourseId(course.getId())).isEmpty();
        assertThat(courseRepository.findByIdElseThrow(course.getId()).isOnlineCourse()).isFalse();

        updateCourse(true);
        assertThat(courseRepository.findByIdElseThrow(course.getId()).isOnlineCourse()).isTrue();
        assertThat(configurationRepository.findByCourseId(course.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedConfigurationUpdateRollsBackBothWrites(boolean initiallyOnline) {
        persistOnlineState(initiallyOnline);
        Long originalConfigurationId = configurationRepository.findByCourseId(course.getId()).map(configuration -> configuration.getId()).orElse(null);
        Course update = courseRepository.findByIdElseThrow(course.getId());
        update.setOnlineCourse(!initiallyOnline);

        assertThatThrownBy(() -> courseRepository.saveWithOnlineCourseConfigurationUpdate(update, saved -> {
            ltiApi.updateOnlineCourseConfiguration(saved);
            courseRepository.flush();
            throw new DataIntegrityViolationException("Simulated configuration failure");
        })).isInstanceOf(DataIntegrityViolationException.class).hasMessage("Simulated configuration failure");

        assertThat(courseRepository.findByIdElseThrow(course.getId()).isOnlineCourse()).isEqualTo(initiallyOnline);
        assertThat(configurationRepository.findByCourseId(course.getId()).map(configuration -> configuration.getId()).orElse(null)).isEqualTo(originalConfigurationId);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void concurrentTogglesAreSerialized(boolean initiallyOnline) throws Exception {
        persistOnlineState(initiallyOnline);
        Course firstUpdate = courseRepository.findByIdElseThrow(course.getId());
        Course secondUpdate = courseRepository.findByIdElseThrow(course.getId());
        firstUpdate.setOnlineCourse(!initiallyOnline);
        secondUpdate.setOnlineCourse(initiallyOnline);
        var firstHasLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var secondHasLock = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> courseRepository.saveWithOnlineCourseConfigurationUpdate(firstUpdate, saved -> {
                firstHasLock.countDown();
                await(releaseFirst);
                ltiApi.updateOnlineCourseConfiguration(saved);
            }));
            try {
                assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return courseRepository.saveWithOnlineCourseConfigurationUpdate(secondUpdate, saved -> {
                        secondHasLock.countDown();
                        ltiApi.updateOnlineCourseConfiguration(saved);
                    });
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThat(secondHasLock.getCount()).isEqualTo(1);
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            }
            finally {
                releaseFirst.countDown();
            }
        }

        assertThat(courseRepository.findByIdElseThrow(course.getId()).isOnlineCourse()).isEqualTo(initiallyOnline);
        assertThat(configurationRepository.findByCourseId(course.getId()).isPresent()).isEqualTo(initiallyOnline);
    }

    private void persistOnlineState(boolean online) {
        course.setOnlineCourse(online);
        course = courseRepository.saveWithOnlineCourseConfigurationUpdate(course, ltiApi::updateOnlineCourseConfiguration);
    }

    private CourseManagementDTO updateCourse(boolean online) throws Exception {
        course.setOnlineCourse(online);
        var mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsBytes(course));
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + course.getId()).file(coursePart);
        var result = request.performMvcRequest(builder).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), CourseManagementDTO.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
