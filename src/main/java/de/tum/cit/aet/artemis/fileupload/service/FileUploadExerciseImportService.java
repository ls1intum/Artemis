package de.tum.cit.aet.artemis.fileupload.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseImportService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FileUploadExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadExerciseImportService.class);

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ChannelService channelService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final ExerciseService exerciseService;

    public FileUploadExerciseImportService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository, ChannelService channelService, FeedbackService feedbackService,
            Optional<CompetencyProgressApi> competencyProgressApi, ExerciseService exerciseService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.channelService = channelService;
        this.competencyProgressApi = competencyProgressApi;
        this.exerciseService = exerciseService;
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

        FileUploadExercise newFileUploadExercise = exerciseService.saveWithCompetencyLinks(newExercise, fileUploadExerciseRepository::save);

        channelService.createExerciseChannel(newFileUploadExercise, Optional.ofNullable(importedExercise.getChannelName()));

        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(newFileUploadExercise));

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
