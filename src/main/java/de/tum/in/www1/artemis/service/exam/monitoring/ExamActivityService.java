package de.tum.in.www1.artemis.service.exam.monitoring;

import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.repository.ExamActivityRepository;

@Service
public class ExamActivityService {

    private final ExamActivityRepository examActivityRepository;

    private final ExamCache examCache;

    public ExamActivityService(ExamActivityRepository examActivityRepository, HazelcastInstance hazelcastInstance) {
        this.examActivityRepository = examActivityRepository;
        this.examCache = new ExamCache(hazelcastInstance);
    }

    public ExamActivity save(ExamActivity examActivity) {
        return this.examActivityRepository.save(examActivity);
    }

    /**
     * Configures Hazelcast for the ExamActivityService before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the ExamActivityService-specific configuration should be added to
     */
    public static void configureHazelcast(Config config) {
        ExamCache.configureHazelcast(config);
    }

    public void updateExamActivity(Long examId, long studentExamId, ExamActivity examActivity) {
        if (examActivity != null) {
            examCache.getTransientWriteCacheFor(examId).getActivities().put(studentExamId, examActivity);
        }
    }

    public void addExamAction(Long examId, long studentExamId, ExamAction examAction) {
        if (examAction != null) {
            ExamActivity activity = examCache.getTransientWriteCacheFor(examId).getActivities().get(studentExamId);
            if (activity == null) {
                activity = new ExamActivity();
                updateExamActivity(examId, studentExamId, activity);
            }
            activity.addExamAction(examAction);
        }
    }
}
