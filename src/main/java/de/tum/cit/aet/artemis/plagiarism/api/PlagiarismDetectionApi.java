package de.tum.cit.aet.artemis.plagiarism.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.IOException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.plagiarism.domain.text.TextPlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.exception.ProgrammingLanguageNotSupportedForPlagiarismDetectionException;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismDetectionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@Profile(PROFILE_CORE)
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
