package de.tum.cit.aet.artemis.account.service.user.deletion;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.AssessmentDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.CommunicationDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.CourseContextDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.ExerciseDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.LearningDataCleanupRepository;
import de.tum.cit.aet.artemis.account.repository.cleanup.PlatformDataCleanupRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

/**
 * Holds the deletion to taking messages out of the search index as well.
 *
 * <p>
 * Global search keeps its own copy of what a message says so that it can be found by it, and that copy lives outside
 * the database. Every other deletion in Artemis removes it through the service that owns the message; this one removes
 * the rows directly, so it has to do that itself, and it has to read the ids before the rows are gone. Neither the
 * index nor the ordering is visible in the database afterwards, which is why it is asserted here rather than in the
 * integration tests - and the index is only wired up at all where global search is configured, so it is optional.
 */
class UserOwnedContentSearchIndexTest {

    private static final long USER_ID = 7L;

    private CommunicationDataCleanupRepository communicationDataCleanupRepository;

    private PlatformDataCleanupRepository platformDataCleanupRepository;

    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    private UserOwnedContentDeletionService userOwnedContentDeletionService;

    @BeforeEach
    void setUp() {
        communicationDataCleanupRepository = mock(CommunicationDataCleanupRepository.class);
        platformDataCleanupRepository = mock(PlatformDataCleanupRepository.class);
        searchableEntityWeaviateService = mock(SearchableEntityWeaviateService.class);
        userOwnedContentDeletionService = new UserOwnedContentDeletionService(mock(UserRepository.class), mock(ParticipationDeletionService.class),
                communicationDataCleanupRepository, mock(AssessmentDataCleanupRepository.class), mock(ExerciseDataCleanupRepository.class),
                mock(CourseContextDataCleanupRepository.class), platformDataCleanupRepository, mock(LearningDataCleanupRepository.class),
                Optional.of(searchableEntityWeaviateService));
    }

    @Test
    void theMessagesOfTheAccountAreTakenOutOfTheSearchIndex() {
        when(communicationDataCleanupRepository.findPostIdsAuthoredBy(USER_ID)).thenReturn(List.of(11L, 12L));
        when(communicationDataCleanupRepository.findAnswerPostIdsAuthoredBy(USER_ID)).thenReturn(List.of(21L));

        userOwnedContentDeletionService.deleteCommunicationContent(USER_ID);

        verify(searchableEntityWeaviateService).deleteEntityAsync(SearchableEntitySchema.TypeValues.POST, 11L);
        verify(searchableEntityWeaviateService).deleteEntityAsync(SearchableEntitySchema.TypeValues.POST, 12L);
        // The reply below the account's thread goes with the thread, so it has to leave the index with it.
        verify(searchableEntityWeaviateService).deleteEntityAsync(SearchableEntitySchema.TypeValues.ANSWER_POST, 21L);
    }

    @Test
    void theIdsAreReadBeforeTheRowsAreGone() {
        when(communicationDataCleanupRepository.findPostIdsAuthoredBy(USER_ID)).thenReturn(List.of(11L));
        when(communicationDataCleanupRepository.findAnswerPostIdsAuthoredBy(USER_ID)).thenReturn(List.of(21L));

        userOwnedContentDeletionService.deleteCommunicationContent(USER_ID);

        // Asking after the deletion would answer nothing, and the index would keep every message.
        InOrder order = inOrder(communicationDataCleanupRepository, searchableEntityWeaviateService);
        order.verify(communicationDataCleanupRepository).findPostIdsAuthoredBy(USER_ID);
        order.verify(communicationDataCleanupRepository).findAnswerPostIdsAuthoredBy(USER_ID);
        order.verify(communicationDataCleanupRepository).deletePosts(USER_ID);
        order.verify(searchableEntityWeaviateService).deleteEntityAsync(SearchableEntitySchema.TypeValues.POST, 11L);
    }

    @Test
    void theDiscussionHeldOnAPlagiarismCaseAlsoLeavesTheIndex() {
        when(platformDataCleanupRepository.findPlagiarismCaseIdsOfStudent(USER_ID)).thenReturn(List.of(31L));
        when(communicationDataCleanupRepository.findPlagiarismCasePostIds(List.of(31L))).thenReturn(List.of(41L));
        when(communicationDataCleanupRepository.findPlagiarismCaseAnswerPostIds(List.of(31L))).thenReturn(List.of(51L));

        userOwnedContentDeletionService.deletePlagiarismCases(USER_ID);

        verify(searchableEntityWeaviateService).deleteEntityAsync(SearchableEntitySchema.TypeValues.POST, 41L);
        verify(searchableEntityWeaviateService).deleteEntityAsync(SearchableEntitySchema.TypeValues.ANSWER_POST, 51L);
    }

    @Test
    void anAccountWithoutAnyMessagesLeavesTheIndexAlone() {
        when(communicationDataCleanupRepository.findPostIdsAuthoredBy(anyLong())).thenReturn(List.of());
        when(communicationDataCleanupRepository.findAnswerPostIdsAuthoredBy(anyLong())).thenReturn(List.of());

        userOwnedContentDeletionService.deleteCommunicationContent(USER_ID);

        verifyNoInteractions(searchableEntityWeaviateService);
    }

    @Test
    void aDeploymentWithoutGlobalSearchStillDeletes() {
        UserOwnedContentDeletionService withoutSearch = new UserOwnedContentDeletionService(mock(UserRepository.class), mock(ParticipationDeletionService.class),
                communicationDataCleanupRepository, mock(AssessmentDataCleanupRepository.class), mock(ExerciseDataCleanupRepository.class),
                mock(CourseContextDataCleanupRepository.class), platformDataCleanupRepository, mock(LearningDataCleanupRepository.class), Optional.empty());
        when(communicationDataCleanupRepository.findPostIdsAuthoredBy(USER_ID)).thenReturn(List.of(11L));

        withoutSearch.deleteCommunicationContent(USER_ID);

        verify(communicationDataCleanupRepository).deletePosts(USER_ID);
    }
}
