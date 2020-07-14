package de.tum.in.www1.artemis.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

@Service
public class TextSubmissionExportService extends SubmissionExportService {

    public TextSubmissionExportService(ExerciseRepository exerciseRepository) {
        super(exerciseRepository);
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

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        return ".txt";
    }
}
