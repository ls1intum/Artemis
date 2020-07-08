package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    protected void saveSubmissionToFile(Submission submission, File file) throws IOException {
        Files.copy(Path.of(((FileUploadSubmission) submission).getFilePath()), file.toPath());
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        String[] parts = ((FileUploadSubmission) submission).getFilePath().split("/");
        String fileName = parts[parts.length - 1];
        int endingIndex = fileName.indexOf(".");
        return fileName.substring(endingIndex);
    }
}
