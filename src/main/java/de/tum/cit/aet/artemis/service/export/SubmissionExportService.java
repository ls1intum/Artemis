package de.tum.cit.aet.artemis.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.service.ExerciseDateService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.ZipFileService;
import de.tum.cit.aet.artemis.service.archival.ArchivalReportEntry;
import de.tum.cit.aet.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@Service
public abstract class SubmissionExportService {

    @Value("${artemis.submission-export-path}")
    private Path submissionExportPath;

    private static final int EXPORTED_SUBMISSIONS_DELETION_DELAY_IN_MINUTES = 30;

    private static final Logger log = LoggerFactory.getLogger(SubmissionExportService.class);

    private final ExerciseRepository exerciseRepository;

    private final ZipFileService zipFileService;

    private final FileService fileService;

    public SubmissionExportService(ExerciseRepository exerciseRepository, ZipFileService zipFileService, FileService fileService) {
        this.exerciseRepository = exerciseRepository;
        this.zipFileService = zipFileService;
        this.fileService = fileService;
    }

    /**
     * Exports student submission of an exercise to a zip file located in the submission exports folder.
     * The zip file is deleted automatically after 30 minutes. The function throws a bad request if the
     * export process fails.
     *
     * @param exerciseId              the id of the exercise to be exported
     * @param submissionExportOptions the options for the export
     * @return the zipped file with the exported submissions
     */
    public File exportStudentSubmissionsElseThrow(Long exerciseId, SubmissionExportOptionsDTO submissionExportOptions) {
        var zippedSubmissionsPaths = exportStudentSubmissions(exerciseId, submissionExportOptions);
        if (zippedSubmissionsPaths.isEmpty()) {
            throw new BadRequestAlertException("Failed to export student submissions.", "SubmissionExport", "noSubmissions");
        }
        return zippedSubmissionsPaths.getFirst().toFile();

    }

    /**
     * Exports student submission of an exercise to a zip file located in the submission exports folder.
     * The zip file is deleted automatically after 30 minutes.
     *
     * @param exerciseId              the id of the exercise to be exported
     * @param submissionExportOptions the options for the export
     * @return the zipped file with the exported submissions
     */
    public List<Path> exportStudentSubmissions(Long exerciseId, SubmissionExportOptionsDTO submissionExportOptions) {
        Path outputDir = fileService.getTemporaryUniqueSubfolderPath(submissionExportPath, EXPORTED_SUBMISSIONS_DELETION_DELAY_IN_MINUTES);
        return exportStudentSubmissions(exerciseId, submissionExportOptions, true, outputDir, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Exports student submissions to a zip file for an exercise.
     * <p>
     * The outputDir is used to store the zip file and temporary files used for zipping so make
     * sure to delete it if it's no longer used.
     *
     * @param exerciseId              the id of the exercise to be exported
     * @param submissionExportOptions the options for the export
     * @param zipSubmissions          true, if the submissions should be zipped
     * @param outputDir               directory to store the temporary files in
     * @param exportErrors            a list of errors for submissions that couldn't be exported and are not included in the file
     * @param reportData              a list of all exercises and their statistics
     * @return paths of the exported submissions
     */
    public List<Path> exportStudentSubmissions(Long exerciseId, SubmissionExportOptionsDTO submissionExportOptions, boolean zipSubmissions, Path outputDir,
            List<String> exportErrors, List<ArchivalReportEntry> reportData) {

        Optional<Exercise> exerciseOpt = exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exerciseId);

        if (exerciseOpt.isEmpty()) {
            return List.of();
        }

        Exercise exercise = exerciseOpt.get();

        // Select the participations that should be exported
        List<StudentParticipation> exportedStudentParticipations;

        if (submissionExportOptions.isExportAllParticipants()) {
            exportedStudentParticipations = new ArrayList<>(exercise.getStudentParticipations());
        }
        else {
            Set<String> participantIds = Arrays.stream(submissionExportOptions.getParticipantIdentifierList().split(",")).map(String::trim).collect(Collectors.toSet());

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

        return exportSubmissionsFromParticipationsOptionallyZipped(exercise, exportedStudentParticipations, enableFilterAfterDueDate, filterLateSubmissionsDate, zipSubmissions,
                outputDir, exportErrors, reportData);
    }

    /**
     * Creates a zip file from a list of participations for an exercise.
     * <p>
     * The outputDir is used to store the zip file and temporary files used for zipping so make
     * sure to delete it if it's no longer used.
     *
     * @param exercise                 the exercise in question
     * @param participations           a list of participations to include
     * @param enableFilterAfterDueDate true, if all submissions that have been submitted after the due date should not be included in the file
     * @param lateSubmissionFilter     an optional date filter for submissions
     * @param outputDir                directory to store the temporary files in
     * @param exportErrors             a list of errors for submissions that couldn't be exported and are not included in the file
     * @param reportData               a list of all exercises and their statistics
     * @return paths of the exported submissions
     */
    private List<Path> exportSubmissionsFromParticipationsOptionallyZipped(Exercise exercise, List<StudentParticipation> participations, boolean enableFilterAfterDueDate,
            @Nullable ZonedDateTime lateSubmissionFilter, boolean zipSubmissions, Path outputDir, List<String> exportErrors, List<ArchivalReportEntry> reportData) {

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        // Create unique name for directory
        String zipGroupName = course.getShortName() + "-" + exercise.getTitle() + "-" + exercise.getId();
        String cleanZipGroupName = FileService.sanitizeFilename(zipGroupName);
        String zipFileName = cleanZipGroupName + "-" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss")) + ".zip";

        // Create directory
        Path zipFilePath = Path.of(outputDir.toString(), zipFileName);

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
            Path submissionFilePath = outputDir.resolve(submissionFileName);

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
        reportData.add(
                new ArchivalReportEntry(exercise, FileService.sanitizeFilename(exercise.getTitle()), participations.size(), submissionFilePaths.size(), skippedEntries.intValue()));

        if (submissionFilePaths.isEmpty()) {
            return List.of();
        }

        // zip stores submissions
        if (zipSubmissions) {
            try {
                zipFileService.createZipFile(zipFilePath, submissionFilePaths);
                return List.of(zipFilePath);
            }
            catch (IOException e) {
                log.error("Failed to zip submissions for exercise {} to {}: {}", exercise.getId(), zipFilePath, e);
                return List.of();
            }
            finally {
                log.debug("Delete all temporary files");
                fileService.deleteFiles(submissionFilePaths);
            }
        }
        else {
            return submissionFilePaths;
        }
    }

    /**
     * Finds the latest submission for the given participation while optionally ignoring all submissions after a given date.
     *
     * @param participation            for which the latest submission should be returned.
     * @param enableFilterAfterDueDate true, if all submissions that have been submitted after the due date should not be included in the file.
     * @param lateSubmissionFilter     an optional date filter for submissions.
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
            boolean isSubmittedBeforeDueDate = ExerciseDateService.getDueDate(participation).map(dueDate -> submission.getSubmissionDate().isBefore(dueDate)).orElse(true);
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
