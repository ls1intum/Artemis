package de.tum.cit.aet.artemis.athena.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Service for exporting programming exercise repositories for Athena.
 */
@Lazy
@Service
@Profile(PROFILE_ATHENA)
public class AthenaRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(AthenaRepositoryExportService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final RepositoryService repositoryService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public AthenaRepositoryExportService(ProgrammingExerciseRepository programmingExerciseRepository, RepositoryService repositoryService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.repositoryService = repositoryService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
    }

    /**
     * Check if feedback suggestions are enabled for the given exercise, otherwise throw an exception.
     *
     * @param exercise the exercise to check
     * @throws AccessForbiddenException if the feedback suggestions are not enabled for the given exercise
     */
    private void checkFeedbackSuggestionsOrAutomaticFeedbackEnabledElseThrow(Exercise exercise) {
        if (!(exercise.areFeedbackSuggestionsEnabled() || exercise.getAllowFeedbackRequests())) {
            log.error("Feedback suggestions are not enabled for exercise {}", exercise.getId());
            throw new ServiceUnavailableException("Feedback suggestions are not enabled for exercise");
        }
    }

    /**
     * Returns a mapping of file paths to contents for a repository.
     * Binary files are omitted.
     *
     * @param exerciseId     the id of the exercise to retrieve the repository for
     * @param submissionId   the id of the submission (only used for student repositories; otherwise pass null)
     * @param repositoryType the type of repository to retrieve. Pass null to retrieve the student repository
     * @return Map of file paths to their textual contents
     * @throws IOException              if reading from the repository fails
     * @throws AccessForbiddenException if the feedback suggestions are not enabled for the given exercise
     */
    public Map<String, String> getRepositoryFilesContent(long exerciseId, Long submissionId, RepositoryType repositoryType) throws IOException {
        log.debug("Retrieving repository file contents for exercise {}, submission {} (repoType: {})", exerciseId, submissionId, repositoryType);

        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        checkFeedbackSuggestionsOrAutomaticFeedbackEnabledElseThrow(programmingExercise);

        // Athena currently does not support individual due dates
        var exportOptions = new RepositoryExportOptionsDTO(false, true, false, programmingExercise.getDueDate(), false, false, false, true, false);

        if (repositoryType != null) {
            // Export instructor repositories
            var repoUri = programmingExercise.getRepositoryURI(repositoryType);
            if (repoUri == null) {
                throw new IOException("Repository URI is null for exercise " + exerciseId + " and repository type " + repositoryType + ". This may indicate that the "
                        + repositoryType.name().toLowerCase() + " repository has not been set up yet.");
            }
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repoUri);
        }

        // Export student repository
        var submission = programmingSubmissionRepository.findById(submissionId).orElseThrow();
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(submission.getParticipation().getId());
        var repoUri = participation.getVcsRepositoryUri();
        if (repoUri == null) {
            throw new IOException(
                    "Repository URI is null for student participation " + participation.getId() + ". This may indicate that the student repository has not been set up yet.");
        }
        ZonedDateTime deadline = exportOptions.filterLateSubmissionsDate();
        if (deadline != null) {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(repoUri, deadline);
        }
        else {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(repoUri);
        }
    }
}
