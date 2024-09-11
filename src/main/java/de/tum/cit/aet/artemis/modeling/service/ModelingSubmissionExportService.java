package de.tum.cit.aet.artemis.modeling.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionExportService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;

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
