package de.tum.cit.aet.artemis.fileupload.service;

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
import de.tum.cit.aet.artemis.fileupload.config.FileUploadEnabled;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;

@Conditional(FileUploadEnabled.class)
@Lazy
@Service
public class FileUploadExerciseService {

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ChannelService channelService;

    private final ParticipationRepository participationRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    public FileUploadExerciseService(ExerciseSpecificationService exerciseSpecificationService, FileUploadExerciseRepository fileUploadExerciseRepository,
            ChannelService channelService, ParticipationRepository participationRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository) {
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.channelService = channelService;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Search for all file upload exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<FileUploadExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final Boolean isCourseFilter, final Boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<FileUploadExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<FileUploadExercise> exercisePage = fileUploadExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Get a summary for the deletion of a file upload exercise.
     *
     * @param exerciseId the id of the file upload exercise
     * @return the summary of the deletion of the file upload exercise
     */
    public FileUploadExerciseDeletionSummaryDTO getDeletionSummary(long exerciseId) {
        final long numberOfStudentParticipations = participationRepository.countByExerciseId(exerciseId);
        final long numberOfSubmissions = submissionRepository.countByExerciseId(exerciseId);
        final long numberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exerciseId);

        final ExerciseCommunicationDeletionSummaryDTO exerciseCommunicationDeletionSummaryDTO = channelService.getExerciseCommunicationDeletionSummary(exerciseId);

        return new FileUploadExerciseDeletionSummaryDTO(numberOfStudentParticipations, numberOfSubmissions, numberOfAssessments,
                exerciseCommunicationDeletionSummaryDTO.numberOfCommunicationPosts(), exerciseCommunicationDeletionSummaryDTO.numberOfAnswerPosts());
    }
}
