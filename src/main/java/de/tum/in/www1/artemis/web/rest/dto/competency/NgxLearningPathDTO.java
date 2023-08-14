package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents simplified learning path optimized for Ngx representation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NgxLearningPathDTO(Set<Node> nodes, Set<Edge> edges) {

    public record Node(String id, NodeType type, Long linkedResource, Long linkedResourceParent, boolean completed, String label) {

        public Node(String id, NodeType type, Long linkedResource, boolean completed, String label) {
            this(id, type, linkedResource, null, completed, label);
        }

        public Node(String id, NodeType type, Long linkedResource, String label) {
            this(id, type, linkedResource, false, label);
        }

        public Node(String id, NodeType type, String label) {
            this(id, type, null, label);
        }

        public Node(String id, NodeType type, Long linkedResource) {
            this(id, type, linkedResource, "");
        }

        public Node(String id, NodeType type) {
            this(id, type, null, "");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Edge(String id, String source, String target) {
    }

    public enum NodeType {
        COMPETENCY_START, COMPETENCY_END, MATCH_START, MATCH_END, EXERCISE, LECTURE_UNIT,
    }
}
