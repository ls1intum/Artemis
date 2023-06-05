package de.tum.in.www1.artemis.service.plagiarism;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

@Service
@Component
@Profile("scheduling")
public class ContinuousPlagiarismControlService {

    private static final Logger log = LoggerFactory.getLogger(ContinuousPlagiarismControlService.class);

    private final ExerciseRepository exerciseRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final TextPlagiarismDetectionService textPlagiarismDetectionService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ModelingPlagiarismDetectionService modelingPlagiarismDetectionService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    public ContinuousPlagiarismControlService(ExerciseRepository exerciseRepository, TextExerciseRepository textExerciseRepository,
            TextPlagiarismDetectionService textPlagiarismDetectionService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingLanguageFeatureService programmingLanguageFeatureService, ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService,
            ModelingExerciseRepository modelingExerciseRepository, ModelingPlagiarismDetectionService modelingPlagiarismDetectionService,
            PlagiarismResultRepository plagiarismResultRepository) {
        this.exerciseRepository = exerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.textPlagiarismDetectionService = textPlagiarismDetectionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.programmingPlagiarismDetectionService = programmingPlagiarismDetectionService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingPlagiarismDetectionService = modelingPlagiarismDetectionService;
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    @Scheduled(fixedDelay = 35_000)
    // @Scheduled(cron = "0 0 4 * * *") // execute this every night at 4:00:00 am
    public void executeChecks() {
        log.info("Starting continuous plagiarism control...");

        var exercises = exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDateAndContinuousPlagiarismControlEnabledIsTrue();
        exercises.forEach(exercise -> {
            log.info("Started continuous plagiarism control for exercise: exerciseId={}, type={}.", exercise.getId(), exercise.getExerciseType());
            final long startTime = System.nanoTime();

            try {
                executeChecksForExercise(exercise);
            }
            catch (ExitException e) {
                log.error("Cannot check plagiarism due to Jplag error: exerciseId={}, type={}, error={}.", exercise.getId(), exercise.getExerciseType(), e.getMessage(), e);
            }
            catch (Exception e) {
                log.error("Cannot check plagiarism due to unknown error: exerciseId={}, type={}, error={}.", exercise.getId(), exercise.getExerciseType(), e.getMessage(), e);
            }

            log.info("Finished continuous plagiarism control for exercise: exerciseId={}, elapsed={}.", exercise.getId(), TimeLogUtil.formatDurationFrom(startTime));
        });

        log.debug("Continuous plagiarism control done.");
    }

    private void executeChecksForExercise(Exercise exercise) throws Exception {
        switch (exercise.getExerciseType()) {
            case TEXT -> {
                var textExercise = textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exercise.getId());
                executeChecksForTextExercise(textExercise);
            }
            case PROGRAMMING -> {
                var programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(exercise.getId());
                executeChecksForProgrammingExercise(programmingExercise);
            }
            case MODELING -> {
                var modelingExercise = modelingExerciseRepository.findByIdWithStudentParticipationsSubmissionsResultsElseThrow(exercise.getId());
                executeChecksForModelingExercise(modelingExercise);
            }
            case FILE_UPLOAD, QUIZ -> {
                log.error("Cannot check plagiarism for exercise: type={}, id={}.", exercise.getExerciseType(), exercise.getId());
            }
        }
    }

    private void executeChecksForTextExercise(TextExercise exercise) throws Exception {
        var plagiarismResult = textPlagiarismDetectionService.checkPlagiarism(exercise, exercise.getPlagiarismChecksConfig().getSimilarityThreshold(),
                exercise.getPlagiarismChecksConfig().getMinimumScore(), exercise.getPlagiarismChecksConfig().getMinimumSize());
        log.info("Finished textPlagiarismDetectionService.checkPlagiarism for exercise {} with {} comparisons,", exercise.getId(), plagiarismResult.getComparisons().size());

        // TODO: limit the amount temporarily because of database issues
        plagiarismResult.sortAndLimit(100);
        plagiarismResultRepository.savePlagiarismResultAndRemovePrevious(plagiarismResult);

        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
    }

    private void executeChecksForProgrammingExercise(ProgrammingExercise exercise) throws Exception {
        var language = exercise.getProgrammingLanguage();
        var programmingLanguageFeature = programmingLanguageFeatureService.getProgrammingLanguageFeatures(language);
        if (!programmingLanguageFeature.plagiarismCheckSupported()) {
            log.error("Artemis does not support plagiarism checks for the programming language {}", language);
        }

        var plagiarismResult = programmingPlagiarismDetectionService.checkPlagiarism(exercise.getId(), exercise.getPlagiarismChecksConfig().getSimilarityThreshold(),
                exercise.getPlagiarismChecksConfig().getMinimumScore());
        log.info("Finished programmingExerciseExportService.checkPlagiarism call for {} comparisons", plagiarismResult.getComparisons().size());

        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
    }

    private void executeChecksForModelingExercise(ModelingExercise exercise) throws Exception {
        var plagiarismResult = modelingPlagiarismDetectionService.checkPlagiarism(exercise, exercise.getPlagiarismChecksConfig().getSimilarityThreshold() / 100,
                exercise.getPlagiarismChecksConfig().getMinimumSize(), exercise.getPlagiarismChecksConfig().getMinimumScore());
        log.info("Finished modelingPlagiarismDetectionService.checkPlagiarism call for {} comparisons", plagiarismResult.getComparisons().size());

        // TODO: limit the amount temporarily because of database issues
        plagiarismResult.sortAndLimit(100);
        plagiarismResultRepository.savePlagiarismResultAndRemovePrevious(plagiarismResult);

        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
    }
}
