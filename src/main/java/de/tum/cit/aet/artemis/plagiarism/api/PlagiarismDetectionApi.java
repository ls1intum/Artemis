package de.tum.cit.aet.artemis.plagiarism.api;

import java.io.File;
import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.exception.ProgrammingLanguageNotSupportedForPlagiarismDetectionException;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismDetectionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ConditionalOnProperty(name = "artemis.plagiarism.enabled", havingValue = "true")
@Controller
public class PlagiarismDetectionApi extends AbstractPlagiarismApi {

    private final PlagiarismDetectionService plagiarismDetectionService;

    public PlagiarismDetectionApi(PlagiarismDetectionService plagiarismDetectionService) {
        this.plagiarismDetectionService = plagiarismDetectionService;
    }

    public PlagiarismResult checkProgrammingExercise(ProgrammingExercise exercise) throws IOException, ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        return plagiarismDetectionService.checkProgrammingExercise(exercise);
    }

    public File checkProgrammingExerciseWithJplagReport(ProgrammingExercise exercise) throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        return plagiarismDetectionService.checkProgrammingExerciseWithJplagReport(exercise);
    }

    public PlagiarismResult checkTextExercise(TextExercise exercise) {
        return plagiarismDetectionService.checkTextExercise(exercise);
    }
}
