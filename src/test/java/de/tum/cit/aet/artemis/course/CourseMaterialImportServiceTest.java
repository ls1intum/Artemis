package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.communication.service.FaqImportService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseMaterialImportOptionsDTO;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseMaterialImportService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;

/**
 * A failed import of one exercise has to reach the caller as an error. Counting it as skipped made the import dialog report a success for an import that
 * created nothing.
 */
class CourseMaterialImportServiceTest {

    private static final long SOURCE_COURSE_ID = 1L;

    private static final long TARGET_COURSE_ID = 2L;

    @Test
    void importCourseMaterial_failingQuizImport_isReportedAsError() throws Exception {
        CourseRepository courseRepository = mock(CourseRepository.class);
        ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
        QuizExerciseRepository quizExerciseRepository = mock(QuizExerciseRepository.class);
        QuizExerciseImportService quizExerciseImportService = mock(QuizExerciseImportService.class);

        QuizExercise sourceQuiz = new QuizExercise();
        sourceQuiz.setId(10L);
        sourceQuiz.setTitle("Sorting quiz");
        when(courseRepository.findByIdElseThrow(TARGET_COURSE_ID)).thenReturn(new Course());
        when(exerciseRepository.findByCourseIdWithCategories(SOURCE_COURSE_ID)).thenReturn(Set.of(sourceQuiz));
        when(quizExerciseRepository.findWithEagerQuestionsAndCompetenciesAndBatchesAndGradingCriteriaById(10L)).thenReturn(Optional.of(sourceQuiz));
        when(quizExerciseImportService.importQuizExercise(any(), any(), any())).thenThrow(new IOException("disk full"));

        var service = new CourseMaterialImportService(courseRepository, exerciseRepository, Optional.empty(), Optional.empty(), mock(ProgrammingExerciseImportService.class),
                mock(ProgrammingExerciseRepository.class), mock(ProgrammingExerciseTaskRepository.class), mock(SubmissionPolicyRepository.class),
                mock(GradingCriterionRepository.class), quizExerciseImportService, quizExerciseRepository, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), mock(FaqImportService.class));

        var result = service.importCourseMaterial(TARGET_COURSE_ID, new CourseMaterialImportOptionsDTO(SOURCE_COURSE_ID, true, false, false, false, false, false), new User());

        assertThat(result.exercisesImported()).isZero();
        assertThat(result.errors()).hasSize(1).first().asString().contains("Sorting quiz").contains("disk full");
    }
}
