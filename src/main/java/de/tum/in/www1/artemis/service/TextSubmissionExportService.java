package de.tum.in.www1.artemis.service;

import java.io.*;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

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
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
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

        File submissionExportFile = new File(submissionsFolderName, submissionFileName);

        if (!submissionExportFile.exists()) {
            submissionExportFile.createNewFile();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(submissionExportFile, StandardCharsets.UTF_8))) {
            writer.write(submission.getText());
        }
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        return ".txt";
    }
}
