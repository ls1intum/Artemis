package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Unit tests for {@link SearchableEntityResolver}.
 * <p>
 * The resolver answers "what should this entity look like in the index right now?". Two of its answers have very
 * different consequences for the dispatcher: an empty result is acted on as a delete, while an exception keeps the
 * outbox row for a retry. A module that is not available on this node must therefore never read as empty.
 */
class SearchableEntityResolverTest {

    private final CourseRepository courseRepository = mock(CourseRepository.class);

    private final SearchableEntityResolver resolver = new SearchableEntityResolver(courseRepository, mock(FaqRepository.class), mock(PostRepository.class),
            mock(AnswerPostRepository.class), mock(ChannelRepository.class), mock(ExerciseSearchableEntityLoadService.class), Optional.empty(), Optional.empty(), Optional.empty());

    @Test
    void testAMissingEntityResolvesToEmpty() {
        when(courseRepository.findById(42L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(SearchableEntitySchema.TypeValues.COURSE, 42L)).isEmpty();
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
