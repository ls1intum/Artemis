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

    public ExamAction save(ExamAction examAction) {
        return this.examActionRepository.save(examAction);
    }

    public Collection<ExamAction> saveAll(Collection<ExamAction> examAction) {
        return this.examActionRepository.saveAll(examAction);
    }
}
