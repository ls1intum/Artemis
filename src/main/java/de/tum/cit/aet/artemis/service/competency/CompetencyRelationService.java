package de.tum.cit.aet.artemis.service.competency;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.domain.Course;

/**
 * Service for managing CompetencyRelations.
 */

@Profile(PROFILE_CORE)
@Service
public class CompetencyRelationService {

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CompetencyRelationService(CompetencyRelationRepository competencyRelationRepository, CourseCompetencyRepository courseCompetencyRepository) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    /**
     * Gets a relation between two competencies.
     * <p>
     * The relation is not persisted.
     *
     * @param tailCompetency the tail Competency
     * @param headCompetency the head Competency
     * @param relationType   the type of the relation
     * @return the created CompetencyRelation
     */
    public CompetencyRelation getCompetencyRelation(CourseCompetency tailCompetency, CourseCompetency headCompetency, RelationType relationType) {
        CompetencyRelation competencyRelation = new CompetencyRelation();
        competencyRelation.setTailCompetency(tailCompetency);
        competencyRelation.setHeadCompetency(headCompetency);
        competencyRelation.setType(relationType);
        return competencyRelation;
    }

    /**
     * Creates a relation between two competencies.
     *
     * @param tailCompetency the tail Competency
     * @param headCompetency the head Competency
     * @param relationType   the type of the relation
     * @param course         the course the relation belongs to
     * @return the persisted CompetencyRelation
     */
    public CompetencyRelation createCompetencyRelation(CourseCompetency tailCompetency, CourseCompetency headCompetency, RelationType relationType, Course course) {
        if (relationType == null) {
            throw new BadRequestException("Competency relation must have a relation type");
        }
        var relation = getCompetencyRelation(tailCompetency, headCompetency, relationType);
        var competencies = courseCompetencyRepository.findAllForCourse(course.getId());
        var competencyRelations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        competencyRelations.add(relation);

        if (doesCreateCircularRelation(competencies, competencyRelations)) {
            throw new BadRequestException("You can't define circular dependencies between competencies");
        }

        return competencyRelationRepository.save(relation);
    }

    /**
     * Checks if the provided competencies and relations between them contain a cycle
     *
     * @param competencies The set of competencies that get checked for cycles
     * @param relations    The set of relations that get checked for cycles
     * @return A boolean that states whether the provided competencies and relations contain a cycle
     */
    private boolean doesCreateCircularRelation(Set<CourseCompetency> competencies, Set<CompetencyRelation> relations) {
        // Inner class Vertex is only used in this method for cycle detection
        class Vertex {

            private final String label;

            private boolean beingVisited;

            private boolean visited;

            private final Set<Vertex> adjacencyList;

            public Vertex(String label) {
                this.label = label;
                this.adjacencyList = new HashSet<>();
            }

            public Set<Vertex> getAdjacencyList() {
                return adjacencyList;
            }

            public void addNeighbor(Vertex adjacent) {
                this.adjacencyList.add(adjacent);
            }

            public boolean isBeingVisited() {
                return beingVisited;
            }

            public void setBeingVisited(boolean beingVisited) {
                this.beingVisited = beingVisited;
            }

            public boolean isVisited() {
                return visited;
            }

            public void setVisited(boolean visited) {
                this.visited = visited;
            }
        }

        // Inner class Graph is only used in this method for cycle detection
        class Graph {

            private final List<Vertex> vertices;

            public Graph() {
                this.vertices = new ArrayList<>();
            }

            public void addVertex(Vertex vertex) {
                this.vertices.add(vertex);
            }

            public void addEdge(Vertex from, Vertex to) {
                from.addNeighbor(to);
            }

            // Checks all vertices of the graph if they are part of a cycle.
            // This is necessary because otherwise we would not traverse the complete graph if it is not connected
            public boolean hasCycle() {
                for (Vertex vertex : vertices) {
                    if (!vertex.isVisited() && vertexIsPartOfCycle(vertex)) {
                        return true;
                    }
                }
                return false;
            }

            public boolean vertexIsPartOfCycle(Vertex sourceVertex) {
                sourceVertex.setBeingVisited(true);
                for (Vertex neighbor : sourceVertex.getAdjacencyList()) {
                    if (neighbor.isBeingVisited() || (!neighbor.isVisited() && vertexIsPartOfCycle(neighbor))) {
                        // backward edge exists -> cycle
                        return true;
                    }
                }
                sourceVertex.setBeingVisited(false);
                sourceVertex.setVisited(true);
                return false;
            }
        }

        var graph = new Graph();
        for (CourseCompetency competency : competencies) {
            graph.addVertex(new Vertex(competency.getTitle()));
        }
        for (CompetencyRelation relation : relations) {
            var headVertex = graph.vertices.stream().filter(vertex -> vertex.label.equals(relation.getHeadCompetency().getTitle())).findFirst().orElseThrow();
            var tailVertex = graph.vertices.stream().filter(vertex -> vertex.label.equals(relation.getTailCompetency().getTitle())).findFirst().orElseThrow();
            // Only EXTENDS and ASSUMES are included in the generated graph as other relations are no problem if they are circular
            // MATCHES relations are considered in the next step by merging the edges and combining the adjacencyLists
            switch (relation.getType()) {
                case EXTENDS, ASSUMES -> graph.addEdge(tailVertex, headVertex);
            }
        }
        // combine vertices that are connected through MATCHES
        for (CompetencyRelation relation : relations) {
            if (relation.getType() == RelationType.MATCHES) {
                var headVertex = graph.vertices.stream().filter(vertex -> vertex.label.equals(relation.getHeadCompetency().getTitle())).findFirst().orElseThrow();
                var tailVertex = graph.vertices.stream().filter(vertex -> vertex.label.equals(relation.getTailCompetency().getTitle())).findFirst().orElseThrow();
                if (headVertex.adjacencyList.contains(tailVertex) || tailVertex.adjacencyList.contains(headVertex)) {
                    return true;
                }
                // create a merged vertex
                var mergedVertex = new Vertex(tailVertex.label + ", " + headVertex.label);
                // add all neighbours to merged vertex
                mergedVertex.getAdjacencyList().addAll(headVertex.getAdjacencyList());
                mergedVertex.getAdjacencyList().addAll(tailVertex.getAdjacencyList());
                // update every vertex that initially had one of the two merged vertices as neighbours to now reference the merged vertex
                for (Vertex vertex : graph.vertices) {
                    for (Vertex adjacentVertex : vertex.getAdjacencyList()) {
                        if (adjacentVertex.label.equals(headVertex.label) || adjacentVertex.label.equals(tailVertex.label)) {
                            vertex.getAdjacencyList().remove(adjacentVertex);
                            vertex.getAdjacencyList().add(mergedVertex);
                        }
                    }
                }
            }
        }
        return graph.hasCycle();
    }
}
