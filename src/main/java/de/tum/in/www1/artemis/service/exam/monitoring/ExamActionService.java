package de.tum.in.www1.artemis.service.exam.monitoring;

import java.util.Collection;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.repository.ExamActionRepository;

@Service
public class ExamActionService {

    private final ExamActionRepository examActionRepository;

    public ExamActionService(ExamActionRepository examActionRepository) {
        this.examActionRepository = examActionRepository;
    }

    /**
     * To avoid direct access to the {@link ExamActionRepository}, we use delegation save the {@link ExamAction}.
     *
     * @param examAction {@link ExamAction} to save
     * @return saved {@link ExamAction}
     */
    public ExamAction save(ExamAction examAction) {
        return this.examActionRepository.save(examAction);
    }

    /**
     * To avoid direct access to the {@link ExamActionRepository}, we use delegation save the {@link ExamAction}s.
     *
     * @param examActions {@link ExamAction}s to save
     * @return saved {@link ExamAction}s
     */
    public Collection<ExamAction> saveAll(Collection<ExamAction> examActions) {
        return this.examActionRepository.saveAll(examActions);
    }

    /**
     * To avoid direct access to the {@link ExamActionRepository}, we use delegation to find the {@link ExamAction} by the activity id.
     *
     * @param examActivityId of the ExamActivity
     * @return saved {@link ExamAction}
     */
    public ExamAction findByExamActivityId(Long examActivityId) {
        return examActionRepository.findByExamActivityId(examActivityId);
    }
}
