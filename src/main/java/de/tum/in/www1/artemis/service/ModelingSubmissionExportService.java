package de.tum.in.www1.artemis.service;

import java.io.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

@Service
public class ModelingSubmissionExportService extends SubmissionExportService {

    public ModelingSubmissionExportService(ExerciseRepository exerciseRepository, ExerciseDateService exerciseDateService, ZipFileService zipFileService, FileService fileService) {
        super(exerciseRepository, exerciseDateService, zipFileService, fileService);
    }

    @Override
    protected void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException {
        if (((ModelingSubmission) submission).getModel() == null) {
            if (!file.exists()) {
                file.createNewFile(); // create empty file if submission is empty
            }
        }
        else {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(((ModelingSubmission) submission).getModel()); // TODO: save explanation text
            writer.close();
        }
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        return ".json";
    }
}
