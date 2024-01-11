package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.Comparator;

import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;

public class LocalCIPriorityQueueComparator implements Comparator<LocalCIBuildJobQueueItem> {

    @Override
    public int compare(LocalCIBuildJobQueueItem o1, LocalCIBuildJobQueueItem o2) {
        int priorityComparison = Integer.compare(o1.priority(), o2.priority());
        if (priorityComparison == 0) {
            return o1.submissionDate().compareTo(o2.submissionDate());
        }
        return priorityComparison;
    }

}
