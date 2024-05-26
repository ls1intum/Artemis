package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a reference to a build job item. This object is used in the build job queue and contains only the necessary
 * information to identify a build job item. The actual build job item is stored in a Hazelcast IMap. This object is used
 * to reduce the amount of data used in the Queue.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildJobItemReference(String id, long participationId, long courseId, int priority, ZonedDateTime submissionDate) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new Local ci build job item reference from a build job item.
     *
     * @param buildJobItem the build job item
     */

    public BuildJobItemReference(BuildJobItem buildJobItem) {
        this(buildJobItem.id(), buildJobItem.participationId(), buildJobItem.courseId(), buildJobItem.priority(), buildJobItem.jobTimingInfo().submissionDate());
    }
}
