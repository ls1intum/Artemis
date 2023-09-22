package de.tum.in.www1.artemis.service.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ZipFileService;

@Service
public class FileUploadSubmissionExportService extends SubmissionExportService {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionExportService.class);

    public FileUploadSubmissionExportService(ExerciseRepository exerciseRepository, ZipFileService zipFileService, FileService fileService) {
        super(exerciseRepository, zipFileService, fileService);
    }

    @Override
    protected void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException {

        if (((FileUploadSubmission) submission).getFilePath() == null) {
            throw new IOException("Cannot export submission " + submission.getId() + " for exercise " + exercise.getId() + " because the file path is null.");
        }

        // we need to get the 'real' file path here, the submission only has the api url path
        Path filePath = FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId());

        if (!Files.exists(filePath)) { // throw if submission file does not exist
            throw new IOException("Cannot export submission " + submission.getId() + " because the uploaded file " + filePath + " doesn't exist.");
        }

        try (var files = Files.list(filePath)) {
            files.forEach(content -> {
                try {
                    FileUtils.copyFile(content.toFile(), file);
                }
                catch (IOException e) {
                    log.error("Failed to copy file {} to zip file", content, e);
                }
            });
        }
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        if (((FileUploadSubmission) submission).getFilePath() == null) {
            return ""; // submission will be ignored by saveSubmissionToFile
        }
        else {
            String[] parts = ((FileUploadSubmission) submission).getFilePath().split(Pattern.quote(File.separator));
            String fileName = parts[parts.length - 1];
            int endingIndex = fileName.indexOf(".");
            return fileName.substring(endingIndex);
        }
    }
}
