package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

@Service
public class FileUploadSubmissionExportService extends SubmissionExportService {

    public FileUploadSubmissionExportService(ExerciseRepository exerciseRepository, ZipFileService zipFileService, FileService fileService) {
        super(exerciseRepository, zipFileService, fileService);
    }

    @Override
    protected void saveSubmissionToFiles(Exercise exercise, Submission submission, File[] files) throws IOException {

        if (((FileUploadSubmission) submission).getFilePaths() == null) {
            throw new IOException("Cannot export submission " + submission.getId() + " for exercise " + exercise.getId() + " because the file path is null.");
        }

        // we need to get the 'real' file path here, the submission only has the api url path
        String filePath = FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId());

        var apiPaths = ((FileUploadSubmission) submission).getFilePaths();
        for (int i = 0; i < apiPaths.size(); i++) {
            String apiPath = apiPaths.get(i);
            String[] apiFilePathParts = apiPath.split(Pattern.quote(File.separator));

            Path submissionPath = Path.of(filePath, apiFilePathParts[apiFilePathParts.length - 1]);

            if (!submissionPath.toFile().exists()) { // throw if submission file does not exist
                throw new IOException("Cannot export submission " + submission.getId() + " because the uploaded file " + submissionPath + " doesn't exist.");
            }

            Files.copy(submissionPath, files[i].toPath());
        }
    }

    @Override
    protected String[] getFileEndingsForSubmission(Submission submission) {
        var filePaths = ((FileUploadSubmission) submission).getFilePaths();

        if (filePaths == null) {
            return null; // submission will be ignored by saveSubmissionToFile
        }
        else {
            String[] fileEndings = new String[filePaths.size()];

            for (int i = 0; i < filePaths.size(); i++) {
                String[] parts = filePaths.get(i).split(Pattern.quote(File.separator));
                String fileName = parts[parts.length - 1];
                int endingIndex = fileName.indexOf(".");
                fileEndings[i] = fileName.substring(endingIndex);
            }

            return fileEndings;
        }
    }
}
