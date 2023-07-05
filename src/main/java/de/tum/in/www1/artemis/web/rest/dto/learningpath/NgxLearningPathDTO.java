package de.tum.in.www1.artemis.web.rest.dto.learningpath;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents simplified
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NgxLearningPathDTO(Set<Node> nodes, Set<Edge> edges, Set<Cluster> clusters) {

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NgxLearningPathDTO)) {
            return false;
        }

        final NgxLearningPathDTO other = (NgxLearningPathDTO) obj;
        return nodes.equals(other.nodes) && edges.equals(other.edges) && clusters.equals(other.clusters);
    }

    @Override
    public String toString() {
        return "NgxLearningPathDTO{nodes=" + nodes + ", edges=" + edges + ", clusters=" + clusters + "}";
    }

    public record Node(String id, NodeType type, long linkedResource, String label) {

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Node)) {
                return false;
            }

            final Node other = (Node) obj;
            return id.equals(other.id) && type.equals(other.type) && linkedResource == other.linkedResource && label.equals(other.label);
        }

        @Override
        public String toString() {
            return "Node{id=" + id + ", type=" + type.name() + ", linkedResource=" + linkedResource + ", label=" + label + "}";
        }
    }

    public record Edge(String id, String source, String target) {

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Edge)) {
                return false;
            }

            final Edge other = (Edge) obj;
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
            if (!(obj instanceof Cluster)) {
                return false;
            }
            final Cluster other = (Cluster) obj;
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
