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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    public SubmissionExportService(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @Value("${artemis.submission-export-path}")
    private String SUBMISSION_EXPORT_PATH;

    /**
     * Exports student submissions to a zip file for an exercise
     * @param exerciseId the id of the exercise to be exported
     * @param submissionExportOptions the options for the expot
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

        String zipGroupName = course.getTitle() + "-" + exercise.getTitle() + "-submissions";
        String zipFileName = zipGroupName + "-" + ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT) + ".zip";

        Path submissionsFolderPath = Paths.get(SUBMISSION_EXPORT_PATH, "zippedSubmissions", zipGroupName);
        Path zipFilePath = Paths.get(SUBMISSION_EXPORT_PATH, "zippedSubmissions", zipFileName);

        File submissionFolder = submissionsFolderPath.toFile();
        if (!submissionFolder.exists() && !submissionFolder.mkdirs()) {
            log.error("Couldn't create dir: " + submissionFolder);
            return Optional.empty();
        }

        // Save all Submissions
        List<Path> submissionFilePaths = participations.stream().map((participation) -> {

            Set<Submission> submissions = participation.getSubmissions();
            Submission latestSubmission = null;

            for (Submission s : submissions) {
                if (lateSubmissionFilter == null || s.getSubmissionDate().isBefore(lateSubmissionFilter)) {
                    if (latestSubmission == null || s.getSubmissionDate().isAfter(latestSubmission.getSubmissionDate())) {
                        latestSubmission = s;
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
            createZipFile(zipFilePath, submissionFilePaths, submissionsFolderPath);
        }
        finally {
            deleteTempFiles(submissionFilePaths);
        }

        scheduleForDeletion(zipFilePath, 15);

        return Optional.of(zipFilePath.toFile());
    }

    protected abstract void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException;

    protected abstract String getFileEndingForSubmission(Submission submission);

    /**
     * Create a zipfile of the given paths and save it in the zipFilePath
     *
     * @param zipFilePath path where the zip file should be saved
     * @param paths the paths that should be zipped
     * @param pathsRoot the root path relative to <code>paths</code>
     * @throws IOException if an error occurred while zipping
     */
    private void createZipFile(Path zipFilePath, List<Path> paths, Path pathsRoot) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            paths.stream().filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(pathsRoot.relativize(path).toString());
                try {
                    zipOutputStream.putNextEntry(zipEntry);
                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
                catch (Exception e) {
                    log.error("Create zip file error", e);
                }
            });
        }
    }

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

    private Map<Path, ScheduledFuture> futures = new HashMap<>();

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private static final TimeUnit MINUTES = TimeUnit.MINUTES; // your time unit

    /**
     * Schedule the deletion of the given path with a given delay
     *
     * @param path The path that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    private void scheduleForDeletion(Path path, long delayInMinutes) {
        ScheduledFuture future = executor.schedule(() -> {
            try {
                log.info("Delete file " + path);
                Files.delete(path);
                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the file " + path + " did not work", e);
            }
        }, delayInMinutes, MINUTES);

        futures.put(path, future);
    }

}
