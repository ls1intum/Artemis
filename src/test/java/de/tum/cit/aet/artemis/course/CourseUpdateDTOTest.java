package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseUpdateDTO;

class CourseUpdateDTOTest {

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void omittedPresentationFlagPreservesExistingValue(boolean enabled) throws Exception {
        Course course = new Course();
        course.setPresentationAssessmentsEnabled(enabled);
        CourseUpdateDTO dto = JsonObjectMapper.get().readValue("{}", CourseUpdateDTO.class);

        dto.applyTo(course);

        assertThat(dto.presentationAssessmentsEnabled()).isNull();
        assertThat(course.getPresentationAssessmentsEnabled()).isEqualTo(enabled);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void explicitPresentationFlagUpdatesExistingValue(boolean enabled) throws Exception {
        Course course = new Course();
        course.setPresentationAssessmentsEnabled(!enabled);
        CourseUpdateDTO dto = JsonObjectMapper.get().readValue("{\"presentationAssessmentsEnabled\":" + enabled + "}", CourseUpdateDTO.class);

        dto.applyTo(course);

        assertThat(course.getPresentationAssessmentsEnabled()).isEqualTo(enabled);
    }
}
