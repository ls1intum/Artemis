package de.tum.cit.aet.artemis.text.service;

import java.util.Collections;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.communication.dto.ExerciseCommunicationDeletionSummaryDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

@Conditional(TextEnabled.class)
@Lazy
@Service
public class TextExerciseService {

    private final TextExerciseRepository textExerciseRepository;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ChannelService channelService;

    private final ParticipationRepository participationRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final TextBlockRepository textBlockRepository;

    public TextExerciseService(TextExerciseRepository textExerciseRepository, ExerciseSpecificationService exerciseSpecificationService,
            InstanceMessageSendService instanceMessageSendService, ChannelService channelService, ParticipationRepository participationRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, TextBlockRepository textBlockRepository) {
        this.textExerciseRepository = textExerciseRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.channelService = channelService;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.textBlockRepository = textBlockRepository;
    }

    /**
     * Search for all text exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<TextExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final boolean isCourseFilter, final boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<TextExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<TextExercise> exercisePage = textExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    public void cancelScheduledOperations(long exerciseId) {
        instanceMessageSendService.sendTextExerciseScheduleCancel(exerciseId);
    }

    /**
     * Get a summary for the deletion of a text exercise.
     *
     * @param exerciseId the id of the text exercise
     * @return the summary of the deletion of the text exercise
     */
    public ExerciseDeletionSummaryDTO getDeletionSummary(long exerciseId) {
        final long numberOfStudentParticipations = participationRepository.countByExerciseId(exerciseId);
        final long numberOfSubmissions = submissionRepository.countByExerciseId(exerciseId);
        final long numberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exerciseId);

        ExerciseCommunicationDeletionSummaryDTO communicationDeletionSummaryDTO = channelService.getExerciseCommunicationDeletionSummary(exerciseId);

        return new ExerciseDeletionSummaryDTO(numberOfStudentParticipations, null, numberOfSubmissions, numberOfAssessments,
                communicationDeletionSummaryDTO.numberOfCommunicationPosts(), communicationDeletionSummaryDTO.numberOfAnswerPosts());
    }
}
