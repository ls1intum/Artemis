package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;

/**
 * Integration coverage for {@link ContentExtractionService} against a real database. The
 * pure-logic assertions live in {@link ContentExtractionServiceTest}; this test exists to de-risk
 * the one thing a unit test cannot: extracting a persisted quiz.
 * <p>
 * A quiz's {@code quizQuestions} collection is {@code LAZY}, and the auto-orchestration pipeline
 * resolves batches through the generic {@link ExerciseRepository} on the async scheduler thread with
 * no open session. This test loads a persisted quiz that way and asserts extraction re-fetches the
 * questions eagerly (via {@code findByIdWithQuestionsElseThrow}) instead of throwing a
 * {@code LazyInitializationException}, and that the assembled learning text carries the real
 * questions and correct answers/solutions.
 */
class ContentExtractionServiceIntegrationTest extends AbstractAtlasIntegrationTest {

    @Autowired
    private ContentExtractionService contentExtractionService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Test
    void extractContent_persistedQuiz_eagerlyLoadsQuestionsAndAssemblesContent() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        long quizId = course.getExercises().iterator().next().getId();

        // Load through the generic exercise repository (as the orchestrator's batch resolution does):
        // the quizQuestions collection is LAZY on this instance. Extraction must re-fetch it eagerly.
        Exercise reloaded = exerciseRepository.findByIdElseThrow(quizId);
        ExtractedContentDTO extracted = assertDoesNotThrow(() -> contentExtractionService.extractContent(reloaded));

        assertThat(extracted.metadata()).containsEntry("exerciseType", "quiz").containsEntry("questionCount", "3");
        // The default quiz carries one MC, one drag-and-drop and one short-answer question. Their prompts,
        // the MC correctness markers, the text drag items and the short-answer solutions must all survive
        // the eager re-fetch from the unfiltered entity.
        assertThat(extracted.extractedLearningText()).contains("Q1").contains("[correct]").contains("D1").contains("Spot 0: is").contains("Spot 2: long");
    }
}
