package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.Comparator;

import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildJobItemReferenceDTO;

/**
 * This comparator allows to prioritize build jobs in the shared build queue
 */
@SuppressWarnings("unused")
public final class LocalCIPriorityQueueComparator implements Comparator<BuildJobItemReferenceDTO> {

    @Override
    public int compare(BuildJobItemReferenceDTO o1, BuildJobItemReferenceDTO o2) {
        int priorityComparison = Integer.compare(o1.priority(), o2.priority());
        if (priorityComparison == 0) {
            return o1.submissionDate().compareTo(o2.submissionDate());
        }
        return priorityComparison;
    }

}
