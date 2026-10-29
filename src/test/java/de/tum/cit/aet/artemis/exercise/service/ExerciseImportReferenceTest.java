package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.api.ExerciseGroupApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseImportSettingsDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseImportReferenceTest {

    private final CourseTestRepository courses = mock(CourseTestRepository.class);

    private final ExerciseGroupApi groups = mock(ExerciseGroupApi.class);

    private final ExerciseImportSettingsDTO dto = mock(ExerciseImportSettingsDTO.class);

    @Test
    void createsOwnedContentThenResolvesCourseBeforeGroup() {
        var exercise = new TextExercise();
        var course = new Course();
        var group = new ExerciseGroup();
        when(dto.gradingCriteria()).thenReturn(List.of(new GradingCriterionDTO(5L, "Rubric", List.of())));
        when(dto.competencyLinks()).thenReturn(Set.of(new CompetencyLinkDTO(new CompetencyDTO(17L, "Competency"), 0.8)));
        when(dto.courseId()).thenReturn(3L);
        when(dto.exerciseGroupId()).thenReturn(9L);
        when(courses.findByIdElseThrow(3L)).thenAnswer(invocation -> {
            assertThat(exercise.getGradingCriteria()).singleElement().satisfies(criterion -> {
                assertThat(criterion.getExercise()).isSameAs(exercise);
                assertThat(criterion.getTitle()).isEqualTo("Rubric");
            });
            assertThat(exercise.getCompetencyLinks()).singleElement().satisfies(link -> {
                assertThat(link.getExercise()).isSameAs(exercise);
                assertThat(link.getCompetency().getId()).isEqualTo(17L);
                assertThat(link.getWeight()).isEqualTo(0.8);
            });
            return course;
        });
        when(groups.findByIdElseThrow(9L)).thenAnswer(invocation -> {
            assertThat(exercise.getCourseViaExerciseGroupOrCourseMember()).isSameAs(course);
            return group;
        });

        ExerciseImportService.applyImportReferences(dto, exercise, courses, Optional.of(groups), "textExercise");

        assertThat(exercise.getExerciseGroup()).isSameAs(group);
        var order = inOrder(courses, groups);
        order.verify(courses).findByIdElseThrow(3L);
        order.verify(groups).findByIdElseThrow(9L);
    }

    @Test
    void malformedCompetencyFailsBeforeDestinationLookups() {
        when(dto.competencyLinks()).thenReturn(Set.of(new CompetencyLinkDTO(null, 1)));
        assertThatThrownBy(() -> ExerciseImportService.applyImportReferences(dto, new TextExercise(), courses, Optional.of(groups), "textExercise"))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("Each competency link must include a competency.");
        verifyNoInteractions(courses, groups);
    }

    @Test
    void courseFailurePrecedesMissingExamModuleAndMissingReferencesPreserveDefaults() {
        when(dto.courseId()).thenReturn(null);
        when(dto.exerciseGroupId()).thenReturn(null);
        var exercise = new TextExercise();
        var criteria = exercise.getGradingCriteria();
        var links = exercise.getCompetencyLinks();
        ExerciseImportService.applyImportReferences(dto, exercise, courses, Optional.empty(), "textExercise");
        assertThat(exercise.getGradingCriteria()).isSameAs(criteria);
        assertThat(exercise.getCompetencyLinks()).isSameAs(links);
        verifyNoInteractions(courses, groups);
        when(dto.courseId()).thenReturn(3L);
        when(dto.exerciseGroupId()).thenReturn(9L);
        when(courses.findByIdElseThrow(3L)).thenThrow(new EntityNotFoundException("Course", 3L));
        assertThatThrownBy(() -> ExerciseImportService.applyImportReferences(dto, exercise, courses, Optional.empty(), "textExercise")).isInstanceOf(EntityNotFoundException.class);
        verify(groups, never()).findByIdElseThrow(9L);
        when(dto.courseId()).thenReturn(null);
        assertThatThrownBy(() -> ExerciseImportService.applyImportReferences(dto, exercise, courses, Optional.empty(), "textExercise"))
                .isInstanceOf(ExamApiNotPresentException.class);
    }
}
