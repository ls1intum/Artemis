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

import org.junit.jupiter.api.Test;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismChecksConfig;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

class PlagiarismChecksServiceTest {

    private final PlagiarismChecksConfig plagiarismChecksConfig = PlagiarismChecksConfig.createDefault();

    private final TextPlagiarismDetectionService textPlagiarismDetectionService = mock();

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService = mock();

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService = mock();

    private final ModelingPlagiarismDetectionService modelingPlagiarismDetectionService = mock();

    private final PlagiarismResultRepository plagiarismResultRepository = mock();

    private final PlagiarismChecksService service = new PlagiarismChecksService(textPlagiarismDetectionService, programmingLanguageFeatureService,
            programmingPlagiarismDetectionService, modelingPlagiarismDetectionService, plagiarismResultRepository);

    @Test
    void shouldExecuteChecksForTextExercise() throws ExitException {
        // given
        var textExercise = new TextExercise();
        textExercise.setPlagiarismChecksConfig(plagiarismChecksConfig);
        var textPlagiarismResult = new TextPlagiarismResult();
        textPlagiarismResult.setComparisons(emptySet());
        when(textPlagiarismDetectionService.checkPlagiarism(eq(textExercise), anyFloat(), anyInt(), anyInt())).thenReturn(textPlagiarismResult);

        // when
        var result = service.checkTextExercise(textExercise);

        // then
        assertThat(result).isEqualTo(textPlagiarismResult);
    }

    @Test
    void shouldExecuteChecksForModelingExercise() {
        // given
        var modelingExercise = new ModelingExercise();
        modelingExercise.setPlagiarismChecksConfig(plagiarismChecksConfig);
        var modelingPlagiarismResult = new ModelingPlagiarismResult();
        modelingPlagiarismResult.setComparisons(emptySet());
        when(modelingPlagiarismDetectionService.checkPlagiarism(eq(modelingExercise), anyDouble(), anyInt(), anyInt())).thenReturn(modelingPlagiarismResult);

        // when
        var result = service.checkModelingExercise(modelingExercise);

        // then
        assertThat(result).isEqualTo(modelingPlagiarismResult);
    }

    @Test
    void shouldExecuteChecksForProgrammingExercise() throws IOException, ExitException, ProgrammingLanguageNotSupportedForPlagiarismChecksException {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(1L);
        programmingExercise.setPlagiarismChecksConfig(plagiarismChecksConfig);
        var programmingPlagiarismResult = new TextPlagiarismResult();
        programmingPlagiarismResult.setComparisons(emptySet());
        when(programmingPlagiarismDetectionService.checkPlagiarism(eq(1L), anyFloat(), anyInt())).thenReturn(programmingPlagiarismResult);

        // and
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false, false, false);
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
        programmingExercise.setPlagiarismChecksConfig(plagiarismChecksConfig);
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, false, false, false, emptyList(), false, false, false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // expect
        assertThatThrownBy(() -> service.checkProgrammingExercise(programmingExercise)).isInstanceOf(ProgrammingLanguageNotSupportedForPlagiarismChecksException.class);
    }

    @Test
    void shouldExecuteChecksWithJplagReportForProgrammingExercise() throws ProgrammingLanguageNotSupportedForPlagiarismChecksException {
        // given
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setPlagiarismChecksConfig(plagiarismChecksConfig);
        programmingExercise.setId(1L);
        var zipFile = new File("");
        when(programmingPlagiarismDetectionService.checkPlagiarismWithJPlagReport(eq(1L), anyFloat(), anyInt())).thenReturn(zipFile);

        // and
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false, false, false);
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
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, false, false, false, emptyList(), false, false, false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);

        // expect
        assertThatThrownBy(() -> service.checkProgrammingExerciseWithJplagReport(programmingExercise))
                .isInstanceOf(ProgrammingLanguageNotSupportedForPlagiarismChecksException.class);
    }
}
