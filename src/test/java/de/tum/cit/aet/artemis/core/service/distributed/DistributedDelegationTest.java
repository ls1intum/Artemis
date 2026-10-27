package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.junit.jupiter.api.Test;
import org.redisson.api.RSet;

import de.tum.cit.aet.artemis.core.service.distributed.api.lock.AbstractDistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.AbstractDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.set.AbstractDistributedSet;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalSet;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedSet;

class DistributedDelegationTest {

    @Test
    @SuppressWarnings("unchecked")
    void mapKeepsLiveViewsAndDetachesCopies() {
        var nativeMap = new HashMap<String, String>();
        nativeMap.put("first", "one");
        AbstractDistributedMap<String, String> map = mock(AbstractDistributedMap.class, withSettings().useConstructor(nativeMap).defaultAnswer(CALLS_REAL_METHODS));
        var values = map.values();
        var copy = map.getMapCopy();
        nativeMap.put("second", "two");
        assertThat(map.size()).isEqualTo(2);
        assertThat(values).containsExactlyInAnyOrder("one", "two");
        assertThat(map.keySet()).containsExactlyInAnyOrder("first", "second");
        assertThat(map.entrySet()).containsExactlyInAnyOrderElementsOf(nativeMap.entrySet());
        assertThat(copy).containsOnlyKeys("first");
        map.clear();
        assertThat(nativeMap).isEmpty();
        assertThat(values).isEmpty();
        assertThat(copy).containsEntry("first", "one");
    }

    @Test
    void setDelegatesMutationsAndReturnsDetachedSnapshot() {
        Set<String> nativeSet = new HashSet<>();
        var set = new AbstractDistributedSet<>(nativeSet) {
        };
        assertThat(set.isEmpty()).isTrue();
        assertThat(set.add("first")).isTrue();
        assertThat(set.add("first")).isFalse();
        assertThat(set.contains("first")).isTrue();
        assertThat(set.size()).isEqualTo(1);
        var snapshot = set.getAll();
        assertThat(set.remove("first")).isTrue();
        assertThat(snapshot).containsExactly("first");
        set.add("second");
        set.clear();
        assertThat(nativeSet).isEmpty();
    }

    @Test
    void localSetsKeepIndependentState() {
        var first = new LocalSet<String>();
        var second = new LocalSet<String>();
        first.add("first");
        assertThat(second.getAll()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisRetainsNativeSnapshotAndDeleteOperations() {
        RSet<String> nativeSet = mock(RSet.class);
        when(nativeSet.readAll()).thenReturn(Set.of("first"));
        var set = new RedissonDistributedSet<>(nativeSet);
        assertThat(set.getAll()).containsExactly("first");
        set.clear();
        verify(nativeSet).delete();
    }

    @Test
    void lockPreservesTimeoutAndInterruption() throws Exception {
        Lock nativeLock = mock(Lock.class);
        var lock = new AbstractDistributedLock(nativeLock) {
        };
        lock.lock();
        lock.unlock();
        verify(nativeLock).lock();
        verify(nativeLock).unlock();
        when(nativeLock.tryLock(250, TimeUnit.MILLISECONDS)).thenReturn(true);
        assertThat(lock.tryLock(Duration.ofMillis(250))).isTrue();
        when(nativeLock.tryLock(250, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());
        try {
            assertThat(lock.tryLock(Duration.ofMillis(250))).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }
}
