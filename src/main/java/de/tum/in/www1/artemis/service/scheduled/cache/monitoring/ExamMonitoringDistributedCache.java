package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.internal.serialization.impl.SerializationServiceV1;
import com.hazelcast.map.IMap;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

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

    /**
     * Make sure this is a class of SUPPORTED_LIST_CLASSES to make easy serialization possible, see
     * {@link SerializationServiceV1}
     */
    List<ScheduledTaskHandler> examActivitySaveHandler;

    private transient Exam exam;

    private transient IMap<Long, ExamActivity> activities;

    public ExamMonitoringDistributedCache(Long examId, List<ScheduledTaskHandler> examActivitySaveHandler, Exam exam) {
        super(Objects.requireNonNull(examId, "examId must not be null"));
        setExam(exam);
        setExamActivitySaveHandler(examActivitySaveHandler);
        log.debug("Creating new ExamMonitoringDistributedCache, id {}", getExamId());
    }

    public ExamMonitoringDistributedCache(Long examId, List<ScheduledTaskHandler> examActivitySaveHandler) {
        this(examId, examActivitySaveHandler, null);
    }

    public ExamMonitoringDistributedCache(Long examId) {
        this(examId, getEmptyExamActivitySaveHandler(), null);
    }

    @Override
    Exam getExam() {
        return exam;
    }

    @Override
    void setExam(Exam exam) {
        this.exam = exam;
    }

    @Override
    Map<Long, ExamActivity> getActivities() {
        return activities;
    }

    @Override
    List<ScheduledTaskHandler> getExamActivitySaveHandler() {
        return examActivitySaveHandler;
    }

    @Override
    void setExamActivitySaveHandler(List<ScheduledTaskHandler> examActivitySaveHandler) {
        this.examActivitySaveHandler = examActivitySaveHandler;
    }

    @Override
    public void clear() {
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

    static class ExamMonitoringDistributedCacheStreamSerializer implements StreamSerializer<ExamMonitoringDistributedCache> {

        @Override
        public int getTypeId() {
            return Constants.HAZELCAST_MONITORING_CACHE_SERIALIZER_ID;
        }

        @Override
        public void write(ObjectDataOutput out, ExamMonitoringDistributedCache examMonitoringDistributedCache) throws IOException {
            out.writeLong(examMonitoringDistributedCache.getExamId());
            out.writeObject(examMonitoringDistributedCache.getExamActivitySaveHandler());
        }

        @Override
        public @NotNull ExamMonitoringDistributedCache read(ObjectDataInput in) throws IOException {
            Long examId = in.readLong();
            List<ScheduledTaskHandler> examActivitySaveHandler = in.readObject();

            // see class JavaDoc why the exercise is null here.
            return new ExamMonitoringDistributedCache(examId, examActivitySaveHandler, null);
        }
    }

    static void registerSerializer(Config config) {
        SerializerConfig serializerConfig = new SerializerConfig();
        serializerConfig.setTypeClass(ExamMonitoringDistributedCache.class);
        serializerConfig.setImplementation(new ExamMonitoringDistributedCacheStreamSerializer());
        config.getSerializationConfig().addSerializerConfig(serializerConfig);
    }
}
