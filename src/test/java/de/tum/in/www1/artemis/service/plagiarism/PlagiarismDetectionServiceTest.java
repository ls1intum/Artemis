package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismDetectionConfig;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

class PlagiarismDetectionServiceTest {

    private final PlagiarismDetectionConfig config = new PlagiarismDetectionConfig(0.5f, 1, 2);

    private final TextPlagiarismDetectionService textPlagiarismDetectionService = mock();

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService = mock();

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService = mock();

    private final ModelingPlagiarismDetectionService modelingPlagiarismDetectionService = mock();

    private final PlagiarismResultRepository plagiarismResultRepository = mock();

    private final PlagiarismDetectionService service = new PlagiarismDetectionService(textPlagiarismDetectionService, Optional.of(programmingLanguageFeatureService),
            programmingPlagiarismDetectionService, modelingPlagiarismDetectionService, plagiarismResultRepository);

    @Test
    void shouldExecuteChecksForTextExercise() throws ExitException {
        // given
        var textExercise = new TextExercise();
        var textPlagiarismResult = new TextPlagiarismResult();
        textPlagiarismResult.setComparisons(emptySet());
        when(textPlagiarismDetectionService.checkPlagiarism(textExercise, config.similarityThreshold(), config.minimumScore(), config.minimumSize()))
                .thenReturn(textPlagiarismResult);

        // when
        var result = service.checkTextExercise(textExercise, config);

        // then
        assertThat(result).isEqualTo(textPlagiarismResult);
    }

    @Test
    void shouldExecuteChecksForModelingExercise() {
        // given
        var modelingExercise = new ModelingExercise();
        var modelingPlagiarismResult = new ModelingPlagiarismResult();
        modelingPlagiarismResult.setComparisons(emptySet());
        when(modelingPlagiarismDetectionService.checkPlagiarism(modelingExercise, config.similarityThreshold(), config.minimumSize(), config.minimumScore()))
                .thenReturn(modelingPlagiarismResult);

        // when
        var result = service.checkModelingExercise(modelingExercise, config);

        // then
        assertThat(result).isEqualTo(modelingPlagiarismResult);
    }

    @Test
    void shouldExecuteChecksForProgrammingExercise() throws IOException, ExitException, ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(1L);
        var programmingPlagiarismResult = new TextPlagiarismResult();
        programmingPlagiarismResult.setComparisons(emptySet());
        when(programmingPlagiarismDetectionService.checkPlagiarism(1L, config.similarityThreshold(), config.minimumScore(), config.minimumSize()))
                .thenReturn(programmingPlagiarismResult);

        // and
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false, false, false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // when
        var result = service.checkProgrammingExercise(programmingExercise, config);

        // then
        assertThat(result).isEqualTo(programmingPlagiarismResult);
    }

    @Test
    void shouldThrowExceptionOnUnsupportedProgrammingLanguage() {
        // given
        var programmingExercise = new ProgrammingExercise();
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, false, false, false, emptyList(), false, false, false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // expect
        assertThatThrownBy(() -> service.checkProgrammingExercise(programmingExercise, config)).isInstanceOf(ProgrammingLanguageNotSupportedForPlagiarismDetectionException.class);
    }

    @Test
    void shouldExecuteChecksWithJplagReportForProgrammingExercise() throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(1L);
        var zipFile = new File("");
        when(programmingPlagiarismDetectionService.checkPlagiarismWithJPlagReport(1L, config.similarityThreshold(), config.minimumScore(), config.minimumSize()))
                .thenReturn(zipFile);

        // and
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false, false, false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // when
        var result = service.checkProgrammingExerciseWithJplagReport(programmingExercise, config);

        // then
        assertThat(result).isEqualTo(zipFile);
    }

    @Test
    void shouldThrowExceptionOnUnsupportedProgrammingLanguageForChecksWithJplagReport() {
        // given
        var programmingExercise = new ProgrammingExercise();
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, false, false, false, emptyList(), false, false, false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // expect
        assertThatThrownBy(() -> service.checkProgrammingExerciseWithJplagReport(programmingExercise, config))
                .isInstanceOf(ProgrammingLanguageNotSupportedForPlagiarismDetectionException.class);
    }
}
