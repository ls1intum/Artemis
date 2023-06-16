package de.tum.in.www1.artemis.service;

import java.util.HashMap;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;

@Service
public class FileUploadExerciseImportService extends ExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(FileUploadExerciseImportService.class);

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ChannelService channelService;

    public FileUploadExerciseImportService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository, ChannelService channelService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository);
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.channelService = channelService;
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

        FileUploadExercise newFileUploadExercise = fileUploadExerciseRepository.save(newExercise);

        Channel createdChannel = channelService.createExerciseChannel(newFileUploadExercise, importedExercise.getChannelName());
        channelService.registerUsersToChannelAsynchronously(true, newFileUploadExercise.getCourseViaExerciseGroupOrCourseMember(), createdChannel);

        return newFileUploadExercise;
    }

    /**
     * This helper method copies all attributes of the {@code importedExercise} into the new exercise.
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
