package de.tum.in.www1.artemis.service.export;

import java.nio.file.Path;
import java.util.List;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.archival.ArchivalReportEntry;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;

/**
 * Service for exporting Text Exercises with the student submissions.
 */
@Service
public class TextExerciseWithSubmissionsExportService extends ExerciseWithSubmissionsExportService {

    public TextExerciseWithSubmissionsExportService(FileService fileService, TextSubmissionExportService textSubmissionExportService,
            MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        super(fileService, springMvcJacksonConverter, textSubmissionExportService);
    }

    /**
     * Exports the text exercise with the student submissions.
     *
     * @param exercise      the exercise that is exported
     * @param optionsDTO    the options that are used for the export
     * @param exportDir     vthe directory where the content of the export is stored
     * @param exportErrors  a list of errors that occurred during the export
     * @param reportEntries report entries that are added during the export
     * @return the path to the exported text exercise
     */
    public Path exportTextExerciseWithSubmissions(Exercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) {
        return exportExerciseWithSubmissions(exercise, optionsDTO, exportDir, exportErrors, reportEntries);
    }
}
