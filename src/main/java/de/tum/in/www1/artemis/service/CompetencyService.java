package de.tum.in.www1.artemis.service;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class CompetencyService {

    private final CompetencyRepository competencyRepository;

    private final AuthorizationCheckService authCheckService;

    private final CompetencyProgressService competencyProgressService;

    public CompetencyService(CompetencyRepository competencyRepository, AuthorizationCheckService authCheckService, CompetencyProgressService competencyProgressService) {
        this.competencyRepository = competencyRepository;
        this.authCheckService = authCheckService;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * Get all competencies for a course, including the progress for the user.
     *
     * @param course         The course for which the competencies should be retrieved.
     * @param user           The user for whom to filter the visible lecture units attached to the competency.
     * @param updateProgress Whether the competency progress should be updated or taken from the database.
     * @return A list of competencies with their lecture units (filtered for the user) and user progress.
     */
    public Set<Competency> findAllForCourse(@NotNull Course course, @NotNull User user, boolean updateProgress) {
        if (updateProgress) {
            // Get the competencies with the updated progress for the specified user.
            return competencyProgressService.getCompetenciesAndUpdateProgressByUserInCourse(user, course);
        }
        else {
            // Fetch the competencies with the user progress from the database.
            return competencyRepository.findAllForCourseWithProgressForUser(course.getId(), user.getId());
        }
    }

    /**
     * Get all prerequisites for a course. Lecture units are removed if the student is not part of the course.
     *
     * @param course The course for which the prerequisites should be retrieved.
     * @param user   The user that is requesting the prerequisites.
     * @return A list of prerequisites (without lecture units if student is not part of course).
     */
    public Set<Competency> findAllPrerequisitesForCourse(@NotNull Course course, @NotNull User user) {
        Set<Competency> prerequisites = competencyRepository.findPrerequisitesByCourseId(course.getId());
        // Remove all lecture units
        for (Competency prerequisite : prerequisites) {
            prerequisite.setLectureUnits(Collections.emptySet());
        }
        return prerequisites;
    }

    /**
     * Search for all competencies fitting a {@link PageableSearchDTO search query}. The result is paged.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user   The user for whom to fetch all available lectures
     * @return A wrapper object containing a list of all found competencies and the total number of pages
     */
    public SearchResultPageDTO<Competency> getAllOnPageWithSize(final PageableSearchDTO<String> search, final User user) {
        final var pageable = PageUtil.createCompetencyPageRequest(search);
        final var searchTerm = search.getSearchTerm();
        final Page<Competency> competencyPage;
        if (authCheckService.isAdmin(user)) {
            competencyPage = competencyRepository.findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(searchTerm, searchTerm, pageable);
        }
        else {
            competencyPage = competencyRepository.findByTitleInLectureOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), pageable);
        }
        return new SearchResultPageDTO<>(competencyPage.getContent(), competencyPage.getTotalPages());
    }

    /**
     * Checks if the provided competencies and relations between them contain a cycle
     *
     * @param competencies The set of competencies that get checked for cycles
     * @param relations    The set of relations that get checked for cycles
     * @return A boolean that states whether the provided competencies and relations contain a cycle
     */
    public boolean doesCreateCircularRelation(Set<Competency> competencies, Set<CompetencyRelation> relations) {
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
                    if (neighbor.isBeingVisited()) {
                        // backward edge exists
                        return true;
                    }
                    else if (!neighbor.isVisited() && vertexIsPartOfCycle(neighbor)) {
                        return true;
                    }
                }
                sourceVertex.setBeingVisited(false);
                sourceVertex.setVisited(true);
                return false;
            }
        }

        var graph = new Graph();
        for (Competency competency : competencies) {
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
            if (relation.getType() == CompetencyRelation.RelationType.MATCHES) {
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
