package de.tum.cit.aet.artemis.service.connectors.localci;

import java.util.Comparator;

import de.tum.cit.aet.artemis.service.connectors.localci.dto.BuildJobQueueItem;

/**
 * This comparator allows to prioritize build jobs in the shared build queue
 */
@SuppressWarnings("unused")
public final class LocalCIPriorityQueueComparator implements Comparator<BuildJobQueueItem> {

    @Override
    public int compare(BuildJobQueueItem o1, BuildJobQueueItem o2) {
        int priorityComparison = Integer.compare(o1.priority(), o2.priority());
        if (priorityComparison == 0) {
            return o1.jobTimingInfo().submissionDate().compareTo(o2.jobTimingInfo().submissionDate());
        }
        return priorityComparison;
    }

}
