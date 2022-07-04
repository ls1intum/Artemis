package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.io.Serial;
import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.scheduledexecutor.NamedTask;
import com.hazelcast.spring.context.SpringAware;

import de.tum.in.www1.artemis.config.Constants;

@SpringAware
final class ExamActivitySaveTask implements Runnable, Serializable, NamedTask {

    static final String HAZELCAST_EXAM_ACTIVITY_SAVE_TASK = "-save";

    @Serial
    private static final long serialVersionUID = 1L;

    final Long examId;

    @Autowired // ok
    transient ExamMonitoringScheduleService examMonitoringScheduleService;

    ExamActivitySaveTask(Long examId) {
        this.examId = examId;
    }

    @Override
    public void run() {
        // Saves the performed action after the exam (to avoid any issues with the database during the exam)
        examMonitoringScheduleService.executeExamActivitySaveTask(examId);
    }

    @Override
    public String getName() {
        return Constants.HAZELCAST_MONITORING_PREFIX + examId + HAZELCAST_EXAM_ACTIVITY_SAVE_TASK;
    }
}
