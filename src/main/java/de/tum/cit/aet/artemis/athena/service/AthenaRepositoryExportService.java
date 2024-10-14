package de.tum.cit.aet.artemis.athena.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;

/**
 * Service for exporting programming exercise repositories for Athena.
 */
@Service
@Profile(PROFILE_ATHENA)
public class AthenaRepositoryExportService {

    private static final Logger log = LoggerFactory.getLogger(AthenaRepositoryExportService.class);

    // The downloaded repos should be cloned into another path in order to not interfere with the repo used by the student
    // We reuse the same directory as the programming exercise export service for this.
    @Value("${artemis.repo-download-clone-path}")
    private Path repoDownloadClonePath;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final FileService fileService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public AthenaRepositoryExportService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseExportService programmingExerciseExportService,
            FileService fileService, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.fileService = fileService;
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
     * Export the repository for the given exercise and participation to a zip file.
     * The ZIP file will be deleted automatically after 15 minutes.
     *
     * @param exerciseId     the id of the exercise to export the repository for
     * @param submissionId   the id of the submission to export the repository for (only for student repository, otherwise pass null)
     * @param repositoryType the type of repository to export. Pass null to export the student repository.
     * @return the zip file containing the exported repository
     * @throws IOException              if the export fails
     * @throws AccessForbiddenException if the feedback suggestions are not enabled for the given exercise
     */
    public File exportRepository(long exerciseId, Long submissionId, RepositoryType repositoryType) throws IOException {
        log.debug("Exporting repository for exercise {}, submission {}", exerciseId, submissionId);

        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        checkFeedbackSuggestionsOrAutomaticFeedbackEnabledElseThrow(programmingExercise);

        var exportOptions = new RepositoryExportOptionsDTO();
        exportOptions.setAnonymizeRepository(true);
        exportOptions.setExportAllParticipants(false);
        exportOptions.setFilterLateSubmissions(true);
        exportOptions.setFilterLateSubmissionsDate(programmingExercise.getDueDate());
        exportOptions.setFilterLateSubmissionsIndividualDueDate(false); // Athena currently does not support individual due dates

        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }

        Path exportDir = fileService.getTemporaryUniqueSubfolderPath(repoDownloadClonePath, 15);
        Path zipFile = null;

        if (repositoryType == null) { // Export student repository
            var submission = programmingSubmissionRepository.findById(submissionId).orElseThrow();
            // Load participation with eager submissions
            var participation = programmingExerciseStudentParticipationRepository.findWithSubmissionsById(submission.getParticipation().getId()).getFirst();
            zipFile = programmingExerciseExportService.getRepositoryWithParticipation(programmingExercise, participation, exportOptions, exportDir, exportDir, true);
        }
        else {
            List<String> exportErrors = List.of();
            var exportFile = programmingExerciseExportService.exportInstructorRepositoryForExercise(programmingExercise.getId(), repositoryType, exportDir, exportDir,
                    exportErrors);
            if (exportFile.isPresent()) {
                zipFile = exportFile.get().toPath();
            }
        }

        if (zipFile == null) {
            throw new IOException("Failed to export repository");
        }

        return zipFile.toFile();
    }
}
