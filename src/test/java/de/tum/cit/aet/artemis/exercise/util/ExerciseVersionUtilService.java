package de.tum.cit.aet.artemis.exercise.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.BiPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

/**
 * Service responsible for exercise versioning utilities in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ExerciseVersionUtilService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionUtilService.class);

    public static final BiPredicate<ZonedDateTime, ZonedDateTime> zonedDateTimeBiPredicate = (a, b) -> {
        if (a == null && b == null) {
            return true;
        }
        else if (a != null && b != null) {
            return Math.abs(a.toInstant().getEpochSecond() - b.toInstant().getEpochSecond()) < 1;
        }
        else {
            return false;
        }
    };

    @Autowired
    private ExerciseVersionTestRepository exerciseVersionRepository;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseTestRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private GitService gitService;

    /**
     * Updates common exercise fields for testing purposes.
     *
     * @param exercise the exercise to update
     */
    public static void updateExercise(Exercise exercise) {
        exercise.setTitle("Updated Title");
        exercise.setProblemStatement("Updated problem statement");
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(10.0);
        exercise.setAllowComplaintsForAutomaticAssessments(true);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setGradingInstructions("Updated grading instructions");
        exercise.setPresentationScoreEnabled(true);
        exercise.setSecondCorrectionEnabled(true);
        exercise.setDifficulty(DifficultyLevel.HARD);
        exercise.setMode(ExerciseMode.TEAM);
        exercise.setAssessmentType(AssessmentType.MANUAL);

        ZonedDateTime now = ZonedDateTime.now();
        exercise.setReleaseDate(now.minusDays(1));
        exercise.setDueDate(now.plusDays(7));
        exercise.setAssessmentDueDate(now.plusDays(10));
        exercise.setExampleSolutionPublicationDate(now.plusDays(12));
    }

    /**
     * Fetches an exercise versioned fields, with the correct exercise type.
     *
     * @param exerciseId   the exercise id to be fetched
     * @param exerciseType the type of the exercise
     * @return the exercise with the given id of the specific subclass, fetched
     *         eagerly with versioned fields,
     *         or null if the exercise does not exist
     */
    private Exercise findExerciseForVersioning(Long exerciseId, ExerciseType exerciseType) {
        return switch (exerciseType) {
            case PROGRAMMING -> programmingExerciseRepository.findForVersioningById(exerciseId).orElse(null);
            case QUIZ -> quizExerciseTestRepository.findForVersioningById(exerciseId).orElse(null);
            case TEXT -> textExerciseRepository.findForVersioningById(exerciseId).orElse(null);
            case MODELING -> modelingExerciseRepository.findForVersioningById(exerciseId).orElse(null);
            case FILE_UPLOAD -> fileUploadExerciseRepository.findForVersioningById(exerciseId).orElse(null);
        };
    }

    /**
     * Generic method to verify exercise version creation.
     *
     * @param exerciseId the ID of the exercise to verify
     * @param username   the username of the expected author
     * @param type       the type of the exercise
     * @return the verified exercise version
     */
    public ExerciseVersion verifyExerciseVersionCreated(Long exerciseId, String username, ExerciseType type) {
        Exercise savedExercise = findExerciseForVersioning(exerciseId, type);
        assertThat(savedExercise).isNotNull();

        Optional<ExerciseVersion> versionOptional = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);
        assertThat(versionOptional).isPresent();

        ExerciseVersion version = versionOptional.get();

        // verify exercise id is set
        assertThat(version.getExerciseId()).isEqualTo(exerciseId);

        // verify author id is set
        Optional<Long> authorIdOp = userTestRepository.findIdByLogin(username);
        assertThat(authorIdOp).isPresent();
        assertThat(version.getAuthorId()).isEqualTo(authorIdOp.get());

        assertThat(version.getCreatedDate()).isNotNull();
        assertThat(version.getExerciseSnapshot()).isNotNull();

        // Verify snapshot contains exercise specific data
        ExerciseSnapshotDTO expectedSnapshot = ExerciseSnapshotDTO.of(savedExercise, gitService);
        ExerciseSnapshotDTO actualSnapshot = version.getExerciseSnapshot();
        assertThat(actualSnapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);

        return version;
    }
}
