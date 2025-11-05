package de.tum.cit.aet.artemis.athena.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.service.ExerciseAthenaConfigService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

/**
 * Service for exporting programming exercise repositories for Athena.
 */
@Lazy
@Service
@Profile(PROFILE_ATHENA)
public class AthenaRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(AthenaRepositoryExportService.class);

    private static final String ENTITY_NAME = "programmingExercise";

    /**
     * Set of valid instructor repository types that can be accessed by Athena (excludes AUXILIARY).
     */
    private static final Set<RepositoryType> ATHENA_INSTRUCTOR_REPOSITORY_TYPES = Set.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final RepositoryService repositoryService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ExerciseAthenaConfigService exerciseAthenaConfigService;

    public AthenaRepositoryExportService(ProgrammingExerciseRepository programmingExerciseRepository, RepositoryService repositoryService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ExerciseAthenaConfigService exerciseAthenaConfigService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.repositoryService = repositoryService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.exerciseAthenaConfigService = exerciseAthenaConfigService;
    }

    /**
     * Check if feedback suggestions are enabled for the given exercise, otherwise throw an exception.
     *
     * @param exercise the exercise to check
     * @throws AccessForbiddenException if the feedback suggestions are not enabled for the given exercise
     */
    private void checkFeedbackSuggestionsOrAutomaticFeedbackEnabledElseThrow(Exercise exercise, ExerciseAthenaConfig config) {
        var feedbackSuggestionModule = config != null ? config.getFeedbackSuggestionModule() : null;
        var preliminaryFeedbackModule = config != null ? config.getPreliminaryFeedbackModule() : null;
        if (feedbackSuggestionModule == null && preliminaryFeedbackModule == null) {
            log.error("Feedback suggestions are not enabled for exercise {}", exercise.getId());
            throw new ServiceUnavailableException("Feedback suggestions are not enabled for exercise");
        }
    }

    /**
     * Returns a mapping of file paths to contents for a student repository.
     * Binary files are omitted.
     *
     * @param exerciseId   the id of the exercise to retrieve the repository for
     * @param submissionId the id of the submission
     * @return Map of file paths to their textual contents
     * @throws IOException              if reading from the repository fails
     * @throws BadRequestAlertException if the repository URI is null
     * @throws AccessForbiddenException if the feedback suggestions are not enabled for the given exercise
     */
    public Map<String, String> getStudentRepositoryFilesContent(long exerciseId, Long submissionId) throws IOException {
        log.debug("Retrieving student repository file contents for exercise {}, submission {}", exerciseId, submissionId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var config = exerciseAthenaConfigService.findByExerciseId(exerciseId).orElse(null);
        programmingExercise.setAthenaConfig(config);
        checkFeedbackSuggestionsOrAutomaticFeedbackEnabledElseThrow(programmingExercise, config);

        ProgrammingSubmission submission = programmingSubmissionRepository.findByIdElseThrow(submissionId);
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(submission.getParticipation().getId());

        // Validate that the submission belongs to the requested exercise
        Long submissionExerciseId = participation.getExercise().getId();
        if (!submissionExerciseId.equals(exerciseId)) {
            throw new BadRequestAlertException("Submission " + submissionId + " does not belong to exercise " + exerciseId + " (belongs to exercise " + submissionExerciseId + ")",
                    ENTITY_NAME, "submissionExerciseMismatch");
        }

        LocalVCRepositoryUri repoUri = participation.getVcsRepositoryUri();
        if (repoUri == null) {
            throw new BadRequestAlertException(
                    "Repository URI is null for student participation " + participation.getId() + ". This may indicate that the student repository has not been set up yet.",
                    ENTITY_NAME, "invalid.student.repository.url");
        }

        // Athena currently does not fully support individual due dates, therefore we use the exercise due date instead of the individual due date
        ZonedDateTime deadline = participation.getExercise().getDueDate();
        if (deadline != null) {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(repoUri, deadline);
        }
        else {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repoUri);
        }
    }

    /**
     * Retrieves the files content of an instructor repository.
     *
     * @param exerciseId     the id of the exercise to retrieve the repository for
     * @param repositoryType the type of repository to retrieve (must be an Athena instructor repository type)
     * @return Map of file paths to their textual contents
     * @throws IOException              if reading from the repository fails
     * @throws BadRequestAlertException if the repository URI is null
     * @throws AccessForbiddenException if the feedback suggestions are not enabled for the given exercise
     * @throws BadRequestAlertException if the repository type is not an Athena instructor repository type
     */
    public Map<String, String> getInstructorRepositoryFilesContent(long exerciseId, RepositoryType repositoryType) throws IOException {
        log.debug("Retrieving instructor repository file contents for exercise {}, repository type {}", exerciseId, repositoryType);

        if (!ATHENA_INSTRUCTOR_REPOSITORY_TYPES.contains(repositoryType)) {
            throw new BadRequestAlertException("Invalid instructor repository type", ENTITY_NAME, "invalid.instructor.repository.type",
                    Map.of("repositoryType", repositoryType, "validTypes", ATHENA_INSTRUCTOR_REPOSITORY_TYPES));
        }

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        var config = exerciseAthenaConfigService.findByExerciseId(exerciseId).orElse(null);
        programmingExercise.setAthenaConfig(config);
        checkFeedbackSuggestionsOrAutomaticFeedbackEnabledElseThrow(programmingExercise, config);

        LocalVCRepositoryUri repoUri = programmingExercise.getRepositoryURI(repositoryType);
        if (repoUri == null) {
            String errorKey = "invalid." + repositoryType.name().toLowerCase() + ".repository.url";
            throw new BadRequestAlertException("Repository URI is null for exercise " + exerciseId + " and repository type " + repositoryType + ". This may indicate that the "
                    + repositoryType.name().toLowerCase() + " repository has not been set up yet.", ENTITY_NAME, errorKey);
        }
        return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repoUri);
    }
}
