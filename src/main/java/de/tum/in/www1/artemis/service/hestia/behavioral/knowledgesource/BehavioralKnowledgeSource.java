package de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralSolutionEntryGenerationException;

public abstract class BehavioralKnowledgeSource {

    protected BehavioralBlackboard blackboard;

    public BehavioralKnowledgeSource(BehavioralBlackboard blackboard) {
        this.blackboard = blackboard;
    }

    /**
     * Checks if the knowledge source can be applied
     *
     * @return true if the knowledge source can be applied
     */
    public abstract boolean executeCondition();

    /**
     * Applies this knowledge source to the blackboard
     *
     * @return true if changes were made
     */
    public abstract boolean executeAction() throws BehavioralSolutionEntryGenerationException;
}
