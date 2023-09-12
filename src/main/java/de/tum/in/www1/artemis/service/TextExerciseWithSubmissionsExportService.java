package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.service.archival.ArchivalReportEntry;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;

@Service
public class TextExerciseWithSubmissionsExportService extends ExerciseExportService {

    private final TextSubmissionExportService textSubmissionExportService;

    protected TextExerciseWithSubmissionsExportService(FileService fileService, TextSubmissionExportService textSubmissionExportService,
            MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        super(fileService, springMvcJacksonConverter);
        this.textSubmissionExportService = textSubmissionExportService;
    }

    public Path exportTextExerciseWithSubmissions(Exercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) throws IOException {
        List<Path> pathsToBeZipped = new ArrayList<>();
        super.exportProblemStatementAndEmbeddedFilesAndExerciseDetails(exercise, exportErrors, exportDir, pathsToBeZipped);
        textSubmissionExportService.exportStudentSubmissions(exercise.getId(), optionsDTO, false, exportDir, exportErrors, reportEntries);
        return exportDir;
    }
}
