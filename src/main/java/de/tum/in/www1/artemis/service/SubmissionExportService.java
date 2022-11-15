package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.archival.ArchivalReportEntry;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public abstract class SubmissionExportService {

    @Value("${artemis.submission-export-path}")
    private String submissionExportPath;

    private final static int EXPORTED_SUBMISSIONS_DELETION_DELAY_IN_MINUTES = 30;

    private final Logger log = LoggerFactory.getLogger(SubmissionExportService.class);

    private final ExerciseRepository exerciseRepository;

    private final ExerciseDateService exerciseDateService;

    private final ZipFileService zipFileService;

    private final FileService fileService;

    public SubmissionExportService(ExerciseRepository exerciseRepository, ExerciseDateService exerciseDateService, ZipFileService zipFileService, FileService fileService) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseDateService = exerciseDateService;
        this.zipFileService = zipFileService;
        this.fileService = fileService;
    }

    /**
     * Exports student submission of an exercise to a zip file located in the submission exports folder.
     * The zip file is deleted automatically after 30 minutes. The function throws a bad request if the
     * export process fails.
     *
     * @param exerciseId the id   of the exercise to be exported
     * @param submissionExportOptions the options for the export
     * @return the zipped file with the exported submissions
     */
    public File exportStudentSubmissionsElseThrow(Long exerciseId, SubmissionExportOptionsDTO submissionExportOptions) {
        return exportStudentSubmissions(exerciseId, submissionExportOptions)
                .orElseThrow(() -> new BadRequestAlertException("Failed to export student submissions.", "SubmissionExport", "noSubmissions"));
    }

    /**
     * Exports student submission of an exercise to a zip file located in the submission exports folder.
     * The zip file is deleted automatically after 30 minutes.
     *
     * @param exerciseId the id   of the exercise to be exported
     * @param submissionExportOptions the options for the export
     * @return the zipped file with the exported submissions
     */
    public Optional<File> exportStudentSubmissions(Long exerciseId, SubmissionExportOptionsDTO submissionExportOptions) {
        Path outputDir = Path.of(fileService.getUniquePathString(submissionExportPath));
        try {
            return exportStudentSubmissions(exerciseId, submissionExportOptions, outputDir, new ArrayList<>(), new ArrayList<>());
        }
        catch (IOException e) {
            log.error("Failed to export student submissions for exercise {} to {}: {}", exerciseId, outputDir, e);
            return Optional.empty();
        }
        finally {
            fileService.scheduleForDirectoryDeletion(outputDir, EXPORTED_SUBMISSIONS_DELETION_DELAY_IN_MINUTES);
        }
    }

    /**
     * Exports student submissions to a zip file for an exercise.
     *
     * The outputDir is used to store the zip file and temporary files used for zipping so make
     * sure to delete it if it's no longer used.
     *
     * @param exerciseId the id of the exercise to be exported
     * @param submissionExportOptions the options for the export
     * @param outputDir directory to store the temporary files in
     * @param exportErrors a list of errors for submissions that couldn't be exported and are not included in the file
     * @param reportData   a list of all exercises and their statistics
     * @return a reference to the zipped file
     * @throws IOException if an error occurred while zipping
     */
    public Optional<File> exportStudentSubmissions(Long exerciseId, SubmissionExportOptionsDTO submissionExportOptions, Path outputDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportData) throws IOException {

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
            List<String> participantIds = Arrays.stream(submissionExportOptions.getParticipantIdentifierList().split(",")).map(String::trim).toList();

            exportedStudentParticipations = exercise.getStudentParticipations().stream().filter(participation -> participantIds.contains(participation.getParticipantIdentifier()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        boolean enableFilterAfterDueDate = false;
        ZonedDateTime filterLateSubmissionsDate = null;
        if (submissionExportOptions.isFilterLateSubmissions()) {
            if (submissionExportOptions.getFilterLateSubmissionsDate() == null) {
                enableFilterAfterDueDate = true;
            }
            else {
                filterLateSubmissionsDate = submissionExportOptions.getFilterLateSubmissionsDate();
            }
        }

        // Sort the student participations by id
        exportedStudentParticipations.sort(Comparator.comparing(DomainObject::getId));

        return createZipFileFromParticipations(exercise, exportedStudentParticipations, enableFilterAfterDueDate, filterLateSubmissionsDate, outputDir, exportErrors, reportData);
    }

    /**
     * Creates a zip file from a list of participations for an exercise.
     *
     * The outputDir is used to store the zip file and temporary files used for zipping so make
     * sure to delete it if it's no longer used.
     *
     * @param exercise the exercise in question
     * @param participations a list of participations to include
     * @param enableFilterAfterDueDate true, if all submissions that have been submitted after the due date should not be included in the file
     * @param lateSubmissionFilter an optional date filter for submissions
     * @param outputDir directory to store the temporary files in
     * @param exportErrors a list of errors for submissions that couldn't be exported and are not included in the file
     * @param reportData   a list of all exercises and their statistics
     * @return the zipped file
     * @throws IOException if an error occurred while zipping
     */
    private Optional<File> createZipFileFromParticipations(Exercise exercise, List<StudentParticipation> participations, boolean enableFilterAfterDueDate,
            @Nullable ZonedDateTime lateSubmissionFilter, Path outputDir, List<String> exportErrors, List<ArchivalReportEntry> reportData) throws IOException {

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        // Create unique name for directory
        String zipGroupName = course.getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId();
        String cleanZipGroupName = fileService.removeIllegalCharacters(zipGroupName);
        String zipFileName = cleanZipGroupName + "-" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss")) + ".zip";

        // Create directory
        Path submissionsFolderPath = Path.of(outputDir.toString(), "zippedSubmissions", zipGroupName);
        Path zipFilePath = Path.of(outputDir.toString(), "zippedSubmissions", zipFileName);

        File submissionFolder = submissionsFolderPath.toFile();
        if (!submissionFolder.exists() && !submissionFolder.mkdirs()) {
            log.error("Couldn't create dir: {}", submissionFolder);
            exportErrors.add("Cannot create directory: " + submissionFolder.toPath());
            return Optional.empty();
        }

        // Create counter for log entry
        MutableInt skippedEntries = new MutableInt();

        // Save all Submissions
        List<Path> submissionFilePaths = participations.stream().map(participation -> {
            Submission latestSubmission = latestSubmission(participation, enableFilterAfterDueDate, lateSubmissionFilter);
            if (latestSubmission == null) {
                skippedEntries.increment();
                return Optional.<Path>empty();
            }

            // create file path
            String submissionFileName = exercise.getTitle() + "-" + participation.getParticipantIdentifier() + "-" + latestSubmission.getId()
                    + this.getFileEndingForSubmission(latestSubmission);
            Path submissionFilePath = Path.of(submissionsFolderPath.toString(), submissionFileName);

            // store file
            try {
                this.saveSubmissionToFile(exercise, latestSubmission, submissionFilePath.toFile());
                return Optional.of(submissionFilePath);
            }
            catch (Exception ex) {
                String message = "Could not create file " + submissionFilePath + "  for exporting: " + ex.getMessage();
                log.error(message, ex);
                exportErrors.add(message);
                return Optional.<Path>empty();
            }
        }).flatMap(Optional::stream).toList();

        // Add report entry
        reportData.add(new ArchivalReportEntry(exercise, fileService.removeIllegalCharacters(exercise.getTitle()), participations.size(), submissionFilePaths.size(),
                skippedEntries.intValue()));

        if (submissionFilePaths.isEmpty()) {
            return Optional.empty();
        }

        // zip stores submissions
        try {
            zipFileService.createZipFile(zipFilePath, submissionFilePaths, submissionsFolderPath);
        }
        finally {
            log.debug("Delete all temporary files");
            fileService.deleteFiles(submissionFilePaths);
        }

        return Optional.of(zipFilePath.toFile());
    }

    /**
     * Finds the latest submission for the given participation while optionally ignoring all submissions after a given date.
     *
     * @param participation for which the latest submission should be returned.
     * @param enableFilterAfterDueDate true, if all submissions that have been submitted after the due date should not be included in the file.
     * @param lateSubmissionFilter an optional date filter for submissions.
     * @return the latest submission of the given participation.
     */
    private Submission latestSubmission(final StudentParticipation participation, boolean enableFilterAfterDueDate, @Nullable ZonedDateTime lateSubmissionFilter) {
        Submission latestSubmission = null;

        for (Submission submission : participation.getSubmissions()) {
            if (submission.getSubmissionDate() == null) {
                // ignore unsubmitted submissions
                continue;
            }
            // filter late submissions
            boolean isSubmittedBeforeDueDate = exerciseDateService.getDueDate(participation).map(dueDate -> submission.getSubmissionDate().isBefore(dueDate)).orElse(true);
            if ((enableFilterAfterDueDate && isSubmittedBeforeDueDate) || lateSubmissionFilter == null || submission.getSubmissionDate().isBefore(lateSubmissionFilter)) {
                if (latestSubmission == null || submission.getSubmissionDate().isAfter(latestSubmission.getSubmissionDate())) {
                    latestSubmission = submission;
                }
            }
        }

        return latestSubmission;
    }

    protected abstract void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException;

    protected abstract String getFileEndingForSubmission(Submission submission);

}
