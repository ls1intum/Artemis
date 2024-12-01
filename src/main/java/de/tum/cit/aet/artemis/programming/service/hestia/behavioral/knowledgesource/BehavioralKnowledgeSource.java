package de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource;

import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.BehavioralSolutionEntryGenerationException;

public abstract class BehavioralKnowledgeSource {

    protected final BehavioralBlackboard blackboard;

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
