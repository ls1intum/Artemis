package de.tum.cit.aet.artemis.athena.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class AthenaModuleServiceTest {

    private AthenaModuleService service;

    private CourseAthenaConfigRepository courseAthenaConfigRepository;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
        courseAthenaConfigRepository = mock(CourseAthenaConfigRepository.class);

        service = new AthenaModuleService(restTemplate, objectMapper, exerciseRepository, courseAthenaConfigRepository);
        ReflectionTestUtils.setField(service, "athenaUrl", "http://athena.example.com");
        ReflectionTestUtils.setField(service, "defaultTextModule", "module_text_llm");
        ReflectionTestUtils.setField(service, "defaultProgrammingModule", "module_programming_llm");
        ReflectionTestUtils.setField(service, "defaultModelingModule", "module_modeling_llm");
    }

    private Course courseWithId(long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    private void mockConfig(long courseId, boolean formative, boolean grading) {
        CourseAthenaConfig config = new CourseAthenaConfig();
        config.setFormativeEnabled(formative);
        config.setGradingEnabled(grading);
        when(courseAthenaConfigRepository.findByCourseId(courseId)).thenReturn(Optional.of(config));
    }

    private void mockNoConfig(long courseId) {
        when(courseAthenaConfigRepository.findByCourseId(courseId)).thenReturn(Optional.empty());
    }

    @Test
    void applyAthenaCourseSettings_gradingEnabled_setsFeedbackSuggestionModule() {
        Course course = courseWithId(1L);
        mockConfig(1L, false, true);
        TextExercise exercise = new TextExercise();
        exercise.setCourse(course);
        service.applyAthenaCourseSettings(exercise, course);
        assertThat(exercise.getFeedbackSuggestionModule()).isEqualTo("module_text_llm");
        assertThat(exercise.getAllowFeedbackRequests()).isFalse();
    }

    @Test
    void applyAthenaCourseSettings_formativeOnlyEnabled_setsAllowRequestsButNotModule() {
        Course course = courseWithId(2L);
        mockConfig(2L, true, false);
        TextExercise exercise = new TextExercise();
        exercise.setCourse(course);
        service.applyAthenaCourseSettings(exercise, course);
        assertThat(exercise.getFeedbackSuggestionModule()).isNull();
        assertThat(exercise.getAllowFeedbackRequests()).isTrue();
    }

    @Test
    void applyAthenaCourseSettings_gradingAndFormativeEnabled_setsBothFields() {
        Course course = courseWithId(3L);
        mockConfig(3L, true, true);
        TextExercise exercise = new TextExercise();
        exercise.setCourse(course);
        service.applyAthenaCourseSettings(exercise, course);
        assertThat(exercise.getFeedbackSuggestionModule()).isEqualTo("module_text_llm");
        assertThat(exercise.getAllowFeedbackRequests()).isTrue();
    }

    @Test
    void applyAthenaCourseSettings_allFlagsDisabled_doesNotSetFields() {
        Course course = courseWithId(4L);
        mockNoConfig(4L);
        TextExercise exercise = new TextExercise();
        exercise.setCourse(course);
        service.applyAthenaCourseSettings(exercise, course);
        assertThat(exercise.getFeedbackSuggestionModule()).isNull();
        assertThat(exercise.getAllowFeedbackRequests()).isFalse();
    }

    @Test
    void getDefaultModule_blankProperty_throwsIllegalStateException() {
        ReflectionTestUtils.setField(service, "defaultTextModule", "");
        assertThatThrownBy(() -> service.getDefaultModule(ExerciseType.TEXT)).isInstanceOf(IllegalStateException.class).hasMessageContaining("default-text-module");
    }
}
