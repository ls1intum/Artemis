package de.tum.cit.aet.artemis.service.plagiarism;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.jplag.exceptions.ExitException;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.TextExercise;
import de.tum.cit.aet.artemis.domain.modeling.ModelingExercise;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.cit.aet.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.cit.aet.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.service.programming.ProgrammingLanguageFeatureService;

/**
 * Service for triggering plagiarism checks.
 */
@Profile(PROFILE_CORE)
@Service
public class PlagiarismDetectionService {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismDetectionService.class);

    private final TextPlagiarismDetectionService textPlagiarismDetectionService;

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService;

    private final ModelingPlagiarismDetectionService modelingPlagiarismDetectionService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    @Value("${artemis.plagiarism-checks.plagiarism-results-limit:100}")
    private int plagiarismResultsLimit;

    public PlagiarismDetectionService(TextPlagiarismDetectionService textPlagiarismDetectionService, Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService,
            ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService, ModelingPlagiarismDetectionService modelingPlagiarismDetectionService,
            PlagiarismResultRepository plagiarismResultRepository) {
        this.textPlagiarismDetectionService = textPlagiarismDetectionService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.programmingPlagiarismDetectionService = programmingPlagiarismDetectionService;
        this.modelingPlagiarismDetectionService = modelingPlagiarismDetectionService;
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    /**
     * Check plagiarism in given text exercise
     *
     * @param exercise exercise to check plagiarism
     * @return result of plagiarism checks
     */
    public TextPlagiarismResult checkTextExercise(TextExercise exercise) throws ExitException {
        var plagiarismResult = textPlagiarismDetectionService.checkPlagiarism(exercise, exercise.getPlagiarismDetectionConfig().getSimilarityThreshold(),
                exercise.getPlagiarismDetectionConfig().getMinimumScore(), exercise.getPlagiarismDetectionConfig().getMinimumSize());
        log.info("Finished textPlagiarismDetectionService.checkPlagiarism for exercise {} with {} comparisons,", exercise.getId(), plagiarismResult.getComparisons().size());

        trimAndSavePlagiarismResult(plagiarismResult);
        return plagiarismResult;
    }

    /**
     * Check plagiarism in given programing exercise
     *
     * @param exercise exercise to check plagiarism
     * @return result of plagiarism checks
     */
    public TextPlagiarismResult checkProgrammingExercise(ProgrammingExercise exercise)
            throws ExitException, IOException, ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        checkProgrammingLanguageSupport(exercise);

        var plagiarismResult = programmingPlagiarismDetectionService.checkPlagiarism(exercise.getId(), exercise.getPlagiarismDetectionConfig().getSimilarityThreshold(),
                exercise.getPlagiarismDetectionConfig().getMinimumScore(), exercise.getPlagiarismDetectionConfig().getMinimumSize());
        log.info("Finished programmingExerciseExportService.checkPlagiarism call for {} comparisons", plagiarismResult.getComparisons().size());

        // make sure that participation is included in the exercise
        plagiarismResult.setExercise(exercise);
        trimAndSavePlagiarismResult(plagiarismResult);
        return plagiarismResult;
    }

    /**
     * Check plagiarism in given programing exercise and outputs a Jplag report
     *
     * @param exercise exercise to check plagiarism
     * @return Jplag report of plagiarism checks
     */
    public File checkProgrammingExerciseWithJplagReport(ProgrammingExercise exercise) throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        checkProgrammingLanguageSupport(exercise);
        return programmingPlagiarismDetectionService.checkPlagiarismWithJPlagReport(exercise.getId(), exercise.getPlagiarismDetectionConfig().getSimilarityThreshold(),
                exercise.getPlagiarismDetectionConfig().getMinimumScore(), exercise.getPlagiarismDetectionConfig().getMinimumSize());
    }

    /**
     * Check plagiarism in given modeling exercise
     *
     * @param exercise exercise to check plagiarism
     * @return result of plagiarism checks
     */
    public ModelingPlagiarismResult checkModelingExercise(ModelingExercise exercise) {
        var plagiarismResult = modelingPlagiarismDetectionService.checkPlagiarism(exercise, exercise.getPlagiarismDetectionConfig().getSimilarityThreshold(),
                exercise.getPlagiarismDetectionConfig().getMinimumSize(), exercise.getPlagiarismDetectionConfig().getMinimumScore());
        log.info("Finished modelingPlagiarismDetectionService.checkPlagiarism call for {} comparisons", plagiarismResult.getComparisons().size());

        trimAndSavePlagiarismResult(plagiarismResult);
        return plagiarismResult;
    }

    private void trimAndSavePlagiarismResult(PlagiarismResult<?> plagiarismResult) {
        // Limit the amount temporarily because of database issues
        plagiarismResult.sortAndLimit(plagiarismResultsLimit);
        plagiarismResultRepository.savePlagiarismResultAndRemovePrevious(plagiarismResult);

        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
    }

    private void checkProgrammingLanguageSupport(ProgrammingExercise exercise) throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        var language = exercise.getProgrammingLanguage();
        var programmingLanguageFeature = programmingLanguageFeatureService.orElseThrow().getProgrammingLanguageFeatures(language);
        if (!programmingLanguageFeature.plagiarismCheckSupported()) {
            throw new ProgrammingLanguageNotSupportedForPlagiarismDetectionException(language);
        }
    }
}
