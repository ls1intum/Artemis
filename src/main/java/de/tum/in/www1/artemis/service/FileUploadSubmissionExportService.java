package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

@Service
public class FileUploadSubmissionExportService extends SubmissionExportService {

    private final ZipFileService zipFileService;

    public FileUploadSubmissionExportService(ExerciseRepository exerciseRepository, ZipFileService zipFileService, FileService fileService) {
        super(exerciseRepository, zipFileService, fileService);
        this.zipFileService = zipFileService;
    }

    @Override
    protected void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException {
        if (((FileUploadSubmission) submission).getFilePaths().isEmpty()) {
            throw new IOException("Cannot export submission " + submission.getId() + " for exercise " + exercise.getId() + " because no files exist.");
        }

        final var urlFilePaths = ((FileUploadSubmission) submission).getFilePaths();
        List<Path> filePaths = new ArrayList<>();

        for (String urlFilePath : urlFilePaths) {
            // we need to get the 'real' file path here, the submission only has the api url path
            String filePath = FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId());
            String[] apiFilePathParts = urlFilePath.replace("/", File.separator).split(Pattern.quote(File.separator));

            Path submissionPath = Path.of(filePath, apiFilePathParts[apiFilePathParts.length - 1]);
            filePaths.add(submissionPath);

            if (!submissionPath.toFile().exists()) { // throw if submission file does not exist
                throw new IOException("Cannot export submission " + submission.getId() + " because the uploaded file " + submissionPath + " doesn't exist.");
            }
        }

        zipFileService.createZipFile(file.toPath(), filePaths);
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        return ".zip";
    }
}
