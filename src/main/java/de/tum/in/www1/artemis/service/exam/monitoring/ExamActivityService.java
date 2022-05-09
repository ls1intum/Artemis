package de.tum.in.www1.artemis.service.exam.monitoring;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.repository.ExamActivityRepository;

@Service
public class ExamActivityService {

    private final ExamActivityRepository examActivityRepository;

    public ExamActivityService(ExamActivityRepository examActivityRepository) {
        this.examActivityRepository = examActivityRepository;
    }

    /**
     * To avoid direct access to the {@link ExamActivityRepository}, we use delegation save the {@link ExamActivity}.
     * @param examActivity {@link ExamActivity} to save
     * @return saved {@link ExamActivity}
     */
    public ExamActivity save(ExamActivity examActivity) {
        return this.examActivityRepository.save(examActivity);
    }

    /**
     * To avoid direct access to the {@link ExamActivityRepository}, we use delegation save all {@link ExamActivity}s.
     * @param examActivities {@link ExamActivity}s to save
     * @return saved {@link ExamActivity}s
     */
    public List<ExamActivity> saveAll(Iterable<ExamActivity> examActivities) {
        return examActivityRepository.saveAll(examActivities);
    }
}
