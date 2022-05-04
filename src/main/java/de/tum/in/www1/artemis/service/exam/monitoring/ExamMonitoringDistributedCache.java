package de.tum.in.www1.artemis.service.exam.monitoring;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.internal.serialization.impl.SerializationServiceV1;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;

public class ExamMonitoringDistributedCache extends ExamMonitoringCache implements HazelcastInstanceAware {

    private static final Logger log = LoggerFactory.getLogger(ExamMonitoringDistributedCache.class);

    private static final String HAZELCAST_CACHE_ACTIVITIES = "-activities";

    /**
     * All {@link List} classes that are supported by Hazelcast {@link SerializationServiceV1}
     */
    private static final Set<Class<?>> SUPPORTED_LIST_CLASSES = Set.of(ArrayList.class, LinkedList.class, CopyOnWriteArrayList.class);

    private transient Exam exam;

    private transient IMap<Long, ExamActivity> activities;

    public ExamMonitoringDistributedCache(Long examId, Exam exam) {
        super(Objects.requireNonNull(examId, "examId must not be null"));
        setExam(exam);
        log.debug("Creating new ExamMonitoringDistributedCache, id {}", getExamId());
    }

    public ExamMonitoringDistributedCache(Long examId) {
        this(examId, null);
    }

    @Override
    Exam getExam() {
        return null;
    }

    @Override
    void setExam(Exam exam) {
        this.exam = exam;
    }

    @Override
    Map<Long, ExamActivity> getActivities() {
        return null;
    }

    @Override
    void clear() {
        int activitiesSize = activities.size();
        if (activitiesSize > 0) {
            log.warn("Cache for Exam {} destroyed with {} activities cached", getExamId(), activitiesSize);
        }
        activities.destroy();
        exam = null;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        /*
         * Distributed Hazelcast objects will be automatically created and set up by Hazelcast, and are cached by the Hazelcast instance itself globally. This is a relatively
         * lightweight operation.
         */
        activities = hazelcastInstance.getMap(Constants.HAZELCAST_MONITORING_PREFIX + getExamId() + HAZELCAST_CACHE_ACTIVITIES);
    }

    static void registerSerializer(Config config) {
        SerializerConfig serializerConfig = new SerializerConfig();
        serializerConfig.setTypeClass(ExamMonitoringDistributedCache.class);
        config.getSerializationConfig().addSerializerConfig(serializerConfig);
    }
}
