package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import de.tum.in.www1.artemis.domain.Exercise;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

@Service
public class FileUploadSubmissionExportService extends SubmissionExportService {

    public FileUploadSubmissionExportService(ExerciseRepository exerciseRepository) {
        super(exerciseRepository);
    }

    @Override
    protected void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException {
        // we need to get the 'real' file path here, the submission only has the api url path
        String filePath = FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId());
        String[] apiFilePathParts = ((FileUploadSubmission) submission).getFilePath().split("/");

        Files.copy(Path.of(filePath, apiFilePathParts[apiFilePathParts.length-1]), file.toPath());
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        String[] parts = ((FileUploadSubmission) submission).getFilePath().split("/");
        String fileName = parts[parts.length - 1];
        int endingIndex = fileName.indexOf(".");
        return fileName.substring(endingIndex);
    }
}
