package de.tum.cit.aet.artemis.plagiarism;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.jplag.exceptions.ExitException;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.exception.ProgrammingLanguageNotSupportedForPlagiarismDetectionException;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismDetectionService;
import de.tum.cit.aet.artemis.plagiarism.service.ProgrammingPlagiarismDetectionService;
import de.tum.cit.aet.artemis.plagiarism.service.TextPlagiarismDetectionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeatureService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class PlagiarismDetectionServiceTest {

    private final PlagiarismDetectionConfig config = PlagiarismDetectionConfig.createDefault();

    private final TextPlagiarismDetectionService textPlagiarismDetectionService = mock();

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService = mock();

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService = mock();

    private final PlagiarismResultRepository plagiarismResultRepository = mock();

    private final PlagiarismDetectionService service = new PlagiarismDetectionService(textPlagiarismDetectionService, Optional.of(programmingLanguageFeatureService),
            programmingPlagiarismDetectionService, plagiarismResultRepository);

    @Test
    void shouldExecuteChecksForTextExercise() throws ExitException {
        // given
        var textExercise = new TextExercise();
        textExercise.setPlagiarismDetectionConfig(config);
        var textPlagiarismResult = new PlagiarismResult();
        textPlagiarismResult.setComparisons(emptySet());
        when(textPlagiarismDetectionService.checkPlagiarism(textExercise, config.getSimilarityThreshold(), config.getMinimumScore(), config.getMinimumSize()))
                .thenReturn(textPlagiarismResult);

        // when
        var result = service.checkTextExercise(textExercise);

        // then
        assertThat(result).isEqualTo(textPlagiarismResult);
    }

    @Test
    void shouldExecuteChecksForProgrammingExercise() throws IOException, ExitException, ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(1L);
        programmingExercise.setPlagiarismDetectionConfig(config);
        var programmingPlagiarismResult = new PlagiarismResult();
        programmingPlagiarismResult.setComparisons(emptySet());
        when(programmingPlagiarismDetectionService.checkPlagiarism(1L, config.getSimilarityThreshold(), config.getMinimumScore(), config.getMinimumSize()))
                .thenReturn(programmingPlagiarismResult);

        // and
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // when
        var result = service.checkProgrammingExercise(programmingExercise);

        // then
        assertThat(result).isEqualTo(programmingPlagiarismResult);
    }

    @Test
    void shouldThrowExceptionOnUnsupportedProgrammingLanguage() {
        // given
        var programmingExercise = new ProgrammingExercise();
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, false, false, false, emptyList(), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // expect
        assertThatThrownBy(() -> service.checkProgrammingExercise(programmingExercise)).isInstanceOf(ProgrammingLanguageNotSupportedForPlagiarismDetectionException.class);
    }

    @Test
    void shouldExecuteChecksWithJplagReportForProgrammingExercise() throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(1L);
        programmingExercise.setPlagiarismDetectionConfig(config);
        var zipFile = Path.of("").toFile();
        when(programmingPlagiarismDetectionService.checkPlagiarismWithJPlagReport(1L, config.getSimilarityThreshold(), config.getMinimumScore(), config.getMinimumSize()))
                .thenReturn(zipFile);

        // and
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // when
        var result = service.checkProgrammingExerciseWithJplagReport(programmingExercise);

        // then
        assertThat(result).isEqualTo(zipFile);
    }

    @Test
    void shouldThrowExceptionOnUnsupportedProgrammingLanguageForChecksWithJplagReport() {
        // given
        var programmingExercise = new ProgrammingExercise();
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, false, false, false, emptyList(), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // expect
        assertThatThrownBy(() -> service.checkProgrammingExerciseWithJplagReport(programmingExercise))
                .isInstanceOf(ProgrammingLanguageNotSupportedForPlagiarismDetectionException.class);
    }
}
