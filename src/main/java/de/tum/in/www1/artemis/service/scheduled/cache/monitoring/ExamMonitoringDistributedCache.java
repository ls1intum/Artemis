package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.IMap;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;

/**
 * This class represents the cache for a single exam monitoring.
 * <p>
 */
public class ExamMonitoringDistributedCache extends ExamMonitoringCache implements HazelcastInstanceAware {

    private final Logger logger = LoggerFactory.getLogger(ExamMonitoringDistributedCache.class);

    private static final String HAZELCAST_CACHE_ACTIVITIES = "-activities";

    /**
     * This IMap is a distributed Hazelcast object and must not be (de-)serialized, it is set in the
     * setHazelcastInstance method.
     */
    private transient IMap<Long, ExamActivity> activities;

    public ExamMonitoringDistributedCache(Long examId) {
        super(Objects.requireNonNull(examId, "examId must not be null"));
        logger.debug("Creating new ExamMonitoringDistributedCache, id {}", getExamId());
    }

    @Override
    Map<Long, ExamActivity> getActivities() {
        return activities;
    }

    @Override
    public void clear() {
        int activitiesSize = activities.size();
        if (activitiesSize > 0) {
            logger.warn("Cache for Exam {} destroyed with {} activities cached", getExamId(), activitiesSize);
        }
        activities.destroy();
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        /*
         * Distributed Hazelcast objects will be automatically created and set up by Hazelcast, and are cached by the Hazelcast instance itself globally. This is a relatively
         * lightweight operation.
         */
        activities = hazelcastInstance.getMap(Constants.HAZELCAST_MONITORING_PREFIX + getExamId() + HAZELCAST_CACHE_ACTIVITIES);
    }

    static class ExamMonitoringDistributedCacheStreamSerializer implements StreamSerializer<ExamMonitoringDistributedCache> {

        @Override
        public int getTypeId() {
            return Constants.HAZELCAST_MONITORING_CACHE_SERIALIZER_ID;
        }

        @Override
        public void write(ObjectDataOutput output, ExamMonitoringDistributedCache examMonitoringDistributedCache) throws IOException {
            output.writeLong(examMonitoringDistributedCache.getExamId());
        }

        @Override
        public @NotNull ExamMonitoringDistributedCache read(ObjectDataInput input) throws IOException {
            Long examId = input.readLong();
            return new ExamMonitoringDistributedCache(examId);
        }
    }

    static void registerSerializer(Config config) {
        SerializerConfig serializerConfig = new SerializerConfig();
        serializerConfig.setTypeClass(ExamMonitoringDistributedCache.class);
        serializerConfig.setImplementation(new ExamMonitoringDistributedCacheStreamSerializer());
        config.getSerializationConfig().addSerializerConfig(serializerConfig);
    }

    @Override
    public void updateActivity(Long activityId, UnaryOperator<ExamActivity> writeOperation) {
        activities.lock(activityId);
        try {
            logger.info("Update activity {}", activityId);
            activities.set(activityId, writeOperation.apply(activities.get(activityId)));
        }
        finally {
            activities.unlock(activityId);
        }
    }
}
