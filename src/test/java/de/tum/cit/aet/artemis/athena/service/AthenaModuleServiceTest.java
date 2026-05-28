package de.tum.cit.aet.artemis.athena.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class AthenaModuleServiceTest {

    private AthenaModuleService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);

        service = new AthenaModuleService(restTemplate, objectMapper, exerciseRepository);
        ReflectionTestUtils.setField(service, "athenaUrl", "http://athena.example.com");
        ReflectionTestUtils.setField(service, "defaultTextModule", "module_text_llm");
        ReflectionTestUtils.setField(service, "defaultProgrammingModule", "module_programming_llm");
        ReflectionTestUtils.setField(service, "defaultModelingModule", "module_modeling_llm");
    }

    @Test
    void applyAthenaCourseSettings_gradingEnabled_setsFeedbackSuggestionModule() {
        Course course = new Course();
        course.setAthenaGradingEnabled(true);
        TextExercise exercise = new TextExercise();
        exercise.setCourse(course);
        service.applyAthenaCourseSettings(exercise, course);
        assertThat(exercise.getFeedbackSuggestionModule()).isEqualTo("module_text_llm");
        assertThat(exercise.getAllowFeedbackRequests()).isFalse();
    }

    @Test
    void applyAthenaCourseSettings_formativeOnlyEnabled_setsFeedbackSuggestionModuleAndAllowRequests() {
        Course course = new Course();
        course.setAthenaFormativeEnabled(true);
        TextExercise exercise = new TextExercise();
        exercise.setCourse(course);
        service.applyAthenaCourseSettings(exercise, course);
        assertThat(exercise.getFeedbackSuggestionModule()).isEqualTo("module_text_llm");
        assertThat(exercise.getAllowFeedbackRequests()).isTrue();
    }

    @Test
    void applyAthenaCourseSettings_gradingAndFormativeEnabled_setsBothFields() {
        Course course = new Course();
        course.setAthenaGradingEnabled(true);
        course.setAthenaFormativeEnabled(true);
        TextExercise exercise = new TextExercise();
        exercise.setCourse(course);
        service.applyAthenaCourseSettings(exercise, course);
        assertThat(exercise.getFeedbackSuggestionModule()).isEqualTo("module_text_llm");
        assertThat(exercise.getAllowFeedbackRequests()).isTrue();
    }

    @Test
    void applyAthenaCourseSettings_allFlagsDisabled_doesNotSetFields() {
        Course course = new Course();
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
