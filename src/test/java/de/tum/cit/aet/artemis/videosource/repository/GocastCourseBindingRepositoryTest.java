package de.tum.cit.aet.artemis.videosource.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;

/**
 * Repository integration tests for {@link GocastCourseBindingRepository}.
 * Requires a running database (PostgreSQL via Testcontainers in CI, H2 locally).
 */
class GocastCourseBindingRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private GocastCourseBindingRepository bindingRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    // ── findByCourseId ────────────────────────────────────────────────────────

    @Test
    void persistAndFindByCourseId_returnsBinding() {
        Course course = courseUtilService.createCourse();

        GocastCourseBinding binding = new GocastCourseBinding();
        binding.setCourseId(course.getId());
        binding.setGocastCourseId(42L);
        binding.setGocastCourseSlug("eidi");
        binding.setStatus(GocastBindingStatus.PENDING);

        bindingRepository.save(binding);

        var found = bindingRepository.findByCourseId(course.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getGocastCourseId()).isEqualTo(42L);
        assertThat(found.get().getGocastCourseSlug()).isEqualTo("eidi");
        assertThat(found.get().getStatus()).isEqualTo(GocastBindingStatus.PENDING);
        assertThat(found.get().getCourseId()).isEqualTo(course.getId());
    }

    @Test
    void findByCourseId_returnsEmpty_whenNoBindingExists() {
        var found = bindingRepository.findByCourseId(Long.MAX_VALUE);
        assertThat(found).isEmpty();
    }

    // ── findByCourseIdElseThrow ───────────────────────────────────────────────

    @Test
    void findByCourseIdElseThrow_returnsBinding_whenExists() {
        Course course = courseUtilService.createCourse();

        GocastCourseBinding binding = new GocastCourseBinding();
        binding.setCourseId(course.getId());
        binding.setGocastCourseId(99L);
        binding.setGocastCourseSlug("tgi");
        binding.setStatus(GocastBindingStatus.ACTIVE);

        bindingRepository.save(binding);

        GocastCourseBinding found = bindingRepository.findByCourseIdElseThrow(course.getId());
        assertThat(found.getGocastCourseId()).isEqualTo(99L);
        assertThat(found.getStatus()).isEqualTo(GocastBindingStatus.ACTIVE);
    }

    @Test
    void findByCourseIdElseThrow_throws_whenNoBindingExists() {
        assertThatThrownBy(() -> bindingRepository.findByCourseIdElseThrow(Long.MAX_VALUE - 1)).isInstanceOf(de.tum.cit.aet.artemis.core.exception.EntityNotFoundException.class);
    }

    // ── unique constraint on course_id ────────────────────────────────────────

    @Test
    void uniqueConstraintOnCourseId_preventsSecondBinding() {
        Course course = courseUtilService.createCourse();

        GocastCourseBinding first = new GocastCourseBinding();
        first.setCourseId(course.getId());
        first.setGocastCourseId(1L);
        first.setGocastCourseSlug("first");
        first.setStatus(GocastBindingStatus.PENDING);
        bindingRepository.save(first);

        GocastCourseBinding second = new GocastCourseBinding();
        second.setCourseId(course.getId());
        second.setGocastCourseId(2L);
        second.setGocastCourseSlug("second");
        second.setStatus(GocastBindingStatus.PENDING);

        assertThatThrownBy(() -> {
            bindingRepository.save(second);
            bindingRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
