package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityPostRepository;

/**
 * Unit tests for {@link SearchableEntityResolver}.
 * <p>
 * The resolver answers "what should this entity look like in the index right now?". Two of its answers have very
 * different consequences for the dispatcher: an empty result is acted on as a delete, while an exception keeps the
 * outbox row for a retry. A module that is not available on this node must therefore never read as empty.
 */
class SearchableEntityResolverTest {

    private final CourseRepository courseRepository = mock(CourseRepository.class);

    private final SearchableEntityPostRepository searchableEntityPostRepository = mock(SearchableEntityPostRepository.class);

    private final SearchableEntityResolver resolver = new SearchableEntityResolver(courseRepository, mock(FaqRepository.class), searchableEntityPostRepository,
            mock(ChannelRepository.class), mock(ExerciseSearchableEntityLoadService.class), Optional.empty(), Optional.empty(), Optional.empty());

    @Test
    void testAMissingEntityResolvesToEmpty() {
        when(courseRepository.findById(42L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(SearchableEntitySchema.TypeValues.COURSE, 42L)).isEmpty();
    }

    /**
     * Regression test for a resolve that loaded the whole {@code Post} entity graph (its {@code FetchType.EAGER}
     * reactions and answers) even though only a few scalar fields end up in the property map. Resolving a post must
     * go through the single, scalar {@link SearchableEntityPostRepository#findIndexablePostProjection} query and
     * touch no other collaborator.
     */
    @Test
    void testAnIndexablePostResolvesFromTheScalarProjectionAlone() {
        PostSearchableEntityDTO dto = new PostSearchableEntityDTO(11L, 2L, 3L, "title", "content");
        when(searchableEntityPostRepository.findIndexablePostProjection(11L)).thenReturn(Optional.of(dto));

        assertThat(resolver.resolve(SearchableEntitySchema.TypeValues.POST, 11L)).contains(dto.toPropertyMap());
        verifyNoMoreInteractions(courseRepository);
    }

    @Test
    void testAPostInANonIndexableChannelOrDeletedResolvesToEmpty() {
        when(searchableEntityPostRepository.findIndexablePostProjection(11L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(SearchableEntitySchema.TypeValues.POST, 11L)).isEmpty();
    }

    /**
     * Same regression as the post case above, but for the answer post branch and its own {@code FetchType.EAGER}
     * reactions plus its default-eager {@code Post} association.
     */
    @Test
    void testAnIndexableAnswerPostResolvesFromTheScalarProjectionAlone() {
        AnswerPostSearchableEntityDTO dto = new AnswerPostSearchableEntityDTO(21L, 11L, 2L, 3L, "reply");
        when(searchableEntityPostRepository.findIndexableAnswerPostProjection(21L)).thenReturn(Optional.of(dto));

        assertThat(resolver.resolve(SearchableEntitySchema.TypeValues.ANSWER_POST, 21L)).contains(dto.toPropertyMap());
        verifyNoMoreInteractions(courseRepository);
    }

    @Test
    void testAnAnswerPostInANonIndexableChannelOrDeletedResolvesToEmpty() {
        when(searchableEntityPostRepository.findIndexableAnswerPostProjection(21L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(SearchableEntitySchema.TypeValues.ANSWER_POST, 21L)).isEmpty();
    }

    @Test
    void testAnAbsentLectureModuleFailsRatherThanReadingAsDeleted() {
        assertThatThrownBy(() -> resolver.resolve(SearchableEntitySchema.TypeValues.LECTURE, 7L)).isInstanceOf(IllegalStateException.class).hasMessageContaining("lecture module");
        assertThatThrownBy(() -> resolver.resolve(SearchableEntitySchema.TypeValues.LECTURE_UNIT, 7L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lecture module");
    }

    @Test
    void testAnAbsentExamModuleFailsRatherThanReadingAsDeleted() {
        assertThatThrownBy(() -> resolver.resolve(SearchableEntitySchema.TypeValues.EXAM, 7L)).isInstanceOf(IllegalStateException.class).hasMessageContaining("exam module");
    }
}
