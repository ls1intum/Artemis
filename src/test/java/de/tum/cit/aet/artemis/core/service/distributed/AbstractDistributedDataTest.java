package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.Cache;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.config.cache.DistributedDataCache;
import de.tum.cit.aet.artemis.core.config.cache.DistributedDataCacheManager;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.listener.QueueListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.set.DistributedSet;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.core.util.IpAddresses;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

public abstract class AbstractDistributedDataTest extends AbstractArtemisBuildAgentTest {

    protected abstract DistributedDataProvider getDistributedDataProvider();

    @Test
    void testQueueListener() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueue");
        // Create a mock listener
        QueueItemListener<String> mockListener = Mockito.mock(QueueItemListener.class);

        queue.addItemListener(mockListener);

        queue.add("item1");
        verify(mockListener, timeout(1000)).itemAdded(argThat("item1"::equals));

        queue.poll();
        verify(mockListener, timeout(1000)).itemRemoved(argThat("item1"::equals));
    }

    @Test
    void testQueueListenerTriggerCount() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueListenerCount");
        QueueItemListener<String> mockListener = Mockito.mock(QueueItemListener.class);

        queue.addItemListener(mockListener);

        for (int i = 0; i < 10; i++) {
            queue.add("item" + i);
        }
        for (int i = 0; i < 10; i++) {
            queue.poll();
        }
        verify(mockListener, timeout(1000).times(10)).itemAdded(argThat(item -> item.startsWith("item")));
        verify(mockListener, timeout(1000).times(10)).itemRemoved(argThat(item -> item.startsWith("item")));
    }

    @Test
    void testPeekDoesNotRemove() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueuePeek");
        QueueItemListener<String> mockListener = Mockito.mock(QueueItemListener.class);
        queue.addItemListener(mockListener);

        queue.add("a");
        verify(mockListener, timeout(1000).times(1)).itemAdded(argThat("a"::equals));

        String head = queue.peek();
        assertThat(head).isEqualTo("a");
        assertThat(queue.size()).isEqualTo(1);

        queue.poll();
        verify(mockListener, timeout(1000).times(1)).itemRemoved(argThat("a"::equals));
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void testClearEmptiesQueue() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueClear");

        queue.add("x");
        queue.add("y");
        queue.add("z");
        assertThat(queue.size()).isEqualTo(3);

        queue.clear();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isEqualTo(0);
        assertThat(queue.peek()).isNull();
        assertThat(queue.poll()).isNull();
    }

    @Test
    void testAddAllRemoveAllGetAll() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueAddAllRemoveAll");
        QueueItemListener<String> mockListener = Mockito.mock(QueueItemListener.class);
        queue.addItemListener(mockListener);

        List<String> items = List.of("a", "b", "c", "d");
        boolean modified = queue.addAll(items);
        assertThat(modified).isTrue();
        assertThat(queue.size()).isEqualTo(4);
        assertThat(queue.getAll()).containsExactlyElementsOf(items);
        verify(mockListener, timeout(1000).times(4)).itemAdded(argThat(s -> Set.of("a", "b", "c", "d").contains(s)));

        queue.removeAll(Set.of("b", "d"));
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.getAll()).containsExactlyElementsOf(List.of("a", "c"));
        verify(mockListener, timeout(1000).times(2)).itemRemoved(argThat(s -> Set.of("b", "d").contains(s)));

        queue.clear();
    }

    @Test
    void testGetNameIsCorrect() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueName");
        assertThat(queue.getName()).isEqualTo("testQueueName");
    }

    @Test
    void testIsEmptyAndSize() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueSizeEmpty");

        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isEqualTo(0);

        queue.add("v1");
        queue.add("v2");
        assertThat(queue.isEmpty()).isFalse();
        assertThat(queue.size()).isEqualTo(2);

        queue.poll();
        assertThat(queue.size()).isEqualTo(1);

        queue.clear();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isEqualTo(0);
    }

    @Test
    void testRemoveItemListenerStopsCallbacks() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueRemoveItemListener");
        QueueItemListener<String> mockListener = Mockito.mock(QueueItemListener.class);
        UUID regId = queue.addItemListener(mockListener);

        queue.add("first");
        verify(mockListener, timeout(1000).times(1)).itemAdded(argThat("first"::equals));

        queue.poll();
        verify(mockListener, timeout(1000).times(1)).itemRemoved(argThat("first"::equals));

        queue.removeListener(regId);

        queue.add("second");
        verify(mockListener, timeout(1000).times(1)).itemAdded(anyString()); // still only 1 call total

        queue.poll();
        verify(mockListener, timeout(1000).times(1)).itemRemoved(anyString()); // still only 1 call total
    }

    @Test
    void testAddAndRemoveQueueListenerRegistration() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testListener");
        QueueListener listener = Mockito.mock(QueueListener.class);

        UUID id = queue.addListener(listener);
        assertThat(id).isNotNull();

        queue.removeListener(id);
    }

    /**
     * The simplified {@link QueueListener} must actually fire, not just register. Registering it and never being
     * notified is indistinguishable from an idle queue at the call site.
     */
    @Test
    void testQueueListenerWithoutItemIsNotified() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueListenerNotification");
        queue.clear();
        QueueListener mockListener = Mockito.mock(QueueListener.class);
        queue.addListener(mockListener);

        queue.add("item");
        verify(mockListener, timeout(2000)).itemAdded();

        queue.poll();
        verify(mockListener, timeout(2000)).itemRemoved();
    }

    @Test
    void testMapEntryListenerTriggers() {
        DistributedMap<String, String> someMap = getDistributedDataProvider().getMap("someMap");

        MapEntryListener<String, String> mockListener = Mockito.mock(MapEntryListener.class);

        someMap.addEntryListener(mockListener);

        someMap.put("key1", "value1");
        verify(mockListener, timeout(1000)).entryAdded(argThat(event -> "key1".equals(event.key()) && "value1".equals(event.value())));

        someMap.put("key1", "value2");
        verify(mockListener, timeout(1000)).entryUpdated(argThat(event -> "key1".equals(event.key()) && "value2".equals(event.value()) && "value1".equals(event.oldValue())));

        someMap.remove("key1");
        verify(mockListener, timeout(1000)).entryRemoved(argThat(event -> "key1".equals(event.key()) && "value2".equals(event.oldValue())));
    }

    @Test
    void testMapEntryListenerTriggerCount() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("testMap");

        MapEntryListener<String, String> mockListener = Mockito.mock(MapEntryListener.class);

        map.addEntryListener(mockListener);

        for (int i = 0; i < 10; i++) {
            map.put("key" + i, "value" + i);
            map.put("key" + i, "newValue" + i);
        }
        for (int i = 0; i < 10; i++) {
            map.remove("key" + i);
        }
        verify(mockListener, timeout(1000).times(10)).entryAdded(argThat(event -> event.key().startsWith("key") && event.value().startsWith("value")));
        verify(mockListener, timeout(1000).times(10)).entryUpdated(argThat(event -> event.key().startsWith("key") && event.value().startsWith("newValue")));
        verify(mockListener, timeout(1000).times(10)).entryRemoved(argThat(event -> event.key().startsWith("key") && event.oldValue().startsWith("newValue")));
    }

    @Test
    void testMapListenerTriggers() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("mapListenerTest");

        MapListener mockListener = Mockito.mock(MapListener.class);
        map.addListener(mockListener);

        map.put("key1", "value1");
        verify(mockListener, timeout(1000)).entryAdded();

        map.put("key1", "value2");
        verify(mockListener, timeout(1000)).entryUpdated();

        map.remove("key1");
        verify(mockListener, timeout(1000)).entryRemoved();
    }

    @Test
    void testMapPutAndRemove() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("valuesMapTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        List<String> values = map.values().stream().toList();
        assertThat(values).hasSize(3);
        assertThat(values).contains("value1", "value2", "value3");

        map.remove("key1");

        assertThat(map.size()).isEqualTo(2);
        assertThat(map.values()).doesNotContain("value1");
        map.clear();
    }

    @Test
    void testMapClear() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("clearMapTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        assertThat(map.size()).isEqualTo(3);
        map.clear();

        assertThat(map.size()).isEqualTo(0);
    }

    @Test
    void testGetCopy() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("getCopyTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        assertThat(map.size()).isEqualTo(3);

        var mapCopy = map.getMapCopy();
        assertThat(mapCopy.size()).isEqualTo(3);
        assertThat(mapCopy.get("key1")).isEqualTo("value1");
        assertThat(mapCopy.get("key2")).isEqualTo("value2");
        assertThat(mapCopy.get("key3")).isEqualTo("value3");

        map.clear();
    }

    @Test
    void testGetAll() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("getAllTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        assertThat(map.size()).isEqualTo(3);

        var allValues = map.getAll(Set.of("key1", "key3"));
        assertThat(allValues.size()).isEqualTo(2);
        assertThat(allValues.get("key1")).isEqualTo("value1");
        assertThat(allValues.get("key2")).isNull();
        assertThat(allValues.get("key3")).isEqualTo("value3");

        map.clear();
    }

    /**
     * A value that only survives a round trip if the backend honours the object's own serialization hooks.
     *
     * <p>
     * {@code derived} is transient and rebuilt in {@code readObject}, which is a stand-in for the far less obvious case
     * that actually broke: a cached Hibernate entity whose lazy collection writes itself as uninitialized under Java
     * serialization, but is walked - and therefore initialized, from a closed session - by a reflective codec.
     */
    private static final class CustomSerializedValue implements java.io.Serializable {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private final String stored;

        private transient String derived;

        private CustomSerializedValue(String stored) {
            this.stored = stored;
            this.derived = stored.toUpperCase(java.util.Locale.ROOT);
        }

        @java.io.Serial
        private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
            in.defaultReadObject();
            derived = stored.toUpperCase(java.util.Locale.ROOT);
        }
    }

    @Test
    void testValueWithCustomSerializationSurvivesARoundTrip() {
        // Every backend must serialize the way Hazelcast does, because Hazelcast is the default and everything stored
        // has to be java.io.Serializable for it. A reflective codec that ignores readObject silently returns a
        // half-built object instead, which is how a Redis deployment answered 500 for every read of a user's saved
        // posts while Hazelcast was fine.
        DistributedMap<String, CustomSerializedValue> map = getDistributedDataProvider().getMap("customSerializationTest");
        map.put("key", new CustomSerializedValue("value"));

        CustomSerializedValue roundTripped = map.get("key");
        assertThat(roundTripped).isNotNull();
        assertThat(roundTripped.stored).isEqualTo("value");
        assertThat(roundTripped.derived).as("the backend must honour readObject rather than copying fields reflectively").isEqualTo("VALUE");

        map.clear();
    }

    @Test
    void testGetAllOmitsAbsentKeys() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("getAllAbsentKeyTest");
        map.put("present", "value");

        // An absent key must be left out rather than mapped to null: a caller iterating the result would otherwise see a
        // null value on one backend and no entry at all on another.
        var values = map.getAll(Set.of("present", "absent"));
        assertThat(values).containsExactly(Map.entry("present", "value"));

        map.clear();
    }

    @Test
    void testSpringCacheStoresReadsAndEvicts() {
        Cache cache = springCacheManager().getCache("springCacheRoundTripTest");
        assertThat(cache).isNotNull();
        assertThat(cache.get("missing")).as("a key that was never written must read as absent").isNull();

        cache.put("key", "value");
        assertThat(cache.get("key").get()).isEqualTo("value");
        assertThat(cache.get("key", String.class)).isEqualTo("value");

        assertThat(cache.putIfAbsent("key", "other").get()).as("putIfAbsent must not overwrite and must report the existing value").isEqualTo("value");
        assertThat(cache.get("key").get()).isEqualTo("value");

        assertThat(cache.evictIfPresent("key")).isTrue();
        assertThat(cache.get("key")).isNull();
        assertThat(cache.evictIfPresent("key")).as("evicting an absent key must report that nothing was removed").isFalse();
    }

    @Test
    void testSpringCacheValueLoaderRunsOnceAndIsThenServedFromTheCache() {
        // The loader path is what @Cacheable(sync = true) uses. It has to hold the map's per-key lock so that a slow
        // loader runs once for the cluster rather than once per node, which means a backend whose lock is not reentrant
        // for the same thread would deadlock here rather than fail an assertion.
        Cache cache = springCacheManager().getCache("springCacheValueLoaderTest");
        AtomicInteger loaderInvocations = new AtomicInteger();

        assertThat(cache.get("key", () -> {
            loaderInvocations.incrementAndGet();
            return "loaded";
        })).isEqualTo("loaded");
        assertThat(cache.get("key", () -> {
            loaderInvocations.incrementAndGet();
            return "recomputed";
        })).as("a cached value must be returned without running the loader again").isEqualTo("loaded");
        assertThat(loaderInvocations.get()).isEqualTo(1);

        cache.clear();
    }

    @Test
    void testSpringCacheStoresNullValues() {
        // Several @Cacheable methods have no "unless = #result == null" guard and rely on a cached null, while the
        // backends reject a null value outright. A cached null must therefore read back as a present, null-valued entry.
        Cache cache = springCacheManager().getCache("springCacheNullValueTest");
        cache.put("nullKey", null);

        assertThat(cache.get("nullKey")).as("a cached null must be a present entry, not a miss").isNotNull();
        assertThat(cache.get("nullKey").get()).isNull();

        cache.clear();
    }

    @Test
    void testSpringCacheEnumeratesKeysForPrefixInvalidation() {
        // The course notification caches are invalidated by key prefix, which needs the keys the cache currently holds.
        Cache cache = springCacheManager().getCache("springCacheKeyEnumerationTest");
        cache.put("user_1_course_2_page0", "cached");
        cache.put("user_1_course_2_page1", "cached");
        cache.put("user_9_course_2_page0", "cached");

        assertThat(cache).isInstanceOf(DistributedDataCache.class);
        assertThat(((DistributedDataCache) cache).cacheKeys()).containsExactlyInAnyOrder("user_1_course_2_page0", "user_1_course_2_page1", "user_9_course_2_page0");

        cache.clear();
    }

    private DistributedDataCacheManager springCacheManager() {
        return new DistributedDataCacheManager(getDistributedDataProvider(), Map.of());
    }

    @Test
    void testMapEntrySet() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("entrySetTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        Set<Map.Entry<String, String>> entrySet = map.entrySet();

        assertThat(entrySet).hasSize(3);
        assertThat(entrySet).anySatisfy(entry -> {
            assertThat(entry.getKey()).isEqualTo("key1");
            assertThat(entry.getValue()).isEqualTo("value1");
        });
        assertThat(entrySet).anySatisfy(entry -> {
            assertThat(entry.getKey()).isEqualTo("key2");
            assertThat(entry.getValue()).isEqualTo("value2");
        });
        assertThat(entrySet).anySatisfy(entry -> {
            assertThat(entry.getKey()).isEqualTo("key3");
            assertThat(entry.getValue()).isEqualTo("value3");
        });

        map.clear();
    }

    @Test
    void testMapKeySet() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("keySetTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        Set<String> keySet = map.keySet();

        assertThat(keySet).hasSize(3);
        assertThat(keySet).containsExactlyInAnyOrder("key1", "key2", "key3");

        map.clear();
    }

    @Test
    void testPublishSubscribe() {
        DistributedTopic<String> topic = getDistributedDataProvider().getTopic("testTopic");

        Consumer<String> mockConsumer = Mockito.mock(Consumer.class);
        var listenerId = topic.addMessageListener(mockConsumer);
        topic.publish("Hello, World!");
        topic.publish("Another message");

        verify(mockConsumer, timeout(500)).accept("Hello, World!");
        verify(mockConsumer, timeout(500)).accept("Another message");

        topic.removeMessageListener(listenerId);

        topic.publish("Unsubscribed Already");
        verify(mockConsumer, timeout(1000).times(2)).accept(anyString()); // still only 2 trigger
    }

    @Test
    void testTopicTriggerCount() {
        DistributedTopic<String> topic = getDistributedDataProvider().getTopic("testTopicTriggerCount");

        Consumer<String> mockConsumer = Mockito.mock(Consumer.class);
        topic.addMessageListener(mockConsumer);
        for (int i = 0; i < 50; i++) {
            topic.publish("Message" + i);
        }
        verify(mockConsumer, timeout(1000).times(50)).accept(argThat(msg -> msg.startsWith("Message")));
    }

    /**
     * Builds a {@link BuildJobQueueItem} with the given priority, reusing the shared factory for every other field.
     * {@link BuildJobQueueItem#compareTo} orders by priority first, so differing priorities make the expected order
     * unambiguous without depending on the submission-date tie-break.
     *
     * @param priority the build job priority (lower sorts first)
     * @return a build job queue item with the given priority
     */
    private static BuildJobQueueItem buildJobWithPriority(int priority) {
        BuildJobQueueItem base = createBaseBuildJobQueueItemForTrigger();
        return new BuildJobQueueItem(base.id(), base.name(), base.buildAgent(), base.participationId(), base.courseId(), base.exerciseId(), base.retryCount(), priority,
                base.status(), base.repositoryInfo(), base.jobTimingInfo(), base.buildConfig(), base.submissionResult());
    }

    /**
     * Whether this backend can order an arbitrarily named priority queue by the items' natural ordering.
     *
     * <p>
     * The Hazelcast backend cannot: its ordering comes from a comparator statically bound to a single configured queue
     * name, so it overrides this to {@code false} and asserts the fail-fast behaviour separately.
     *
     * @return true if any queue name can be used as a priority queue
     */
    protected boolean supportsPriorityQueueForArbitraryNames() {
        return true;
    }

    @Test
    void testPriorityQueuePollsInPriorityOrder() {
        assumeTrue(supportsPriorityQueueForArbitraryNames(), "backend does not support priority ordering for arbitrary queue names");
        DistributedQueue<BuildJobQueueItem> queue = getDistributedDataProvider().getPriorityQueue("testPriorityOrderQueue");
        queue.clear();

        // deliberately inserted out of priority order
        queue.add(buildJobWithPriority(5));
        queue.add(buildJobWithPriority(1));
        queue.add(buildJobWithPriority(3));

        assertThat(queue.poll().priority()).isEqualTo(1);
        assertThat(queue.poll().priority()).isEqualTo(3);
        assertThat(queue.poll().priority()).isEqualTo(5);
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void testPriorityQueueGetAllIsInPriorityOrder() {
        assumeTrue(supportsPriorityQueueForArbitraryNames(), "backend does not support priority ordering for arbitrary queue names");
        DistributedQueue<BuildJobQueueItem> queue = getDistributedDataProvider().getPriorityQueue("testPriorityGetAllQueue");
        queue.clear();

        queue.add(buildJobWithPriority(5));
        queue.add(buildJobWithPriority(1));
        queue.add(buildJobWithPriority(3));

        // getAll backs the admin build queue display, so it must agree with the order jobs are dequeued in
        assertThat(queue.getAll()).extracting(BuildJobQueueItem::priority).containsExactly(1, 3, 5);

        queue.clear();
    }

    /**
     * Time-to-live values here are deliberately at least a second. Sub-second lifetimes race the assertion itself on
     * Hazelcast, whose put path is slow enough that an entry can legitimately expire before the following read, and no
     * production cache uses a lifetime that short anyway (the shortest is the 60s node-metrics map).
     */
    @Test
    void testExpiringMapEntryIsGoneAfterTimeToLive() {
        DistributedMap<String, String> map = getDistributedDataProvider().getExpiringMap("expiringMapTest", Duration.ofSeconds(1));
        map.clear();

        map.put("shortLived", "value");

        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> assertThat(map.get("shortLived")).isNull());
    }

    @Test
    void testExpiringMapAppliesPerEntryTimeToLive() {
        DistributedMap<String, String> map = getDistributedDataProvider().getExpiringMap("perEntryTtlMapTest", Duration.ofMinutes(10));
        map.clear();

        // An explicit per-entry TTL must win over the map default, otherwise callers cannot mix lifetimes in one map.
        map.put("shortLived", "value", Duration.ofSeconds(1));
        map.put("longLived", "value");

        // Also covers that an expiring map stores readable entries at all: longLived uses the generous map default.
        assertThat(map.get("longLived")).as("entry stored with the map default must be readable").isEqualTo("value");

        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> assertThat(map.get("shortLived")).isNull());
        assertThat(map.get("longLived")).as("the entry using the map default must outlive the short-lived one").isEqualTo("value");

        map.clear();
    }

    @Test
    void testNonExpiringMapRejectsPerEntryTimeToLive() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("nonExpiringMapTest");

        // Silently ignoring the TTL would leave callers believing entries expire when they never do.
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> map.put("key", "value", Duration.ofSeconds(1)));
    }

    @Test
    void testMapPutIfAbsentOnlyStoresWhenKeyIsFree() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("putIfAbsentMapTest");
        map.clear();

        assertThat(map.putIfAbsent("key", "first")).as("storing into a free key reports no previous value").isNull();
        assertThat(map.putIfAbsent("key", "second")).as("a second attempt reports the value that won").isEqualTo("first");
        assertThat(map.get("key")).isEqualTo("first");

        map.clear();
    }

    @Test
    void testMapConditionalRemoveOnlyRemovesMatchingValue() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("conditionalRemoveMapTest");
        map.clear();
        map.put("key", "current");

        assertThat(map.remove("key", "stale")).as("an entry replaced by another node must not be removed").isFalse();
        assertThat(map.get("key")).isEqualTo("current");

        assertThat(map.remove("key", "current")).isTrue();
        assertThat(map.get("key")).isNull();
    }

    @Test
    void testMapComputeIfAbsentStoresAndThenReuses() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("computeIfAbsentMapTest");
        map.clear();

        assertThat(map.computeIfAbsent("key", key -> "computed-" + key)).isEqualTo("computed-key");
        // The second call must reuse the stored value instead of recomputing over it.
        assertThat(map.computeIfAbsent("key", key -> "recomputed")).isEqualTo("computed-key");
        assertThat(map.get("key")).isEqualTo("computed-key");

        assertThat(map.computeIfAbsent("nullKey", key -> null)).as("a mapping function returning null stores nothing").isNull();
        assertThat(map.containsKey("nullKey")).isFalse();

        map.clear();
    }

    @Test
    void testMapContainsKeyIsEmptyAndPutAll() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("bulkMapTest");
        map.clear();

        assertThat(map.isEmpty()).isTrue();
        assertThat(map.containsKey("a")).isFalse();

        map.putAll(Map.of("a", "1", "b", "2"));

        assertThat(map.isEmpty()).isFalse();
        assertThat(map.containsKey("a")).isTrue();
        assertThat(map.containsKey("b")).isTrue();
        assertThat(map.size()).isEqualTo(2);

        map.clear();
        assertThat(map.isEmpty()).isTrue();
    }

    @Test
    void testExpiringMapPutIfAbsentAppliesDefaultTimeToLive() {
        DistributedMap<String, String> map = getDistributedDataProvider().getExpiringMap("expiringPutIfAbsentTest", Duration.ofSeconds(1));
        map.clear();

        // A claim made through putIfAbsent must not outlive the map, otherwise a crashed claimant blocks the key forever.
        assertThat(map.putIfAbsent("claim", "owner")).isNull();

        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> assertThat(map.get("claim")).isNull());
    }

    @Test
    void testDistributedLockTryLockSucceedsWhenFree() {
        DistributedLock lock = getDistributedDataProvider().getLock("freeTryLockTest");

        assertThat(lock.tryLock(Duration.ofSeconds(5))).isTrue();
        lock.unlock();

        // Releasing must make it available again rather than leaving it wedged.
        assertThat(lock.tryLock(Duration.ofSeconds(5))).isTrue();
        lock.unlock();
    }

    @Test
    void testDistributedSetAddRemoveContains() {
        DistributedSet<Long> set = getDistributedDataProvider().getSet("distributedSetTest");
        set.clear();

        assertThat(set.isEmpty()).isTrue();
        assertThat(set.add(42L)).as("adding a new element reports a change").isTrue();
        assertThat(set.add(42L)).as("adding an element twice reports no change").isFalse();

        assertThat(set.contains(42L)).isTrue();
        assertThat(set.contains(7L)).isFalse();
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.getAll()).containsExactly(42L);

        assertThat(set.remove(42L)).isTrue();
        assertThat(set.remove(42L)).as("removing an absent element reports no change").isFalse();
        assertThat(set.isEmpty()).isTrue();
    }

    @Test
    void testDistributedSetClear() {
        DistributedSet<Long> set = getDistributedDataProvider().getSet("distributedSetClearTest");
        set.clear();

        set.add(1L);
        set.add(2L);
        assertThat(set.size()).isEqualTo(2);

        set.clear();
        assertThat(set.isEmpty()).isTrue();
        assertThat(set.getAll()).isEmpty();
    }

    @Test
    void testDistributedLockIsMutuallyExclusive() throws InterruptedException {
        DistributedLock lock = getDistributedDataProvider().getLock("distributedLockTest");
        CountDownLatch contenderAcquiredLock = new CountDownLatch(1);

        lock.lock();
        Thread contender = new Thread(() -> {
            DistributedLock sameLock = getDistributedDataProvider().getLock("distributedLockTest");
            sameLock.lock();
            try {
                contenderAcquiredLock.countDown();
            }
            finally {
                sameLock.unlock();
            }
        }, "distributed-lock-contender");
        contender.start();

        try {
            assertThat(contenderAcquiredLock.await(500, TimeUnit.MILLISECONDS)).as("another thread must not hold the lock concurrently").isFalse();
        }
        finally {
            lock.unlock();
        }

        assertThat(contenderAcquiredLock.await(5, TimeUnit.SECONDS)).as("another thread must acquire the lock once released").isTrue();
        contender.join(5000);
    }

    @Test
    void testDistributedLockTryLockFailsWhileHeldElsewhere() throws InterruptedException {
        DistributedLock lock = getDistributedDataProvider().getLock("distributedTryLockTest");
        CountDownLatch tryLockOutcomeKnown = new CountDownLatch(1);
        AtomicBoolean acquiredWhileHeld = new AtomicBoolean(true);

        lock.lock();
        Thread contender = new Thread(() -> {
            DistributedLock sameLock = getDistributedDataProvider().getLock("distributedTryLockTest");
            boolean acquired = sameLock.tryLock(Duration.ofMillis(200));
            acquiredWhileHeld.set(acquired);
            if (acquired) {
                sameLock.unlock();
            }
            tryLockOutcomeKnown.countDown();
        }, "distributed-try-lock-contender");
        contender.start();

        try {
            assertThat(tryLockOutcomeKnown.await(10, TimeUnit.SECONDS)).as("tryLock must return within its timeout").isTrue();
            assertThat(acquiredWhileHeld.get()).as("tryLock must not succeed while the lock is held by another thread").isFalse();
        }
        finally {
            lock.unlock();
        }
        contender.join(5000);
    }

    /**
     * A reliable topic must behave like a plain topic for the straightforward case. Its added value (surviving a slow or
     * briefly disconnected subscriber) cannot be provoked from a single-process test, so it is not asserted here.
     */
    @Test
    void testReliableTopicDeliversMessages() {
        DistributedTopic<String> topic = getDistributedDataProvider().getReliableTopic("reliableTopicTest");

        Consumer<String> mockConsumer = Mockito.mock(Consumer.class);
        var listenerId = topic.addMessageListener(mockConsumer);

        topic.publish("first");
        topic.publish("second");

        verify(mockConsumer, timeout(5000)).accept("first");
        verify(mockConsumer, timeout(5000)).accept("second");

        topic.removeMessageListener(listenerId);
    }

    /**
     * The scheduling topics carry {@code Long} and {@code Long[]} payloads, so a codec that mangled either would break
     * scheduling silently. This round-trips both shapes rather than only the String used elsewhere.
     */
    @Test
    void testReliableTopicRoundTripsSchedulingPayloadTypes() {
        DistributedTopic<Long> singleValueTopic = getDistributedDataProvider().getReliableTopic("reliableTopicLongTest");
        Consumer<Long> singleValueConsumer = Mockito.mock(Consumer.class);
        singleValueTopic.addMessageListener(singleValueConsumer);
        singleValueTopic.publish(4711L);
        verify(singleValueConsumer, timeout(5000)).accept(4711L);

        DistributedTopic<Long[]> arrayTopic = getDistributedDataProvider().getReliableTopic("reliableTopicLongArrayTest");
        Consumer<Long[]> arrayConsumer = Mockito.mock(Consumer.class);
        arrayTopic.addMessageListener(arrayConsumer);
        arrayTopic.publish(new Long[] { 1L, 2L, 3L });
        verify(arrayConsumer, timeout(5000)).accept(argThat(payload -> payload.length == 3 && payload[0] == 1L && payload[2] == 3L));
    }

    @Test
    void testExpiringMapRejectsNonPositiveDefaultTimeToLive() {
        // A zero or negative lifetime means "never expires" on the backends, which would turn an expiring map into a
        // permanent one without any signal at the call site.
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> getDistributedDataProvider().getExpiringMap("zeroTtlMapTest", Duration.ZERO));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> getDistributedDataProvider().getExpiringMap("negativeTtlMapTest", Duration.ofSeconds(-1)));
    }

    /**
     * The observed client addresses back the build agent origin check, so the two ways a backend can answer must stay
     * distinguishable and must agree with the name view.
     * <p>
     * An empty {@link Optional} means "this deployment cannot observe client connections" and lets every agent clone
     * unconstrained; a present map means the backend answered and an absent client really is gone. A backend that
     * returned an empty map for a failed query would silently drop every registered address instead, so the contract
     * asserted here is that the value is never null, that every reported name is usable as a map key, and that every
     * reported address parses as a single host - the registry compares them with {@link IpAddresses#sameHost}, which
     * matches nothing at all for a value that does not.
     * <p>
     * The names must also be a subset of {@link DistributedDataProvider#getConnectedClientNames()}. A backend that
     * answered the two questions from different sources would make the same client look present to one caller and gone
     * to the other, which is exactly the disagreement the origin check cannot survive.
     */
    @Test
    void testConnectedClientAddressesAgreeWithTheConnectedClientNames() {
        Optional<Map<String, Set<String>>> observedAddresses = getDistributedDataProvider().getConnectedClientAddresses();
        assertThat(observedAddresses).as("a provider must answer this question rather than return null").isNotNull();

        if (observedAddresses.isEmpty()) {
            // A supported answer: this node cannot observe client connections, so no agent's origin can be constrained
            return;
        }

        Map<String, Set<String>> addressesByClientName = observedAddresses.get();
        assertThat(addressesByClientName.keySet()).allSatisfy(clientName -> assertThat(clientName).as("a client name keys the registry and must be usable").isNotBlank());
        assertThat(addressesByClientName.values()).allSatisfy(addresses -> assertThat(addresses)
                .allSatisfy(address -> assertThat(IpAddresses.canonical(address)).as("'%s' must denote a single host, or it can never match a request", address).isNotNull()));
        assertThat(getDistributedDataProvider().getConnectedClientNames()).as("the address view and the name view must not disagree about who is connected")
                .containsAll(addressesByClientName.keySet());
    }

    /**
     * Whether an observed client address may be used to authorize that client's git requests.
     * <p>
     * It may only when the client's connection to the middleware terminates on a core node, because then it is the same
     * path the clone takes. Hazelcast clients connect to the cluster members, which are those nodes; Redis is a
     * separate service, and the addresses genuinely differ - with Redis in a container and the nodes on the host, one
     * side sees the docker bridge gateway and the other loopback. Enforcing that comparison refused every clone in a
     * multi-node run, so the answer is asserted per backend rather than assumed.
     */
    @Test
    void testClientAddressesAreOnlyUsableForAuthorizationWhereClientsReachCoreNodes() {
        assertThat(getDistributedDataProvider().clientsConnectDirectlyToCoreNodes()).isEqualTo(clientsReachCoreNodesDirectly());
    }

    /**
     * @return whether this backend's clients connect to a core node rather than to a separate middleware service
     */
    protected abstract boolean clientsReachCoreNodesDirectly();

    @Test
    void testMapLockIsMutuallyExclusive() throws InterruptedException {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("lockTestMap");
        CountDownLatch contenderAcquiredLock = new CountDownLatch(1);

        map.lock("lockedKey");
        Thread contender = new Thread(() -> {
            map.lock("lockedKey");
            try {
                contenderAcquiredLock.countDown();
            }
            finally {
                map.unlock("lockedKey");
            }
        }, "distributed-map-lock-contender");
        contender.start();

        try {
            assertThat(contenderAcquiredLock.await(500, TimeUnit.MILLISECONDS)).as("another thread must not acquire the lock while it is held").isFalse();
        }
        finally {
            map.unlock("lockedKey");
        }

        assertThat(contenderAcquiredLock.await(5, TimeUnit.SECONDS)).as("another thread must acquire the lock once it is released").isTrue();
        contender.join(5000);
    }
}
