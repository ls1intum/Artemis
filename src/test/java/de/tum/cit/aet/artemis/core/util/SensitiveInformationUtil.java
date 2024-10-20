package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

public class SensitiveInformationUtil {

    // look at FileUploadExercise.filterSensitiveInformation
    public static void assertSensitiveInformationWasFilteredFileUploadExercise(FileUploadExercise exercise) {
        assertThat(exercise.getExampleSolution()).isNullOrEmpty();
        assertSensitiveInformationWasFilteredExercise(exercise);
    }

    // look at ModelingExercise.filterSensitiveInformation
    public static void assertSensitiveInformationWasFilteredModelingExercise(ModelingExercise exercise) {
        assertThat(exercise.getExampleSolutionModel()).isNullOrEmpty();
        assertThat(exercise.getExampleSolutionExplanation()).isNullOrEmpty();
        assertSensitiveInformationWasFilteredExercise(exercise);
    }

    // look at TextExercise.filterSensitiveInformation
    public static void assertSensitiveInformationWasFilteredTextExercise(TextExercise exercise) {
        assertThat(exercise.getExampleSolution()).isNullOrEmpty();
        assertSensitiveInformationWasFilteredExercise(exercise);
    }

    // look at ProgrammingExercise.filterSensitiveInformation
    public static void assertSensitiveInformationWasFilteredProgrammingExercise(ProgrammingExercise exercise) {
        assertThat(exercise.getTemplateBuildPlanId()).isNullOrEmpty();
        assertThat(exercise.getSolutionBuildPlanId()).isNullOrEmpty();
        assertThat(exercise.getTemplateRepositoryUri()).isNullOrEmpty();
        assertThat(exercise.getSolutionRepositoryUri()).isNullOrEmpty();
        assertThat(exercise.getTestRepositoryUri()).isNullOrEmpty();
        assertSensitiveInformationWasFilteredExercise(exercise);
    }

    // look at Exercise.filterSensitiveInformation
    public static void assertSensitiveInformationWasFilteredExercise(Exercise exercise) {
        assertThat(exercise.getGradingInstructions()).isNullOrEmpty();
        assertThat(exercise.getGradingCriteria()).isNullOrEmpty();
    }
}
