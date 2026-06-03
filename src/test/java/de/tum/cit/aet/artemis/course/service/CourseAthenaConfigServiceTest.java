package de.tum.cit.aet.artemis.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;

class CourseAthenaConfigServiceTest {

    private CourseAthenaConfigRepository repository;

    private CourseAthenaConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(CourseAthenaConfigRepository.class);
        service = new CourseAthenaConfigService(repository);
    }

    private Course courseWithId(long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    @Test
    void updateConfig_bothFalse_noRowExists_doesNothing() {
        Course course = courseWithId(1L);
        when(repository.findByCourseId(1L)).thenReturn(Optional.empty());

        service.updateConfig(course, false, false);

        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void updateConfig_bothFalse_rowExists_deletesRow() {
        Course course = courseWithId(1L);
        CourseAthenaConfig existing = new CourseAthenaConfig();
        existing.setGradingEnabled(true);
        when(repository.findByCourseId(1L)).thenReturn(Optional.of(existing));

        service.updateConfig(course, false, false);

        verify(repository).delete(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void updateConfig_gradingTrue_noRowExists_createsRow() {
        Course course = courseWithId(2L);
        when(repository.findByCourseId(2L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateConfig(course, false, true);

        verify(repository).save(any(CourseAthenaConfig.class));
    }

    @Test
    void updateConfig_formativeTrue_rowExists_updatesExisting() {
        Course course = courseWithId(3L);
        CourseAthenaConfig existing = new CourseAthenaConfig();
        existing.setGradingEnabled(true);
        when(repository.findByCourseId(3L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateConfig(course, true, false);

        assertThat(existing.isFormativeEnabled()).isTrue();
        assertThat(existing.isGradingEnabled()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void stampAthenaConfig_rowExists_stampsTransientFields() {
        Course course = courseWithId(4L);
        CourseAthenaConfig config = new CourseAthenaConfig();
        config.setFormativeEnabled(true);
        config.setGradingEnabled(true);
        when(repository.findByCourseId(4L)).thenReturn(Optional.of(config));

        service.stampAthenaConfig(course);

        assertThat(course.isAthenaFormativeEnabled()).isTrue();
        assertThat(course.isAthenaGradingEnabled()).isTrue();
    }

    @Test
    void stampAthenaConfig_noRow_stampsDefaultFalse() {
        Course course = courseWithId(5L);
        when(repository.findByCourseId(5L)).thenReturn(Optional.empty());

        service.stampAthenaConfig(course);

        assertThat(course.isAthenaFormativeEnabled()).isFalse();
        assertThat(course.isAthenaGradingEnabled()).isFalse();
    }

    @Test
    void isGradingEnabled_noRow_returnsFalse() {
        when(repository.findByCourseId(6L)).thenReturn(Optional.empty());
        assertThat(service.isGradingEnabled(6L)).isFalse();
    }

    @Test
    void isGradingEnabled_rowWithGradingTrue_returnsTrue() {
        CourseAthenaConfig config = new CourseAthenaConfig();
        config.setGradingEnabled(true);
        when(repository.findByCourseId(7L)).thenReturn(Optional.of(config));
        assertThat(service.isGradingEnabled(7L)).isTrue();
    }

    @Test
    void isFormativeEnabled_noRow_returnsFalse() {
        when(repository.findByCourseId(8L)).thenReturn(Optional.empty());
        assertThat(service.isFormativeEnabled(8L)).isFalse();
    }

    @Test
    void isFormativeEnabled_rowWithFormativeTrue_returnsTrue() {
        CourseAthenaConfig config = new CourseAthenaConfig();
        config.setFormativeEnabled(true);
        when(repository.findByCourseId(9L)).thenReturn(Optional.of(config));
        assertThat(service.isFormativeEnabled(9L)).isTrue();
    }
}
