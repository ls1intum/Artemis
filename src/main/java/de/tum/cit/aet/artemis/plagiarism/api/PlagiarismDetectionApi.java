package de.tum.cit.aet.artemis.plagiarism.api;

import java.io.File;
import java.io.IOException;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.text.TextPlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.exception.ProgrammingLanguageNotSupportedForPlagiarismDetectionException;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismDetectionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@Conditional(PlagiarismEnabled.class)
@Controller
public class PlagiarismDetectionApi extends AbstractPlagiarismApi {

    private final PlagiarismDetectionService plagiarismDetectionService;

    public PlagiarismDetectionApi(PlagiarismDetectionService plagiarismDetectionService) {
        this.plagiarismDetectionService = plagiarismDetectionService;
    }

    public TextPlagiarismResult checkProgrammingExercise(ProgrammingExercise exercise) throws IOException, ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        return plagiarismDetectionService.checkProgrammingExercise(exercise);
    }

    public File checkProgrammingExerciseWithJplagReport(ProgrammingExercise exercise) throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        return plagiarismDetectionService.checkProgrammingExerciseWithJplagReport(exercise);
    }

    public TextPlagiarismResult checkTextExercise(TextExercise exercise) {
        return plagiarismDetectionService.checkTextExercise(exercise);
    }
}
