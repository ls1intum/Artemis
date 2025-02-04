package de.tum.cit.aet.artemis.fileupload.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Path;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadExerciseWithSubmissionsExportService;

@Profile(PROFILE_CORE)
@Controller
public class FileSubmissionExportApi {

    private final FileUploadExerciseWithSubmissionsExportService fileUploadExerciseWithSubmissionsExportService;

    public FileSubmissionExportApi(FileUploadExerciseWithSubmissionsExportService fileUploadExerciseWithSubmissionsExportService) {
        this.fileUploadExerciseWithSubmissionsExportService = fileUploadExerciseWithSubmissionsExportService;
    }

    public Path exportFileUploadExerciseWithSubmissions(FileUploadExercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) {
        return fileUploadExerciseWithSubmissionsExportService.exportFileUploadExerciseWithSubmissions(exercise, optionsDTO, exportDir, exportErrors, reportEntries);

    }
}
