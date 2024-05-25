package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.RelationType;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.repository.competency.StandardizedCompetencyRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.service.learningpath.LearningPathService;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyRelationDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.CompetencyPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

/**
 * Service for managing competencies.
 */
@Profile(PROFILE_CORE)
@Service
public class CompetencyService {

    private static final Logger log = LoggerFactory.getLogger(CompetencyService.class);

    private static final String ENTITY_NAME = "Competency";

    private final CompetencyRepository competencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final AuthorizationCheckService authCheckService;

    private final LearningPathService learningPathService;

    private final CompetencyProgressService competencyProgressService;

    private final LectureUnitService lectureUnitService;

    private final ExerciseService exerciseService;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    private final CourseRepository courseRepository;

    public CompetencyService(CompetencyRepository competencyRepository, AuthorizationCheckService authCheckService, CompetencyRelationRepository competencyRelationRepository,
            LearningPathService learningPathService, CompetencyProgressService competencyProgressService, LectureUnitService lectureUnitService, ExerciseService exerciseService,
            CompetencyProgressRepository competencyProgressRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            StandardizedCompetencyRepository standardizedCompetencyRepository, CourseRepository courseRepository) {
        this.competencyRepository = competencyRepository;
        this.authCheckService = authCheckService;
        this.competencyRelationRepository = competencyRelationRepository;
        this.learningPathService = learningPathService;
        this.competencyProgressService = competencyProgressService;
        this.lectureUnitService = lectureUnitService;
        this.exerciseService = exerciseService;
        this.competencyProgressRepository = competencyProgressRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Get all prerequisites for a course. Lecture units are removed.
     *
     * @param course The course for which the prerequisites should be retrieved.
     * @return A list of prerequisites.
     */
    public Set<Competency> findAllPrerequisitesForCourse(@NotNull Course course) {
        Set<Competency> prerequisites = competencyRepository.findPrerequisitesByCourseId(course.getId());
        // Remove all lecture units
        for (Competency prerequisite : prerequisites) {
            prerequisite.setLectureUnits(Collections.emptySet());
        }
        return prerequisites;
    }

    /**
     * Search for all competencies fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user   The user for whom to the competencies
     * @return A wrapper object containing a list of all found competencies and the total number of pages
     */
    public SearchResultPageDTO<Competency> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final User user) {
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.COMPETENCY);
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
     * Search for all competencies fitting a {@link CompetencyPageableSearchDTO search query}. The result is paged.
     *
     * @param search The search query defining the search terms and the size of the returned page
     * @param user   The user for whom to fetch the competencies
     * @return A wrapper object containing a list of all found competencies and the total number of pages
     */
    public SearchResultPageDTO<Competency> getOnPageWithSizeForImport(final CompetencyPageableSearchDTO search, final User user) {
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.COMPETENCY);
        final String title = StringUtils.isEmpty(search.getTitle()) ? null : search.getTitle();
        final String description = StringUtils.isEmpty(search.getDescription()) ? null : search.getDescription();
        final String courseTitle = StringUtils.isEmpty(search.getCourseTitle()) ? null : search.getCourseTitle();
        final String semester = StringUtils.isEmpty(search.getSemester()) ? null : search.getSemester();

        final Page<Competency> competencyPage;
        if (authCheckService.isAdmin(user)) {
            competencyPage = competencyRepository.findForImport(title, description, courseTitle, semester, pageable);
        }
        else {
            competencyPage = competencyRepository.findForImportAndUserHasAccessToCourse(title, description, courseTitle, semester, user.getGroups(), pageable);
        }
        return new SearchResultPageDTO<>(competencyPage.getContent(), competencyPage.getTotalPages());
    }

    /**
     * Imports the given competencies and relations into a course
     *
     * @param course       the course to import into
     * @param competencies the competencies to import
     * @param relations    the relations to import
     * @return The list of imported competencies, each also containing the relations it is the tail competency for.
     */
    public List<CompetencyWithTailRelationDTO> importCompetenciesAndRelations(Course course, Set<Competency> competencies, Set<CompetencyRelation> relations) {
        var idToImportedCompetency = new HashMap<Long, CompetencyWithTailRelationDTO>();

        for (var competency : competencies) {
            Competency importedCompetency = getCompetencyToCreate(competency);
            importedCompetency.setCourse(course);

            importedCompetency = competencyRepository.save(importedCompetency);
            idToImportedCompetency.put(competency.getId(), new CompetencyWithTailRelationDTO(importedCompetency, new ArrayList<>()));
        }

        if (course.getLearningPathsEnabled()) {
            var importedCompetencies = idToImportedCompetency.values().stream().map(CompetencyWithTailRelationDTO::competency).toList();
            learningPathService.linkCompetenciesToLearningPathsOfCourse(importedCompetencies, course.getId());
        }

        if (!relations.isEmpty()) {
            for (var relation : relations) {
                var tailCompetencyDTO = idToImportedCompetency.get(relation.getTailCompetency().getId());
                var headCompetencyDTO = idToImportedCompetency.get(relation.getHeadCompetency().getId());

                CompetencyRelation relationToImport = new CompetencyRelation();
                relationToImport.setType(relation.getType());
                relationToImport.setTailCompetency(tailCompetencyDTO.competency());
                relationToImport.setHeadCompetency(headCompetencyDTO.competency());

                relationToImport = competencyRelationRepository.save(relationToImport);
                tailCompetencyDTO.tailRelations().add(CompetencyRelationDTO.of(relationToImport));
            }
        }
        return idToImportedCompetency.values().stream().toList();
    }

    /**
     * Imports the standardized competencies with the given ids as competencies into a course
     *
     * @param competencyIdsToImport the ids of the standardized competencies to import
     * @param courseId              the id of the course to import into
     * @return the list of imported competencies
     */
    public List<Competency> importStandardizedCompetencies(List<Long> competencyIdsToImport, long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);
        List<StandardizedCompetency> standardizedCompetencies = standardizedCompetencyRepository.findAllById(competencyIdsToImport);

        if (standardizedCompetencies.size() != competencyIdsToImport.size()) {
            throw new EntityNotFoundException("Could not find all standardized competencies to import in the database!");
        }

        List<Competency> competenciesToCreate = new ArrayList<>();

        for (var standardizedCompetency : standardizedCompetencies) {
            var competency = new Competency();
            competency.setTitle(standardizedCompetency.getTitle());
            competency.setDescription(standardizedCompetency.getDescription());
            competency.setTaxonomy(standardizedCompetency.getTaxonomy());
            competency.setMasteryThreshold(Competency.DEFAULT_MASTERY_THRESHOLD);
            competency.setLinkedStandardizedCompetency(standardizedCompetency);
            competency.setCourse(course);

            competenciesToCreate.add(competency);
        }

        var importedCompetencies = competencyRepository.saveAll(competenciesToCreate);

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetenciesToLearningPathsOfCourse(importedCompetencies, course.getId());
        }

        return importedCompetencies;
    }

    /**
     * Imports the given competencies into a course
     *
     * @param course       the course to import into
     * @param competencies the competencies to import
     * @return The list of imported competencies
     */
    public List<Competency> importCompetencies(Course course, Set<Competency> competencies) {
        var importedCompetencies = new ArrayList<Competency>();

        for (var competency : competencies) {
            Competency importedCompetency = getCompetencyToCreate(competency);
            importedCompetency.setCourse(course);

            importedCompetency = competencyRepository.save(importedCompetency);
            importedCompetencies.add(importedCompetency);
        }

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetenciesToLearningPathsOfCourse(importedCompetencies, course.getId());
        }
        return importedCompetencies;
    }

    /**
     * Creates a new competency and links it to a course and lecture units.
     *
     * @param competency the competency to create
     * @param course     the course to link the competency to
     * @return the persisted competency
     */
    public Competency createCompetency(Competency competency, Course course) {
        Competency competencyToCreate = getCompetencyToCreate(competency);
        competencyToCreate.setCourse(course);

        var persistedCompetency = competencyRepository.save(competencyToCreate);

        lectureUnitService.linkLectureUnitsToCompetency(persistedCompetency, competency.getLectureUnits(), Set.of());

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetencyToLearningPathsOfCourse(persistedCompetency, course.getId());
        }

        return persistedCompetency;
    }

    /**
     * Creates a list of new competencies and links them to a course and lecture units.
     *
     * @param competencies the competencies to create
     * @param course       the course to link the competencies to
     * @return the persisted competencies
     */
    public List<Competency> createCompetencies(List<Competency> competencies, Course course) {
        var createdCompetencies = new ArrayList<Competency>();

        for (var competency : competencies) {
            var createdCompetency = getCompetencyToCreate(competency);
            createdCompetency.setCourse(course);
            createdCompetency = competencyRepository.save(createdCompetency);

            lectureUnitService.linkLectureUnitsToCompetency(createdCompetency, competency.getLectureUnits(), Set.of());
            createdCompetencies.add(createdCompetency);
        }

        if (course.getLearningPathsEnabled()) {
            learningPathService.linkCompetenciesToLearningPathsOfCourse(createdCompetencies, course.getId());
        }

        return createdCompetencies;
    }

    /**
     * Updates a competency with the values of another one. Updates progress if necessary.
     *
     * @param competencyToUpdate the competency to update
     * @param competency         the competency to update with
     * @return the updated competency
     */
    public Competency updateCompetency(Competency competencyToUpdate, Competency competency) {
        competencyToUpdate.setTitle(competency.getTitle().trim());
        competencyToUpdate.setDescription(competency.getDescription());
        competencyToUpdate.setSoftDueDate(competency.getSoftDueDate());
        competencyToUpdate.setMasteryThreshold(competency.getMasteryThreshold());
        competencyToUpdate.setTaxonomy(competency.getTaxonomy());
        competencyToUpdate.setOptional(competency.isOptional());
        final var persistedCompetency = competencyRepository.save(competencyToUpdate);

        // update competency progress if necessary
        if (competency.getLectureUnits().size() != competencyToUpdate.getLectureUnits().size() || !competencyToUpdate.getLectureUnits().containsAll(competency.getLectureUnits())) {
            log.debug("Linked lecture units changed, updating student progress for competency...");
            competencyProgressService.updateProgressByCompetencyAsync(persistedCompetency);
        }

        return persistedCompetency;
    }

    /**
     * Deletes a competency and all its relations.
     *
     * @param competency the competency to delete
     * @param course     the course the competency belongs to
     */
    public void deleteCompetency(Competency competency, Course course) {
        competencyRelationRepository.deleteAllByCompetencyId(competency.getId());
        competencyProgressService.deleteProgressForCompetency(competency.getId());

        exerciseService.removeCompetency(competency.getExercises(), competency);
        lectureUnitService.removeCompetency(competency.getLectureUnits(), competency);

        if (course.getLearningPathsEnabled()) {
            learningPathService.removeLinkedCompetencyFromLearningPathsOfCourse(competency, course.getId());
        }

        competencyRepository.deleteById(competency.getId());
    }

    /**
     * Finds a competency by its id and fetches its lecture units, exercises and progress for the provided user. It also fetches the lecture unit progress for the same user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param competencyId The id of the competency to find
     * @param userId       The id of the user for which to fetch the progress
     * @return The found competency
     */
    public Competency findCompetencyWithExercisesAndLectureUnitsAndProgressForUser(Long competencyId, Long userId) {
        Competency competency = competencyRepository.findWithLectureUnitsAndExercisesByIdElseThrow(competencyId);
        competencyProgressRepository.findByCompetencyIdAndUserId(competencyId, userId).ifPresent(progress -> competency.setUserProgress(Set.of(progress)));
        // collect to map lecture unit id -> this
        var completions = lectureUnitCompletionRepository.findByLectureUnitsAndUserId(competency.getLectureUnits(), userId).stream()
                .collect(Collectors.toMap(completion -> completion.getLectureUnit().getId(), completion -> completion));
        competency.getLectureUnits().forEach(lectureUnit -> {
            if (completions.containsKey(lectureUnit.getId())) {
                lectureUnit.setCompletedUsers(Set.of(completions.get(lectureUnit.getId())));
            }
            else {
                lectureUnit.setCompletedUsers(Collections.emptySet());
            }
        });

        return competency;
    }

    /**
     * Finds competencies within a course and fetch progress for the provided user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param courseId The id of the course for which to fetch the competencies
     * @param userId   The id of the user for which to fetch the progress
     * @return The found competency
     */
    public List<Competency> findCompetenciesWithProgressForUserByCourseId(Long courseId, Long userId) {
        List<Competency> competencies = competencyRepository.findByCourseId(courseId);
        var progress = competencyProgressRepository.findByCompetenciesAndUser(competencies, userId).stream()
                .collect(Collectors.toMap(completion -> completion.getCompetency().getId(), completion -> completion));

        competencies.forEach(competency -> {
            if (progress.containsKey(competency.getId())) {
                competency.setUserProgress(Set.of(progress.get(competency.getId())));
            }
            else {
                competency.setUserProgress(Collections.emptySet());
            }
        });

        return competencies;
    }

    /**
     * Gets a new competency from an existing one (without relations).
     * <p>
     * The competency is not persisted.
     *
     * @param competency the existing competency
     * @return the new competency
     */
    public Competency getCompetencyToCreate(Competency competency) {
        return new Competency(competency.getTitle().trim(), competency.getDescription(), competency.getSoftDueDate(), competency.getMasteryThreshold(), competency.getTaxonomy(),
                competency.isOptional());
    }

    /**
     * Converts a list of competencies to a list of {@link CompetencyWithTailRelationDTO CompetencyWithTailRelationDTOs}
     *
     * @param competencies the list of competencies
     * @return the list of CompetencyWithTailRelationDTOs
     */
    public List<CompetencyWithTailRelationDTO> competenciesToCompetencyWithTailRelationDTOs(List<Competency> competencies) {
        return competencies.stream().map(competency -> new CompetencyWithTailRelationDTO(competency, Collections.emptyList())).toList();
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

    /**
     * Checks if the competency is part of the course
     *
     * @param competencyId The id of the competency
     * @param courseId     The id of the course
     * @throws BadRequestAlertException if the competency does not belong to the course
     */
    public void checkIfCompetencyBelongsToCourse(long competencyId, long courseId) {
        if (!competencyRepository.existsByIdAndCourseId(competencyId, courseId)) {
            throw new BadRequestAlertException("The competency does not belong to the course", ENTITY_NAME, "competencyWrongCourse");
        }
    }
}
