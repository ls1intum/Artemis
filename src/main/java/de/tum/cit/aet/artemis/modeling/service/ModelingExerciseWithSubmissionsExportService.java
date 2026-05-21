package de.tum.cit.aet.artemis.modeling.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.service.ExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

/**
 * Service for exporting Modeling Exercises with the student submissions.
 */
@Conditional(ModelingEnabled.class)
@Lazy
@Service
public class ModelingExerciseWithSubmissionsExportService extends ExerciseWithSubmissionsExportService {

    public ModelingExerciseWithSubmissionsExportService(FileService fileService, ModelingSubmissionExportService modelingSubmissionExportService,
            MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        super(fileService, springMvcJacksonConverter, modelingSubmissionExportService);
    }

    /**
     * Exports the modeling exercise with the student submissions.
     *
     * @param exercise      the exercise that is exported
     * @param optionsDTO    the options that are used for the export
     * @param exportDir     the directory where the content of the export is stored
     * @param exportErrors  a list of errors that occurred during the export
     * @param reportEntries report entries that are added during the export
     * @return the path to the exported modeling exercise
     */
    public Path exportModelingExerciseWithSubmissions(ModelingExercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) {
        return exportExerciseWithSubmissions(exercise, optionsDTO, exportDir, exportErrors, reportEntries);
    }
}
