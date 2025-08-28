package de.tum.cit.aet.artemis.athena.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.GitRepositoryExportService;

/**
 * Service for exporting programming exercise repositories for Athena.
 */
@Lazy
@Service
@Profile(PROFILE_ATHENA)
public class AthenaRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(AthenaRepositoryExportService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GitRepositoryExportService gitRepositoryExportService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public AthenaRepositoryExportService(ProgrammingExerciseRepository programmingExerciseRepository, GitRepositoryExportService gitRepositoryExportService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitRepositoryExportService = gitRepositoryExportService;
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
     * Export the repository for the given exercise and participation directly to memory.
     * This method avoids creating temporary files on disk.
     *
     * @param exerciseId     the id of the exercise to export the repository for
     * @param submissionId   the id of the submission to export the repository for (only for student repository, otherwise pass null)
     * @param repositoryType the type of repository to export. Pass null to export the student repository.
     * @return InputStreamResource containing the zipped repository content
     * @throws IOException              if the export fails
     * @throws AccessForbiddenException if the feedback suggestions are not enabled for the given exercise
     */
    public InputStreamResource exportRepository(long exerciseId, Long submissionId, RepositoryType repositoryType) throws IOException {
        log.debug("Exporting repository for exercise {}, submission {}", exerciseId, submissionId);

        // Load exercise with template and solution participation for repository URI access
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming exercise with id " + exerciseId + " not found"));
        checkFeedbackSuggestionsOrAutomaticFeedbackEnabledElseThrow(programmingExercise);

        List<String> exportErrors = new ArrayList<>();
        InputStreamResource resource = null;

        if (repositoryType == null) { // Export student repository
            var submission = programmingSubmissionRepository.findById(submissionId).orElseThrow();
            // Load participation with eager submissions
            var participation = programmingExerciseStudentParticipationRepository.findWithSubmissionsById(submission.getParticipation().getId()).getFirst();
            resource = gitRepositoryExportService.exportStudentRepositoryInMemory(programmingExercise, participation, exportErrors);
        }
        else {
            resource = gitRepositoryExportService.exportInstructorRepositoryForExerciseInMemory(programmingExercise, repositoryType, exportErrors);
        }

        if (resource == null) {
            throw new IOException("Failed to export repository");
        }

        return resource;
    }
}
