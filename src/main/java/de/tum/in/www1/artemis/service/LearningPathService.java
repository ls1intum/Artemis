package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.jgrapht.alg.util.UnionFind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathHealthDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.NgxLearningPathDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

/**
 * Service Implementation for managing Learning Paths.
 * <p>
 * This includes
 * <ul>
 * <li>the generation of learning paths in courses,</li>
 * <li>and performing pageable searches for learning paths.</li>
 * </ul>
 */
@Service
public class LearningPathService {

    private final Logger log = LoggerFactory.getLogger(LearningPathService.class);

    private final UserRepository userRepository;

    private final LearningPathRepository learningPathRepository;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final CourseRepository courseRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    public LearningPathService(UserRepository userRepository, LearningPathRepository learningPathRepository, CompetencyProgressRepository competencyProgressRepository,
            CourseRepository courseRepository, CompetencyRelationRepository competencyRelationRepository) {
        this.userRepository = userRepository;
        this.learningPathRepository = learningPathRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.courseRepository = courseRepository;
        this.competencyRelationRepository = competencyRelationRepository;
    }

    /**
     * Generate learning paths for all students enrolled in the course
     *
     * @param course course the learning paths are created for
     */
    public void generateLearningPaths(@NotNull Course course) {
        var students = userRepository.getStudents(course);
        students.forEach(student -> generateLearningPathForUser(course, student));
        log.debug("Successfully created learning paths for all {} students in course (id={})", students.size(), course.getId());
    }

    /**
     * Generate learning path for the user in the course if the learning path is not present
     *
     * @param course course that defines the learning path
     * @param user   student for which the learning path is generated
     * @return the learning path of the user
     */
    public LearningPath generateLearningPathForUser(@NotNull Course course, @NotNull User user) {
        var existingLearningPath = learningPathRepository.findByCourseIdAndUserId(course.getId(), user.getId());
        // the learning path has not to be generated if it already exits
        if (existingLearningPath.isPresent()) {
            return existingLearningPath.get();
        }
        LearningPath lpToCreate = new LearningPath();
        lpToCreate.setUser(user);
        lpToCreate.setCourse(course);
        lpToCreate.getCompetencies().addAll(course.getCompetencies());
        var persistedLearningPath = learningPathRepository.save(lpToCreate);
        log.debug("Created LearningPath (id={}) for user (id={}) in course (id={})", persistedLearningPath.getId(), user.getId(), course.getId());
        updateLearningPathProgress(persistedLearningPath);
        return persistedLearningPath;
    }

    /**
     * Search for all learning paths fitting a {@link PageableSearchDTO search query}. The result is paged.
     *
     * @param search the search query defining the search term and the size of the returned page
     * @param course the course the learning paths are linked to
     * @return A wrapper object containing a list of all found learning paths and the total number of pages
     */
    public SearchResultPageDTO<LearningPathPageableSearchDTO> getAllOfCourseOnPageWithSize(@NotNull PageableSearchDTO<String> search, @NotNull Course course) {
        final var pageable = PageUtil.createLearningPathPageRequest(search);
        final var searchTerm = search.getSearchTerm();
        final Page<LearningPath> learningPathPage = learningPathRepository.findByLoginOrNameInCourse(searchTerm, course.getId(), pageable);
        final List<LearningPathPageableSearchDTO> contentDTOs = learningPathPage.getContent().stream().map(LearningPathPageableSearchDTO::new).toList();
        return new SearchResultPageDTO<>(contentDTOs, learningPathPage.getTotalPages());
    }

    /**
     * Links given competency to all learning paths of the course.
     *
     * @param competency Competency that should be added to each learning path
     * @param courseId   course id that the learning paths belong to
     */
    public void linkCompetencyToLearningPathsOfCourse(@NotNull Competency competency, long courseId) {
        var course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(courseId);
        var learningPaths = course.getLearningPaths();
        learningPaths.forEach(learningPath -> learningPath.addCompetency(competency));
        learningPathRepository.saveAll(learningPaths);
        log.debug("Linked competency (id={}) to learning paths", competency.getId());
    }

    /**
     * Remove linked competency from all learning paths of the course.
     *
     * @param competency Competency that should be removed from each learning path
     * @param courseId   course id that the learning paths belong to
     */
    public void removeLinkedCompetencyFromLearningPathsOfCourse(@NotNull Competency competency, long courseId) {
        var course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(courseId);
        var learningPaths = course.getLearningPaths();
        learningPaths.forEach(learningPath -> learningPath.removeCompetency(competency));
        learningPathRepository.saveAll(learningPaths);
        log.debug("Removed linked competency (id={}) from learning paths", competency.getId());
    }

    /**
     * Updates progress of the learning path specified by course and user id.
     *
     * @param courseId id of the course the learning path is linked to
     * @param userId   id of the user the learning path is linked to
     */
    public void updateLearningPathProgress(long courseId, long userId) {
        final var learningPath = learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId);
        learningPath.ifPresent(this::updateLearningPathProgress);
    }

    /**
     * Updates progress of the given learning path. Competencies of the learning path must be loaded eagerly.
     *
     * @param learningPath learning path that is updated
     */
    private void updateLearningPathProgress(@NotNull LearningPath learningPath) {
        final var userId = learningPath.getUser().getId();
        final var competencyIds = learningPath.getCompetencies().stream().map(Competency::getId).collect(Collectors.toSet());
        final var competencyProgresses = competencyProgressRepository.findAllByCompetencyIdsAndUserId(competencyIds, userId);

        final float completed = competencyProgresses.stream().filter(CompetencyProgressService::isMastered).count();
        final var numberOfCompetencies = learningPath.getCompetencies().size();
        if (numberOfCompetencies == 0) {
            learningPath.setProgress(0);
        }
        else {
            learningPath.setProgress(Math.round(completed * 100 / numberOfCompetencies));
        }
        learningPathRepository.save(learningPath);
        log.debug("Updated LearningPath (id={}) for user (id={})", learningPath.getId(), userId);
    }

    /**
     * Gets the health status of learning paths for the given course.
     *
     * @param course the course for which the health status should be generated
     * @return dto containing the health status and additional information (missing learning paths) if needed
     */
    public LearningPathHealthDTO getHealthStatusForCourse(@NotNull Course course) {
        if (!course.getLearningPathsEnabled()) {
            return new LearningPathHealthDTO(LearningPathHealthDTO.HealthStatus.DISABLED);
        }

        long numberOfStudents = userRepository.countUserInGroup(course.getStudentGroupName());
        long numberOfLearningPaths = learningPathRepository.countLearningPathsOfEnrolledStudentsInCourse(course.getId());

        if (numberOfStudents == numberOfLearningPaths) {
            return new LearningPathHealthDTO(LearningPathHealthDTO.HealthStatus.OK);
        }
        else {
            return new LearningPathHealthDTO(LearningPathHealthDTO.HealthStatus.MISSING, numberOfStudents - numberOfLearningPaths);
        }
    }

    /**
     * Generates Ngx graph representation of the learning path graph.
     *
     * @param learningPath the learning path for which the Ngx representation should be created
     * @return Ngx graph representation of the learning path
     * @see NgxLearningPathDTO
     */
    public NgxLearningPathDTO generateNgxGraphRepresentation(@NotNull LearningPath learningPath) {
        Set<NgxLearningPathDTO.Node> nodes = new HashSet<>();
        Set<NgxLearningPathDTO.Edge> edges = new HashSet<>();
        learningPath.getCompetencies().forEach(competency -> generateNgxGraphRepresentationForCompetency(learningPath, competency, nodes, edges));
        generateNgxGraphRepresentationForRelations(learningPath, nodes, edges);
        return new NgxLearningPathDTO(nodes, edges);
    }

    /**
     * Generates Ngx graph representation for competency.
     * <p>
     * A competency's representation consists of
     * <ul>
     * <li>start node</li>
     * <li>end node</li>
     * <li>a node for each learning unit (exercises or lecture unit)</li>
     * <li>edges from start node to each learning unit</li>
     * <li>edges from each learning unit to end node</li>
     * </ul>
     *
     * @param learningPath the learning path for which the representation should be created
     * @param competency   the competency for which the representation will be created
     * @param nodes        set of nodes to store the new nodes
     * @param edges        set of edges to store the new edges
     */
    private void generateNgxGraphRepresentationForCompetency(LearningPath learningPath, Competency competency, Set<NgxLearningPathDTO.Node> nodes,
            Set<NgxLearningPathDTO.Edge> edges) {
        Set<NgxLearningPathDTO.Node> currentCluster = new HashSet<>();
        // generates start and end node
        final var startNodeId = getCompetencyStartNodeId(competency.getId());
        final var endNodeId = getCompetencyEndNodeId(competency.getId());
        currentCluster.add(new NgxLearningPathDTO.Node(startNodeId, NgxLearningPathDTO.NodeType.COMPETENCY_START, competency.getId()));
        currentCluster.add(new NgxLearningPathDTO.Node(endNodeId, NgxLearningPathDTO.NodeType.COMPETENCY_END, competency.getId()));

        // generate nodes and edges for lecture units
        competency.getLectureUnits().forEach(lectureUnit -> {
            currentCluster.add(new NgxLearningPathDTO.Node(getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), NgxLearningPathDTO.NodeType.LECTURE_UNIT,
                    lectureUnit.getId(), lectureUnit.getLecture().getId(), lectureUnit.isCompletedFor(learningPath.getUser()), lectureUnit.getName()));
            edges.add(new NgxLearningPathDTO.Edge(getLectureUnitInEdgeId(competency.getId(), lectureUnit.getId()), startNodeId,
                    getLectureUnitNodeId(competency.getId(), lectureUnit.getId())));
            edges.add(new NgxLearningPathDTO.Edge(getLectureUnitOutEdgeId(competency.getId(), lectureUnit.getId()), getLectureUnitNodeId(competency.getId(), lectureUnit.getId()),
                    endNodeId));
        });
        // generate nodes and edges for exercises
        competency.getExercises().forEach(exercise -> {
            currentCluster.add(new NgxLearningPathDTO.Node(getExerciseNodeId(competency.getId(), exercise.getId()), NgxLearningPathDTO.NodeType.EXERCISE, exercise.getId(),
                    exercise.isCompletedFor(learningPath.getUser()), exercise.getTitle()));
            edges.add(new NgxLearningPathDTO.Edge(getExerciseInEdgeId(competency.getId(), exercise.getId()), startNodeId, getExerciseNodeId(competency.getId(), exercise.getId())));
            edges.add(new NgxLearningPathDTO.Edge(getExerciseOutEdgeId(competency.getId(), exercise.getId()), getExerciseNodeId(competency.getId(), exercise.getId()), endNodeId));
        });
        // if no linked learning units exist directly link start to end
        if (currentCluster.size() == 2) {
            edges.add(new NgxLearningPathDTO.Edge(getDirectEdgeId(competency.getId()), startNodeId, endNodeId));
        }

        nodes.addAll(currentCluster);
    }

    /**
     * Generates Ngx graph representations for competency relations.
     * <p>
     * The representation will contain:
     * <ul>
     * <li>
     * For each matching cluster (transitive closure of competencies that are in a match relation):
     * <ul>
     * <li>two nodes (start and end of cluster) will be created</li>
     * <li>edges from the start node of the cluster to each start node of the competencies</li>
     * <li>edges from each end node of the competency to the end node of the cluster</li>
     * </ul>
     * </li>
     * <li>
     * For each other relation: edge from head competency end node to tail competency start node. If competency is part of a matching cluster, the edge will be linked to the
     * corresponding cluster start/end node.
     * </li>
     * </ul>
     *
     * two nodes (start and end of cluster) will be created.
     *
     * @param learningPath the learning path for which the Ngx representation should be created
     * @param nodes        set of nodes to store the new nodes
     * @param edges        set of edges to store the new edges
     */
    private void generateNgxGraphRepresentationForRelations(LearningPath learningPath, Set<NgxLearningPathDTO.Node> nodes, Set<NgxLearningPathDTO.Edge> edges) {
        final var relations = competencyRelationRepository.findAllByCourseId(learningPath.getCourse().getId());

        // compute match clusters
        Map<Long, Integer> competencyToMatchCluster = new HashMap<>();
        final var competenciesInMatchRelation = relations.stream().filter(relation -> relation.getType().equals(CompetencyRelation.RelationType.MATCHES))
                .flatMap(relation -> Stream.of(relation.getHeadCompetency().getId(), relation.getTailCompetency().getId())).collect(Collectors.toSet());
        if (!competenciesInMatchRelation.isEmpty()) {
            UnionFind<Long> matchClusters = new UnionFind<>(competenciesInMatchRelation);
            relations.stream().filter(relation -> relation.getType().equals(CompetencyRelation.RelationType.MATCHES))
                    .forEach(relation -> matchClusters.union(relation.getHeadCompetency().getId(), relation.getTailCompetency().getId()));

            // generate map between competencies and cluster node
            AtomicInteger matchClusterId = new AtomicInteger();
            relations.stream().filter(relation -> relation.getType().equals(CompetencyRelation.RelationType.MATCHES))
                    .flatMapToLong(relation -> LongStream.of(relation.getHeadCompetency().getId(), relation.getTailCompetency().getId())).distinct().forEach(competencyId -> {
                        var parentId = matchClusters.find(competencyId);
                        var clusterId = competencyToMatchCluster.computeIfAbsent(parentId, (key) -> matchClusterId.getAndIncrement());
                        competencyToMatchCluster.put(competencyId, clusterId);
                    });

            // generate match cluster start and end nodes
            for (int i = 0; i < matchClusters.numberOfSets(); i++) {
                nodes.add(new NgxLearningPathDTO.Node(getMatchingClusterStartNodeId(i), NgxLearningPathDTO.NodeType.MATCH_START));
                nodes.add(new NgxLearningPathDTO.Node(getMatchingClusterEndNodeId(i), NgxLearningPathDTO.NodeType.MATCH_END));
            }

            // generate edges between match cluster nodes and corresponding competencies
            competencyToMatchCluster.forEach((competency, cluster) -> {
                edges.add(new NgxLearningPathDTO.Edge(getInEdgeId(competency), getMatchingClusterStartNodeId(cluster), getCompetencyStartNodeId(competency)));
                edges.add(new NgxLearningPathDTO.Edge(getOutEdgeId(competency), getCompetencyEndNodeId(competency), getMatchingClusterEndNodeId(cluster)));
            });
        }

        // generate edges for remaining relations
        final Set<String> createdRelations = new HashSet<>();
        relations.stream().filter(relation -> !relation.getType().equals(CompetencyRelation.RelationType.MATCHES))
                .forEach(relation -> generateNgxGraphRepresentationForRelation(relation, competencyToMatchCluster, createdRelations, edges));
    }

    /**
     * Generates Ngx graph representations for competency relation.
     *
     * @param relation                 the relation for which the Ngx representation should be created
     * @param competencyToMatchCluster map from competencies to corresponding cluster
     * @param createdRelations         set of edge ids that have already been created
     * @param edges                    set of edges to store the new edges
     */
    private void generateNgxGraphRepresentationForRelation(CompetencyRelation relation, Map<Long, Integer> competencyToMatchCluster, Set<String> createdRelations,
            Set<NgxLearningPathDTO.Edge> edges) {
        final var sourceId = relation.getHeadCompetency().getId();
        String sourceNodeId;
        if (competencyToMatchCluster.containsKey(sourceId)) {
            sourceNodeId = getMatchingClusterEndNodeId(competencyToMatchCluster.get(sourceId));
        }
        else {
            sourceNodeId = getCompetencyEndNodeId(sourceId);
        }
        final var targetId = relation.getTailCompetency().getId();
        String targetNodeId;
        if (competencyToMatchCluster.containsKey(targetId)) {
            targetNodeId = getMatchingClusterStartNodeId(competencyToMatchCluster.get(targetId));
        }
        else {
            targetNodeId = getCompetencyStartNodeId(targetId);
        }
        final String relationEdgeId = getRelationEdgeId(sourceNodeId, targetNodeId);
        // skip if relation has already been created (possible for edges linked to matching cluster start/end nodes)
        if (!createdRelations.contains(relationEdgeId)) {
            final var edge = new NgxLearningPathDTO.Edge(relationEdgeId, sourceNodeId, targetNodeId);
            edges.add(edge);
            createdRelations.add(relationEdgeId);
        }
    }

    public static String getCompetencyStartNodeId(long competencyId) {
        return "node-" + competencyId + "-start";
    }

    public static String getCompetencyEndNodeId(long competencyId) {
        return "node-" + competencyId + "-end";
    }

    public static String getLectureUnitNodeId(long competencyId, long lectureUnitId) {
        return "node-" + competencyId + "-lu-" + lectureUnitId;
    }

    public static String getExerciseNodeId(long competencyId, long exerciseId) {
        return "node-" + competencyId + "-ex-" + exerciseId;
    }

    public static String getMatchingClusterStartNodeId(long matchingClusterId) {
        return "matching-" + matchingClusterId + "-start";
    }

    public static String getMatchingClusterEndNodeId(long matchingClusterId) {
        return "matching-" + matchingClusterId + "-end";
    }

    public static String getLectureUnitInEdgeId(long competencyId, long lectureUnitId) {
        return "edge-" + competencyId + "-lu-" + getInEdgeId(lectureUnitId);
    }

    public static String getLectureUnitOutEdgeId(long competencyId, long lectureUnitId) {
        return "edge-" + competencyId + "-lu-" + getOutEdgeId(lectureUnitId);
    }

    public static String getExerciseInEdgeId(long competencyId, long exercise) {
        return "edge-" + competencyId + "-ex-" + getInEdgeId(exercise);
    }

    public static String getExerciseOutEdgeId(long competencyId, long exercise) {
        return "edge-" + competencyId + "-ex-" + getOutEdgeId(exercise);
    }

    public static String getInEdgeId(long id) {
        return "edge-" + id + "-in";
    }

    public static String getOutEdgeId(long id) {
        return "edge-" + id + "-out";
    }

    public static String getRelationEdgeId(String sourceNodeId, String targetNodeId) {
        return "edge-relation-" + sourceNodeId + "-" + targetNodeId;
    }

    public static String getDirectEdgeId(long competencyId) {
        return "edge-" + competencyId + "-direct";
    }
}
