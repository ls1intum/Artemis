package de.tum.cit.aet.artemis.atlas.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents simplified learning path optimized for Ngx representation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NgxLearningPathDTO(Set<Node> nodes, Set<Edge> edges) {

    public record Node(String id, NodeType type, Long linkedResource, Long linkedResourceParent, boolean completed, String label) {

        public static Node of(String id, NodeType type, Long linkedResource, Long linkedResourceParent, boolean completed, String label) {
            return new Node(id, type, linkedResource, linkedResourceParent, completed, label);
        }

        public static Node of(String id, NodeType type, Long linkedResource, boolean completed, String label) {
            return of(id, type, linkedResource, null, completed, label);
        }

        public static Node of(String id, NodeType type, Long linkedResource, String label) {
            return of(id, type, linkedResource, false, label);
        }

        public static Node of(String id, NodeType type, String label) {
            return of(id, type, null, label);
        }

        public static Node of(String id, NodeType type, Long linkedResource) {
            return of(id, type, linkedResource, "");
        }

        public static Node of(String id, NodeType type) {
            return of(id, type, null, "");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Edge(String id, String source, String target) {
    }

    public enum NodeType {
        COMPETENCY_START, COMPETENCY_END, MATCH_START, MATCH_END, EXERCISE, LECTURE_UNIT,
    }
}
