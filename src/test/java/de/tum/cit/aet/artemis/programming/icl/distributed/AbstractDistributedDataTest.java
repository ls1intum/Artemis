package de.tum.cit.aet.artemis.programming.icl.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;
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

        verify(mockConsumer, timeout(100)).accept("Hello, World!");
        verify(mockConsumer, timeout(100)).accept("Another message");

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
}
