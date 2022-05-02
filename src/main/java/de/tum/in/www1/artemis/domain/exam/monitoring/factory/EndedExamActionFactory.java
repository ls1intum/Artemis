package de.tum.in.www1.artemis.domain.exam.monitoring.factory;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActionFactory;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.EndedExamAction;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;

@Service
public class EndedExamActionFactory implements ExamActionFactory {

    @Override
    public ExamAction create(ExamActionDTO examActionDTO) {
        return new EndedExamAction();
    }

    @Override
    public boolean match(ExamActionType examActionType) {
        return examActionType == ExamActionType.ENDED_EXAM;
    }
}
