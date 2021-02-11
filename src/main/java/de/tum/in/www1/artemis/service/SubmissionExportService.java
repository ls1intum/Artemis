package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;

@Service
public abstract class SubmissionExportService {

    private final Logger log = LoggerFactory.getLogger(SubmissionExportService.class);

    private final ExerciseRepository exerciseRepository;

    private final ZipFileService zipFileService;

    private final FileService fileService;

    public SubmissionExportService(ExerciseRepository exerciseRepository, ZipFileService zipFileService, FileService fileService) {
        this.exerciseRepository = exerciseRepository;
        this.zipFileService = zipFileService;
        this.fileService = fileService;
    }

    @Value("${artemis.submission-export-path}")
    private String submissionExportPath;

    /**
     * Exports student submissions to a zip file for an exercise
     * @param exerciseId the id of the exercise to be exported
     * @param submissionExportOptions the options for the export
     * @return a reference to the zipped file
     * @throws IOException if an error occurred while zipping
     */
    public Optional<File> exportStudentSubmissions(Long exerciseId, SubmissionExportOptionsDTO submissionExportOptions) throws IOException {

        Optional<Exercise> exerciseOpt = exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exerciseId);

        if (exerciseOpt.isEmpty()) {
            return Optional.empty();
        }

        Exercise exercise = exerciseOpt.get();

        // Select the participations that should be exported
        List<StudentParticipation> exportedStudentParticipations;

        if (submissionExportOptions.isExportAllParticipants()) {
            exportedStudentParticipations = new ArrayList<>(exercise.getStudentParticipations());
        }
        else {
            List<String> participantIds = Arrays.stream(submissionExportOptions.getParticipantIdentifierList().split(",")).map(String::trim).collect(Collectors.toList());

            exportedStudentParticipations = exercise.getStudentParticipations().stream().filter(participation -> participantIds.contains(participation.getParticipantIdentifier()))
                    .collect(Collectors.toList());
        }

        if (exportedStudentParticipations.isEmpty()) {
            return Optional.empty();
        }

        ZonedDateTime filterLateSubmissionsDate = null;
        if (submissionExportOptions.isFilterLateSubmissions()) {
            if (submissionExportOptions.getFilterLateSubmissionsDate() == null) {
                filterLateSubmissionsDate = exercise.getDueDate();
            }
            else {
                filterLateSubmissionsDate = submissionExportOptions.getFilterLateSubmissionsDate();
            }
        }

        return this.createZipFileFromParticipations(exercise, exportedStudentParticipations, filterLateSubmissionsDate);

    }

    /**
     * Creates a zip file from a list of participations for an exercise
     * @param exercise the exercise in question
     * @param participations a list of participations to include
     * @param lateSubmissionFilter an optional date filter for submissions
     * @return the zipped file
     * @throws IOException if an error occurred while zipping
     */
    private Optional<File> createZipFileFromParticipations(Exercise exercise, List<StudentParticipation> participations, @Nullable ZonedDateTime lateSubmissionFilter)
            throws IOException {

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        String zipGroupName = course.getShortName() + "-" + exercise.getTitle();
        String zipFileName = zipGroupName + "-" + ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-Hms")) + ".zip";

        Path submissionsFolderPath = Paths.get(submissionExportPath, "zippedSubmissions", zipGroupName);
        Path zipFilePath = Paths.get(submissionExportPath, "zippedSubmissions", zipFileName);

        File submissionFolder = submissionsFolderPath.toFile();
        if (!submissionFolder.exists() && !submissionFolder.mkdirs()) {
            log.error("Couldn't create dir: " + submissionFolder);
            return Optional.empty();
        }

        // Save all Submissions
        List<Path> submissionFilePaths = participations.stream().map((participation) -> {

            Set<Submission> submissions = participation.getSubmissions();
            Submission latestSubmission = null;

            for (Submission submission : submissions) {
                if (submission.getSubmissionDate() == null) {
                    // ignore unsubmitted submissions
                    continue;
                }
                if (lateSubmissionFilter == null || submission.getSubmissionDate().isBefore(lateSubmissionFilter)) {
                    if (latestSubmission == null || submission.getSubmissionDate().isAfter(latestSubmission.getSubmissionDate())) {
                        latestSubmission = submission;
                    }
                }
            }

            if (latestSubmission == null) {
                return Optional.<Path>empty();
            }

            String submissionFileName = exercise.getTitle() + "-" + participation.getParticipantIdentifier() + "-" + latestSubmission.getId()
                    + this.getFileEndingForSubmission(latestSubmission);
            Path submissionFilePath = Paths.get(submissionsFolderPath.toString(), submissionFileName);

            try {
                this.saveSubmissionToFile(exercise, latestSubmission, submissionFilePath.toFile());
                return Optional.of(submissionFilePath);
            }
            catch (IOException ioException) {
                log.error("Could not create file " + submissionFilePath.toString() + " for exporting: " + ioException.getMessage());
                return Optional.<Path>empty();
            }

        }).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

        if (submissionFilePaths.isEmpty()) {
            return Optional.empty();
        }

        try {
            zipFileService.createZipFile(zipFilePath, submissionFilePaths, submissionsFolderPath);
        }
        finally {
            deleteTempFiles(submissionFilePaths);
        }

        fileService.scheduleForDeletion(zipFilePath, 5);

        return Optional.of(zipFilePath.toFile());
    }

    protected abstract void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException;

    protected abstract String getFileEndingForSubmission(Submission submission);

    /**
     * Delete all temporary files created during export
     *
     * @param pathsToTempFiles A list of all paths to temporary files, that should be deleted
     */
    private void deleteTempFiles(List<Path> pathsToTempFiles) {
        log.debug("Delete all temporary files");
        // delete the temporary zipped repo files
        for (Path tempFile : pathsToTempFiles) {
            try {
                Files.delete(tempFile);
            }
            catch (Exception ex) {
                log.warn("Could not delete file " + tempFile + ". Error message: " + ex.getMessage());
            }
        }
    }
}
