package de.tum.in.www1.artemis.domain.exam.monitoring.factory;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActionFactory;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.ConnectionUpdatedAction;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.ConnectionUpdatedActionDTO;

@Service
public class ConnectionUpdatedActionFactory implements ExamActionFactory {

    @Override
    public ExamAction create(ExamActionDTO examActionDTO) {
        return new ConnectionUpdatedAction(((ConnectionUpdatedActionDTO) examActionDTO).isConnected());
    }

    @Override
    public boolean match(ExamActionType examActionType) {
        return examActionType == ExamActionType.CONNECTION_UPDATED;
    }
}
