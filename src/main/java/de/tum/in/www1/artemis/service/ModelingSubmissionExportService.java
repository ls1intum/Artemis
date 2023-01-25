package de.tum.in.www1.artemis.service;

import java.io.*;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

@Service
public class ModelingSubmissionExportService extends SubmissionExportService {

    public ModelingSubmissionExportService(ExerciseRepository exerciseRepository, ZipFileService zipFileService, FileService fileService) {
        super(exerciseRepository, zipFileService, fileService);
    }

    @Override
    protected void saveSubmissionToFiles(Exercise exercise, Submission submission, File[] files) throws IOException {
        if (((ModelingSubmission) submission).getModel() == null) {
            if (!files[0].exists()) {
                files[0].createNewFile(); // create empty file if submission is empty
            }
        }
        else {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(files[0], StandardCharsets.UTF_8))) {
                writer.write(((ModelingSubmission) submission).getModel()); // TODO: save explanation text
            }
        }
    }

    @Override
    protected String[] getFileEndingsForSubmission(Submission submission) {
        return new String[] { ".json" };
    }
}
