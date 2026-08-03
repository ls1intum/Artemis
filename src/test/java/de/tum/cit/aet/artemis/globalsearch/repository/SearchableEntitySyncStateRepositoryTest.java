package de.tum.cit.aet.artemis.globalsearch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;

@ExtendWith(MockitoExtension.class)
class SearchableEntitySyncStateRepositoryTest {

    private static final String TYPE = "faq";

    private static final long ENTITY_ID = 42L;

    @Mock
    private SearchableEntitySyncStateRepository repository;

    private void callRealMarkDirty() {
        doCallRealMethod().when(repository).markDirty(anyString(), anyLong(), nullable(String.class), any(ZonedDateTime.class));
    }

    private void callRealClaimRetry() {
        doCallRealMethod().when(repository).claimRetry(anyString(), anyLong(), any(ZonedDateTime.class), any(ZonedDateTime.class));
    }

    @Test
    void markDirtyCreatesADirtyStateWhenNoneExists() {
        callRealMarkDirty();
        when(repository.findByEntityTypeAndEntityId(TYPE, ENTITY_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ZonedDateTime nextRetryAt = ZonedDateTime.now().plusMinutes(1);

        SearchableEntitySyncState state = repository.markDirty(TYPE, ENTITY_ID, "hash-a", nextRetryAt);

        assertThat(state.getEntityType()).isEqualTo(TYPE);
        assertThat(state.getEntityId()).isEqualTo(ENTITY_ID);
        assertThat(state.getContentHash()).isEqualTo("hash-a");
        assertThat(state.getStatus()).isEqualTo(SearchableEntitySyncState.STATUS_DIRTY);
        assertThat(state.getNextRetryAt()).isEqualTo(nextRetryAt);
    }

    @Test
    void markDirtySettlesToCleanWhenContentMatchesLastSynced() {
        callRealMarkDirty();
        SearchableEntitySyncState existing = new SearchableEntitySyncState();
        existing.setEntityType(TYPE);
        existing.setEntityId(ENTITY_ID);
        existing.setLastSyncedContentHash("hash-a");
        existing.setStatus(SearchableEntitySyncState.STATUS_DIRTY);
        existing.setNextRetryAt(ZonedDateTime.now());
        when(repository.findByEntityTypeAndEntityId(TYPE, ENTITY_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SearchableEntitySyncState state = repository.markDirty(TYPE, ENTITY_ID, "hash-a", ZonedDateTime.now().plusMinutes(1));

        assertThat(state.getStatus()).isEqualTo(SearchableEntitySyncState.STATUS_CLEAN);
        assertThat(state.getNextRetryAt()).isNull();
    }

    @Test
    void claimRetrySkipsARowThatIsNotYetDue() {
        callRealClaimRetry();
        SearchableEntitySyncState notDue = new SearchableEntitySyncState();
        notDue.setStatus(SearchableEntitySyncState.STATUS_DIRTY);
        notDue.setNextRetryAt(ZonedDateTime.now().plusMinutes(30));
        when(repository.findByEntityTypeAndEntityIdForUpdate(TYPE, ENTITY_ID)).thenReturn(Optional.of(notDue));

        Optional<SearchableEntitySyncState> claimed = repository.claimRetry(TYPE, ENTITY_ID, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(5));

        assertThat(claimed).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void markDirtyThenClaimRetryLeasesTheRow() {
        callRealMarkDirty();
        callRealClaimRetry();
        AtomicReference<SearchableEntitySyncState> persisted = new AtomicReference<>();
        when(repository.findByEntityTypeAndEntityId(TYPE, ENTITY_ID)).thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(repository.findByEntityTypeAndEntityIdForUpdate(TYPE, ENTITY_ID)).thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        repository.markDirty(TYPE, ENTITY_ID, "hash-a", ZonedDateTime.now().minusSeconds(1));
        ZonedDateTime leaseUntil = ZonedDateTime.now().plusMinutes(5);
        Optional<SearchableEntitySyncState> claimed = repository.claimRetry(TYPE, ENTITY_ID, ZonedDateTime.now(), leaseUntil);

        assertThat(claimed).isPresent();
        assertThat(claimed.get().getStatus()).isEqualTo(SearchableEntitySyncState.STATUS_IN_PROGRESS);
        assertThat(claimed.get().getNextRetryAt()).isEqualTo(leaseUntil);
    }
}
