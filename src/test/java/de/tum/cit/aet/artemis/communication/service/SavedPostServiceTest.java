package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.test_repository.SavedPostTestRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

@ExtendWith(MockitoExtension.class)
class SavedPostServiceTest {

    private SavedPostService savedPostService;

    @Mock
    private SavedPostTestRepository savedPostRepository;

    @Mock
    private UserTestRepository userRepository;

    private User testUser;

    private Post testPost;

    private SavedPost testSavedPost;

    @BeforeEach
    void setUp() {
        savedPostService = new SavedPostService(savedPostRepository, userRepository);

        testUser = new User();
        testUser.setId(1L);

        testPost = new Post();
        testPost.setId(1L);

        testSavedPost = new SavedPost(testUser, testPost.getId(), PostingType.POST, SavedPostStatus.IN_PROGRESS, null);

        when(userRepository.getUser()).thenReturn(testUser);
    }

    @Test
    void shouldSavePostSuccessfullyWhenPostIsNotSavedYet() {
        when(savedPostRepository.findSavedPostsByUserIdAndPostIdAndPostType(testUser.getId(), testPost.getId(), PostingType.POST)).thenReturn(List.of());

        savedPostService.savePostForCurrentUser(testPost);

        verify(savedPostRepository).save(any(SavedPost.class));
    }

    @Test
    void shouldNotSavePostWhenPostIsSavedAlready() {
        when(savedPostRepository.findSavedPostsByUserIdAndPostIdAndPostType(testUser.getId(), testPost.getId(), PostingType.POST)).thenReturn(List.of(testSavedPost));

        savedPostService.savePostForCurrentUser(testPost);

        verify(savedPostRepository, never()).save(any());
    }

    @Test
    void shouldRemoveSavedPostWhenPostIsBookmarked() {
        when(savedPostRepository.findSavedPostsByUserIdAndPostIdAndPostType(testUser.getId(), testPost.getId(), PostingType.POST)).thenReturn(List.of(testSavedPost));

        savedPostService.removeSavedPostForCurrentUser(testPost);

        verify(savedPostRepository).delete(testSavedPost);
    }

    @Test
    void shouldNotRemoveSavedPostWhenPostIsNotBookmarked() {
        when(savedPostRepository.findSavedPostsByUserIdAndPostIdAndPostType(testUser.getId(), testPost.getId(), PostingType.POST)).thenReturn(List.of());

        savedPostService.removeSavedPostForCurrentUser(testPost);

        verify(savedPostRepository, never()).delete(any());
    }

    @Test
    void shouldUpdateStatusAndCompletedAtOfSavedPostWhenPostIsInProgress() {
        when(savedPostRepository.findSavedPostsByUserIdAndPostIdAndPostType(testUser.getId(), testPost.getId(), PostingType.POST)).thenReturn(List.of(testSavedPost));

        savedPostService.updateStatusOfSavedPostForCurrentUser(testPost, SavedPostStatus.COMPLETED);

        assertThat(testSavedPost.getStatus()).isEqualTo(SavedPostStatus.COMPLETED);
        assertThat(testSavedPost.getCompletedAt()).isNotNull();
        verify(savedPostRepository).save(testSavedPost);
    }

    @Test
    void shouldUpdateStatusAndResetCompletedAtOfSavedPostWhenPostIsSetToInProgress() {
        testSavedPost.setCompletedAt(ZonedDateTime.now());
        when(savedPostRepository.findSavedPostsByUserIdAndPostIdAndPostType(testUser.getId(), testPost.getId(), PostingType.POST)).thenReturn(List.of(testSavedPost));

        savedPostService.updateStatusOfSavedPostForCurrentUser(testPost, SavedPostStatus.IN_PROGRESS);

        assertThat(testSavedPost.getStatus()).isEqualTo(SavedPostStatus.IN_PROGRESS);
        assertThat(testSavedPost.getCompletedAt()).isNull();
        verify(savedPostRepository).save(testSavedPost);
    }

    @Test
    void shouldReturnListFromRepositoryWhenQueriedByCurrentUser() {
        SavedPostStatus status = SavedPostStatus.IN_PROGRESS;
        when(savedPostRepository.findSavedPostsByUserIdAndStatusOrderByCompletedAtDescIdDesc(testUser.getId(), status)).thenReturn(List.of(testSavedPost));

        List<SavedPost> result = savedPostService.getSavedPostsForCurrentUserByStatus(status);

        assertThat(result).containsExactly(testSavedPost);
    }

    @Test
    void shouldReturnLimitExceededWhenUserHasToManySavedPosts() {
        when(savedPostRepository.countByUserId(testUser.getId())).thenReturn(100L);

        boolean result = savedPostService.isMaximumSavedPostsReached();

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnLimitNotExceededWhenUserIsBelowThreshold() {
        when(savedPostRepository.countByUserId(testUser.getId())).thenReturn(99L);

        boolean result = savedPostService.isMaximumSavedPostsReached();

        assertThat(result).isFalse();
    }
}
