package de.tum.in.www1.artemis.domain.exam.monitoring;

import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;

public interface ExamActionFactory {

    ExamAction create(ExamActionDTO examActionDTO);

    boolean match(ExamActionType examActionType);
}
