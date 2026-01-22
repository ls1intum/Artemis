package de.tum.cit.aet.artemis.modeling.service;

import java.util.Collections;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.communication.dto.ExerciseCommunicationDeletionSummaryDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;

@Conditional(ModelingEnabled.class)
@Lazy
@Service
public class ModelingExerciseService {

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final ChannelService channelService;

    private final ParticipationRepository participationRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    public ModelingExerciseService(ModelingExerciseRepository modelingExerciseRepository, ExerciseSpecificationService exerciseSpecificationService, ChannelService channelService,
            ParticipationRepository participationRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.channelService = channelService;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Search for all modeling exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ModelingExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final boolean isCourseFilter, final boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<ModelingExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<ModelingExercise> exercisePage = modelingExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Get a summary for the deletion of a modeling exercise.
     *
     * @param exerciseId the id of the modeling exercise
     * @return the summary of the deletion of the modeling exercise
     */
    public ModelingExerciseDeletionSummaryDTO getDeletionSummary(long exerciseId) {
        final long numberOfStudentParticipations = participationRepository.countByExerciseId(exerciseId);
        final long numberOfSubmissions = submissionRepository.countByExerciseId(exerciseId);
        final long numberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exerciseId);

        ExerciseCommunicationDeletionSummaryDTO communicationDeletionSummaryDTO = channelService.getExerciseCommunicationDeletionSummary(exerciseId);

        return new ModelingExerciseDeletionSummaryDTO(numberOfStudentParticipations, numberOfSubmissions, numberOfAssessments,
                communicationDeletionSummaryDTO.numberOfCommunicationPosts(), communicationDeletionSummaryDTO.numberOfAnswerPosts());
    }
}
