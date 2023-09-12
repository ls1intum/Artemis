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
public class FileUploadExerciseWithSubmissionsExportService extends ExerciseExportService {

    private final FileUploadSubmissionExportService fileUploadSubmissionExportService;

    protected FileUploadExerciseWithSubmissionsExportService(FileService fileService, FileUploadSubmissionExportService fileUploadSubmissionExportService,
            MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        super(fileService, springMvcJacksonConverter);
        this.fileUploadSubmissionExportService = fileUploadSubmissionExportService;
    }

    public Path exportFileUploadExerciseWithSubmissions(Exercise exercise, SubmissionExportOptionsDTO optionsDTO, Path exportDir, List<String> exportErrors,
            List<ArchivalReportEntry> reportEntries) throws IOException {
        List<Path> pathsToBeZipped = new ArrayList<>();
        super.exportProblemStatementAndEmbeddedFilesAndExerciseDetails(exercise, exportErrors, exportDir, pathsToBeZipped);
        fileUploadSubmissionExportService.exportStudentSubmissions(exercise.getId(), optionsDTO, false, exportDir, exportErrors, reportEntries);
        return exportDir;
    }
}
