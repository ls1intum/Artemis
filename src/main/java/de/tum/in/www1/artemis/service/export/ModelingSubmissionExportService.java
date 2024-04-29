package de.tum.in.www1.artemis.service.export;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ZipFileService;

@Profile(PROFILE_CORE)
@Service
public class ModelingSubmissionExportService extends SubmissionExportService {

    public ModelingSubmissionExportService(ExerciseRepository exerciseRepository, ZipFileService zipFileService, FileService fileService) {
        super(exerciseRepository, zipFileService, fileService);
    }

    @Override
    protected void saveSubmissionToFile(Exercise exercise, Submission submission, File file) throws IOException {
        if (((ModelingSubmission) submission).getModel() == null) {
            if (!file.exists()) {
                file.createNewFile(); // create empty file if submission is empty
            }
        }
        else {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
                writer.write(((ModelingSubmission) submission).getModel()); // TODO: save explanation text
            }
        }
    }

    @Override
    protected String getFileEndingForSubmission(Submission submission) {
        return ".json";
    }
}
