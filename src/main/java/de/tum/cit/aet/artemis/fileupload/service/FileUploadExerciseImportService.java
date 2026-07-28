package de.tum.cit.aet.artemis.fileupload.service;

import java.util.HashMap;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseImportService;
import de.tum.cit.aet.artemis.fileupload.config.FileUploadEnabled;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;

@Conditional(FileUploadEnabled.class)
@Lazy
@Service
public class FileUploadExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadExerciseImportService.class);

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ChannelService channelService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    public FileUploadExerciseImportService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository, ChannelService channelService, FeedbackService feedbackService,
            Optional<CompetencyProgressApi> competencyProgressApi, CompetencyExerciseLinkService competencyExerciseLinkService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.channelService = channelService;
        this.competencyProgressApi = competencyProgressApi;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
    }

    /**
     * Imports a file upload exercise: builds a new entity from {@code newExercise} (the destination and any caller
     * overrides), backfills its basis from {@code sourceExercise} (the original), and saves it. Student-/tutor
     * participations are not copied.
     * This method calls {@link #copyFileUploadExerciseBasis(FileUploadExercise, FileUploadExercise)} to set up the basis
     * of the exercise.
     *
     * @param newExercise    the exercise to build; already carries the destination (course / exercise group) and any overrides
     * @param sourceExercise the source exercise whose content is copied
     * @return The newly created exercise
     */
    @NonNull
    public FileUploadExercise importFileUploadExercise(final FileUploadExercise newExercise, final FileUploadExercise sourceExercise) {
        log.debug("Creating a new file upload exercise based on exercise {}", sourceExercise);
        copyFileUploadExerciseBasis(newExercise, sourceExercise);

        var competencyLinks = competencyExerciseLinkService.extractCompetencyLinksForCreation(newExercise);
        // Only the first save is identity-preserving (the id was cleared, so Spring Data persists newExercise itself). The
        // second save operates on a detached entity and therefore merges into a new instance, so its result must be used:
        // otherwise the freshly added competency links keep their unset embedded id on the returned graph.
        FileUploadExercise savedExercise = fileUploadExerciseRepository.save(newExercise);
        if (!competencyLinks.isEmpty()) {
            competencyExerciseLinkService.addCompetencyLinksForCreation(savedExercise, competencyLinks);
            savedExercise = fileUploadExerciseRepository.save(savedExercise);
        }
        final FileUploadExercise persistedExercise = savedExercise;

        // The channel name is transient, so it is only present on the caller's object and not on a merged copy.
        channelService.createExerciseChannel(persistedExercise, Optional.ofNullable(newExercise.getChannelName()));

        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(persistedExercise));

        return persistedExercise;
    }

    /**
     * Backfills the file upload exercise basis onto {@code newExercise} from {@code sourceExercise}: the generic basis
     * follows the "keep the caller's value, else take the source's" rule (see
     * {@link ExerciseImportService#copyExerciseBasis}), and the file-upload-specific fields likewise prefer the caller's
     * edited value. The assessment type is always set to {@code MANUAL} (file upload exercises are manually assessed).
     * All external entities and the start-, end-, and assessment due dates are intentionally not copied here.
     *
     * @param newExercise    the exercise being built; mutated in place
     * @param sourceExercise the source exercise providing the content to backfill
     */
    private void copyFileUploadExerciseBasis(FileUploadExercise newExercise, FileUploadExercise sourceExercise) {
        log.debug("Copying the file upload exercise basis from {}", sourceExercise);
        prepareNewExerciseForImport(newExercise);
        super.copyExerciseBasis(newExercise, sourceExercise, new HashMap<>());
        newExercise.setAssessmentType(AssessmentType.MANUAL);
        // Prefer the caller's edited value (standalone import form), fall back to the source content.
        newExercise.setFilePattern(firstNonNull(newExercise.getFilePattern(), sourceExercise.getFilePattern()));
        newExercise.setExampleSolution(firstNonNull(newExercise.getExampleSolution(), sourceExercise.getExampleSolution()));
    }

}
