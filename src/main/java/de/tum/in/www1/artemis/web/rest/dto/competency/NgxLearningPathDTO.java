package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents simplified learning path optimized for Ngx representation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NgxLearningPathDTO(Set<Node> nodes, Set<Edge> edges, Set<Cluster> clusters) {

    public record Node(String id, NodeType type, long linkedResource, boolean completed, String label) {

        public Node(String id, NodeType type, long linkedResource, String label) {
            this(id, type, linkedResource, false, label);
        }

        public Node(String id, NodeType type, String label) {
            this(id, type, -1, label);
        }

        public Node(String id, NodeType type, long linkedResource) {
            this(id, type, linkedResource, "");
        }

        public Node(String id, NodeType type) {
            this(id, type, -1);
        }
    }

    public record Edge(String id, String source, String target) {
    }

    public record Cluster(String id, String label, Set<String> childNodeIds) {
    }

    public enum NodeType {
        COMPETENCY_START, COMPETENCY_END, MATCH_START, MATCH_END, EXERCISE, LECTURE_UNIT,
    }
}
