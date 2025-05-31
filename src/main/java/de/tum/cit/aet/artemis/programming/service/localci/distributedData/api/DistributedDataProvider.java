package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api;

import java.util.Set;

public interface DistributedDataProvider {

    <T> DistributedQueue<T> getQueue(String name);

    <K, V> DistributedMap<K, V> getMap(String name);

    <T> DistributedTopic<T> getTopic(String name);

    boolean isInstanceRunning();

    String getLocalMemberAddress();

    Set<String> getClusterMemberAddresses();

    boolean noDataMemberInClusterAvailable();
}
