package de.tum.cit.aet.artemis.programming.service.localci;

import java.util.Comparator;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;

/**
 * This comparator allows to prioritize build jobs in the shared build queue
 */
@SuppressWarnings("unused")
public final class LocalCIPriorityQueueComparator implements Comparator<BuildJobQueueItem> {

    @Override
    public int compare(BuildJobQueueItem o1, BuildJobQueueItem o2) {
        return o1.compareTo(o2);
    }

}
