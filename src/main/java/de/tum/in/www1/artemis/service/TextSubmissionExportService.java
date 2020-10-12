package de.tum.in.www1.artemis.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.text.TextExercise;
import de.tum.in.www1.artemis.domain.text.TextSubmission;
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
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(((TextSubmission) submission).getText());
            writer.close();
        }
    }

    /**
     * Save the content of the given TextSubmission to a file.
     *
     * @param exercise the submission belongs to
     * @param submission that will be saved to a file
     * @param submissionsFolderName base folder name to save the file to
     * @throws IOException
     */
    public void saveSubmissionToFile(TextExercise exercise, TextSubmission submission, String submissionsFolderName) throws IOException {
        String submissionFileName = String.format("%s-Submission-%s%s", exercise.getTitle(), submission.getId(), this.getFileEndingForSubmission(submission));

        File submissionExportFile = new File(submissionsFolderName, submissionFileName);

        if (!submissionExportFile.exists()) {
            submissionExportFile.createNewFile();
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(submissionExportFile));
        writer.write(submission.getText());
        writer.close();
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        return ".txt";
    }
}
