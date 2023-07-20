package de.tum.in.www1.artemis.web.rest.dto.learningpath;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents simplified learning path optimized for Ngx representation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NgxLearningPathDTO(Set<Node> nodes, Set<Edge> edges, Set<Cluster> clusters) {

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NgxLearningPathDTO other)) {
            return false;
        }
        return nodes.equals(other.nodes) && edges.equals(other.edges) && clusters.equals(other.clusters);
    }

    @Override
    public String toString() {
        return "NgxLearningPathDTO{nodes=" + nodes + ", edges=" + edges + ", clusters=" + clusters + "}";
    }

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

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Node other)) {
                return false;
            }
            return id.equals(other.id) && type.equals(other.type) && linkedResource == other.linkedResource && label.equals(other.label);
        }

        @Override
        public String toString() {
            return "Node{id=" + id + ", type=" + type.name() + ", linkedResource=" + linkedResource + ", completed=" + completed + ", label=" + label + "}";
        }
    }

    public record Edge(String id, String source, String target) {

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Edge other)) {
                return false;
            }
            return id.equals(other.id) && source.equals(other.source) && target.equals(other.target);
        }

        @Override
        public String toString() {
            return "Edge{id=" + id + ", source=" + source + ", target=" + target + "}";
        }
    }

    public record Cluster(String id, String label, Set<String> childNodeIds) {

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Cluster other)) {
                return false;
            }
            return id.equals(other.id) && label.equals(other.label) && childNodeIds.equals(other.childNodeIds);
        }

        @Override
        public String toString() {
            return "Cluster{id=" + id + ", label=" + label + ", childNodeIds=" + childNodeIds + "}";
        }
    }

    public enum NodeType {
        COMPETENCY_START, COMPETENCY_END, MATCH_START, MATCH_END, EXERCISE, LECTURE_UNIT,
    }
}
