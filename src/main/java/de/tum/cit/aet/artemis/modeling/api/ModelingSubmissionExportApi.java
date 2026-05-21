package de.tum.cit.aet.artemis.modeling.api;

import java.nio.file.Path;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseWithSubmissionsExportService;

/**
 * API for exporting modeling submissions.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
public class ModelingSubmissionExportApi extends AbstractModelingApi {

    private final ModelingExerciseWithSubmissionsExportService modelingExerciseWithSubmissionsExportService;

    public ModelingSubmissionExportApi(ModelingExerciseWithSubmissionsExportService modelingExerciseWithSubmissionsExportService) {
        this.modelingExerciseWithSubmissionsExportService = modelingExerciseWithSubmissionsExportService;
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
        return modelingExerciseWithSubmissionsExportService.exportModelingExerciseWithSubmissions(exercise, optionsDTO, exportDir, exportErrors, reportEntries);
    }
}
