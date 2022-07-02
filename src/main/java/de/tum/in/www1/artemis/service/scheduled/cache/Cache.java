package de.tum.in.www1.artemis.service.scheduled.cache;

public interface Cache {

    /**
     * Releases all (Hazelcast) resources, all cached objects will be lost.
     * <p>
     * This should only be used for exceptional cases (e.g. for testing).
     */
    void clear();

}
