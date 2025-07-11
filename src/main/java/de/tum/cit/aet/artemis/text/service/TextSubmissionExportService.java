package de.tum.cit.aet.artemis.text.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionExportService;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Conditional(TextEnabled.class)
@Lazy
@Service
public class TextSubmissionExportService extends SubmissionExportService {

    public TextSubmissionExportService(ExerciseRepository exerciseRepository, ZipFileService zipFileService, FileService fileService) {
        super(exerciseRepository, zipFileService, fileService);
    }

    @Override
    protected void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException {
        if (((TextSubmission) submission).getText() == null) {
            if (!file.exists()) {
                file.createNewFile(); // create empty file if submission is empty
            }
        }
        else {
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                writer.write(((TextSubmission) submission).getText());
            }
        }
    }

    /**
     * Save the content of the given TextSubmission to a file.
     *
     * @param submission            that will be saved to a file
     * @param studentLogin          of the given submission
     * @param submissionsFolderName base folder name to save the file to
     */
    public void saveSubmissionToFile(TextSubmission submission, String studentLogin, String submissionsFolderName) throws IOException {
        String submissionFileName = String.format("%s-%s%s", submission.getId(), studentLogin, this.getFileEndingForSubmission(submission));
        Path submissionPath = Path.of(submissionsFolderName, submissionFileName);

        if (!Files.exists(submissionPath)) {
            Files.createFile(submissionPath);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(submissionPath, StandardCharsets.UTF_8)) {
            writer.write(submission.getText());
        }
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        return ".txt";
    }
}
