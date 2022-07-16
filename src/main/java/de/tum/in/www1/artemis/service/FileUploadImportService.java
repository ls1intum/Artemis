package de.tum.in.www1.artemis.service;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sun.istack.NotNull;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

@Service
public class FileUploadImportService extends ExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(TextExerciseImportService.class);

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    public FileUploadImportService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository);
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
    }

    /**
     * Imports a file upload exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyFileUploadExerciseBasis(FileUploadExercise)} to set up the basis of the exercise
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NotNull
    public FileUploadExercise importFileUploadExercise(final FileUploadExercise templateExercise, FileUploadExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        FileUploadExercise newExercise = copyFileUploadExerciseBasis(importedExercise);
        return fileUploadExerciseRepository.save(newExercise);
    }

    /** This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and assessment due date.
     *
     * @param importedExercise The exercise from which to copy the basis
     * @return the cloned TextExercise basis
     */
    @NotNull
    private FileUploadExercise copyFileUploadExerciseBasis(FileUploadExercise importedExercise) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        FileUploadExercise newExercise = new FileUploadExercise();

        super.copyExerciseBasis(newExercise, importedExercise, new HashMap<>());
        newExercise.setFilePattern(importedExercise.getFilePattern());
        newExercise.setExampleSolution(importedExercise.getExampleSolution());
        return newExercise;
    }

}
