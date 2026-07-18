package de.tum.cit.aet.artemis.exercise.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Shared assertions verifying that importing an exercise preserves its content. This exists so every import path
 * (standalone REST import, exam import, course-material import) can assert the same field-level guarantees. The gap
 * these assertions close is exactly the regression fixed in PR #13268 and hardened afterwards: content fields
 * (problem statement, difficulty, assessment type, grading criteria, and type-specific fields) were silently dropped
 * during bulk import because no test asserted them.
 */
public final class ImportedExerciseAssertions {

    private ImportedExerciseAssertions() {
    }

    /**
     * Asserts that {@code imported} preserved all content fields of {@code source}. Fields that are intentionally reset
     * on import (id, dates) or transformed (file upload assessment type forced to MANUAL, quiz max points recomputed
     * from the question points) are handled here so callers do not need to special-case them.
     *
     * @param source   the original exercise that was imported
     * @param imported the freshly imported exercise
     */
    public static void assertContentPreserved(Exercise source, Exercise imported) {
        assertThat(imported.getId()).as("imported exercise is a new entity").isNotNull().isNotEqualTo(source.getId());
        assertThat(imported.getProblemStatement()).as("problem statement preserved").isEqualTo(source.getProblemStatement());
        assertThat(imported.getDifficulty()).as("difficulty preserved").isEqualTo(source.getDifficulty());
        assertThat(imported.getGradingInstructions()).as("grading instructions preserved").isEqualTo(source.getGradingInstructions());
        assertThat(imported.getBonusPoints()).as("bonus points preserved").isEqualTo(source.getBonusPoints());
        assertThat(imported.getIncludedInOverallScore()).as("includedInOverallScore preserved").isEqualTo(source.getIncludedInOverallScore());

        // Grading criteria are deep-copied: assert the count and titles match, but the entities must be new (different ids).
        assertThat(imported.getGradingCriteria()).as("grading criteria count preserved").hasSameSizeAs(source.getGradingCriteria());
        assertThat(imported.getGradingCriteria().stream().map(criterion -> criterion.getTitle()).toList()).as("grading criteria titles preserved")
                .containsExactlyInAnyOrderElementsOf(source.getGradingCriteria().stream().map(criterion -> criterion.getTitle()).toList());

        // File upload exercises are always manually assessed; every other type preserves the source assessment type.
        if (imported instanceof FileUploadExercise) {
            assertThat(imported.getAssessmentType()).as("file upload assessment type is MANUAL").isEqualTo(AssessmentType.MANUAL);
        }
        else {
            assertThat(imported.getAssessmentType()).as("assessment type preserved").isEqualTo(source.getAssessmentType());
        }

        // Quiz max points are recomputed from the question points on save, so only assert it for the other types.
        if (!(imported instanceof QuizExercise)) {
            assertThat(imported.getMaxPoints()).as("max points preserved").isEqualTo(source.getMaxPoints());
        }

        assertTypeSpecificContentPreserved(source, imported);
    }

    private static void assertTypeSpecificContentPreserved(Exercise source, Exercise imported) {
        switch (imported) {
            case TextExercise importedText ->
                assertThat(importedText.getExampleSolution()).as("text example solution preserved").isEqualTo(((TextExercise) source).getExampleSolution());
            case ModelingExercise importedModeling -> {
                ModelingExercise sourceModeling = (ModelingExercise) source;
                assertThat(importedModeling.getDiagramType()).as("diagram type preserved").isEqualTo(sourceModeling.getDiagramType());
                assertThat(importedModeling.getExampleSolutionModel()).as("example solution model preserved").isEqualTo(sourceModeling.getExampleSolutionModel());
                assertThat(importedModeling.getExampleSolutionExplanation()).as("example solution explanation preserved").isEqualTo(sourceModeling.getExampleSolutionExplanation());
            }
            case FileUploadExercise importedFileUpload -> {
                FileUploadExercise sourceFileUpload = (FileUploadExercise) source;
                assertThat(importedFileUpload.getFilePattern()).as("file pattern preserved").isEqualTo(sourceFileUpload.getFilePattern());
                assertThat(importedFileUpload.getExampleSolution()).as("file upload example solution preserved").isEqualTo(sourceFileUpload.getExampleSolution());
            }
            case QuizExercise importedQuiz -> {
                QuizExercise sourceQuiz = (QuizExercise) source;
                assertThat(importedQuiz.getQuizMode()).as("quiz mode preserved").isEqualTo(sourceQuiz.getQuizMode());
                assertThat(importedQuiz.getDuration()).as("quiz duration preserved").isEqualTo(sourceQuiz.getDuration());
                assertThat(importedQuiz.isRandomizeQuestionOrder()).as("quiz randomize question order preserved").isEqualTo(sourceQuiz.isRandomizeQuestionOrder());
                assertThat(importedQuiz.getQuizQuestions()).as("quiz questions preserved").hasSameSizeAs(sourceQuiz.getQuizQuestions());
            }
            default -> {
                // Programming exercises use a dedicated import path and are asserted separately.
            }
        }
    }
}
