package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;

/**
 * Unit test for {@link AbstractIrisChatSessionService#setSessionTitle}. The point of these assertions is the WAY the
 * title is written: the status handler hands in a session that was loaded together with its message collection, and
 * writing one column must not turn into a merge of that whole aggregate. A merge does not lose messages by itself
 * (the loaded collection is a Hibernate {@code PersistentList} and carries its snapshot), but it drags the collection
 * into a write that has nothing to do with it, and it stops being harmless the moment anything replaces that
 * collection with a plain list. So the contract asserted here is: scalar update, never {@code save(session)}.
 */
class AbstractIrisChatSessionServiceTitleTest {

    @Test
    void setSessionTitleWritesTheColumnAndNeverMergesTheAggregate() {
        var repository = mock(IrisSessionRepository.class);
        var session = new IrisChatSession();
        session.setId(7L);

        String returned = AbstractIrisChatSessionService.setSessionTitle(session, "A title", repository);

        assertThat(returned).isEqualTo("A title");
        assertThat(session.getTitle()).as("the in-memory instance the caller keeps using must carry the new title").isEqualTo("A title");
        verify(repository).updateTitle(7L, "A title");
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void setSessionTitleTruncatesToTheColumnWidth() {
        var repository = mock(IrisSessionRepository.class);
        var session = new IrisChatSession();
        session.setId(8L);
        String tooLong = "t".repeat(300);

        String returned = AbstractIrisChatSessionService.setSessionTitle(session, tooLong, repository);

        assertThat(returned).hasSize(255);
        verify(repository).updateTitle(8L, returned);
    }

    @Test
    void setSessionTitleWritesNothingForABlankTitle() {
        var repository = mock(IrisSessionRepository.class);
        var session = new IrisChatSession();
        session.setId(9L);

        assertThat(AbstractIrisChatSessionService.setSessionTitle(session, "   ", repository)).isNull();
        assertThat(AbstractIrisChatSessionService.setSessionTitle(session, null, repository)).isNull();

        verify(repository, never()).updateTitle(anyLong(), any());
        verify(repository, never()).save(any());
    }
}
