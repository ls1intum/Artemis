package de.tum.cit.aet.artemis.programming.icl.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

public abstract class AbstractDistributedDataTest {

    protected abstract DistributedDataProvider getDistributedDataProvider();

    @Test
    void testQueueListener() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueue");
        // Create a mock listener
        QueueItemListener<String> mockListener = Mockito.mock(QueueItemListener.class);
        // Register the mock listener
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
        // Register the mock listener
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
        assertEquals("a", head);
        assertEquals(1, queue.size());

        queue.poll();
        verify(mockListener, timeout(1000).times(1)).itemRemoved(argThat("a"::equals));
        assertTrue(queue.isEmpty());
    }

    @Test
    void testClearEmptiesQueue() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueClear");

        queue.add("x");
        queue.add("y");
        queue.add("z");
        assertEquals(3, queue.size());

        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertNull(queue.peek());
        assertNull(queue.poll());
    }

    @Test
    void testAddAllRemoveAllGetAll() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueAddAllRemoveAll");
        QueueItemListener<String> mockListener = Mockito.mock(QueueItemListener.class);
        queue.addItemListener(mockListener);

        List<String> items = List.of("a", "b", "c", "d");
        boolean modified = queue.addAll(items);
        assertTrue(modified);
        assertEquals(4, queue.size());
        assertIterableEquals(items, queue.getAll());
        verify(mockListener, timeout(1000).times(4)).itemAdded(argThat(s -> Set.of("a", "b", "c", "d").contains(s)));

        queue.removeAll(Set.of("b", "d"));
        assertEquals(2, queue.size());
        assertIterableEquals(List.of("a", "c"), queue.getAll());
        verify(mockListener, timeout(1000).times(2)).itemRemoved(argThat(s -> Set.of("b", "d").contains(s)));

        queue.clear();
    }

    @Test
    void testGetNameIsCorrect() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueName");
        assertEquals("testQueueName", queue.getName());
    }

    @Test
    void testIsEmptyAndSize() {
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueSizeEmpty");

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());

        queue.add("v1");
        queue.add("v2");
        assertFalse(queue.isEmpty());
        assertEquals(2, queue.size());

        queue.poll();
        assertEquals(1, queue.size());

        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
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
        DistributedQueue<String> queue = getDistributedDataProvider().getQueue("testQueueGenericListener");
        QueueListener genericListener = Mockito.mock(QueueListener.class);

        UUID id = queue.addListener(genericListener);
        assertNotNull(id);

        // Should not throw
        queue.removeListener(id);
    }

    @Test
    void testMapListenerTriggers() {
        DistributedMap<String, String> someMap = getDistributedDataProvider().getMap("someMap");

        // Create a mock listener
        MapEntryListener<String, String> mockListener = Mockito.mock(MapEntryListener.class);

        // Register the mock listener
        someMap.addEntryListener(mockListener);

        // Test add operation
        someMap.put("key1", "value1");
        verify(mockListener, timeout(1000)).entryAdded(argThat(event -> "key1".equals(event.key()) && "value1".equals(event.value())));

        // Test update operation
        someMap.put("key1", "value2");
        verify(mockListener, timeout(1000)).entryUpdated(argThat(event -> "key1".equals(event.key()) && "value2".equals(event.value()) && "value1".equals(event.oldValue())));

        // Test remove operation
        someMap.remove("key1");
        verify(mockListener, timeout(1000)).entryRemoved(argThat(event -> "key1".equals(event.key()) && "value2".equals(event.oldValue())));
    }

    @Test
    void testMapListenerTriggerCount() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("testMap");

        // Create a mock listener
        MapEntryListener<String, String> mockListener = Mockito.mock(MapEntryListener.class);

        // Register the mock listener
        map.addEntryListener(mockListener);

        // Test add operation
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
    void testMapPutAndRemove() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("valuesMapTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        List<String> values = map.values().stream().toList();
        assert values.size() == 3;
        assert values.contains("value1");
        assert values.contains("value2");
        assert values.contains("value3");

        map.remove("key1");

        assert map.size() == 2;
        assert !map.values().contains("value1");
        map.clear();
    }

    @Test
    void testMapClear() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("clearMapTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        assert map.size() == 3;
        map.clear();

        assert map.size() == 0;
    }

    @Test
    void testGetCopy() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("getCopyTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        assert map.size() == 3;

        var mapCopy = map.getMapCopy();
        assert mapCopy.size() == 3;
        assert mapCopy.get("key1").equals("value1");
        assert mapCopy.get("key2").equals("value2");
        assert mapCopy.get("key3").equals("value3");

        map.clear();
    }

    @Test
    void testGetAll() {
        DistributedMap<String, String> map = getDistributedDataProvider().getMap("getAllTest");
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        assert map.size() == 3;

        var allValues = map.getAll(Set.of("key1", "key3"));
        assert allValues.size() == 2;
        assert allValues.get("key1").equals("value1");
        assert allValues.get("key3").equals("value3");

        map.clear();
    }

    @Test
    void testPublishSubscribe() {
        DistributedTopic<String> topic = getDistributedDataProvider().getTopic("testTopic");

        // Create a mock listener
        Consumer<String> mockConsumer = Mockito.mock(Consumer.class);
        topic.addMessageListener(mockConsumer);
        topic.publish("Hello, World!");

        // Verify that the mock listener received the message
        verify(mockConsumer, timeout(1000)).accept("Hello, World!");

    }
}
