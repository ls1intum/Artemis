package de.tum.in.www1.artemis.service.exam.monitoring;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActionFactory;
import de.tum.in.www1.artemis.repository.ExamActionRepository;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;

@Service
public class ExamActionService {

    private final ExamActionRepository examActionRepository;

    private final List<ExamActionFactory> examActionFactories;

    public ExamActionService(ExamActionRepository examActionRepository, List<ExamActionFactory> examActionFactories) {
        this.examActionRepository = examActionRepository;
        this.examActionFactories = examActionFactories;
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
     * This method maps the {@link ExamActionDTO} to the associated {@link ExamAction}.
     *
     * @param examActionDTO received action
     * @return mapped and transformed action
     */
    public ExamAction mapExamAction(ExamActionDTO examActionDTO) {
        Optional<ExamActionFactory> factory = examActionFactories.stream().filter((f) -> f.match(examActionDTO.getType())).findFirst();
        if (factory.isEmpty())
            return null;
        ExamAction action = factory.get().create(examActionDTO);
        if (action != null) {
            action.setTimestamp(examActionDTO.getTimestamp());
            action.setType(examActionDTO.getType());
        }
        return action;
    }
}
