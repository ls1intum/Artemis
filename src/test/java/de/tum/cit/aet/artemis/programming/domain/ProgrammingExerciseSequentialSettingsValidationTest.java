package de.tum.cit.aet.artemis.programming.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;

class ProgrammingExerciseSequentialSettingsValidationTest {

    private static ProgrammingLanguageFeature feature(ProgrammingLanguage language, boolean sequentialTestRuns, boolean staticCodeAnalysis, List<ProjectType> projectTypes) {
        return new ProgrammingLanguageFeature(language, sequentialTestRuns, staticCodeAnalysis, false, false, false, projectTypes, true);
    }

    private static ProgrammingExercise exercise(ProgrammingLanguage language, ProjectType projectType) {
        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(language);
        exercise.setProjectType(projectType);
        exercise.setStaticCodeAnalysisEnabled(false);
        return exercise;
    }

    private static ProgrammingExerciseBuildConfig buildConfig(boolean sequentialTestRuns) {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setSequentialTestRuns(sequentialTestRuns);
        return buildConfig;
    }

    @Test
    void rejectsSequentialTestRunsForALanguageWithoutSequentialTemplates() {
        var exercise = exercise(ProgrammingLanguage.C, ProjectType.GCC);
        var feature = feature(ProgrammingLanguage.C, false, true, List.of(ProjectType.FACT, ProjectType.GCC));

        assertThatThrownBy(() -> exercise.validateStaticCodeAnalysisSettings(feature, buildConfig(true))).isInstanceOf(BadRequestAlertException.class)
                .extracting(exception -> ((BadRequestAlertException) exception).getErrorKey()).isEqualTo("sequentialTestRunsNotSupportedForLanguage");
    }

    @Test
    void rejectsSequentialTestRunsForBlackboxExercises() {
        var exercise = exercise(ProgrammingLanguage.JAVA, ProjectType.MAVEN_BLACKBOX);
        var feature = feature(ProgrammingLanguage.JAVA, true, true, List.of(ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_BLACKBOX));

        assertThatThrownBy(() -> exercise.validateStaticCodeAnalysisSettings(feature, buildConfig(true))).isInstanceOf(BadRequestAlertException.class)
                .extracting(exception -> ((BadRequestAlertException) exception).getErrorKey()).isEqualTo("sequentialTestRunsNotSupportedForBlackbox");
    }

    @Test
    void rejectsSequentialTestRunsWithoutProjectTypeForSwift() {
        var exercise = exercise(ProgrammingLanguage.SWIFT, null);
        var feature = feature(ProgrammingLanguage.SWIFT, true, true, List.of(ProjectType.PLAIN));

        assertThatThrownBy(() -> exercise.validateStaticCodeAnalysisSettings(feature, buildConfig(true))).isInstanceOf(BadRequestAlertException.class)
                .extracting(exception -> ((BadRequestAlertException) exception).getErrorKey()).isEqualTo("sequentialTestRunsRequireProjectType");
    }

    @Test
    void acceptsSequentialTestRunsWithoutProjectTypeForJava() {
        var exercise = exercise(ProgrammingLanguage.JAVA, null);
        var feature = feature(ProgrammingLanguage.JAVA, true, true, List.of(ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_BLACKBOX));

        assertThatCode(() -> exercise.validateStaticCodeAnalysisSettings(feature, buildConfig(true))).doesNotThrowAnyException();
    }

    @Test
    void acceptsSequentialTestRunsWhereTheLanguageSupportsThem() {
        var exercise = exercise(ProgrammingLanguage.PYTHON, null);
        var feature = feature(ProgrammingLanguage.PYTHON, true, true, List.of());

        assertThatCode(() -> exercise.validateStaticCodeAnalysisSettings(feature, buildConfig(true))).doesNotThrowAnyException();
    }

    @Test
    void acceptsBlackboxExercisesWithoutSequentialTestRuns() {
        var exercise = exercise(ProgrammingLanguage.JAVA, ProjectType.MAVEN_BLACKBOX);
        var feature = feature(ProgrammingLanguage.JAVA, true, true, List.of(ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_BLACKBOX));

        assertThat(buildConfig(false).hasSequentialTestRuns()).isFalse();
        assertThatCode(() -> exercise.validateStaticCodeAnalysisSettings(feature, buildConfig(false))).doesNotThrowAnyException();
    }
}
