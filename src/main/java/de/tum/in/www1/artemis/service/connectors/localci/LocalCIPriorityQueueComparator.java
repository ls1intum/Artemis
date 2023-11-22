package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;

public class LocalCIPriorityQueueComparator implements Comparator<LocalCIBuildJobQueueItem> {

    private final Logger log = LoggerFactory.getLogger(LocalCIPriorityQueueComparator.class);

    public LocalCIPriorityQueueComparator() {
        log.debug("LocalCIPriorityQueueComparator classloader: " + this.getClass().getClassLoader());
    }

    @Override
    public int compare(LocalCIBuildJobQueueItem o1, LocalCIBuildJobQueueItem o2) {
        int priorityComparison = Integer.compare(o1.getPriority(), o2.getPriority());
        if (priorityComparison == 0) {
            return Long.compare(o1.getSubmissionDate(), o2.getSubmissionDate());
        }
        return priorityComparison;
    }

}
