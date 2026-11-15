package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnqueueExecutorConfiguration;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ChannelSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;

/**
 * Exercises enqueue ordering with the production executor; the synchronous executor of the test profile cannot expose a delete overtaking a queued upsert.
 */
class SearchableEntityWeaviateServiceEnqueueOrderingTest {

    @ParameterizedTest
    @EnumSource(value = WeaviateOutboxOperation.class, mode = EnumSource.Mode.EXCLUDE, names = "UPSERT")
    void aDeleteWaitsForEarlierUpsertsAndItsOwnOutboxWrite(WeaviateOutboxOperation operation) throws Exception {
        var executor = (ThreadPoolTaskExecutor) new WeaviateEnqueueExecutorConfiguration().weaviateOutboxEnqueueExecutor(new MockEnvironment());
        executor.initialize();
        var workerStarted = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var deleteStarted = new CountDownLatch(1);
        var writes = new ConcurrentLinkedQueue<WeaviateOutboxEntry>();
        var repository = mock(WeaviateOutboxRepository.class);
        doAnswer(invocation -> {
            WeaviateOutboxEntry entry = invocation.getArgument(0);
            writes.add(entry);
            return entry;
        }).when(repository).save(any(WeaviateOutboxEntry.class));
        var mapper = JsonObjectMapper.get();
        var service = new SearchableEntityWeaviateService(mock(WeaviateService.class), repository, mock(SearchableEntityResolver.class), mapper,
                new SearchableEntityContentHasher(mapper), mock(ApplicationEventPublisher.class), mock(SearchableEntityVectorWriter.class),
                mock(SearchableEntityEmbeddingClient.class), executor);

        try (var caller = Executors.newSingleThreadExecutor()) {
            try {
                // A slow earlier write occupies the sole worker; the channel update must remain queued behind it.
                executor.execute(() -> {
                    workerStarted.countDown();
                    try {
                        releaseWorker.await();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                assertThat(workerStarted.await(10, TimeUnit.SECONDS)).isTrue();
                service.upsertChannelAsync(new ChannelSearchableEntityDTO(7L, 10L, "channel", null, false, true, null));

                var deletion = caller.submit(() -> {
                    deleteStarted.countDown();
                    switch (operation) {
                        case DELETE_ENTITY -> service.deleteEntityAsync(SearchableEntitySchema.TypeValues.CHANNEL, 7L);
                        case DELETE_POSTS_FOR_CHANNEL -> service.deleteAllPostsForChannelAsync(7L);
                        case DELETE_POSTS_FOR_COURSE -> service.deleteAllPostsForCourseAsync(10L);
                        case DELETE_ANSWER_POSTS_FOR_POST -> service.deleteAllAnswerPostsForPostAsync(8L);
                        case DELETE_ALL_FOR_COURSE -> service.deleteAllForCourseAsync(10L);
                        case DELETE_LECTURE_UNITS_FOR_LECTURE -> service.deleteAllLectureUnitsForLectureAsync(9L);
                        default -> throw new IllegalArgumentException("Not a delete: " + operation);
                    }
                });
                assertThat(deleteStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> deletion.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThat(writes).as("a delete must not overtake the queued update").isEmpty();

                releaseWorker.countDown();
                deletion.get(10, TimeUnit.SECONDS);
                assertThat(List.copyOf(writes)).extracting(WeaviateOutboxEntry::getOperation).containsExactly(WeaviateOutboxOperation.UPSERT, operation);
            }
            finally {
                releaseWorker.countDown();
            }
        }
        finally {
            executor.shutdown();
        }
    }
}
