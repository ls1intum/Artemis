package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.test_repository.SavedPostTestRepository;

@ExtendWith(MockitoExtension.class)
class SavedPostScheduleServiceTest {

    @Mock
    private SavedPostTestRepository savedPostRepository;

    private SavedPostScheduleService savedPostScheduleService;

    @Captor
    private ArgumentCaptor<List<SavedPost>> savedPostCaptor;

    @BeforeEach
    void setUp() {
        savedPostScheduleService = new SavedPostScheduleService(savedPostRepository);
    }

    @Test
    void shouldDeleteArchivedSavedPostsWhenDateExceedsCutoff() {
        List<SavedPost> oldPosts = List.of(createSavedPost(1L, ZonedDateTime.now().minusDays(101)), createSavedPost(2L, ZonedDateTime.now().minusDays(102)));
        when(savedPostRepository.findByCompletedAtBefore(any())).thenReturn(oldPosts);

        savedPostScheduleService.cleanupArchivedSavedPosts();

        verify(savedPostRepository).deleteAll(savedPostCaptor.capture());
        List<SavedPost> deletedPosts = savedPostCaptor.getValue();
        assertThat(deletedPosts).hasSize(2);
        assertThat(deletedPosts).containsExactlyElementsOf(oldPosts);
    }

    @Test
    void shouldNotDeleteAnythingWhenNoSavedPostsAreFound() {
        when(savedPostRepository.findByCompletedAtBefore(any())).thenReturn(new ArrayList<>());

        savedPostScheduleService.cleanupArchivedSavedPosts();

        verify(savedPostRepository, never()).deleteAll(any());
    }

    @Test
    void shouldDeleteSavedPostsWhenTheyAreOrphaned() {
        // Arrange
        List<SavedPost> orphanedPosts = List.of(createSavedPost(1L, PostingType.POST), createSavedPost(2L, PostingType.POST));
        when(savedPostRepository.findOrphanedPostReferences()).thenReturn(orphanedPosts);

        // Act
        savedPostScheduleService.cleanupOrphanedSavedPosts();

        // Assert
        verify(savedPostRepository).deleteAll(savedPostCaptor.capture());
        List<SavedPost> deletedPosts = savedPostCaptor.getValue();
        assertThat(deletedPosts).hasSize(2);
        assertThat(deletedPosts).containsExactlyElementsOf(orphanedPosts);
    }

    @Test
    void shouldNotDeleteAnythingWhenNoOrphanedPostsAreFound() {
        when(savedPostRepository.findOrphanedPostReferences()).thenReturn(new ArrayList<>());

        savedPostScheduleService.cleanupOrphanedSavedPosts();

        verify(savedPostRepository, never()).deleteAll(any());
    }

    @Test
    void shouldDeleteSavedAnswerPostsWhenTheyAreOrphaned() {
        List<SavedPost> orphanedAnswers = List.of(createSavedPost(1L, PostingType.ANSWER), createSavedPost(2L, PostingType.ANSWER));
        when(savedPostRepository.findOrphanedAnswerReferences()).thenReturn(orphanedAnswers);

        savedPostScheduleService.cleanupOrphanedSavedAnswerPosts();

        verify(savedPostRepository).deleteAll(savedPostCaptor.capture());
        List<SavedPost> deletedPosts = savedPostCaptor.getValue();
        assertThat(deletedPosts).hasSize(2);
        assertThat(deletedPosts).containsExactlyElementsOf(orphanedAnswers);
    }

    @Test
    void shouldNotDeleteAnythingWhenNoOrphanedAnswerPostsAreFound() {
        when(savedPostRepository.findOrphanedAnswerReferences()).thenReturn(new ArrayList<>());

        savedPostScheduleService.cleanupOrphanedSavedAnswerPosts();

        verify(savedPostRepository, never()).deleteAll(any());
    }

    private SavedPost createSavedPost(Long id, ZonedDateTime completedAt) {
        SavedPost savedPost = new SavedPost();
        savedPost.setId(id);
        savedPost.setCompletedAt(completedAt);
        return savedPost;
    }

    private SavedPost createSavedPost(Long id, PostingType postType) {
        SavedPost savedPost = new SavedPost();
        savedPost.setId(id);
        savedPost.setPostType(postType);
        savedPost.setCompletedAt(ZonedDateTime.now());
        return savedPost;
    }
}
