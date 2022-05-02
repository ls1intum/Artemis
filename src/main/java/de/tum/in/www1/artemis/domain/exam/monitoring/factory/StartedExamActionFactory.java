package de.tum.in.www1.artemis.domain.exam.monitoring.factory;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActionFactory;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.StartedExamAction;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.StartedExamActionDTO;
import de.tum.in.www1.artemis.service.exam.ExamSessionService;

@Service
public class StartedExamActionFactory implements ExamActionFactory {

    private final ExamSessionService examSessionService;

    public StartedExamActionFactory(ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
    }

    @Override
    public ExamAction create(ExamActionDTO examActionDTO) {
        ExamSession session = examSessionService.findById(((StartedExamActionDTO) examActionDTO).getExamSessionId()).orElse(null);
        return new StartedExamAction(session);
    }

    @Override
    public boolean match(ExamActionType examActionType) {
        return examActionType == ExamActionType.STARTED_EXAM;
    }
}
