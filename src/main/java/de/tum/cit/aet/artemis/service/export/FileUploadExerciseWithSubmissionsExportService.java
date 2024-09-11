package de.tum.cit.aet.artemis.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Path;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.archival.ArchivalReportEntry;
import de.tum.cit.aet.artemis.web.rest.dto.SubmissionExportOptionsDTO;

/**
 * Service for exporting File Upload Exercises with the student submissions.
 */
@Profile(PROFILE_CORE)
@Service
public class FileUploadExerciseWithSubmissionsExportService extends ExerciseWithSubmissionsExportService {

    public FileUploadExerciseWithSubmissionsExportService(FileService fileService, FileUploadSubmissionExportService fileUploadSubmissionExportService,
            MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        super(fileService, springMvcJacksonConverter, fileUploadSubmissionExportService);
    }

    /**
     * Exports the file upload exercise with the student submissions.
     *
     * @param exercise      the exercise that is exported
     * @param optionsDTO    the options that are used for the export
     * @param exportDir     the directory where the content of the export is stored
     * @param exportErrors  a list of errors that occurred during the export
     * @param reportEntries report entries that are added during the export
     * @return the path to the exported file upload exercise
     */
    public Path exportFileUploadExerciseWithSubmissions(FileUploadExercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) {
        return exportExerciseWithSubmissions(exercise, optionsDTO, exportDir, exportErrors, reportEntries);
    }
}
