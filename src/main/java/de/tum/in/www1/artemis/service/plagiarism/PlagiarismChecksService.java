package de.tum.in.www1.artemis.service.plagiarism;

import java.io.File;
import java.io.IOException;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Service
@Component
public class PlagiarismChecksService {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismChecksService.class);

    private final TextPlagiarismDetectionService textPlagiarismDetectionService;

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService;

    private final ModelingPlagiarismDetectionService modelingPlagiarismDetectionService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    public PlagiarismChecksService(TextPlagiarismDetectionService textPlagiarismDetectionService, ProgrammingLanguageFeatureService programmingLanguageFeatureService,
            ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService, ModelingPlagiarismDetectionService modelingPlagiarismDetectionService,
            PlagiarismResultRepository plagiarismResultRepository) {
        this.textPlagiarismDetectionService = textPlagiarismDetectionService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.programmingPlagiarismDetectionService = programmingPlagiarismDetectionService;
        this.modelingPlagiarismDetectionService = modelingPlagiarismDetectionService;
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    public TextPlagiarismResult checkTextExercise(TextExercise exercise) throws ExitException {
        var plagiarismResult = textPlagiarismDetectionService.checkPlagiarism(exercise, exercise.getPlagiarismChecksConfig().getSimilarityThreshold(),
                exercise.getPlagiarismChecksConfig().getMinimumScore(), exercise.getPlagiarismChecksConfig().getMinimumSize());
        log.info("Finished textPlagiarismDetectionService.checkPlagiarism for exercise {} with {} comparisons,", exercise.getId(), plagiarismResult.getComparisons().size());

        // TODO: limit the amount temporarily because of database issues
        plagiarismResult.sortAndLimit(100);
        plagiarismResultRepository.savePlagiarismResultAndRemovePrevious(plagiarismResult);

        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return plagiarismResult;
    }

    private void checkProgrammingLanguageSupport(ProgrammingExercise exercise) throws ProgrammingLanguageNotSupportedFroPlagiarismChecksException {
        var language = exercise.getProgrammingLanguage();
        var programmingLanguageFeature = programmingLanguageFeatureService.getProgrammingLanguageFeatures(language);
        if (!programmingLanguageFeature.plagiarismCheckSupported()) {
            throw new ProgrammingLanguageNotSupportedFroPlagiarismChecksException(language);
        }
    }

    public TextPlagiarismResult checkProgrammingExercise(ProgrammingExercise exercise)
            throws ExitException, IOException, ProgrammingLanguageNotSupportedFroPlagiarismChecksException {
        checkProgrammingLanguageSupport(exercise);

        var plagiarismResult = programmingPlagiarismDetectionService.checkPlagiarism(exercise.getId(), exercise.getPlagiarismChecksConfig().getSimilarityThreshold(),
                exercise.getPlagiarismChecksConfig().getMinimumScore());
        log.info("Finished programmingExerciseExportService.checkPlagiarism call for {} comparisons", plagiarismResult.getComparisons().size());

        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return plagiarismResult;
    }

    public File checkProgrammingExerciseWithJplagReport(ProgrammingExercise exercise) throws ProgrammingLanguageNotSupportedFroPlagiarismChecksException {
        checkProgrammingLanguageSupport(exercise);
        return programmingPlagiarismDetectionService.checkPlagiarismWithJPlagReport(exercise.getId(), exercise.getPlagiarismChecksConfig().getSimilarityThreshold(),
                exercise.getPlagiarismChecksConfig().getMinimumScore());
    }

    public ModelingPlagiarismResult checkModelingExercise(ModelingExercise exercise) {
        var plagiarismResult = modelingPlagiarismDetectionService.checkPlagiarism(exercise, exercise.getPlagiarismChecksConfig().getSimilarityThreshold(),
                exercise.getPlagiarismChecksConfig().getMinimumSize(), exercise.getPlagiarismChecksConfig().getMinimumScore());
        log.info("Finished modelingPlagiarismDetectionService.checkPlagiarism call for {} comparisons", plagiarismResult.getComparisons().size());

        // TODO: limit the amount temporarily because of database issues
        plagiarismResult.sortAndLimit(100);
        plagiarismResultRepository.savePlagiarismResultAndRemovePrevious(plagiarismResult);

        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return plagiarismResult;
    }
}
