package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Repository
public class TextExerciseExportService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseExportService.class);

    private final TextExerciseRepository textExerciseRepository;

    private final SubmissionRepository submissionRepository;

    public TextExerciseExportService(
        TextExerciseRepository textExerciseRepository,
        SubmissionRepository submissionRepository) {
        this.textExerciseRepository = textExerciseRepository;
        this.submissionRepository = submissionRepository;
    }

    @Value("${artemis.submission-export-path}")
    private String SUBMISSION_EXPORT_PATH;

    public File exportStudentSubmissions(Long exerciseId, List<StudentParticipation> participations, @Nullable ZonedDateTime lateSubmissionFilter) {

        TextExercise textExercise = textExerciseRepository.findById(exerciseId).get();
        Course course = textExercise.getCourseViaExerciseGroupOrCourseMember();

        String zipGroupName = course.getTitle()+"-"+textExercise.getTitle()+"-submissions";
        String zipFileName = zipGroupName+"-"+ZonedDateTime.now().toString()+".zip";

        Path submissionsFolderPath = Paths.get(SUBMISSION_EXPORT_PATH, "zippedTextExercises", zipGroupName);
        Path zipFilePath = Paths.get(SUBMISSION_EXPORT_PATH, "zippedTextExercises", zipFileName);

        // Save all Submissions
        List<Path> submissionFilePaths = participations.stream()
            .map((participation) -> {
                Optional<Submission> submission = submissionRepository.findLatestSubmissionByParticipationIdBefore(participation.getId(), lateSubmissionFilter);

                if (submission.isEmpty())
                    return Optional.<Path>empty();

                String submissionFileName = textExercise.getTitle() + "-" + participation.getParticipantIdentifier() + "-"
                    + submission.get().getId() + ".txt";
                Path submissionFilePath = Paths.get(submissionsFolderPath.toString(), submissionFileName);

                try {

                    saveSubmissionToFile((TextSubmission)submission.get(), submissionFilePath.toFile());
                    return Optional.of(submissionFilePath);

                } catch (IOException ioException) {
                    log.error("Could not create file " + submissionFilePath.toString() + "for exporting: " + ioException.getMessage());
                    return Optional.<Path>empty();
                }

            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        try {
            createZipFile(zipFilePath, submissionFilePaths);
            scheduleForDeletion(zipFilePath, 15);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            deleteTempFiles(submissionFilePaths);
        }

        return zipFilePath.toFile();
    }

    private void saveSubmissionToFile(TextSubmission submission, File file) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(submission.getText());
        writer.close();
    }
    
    /**
     * Create a zipfile of the given paths and save it in the zipFilePath
     *
     * @param zipFilePath path where the zip file should be saved
     * @param paths the paths that should be zipped
     * @throws IOException if an error occurred while zipping
     */
    private void createZipFile(Path zipFilePath, List<Path> paths) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            paths.stream().filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(path.toString());
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
