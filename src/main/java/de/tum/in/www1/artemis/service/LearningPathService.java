package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
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

    private final CompetencyRepository competencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    /**
     * Base utility that is used to calculate a competencies' utility with respect to the earliest due date of the competency.
     */
    private static final double DUE_DATE_UTILITY = 10;

    /**
     * Base utility that is used to calculate a competencies' utility with respect to the number of mastered prior competencies.
     */
    private static final double PRIOR_UTILITY = 150;

    /**
     * Base utility and ratios that are used to calculate a competencies' utility with respect to the number of competencies that this competency extends or assumes.
     * <p>
     * Ratios donate the importance of the relation compared to the other.
     * Important: EXTENDS_UTILITY_RATIO should be smaller than ASSUMES_UTILITY_RATIO to prefer extends-relation to assumes-relations.
     */
    private static final double EXTENDS_UTILITY_RATIO = 1;

    private static final double ASSUMES_UTILITY_RATIO = 2;

    private static final double EXTENDS_OR_ASSUMES_UTILITY = 100;

    /**
     * Base utility that is used to calculate a competencies' utility with respect to the mastery level.
     */
    private static final double MASTERY_PROGRESS_UTILITY = 1;

    public LearningPathService(UserRepository userRepository, LearningPathRepository learningPathRepository, CompetencyProgressRepository competencyProgressRepository,
            CourseRepository courseRepository, CompetencyRepository competencyRepository, CompetencyRelationRepository competencyRelationRepository) {
        this.userRepository = userRepository;
        this.learningPathRepository = learningPathRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.courseRepository = courseRepository;
        this.competencyRepository = competencyRepository;
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
            return new LearningPathHealthDTO(Set.of(LearningPathHealthDTO.HealthStatus.DISABLED));
        }

        Set<LearningPathHealthDTO.HealthStatus> status = new HashSet<>();
        Long numberOfMissingLearningPaths = checkMissingLearningPaths(course, status);
        checkNoCompetencies(course, status);
        checkNoRelations(course, status);

        // if no issues where found, add OK status
        if (status.isEmpty()) {
            status.add(LearningPathHealthDTO.HealthStatus.OK);
        }

        return new LearningPathHealthDTO(status, numberOfMissingLearningPaths);
    }

    private Long checkMissingLearningPaths(@NotNull Course course, @NotNull Set<LearningPathHealthDTO.HealthStatus> status) {
        long numberOfStudents = userRepository.countUserInGroup(course.getStudentGroupName());
        long numberOfLearningPaths = learningPathRepository.countLearningPathsOfEnrolledStudentsInCourse(course.getId());
        Long numberOfMissingLearningPaths = numberOfStudents - numberOfLearningPaths;

        if (numberOfMissingLearningPaths != 0) {
            status.add(LearningPathHealthDTO.HealthStatus.MISSING);
        }
        else {
            numberOfMissingLearningPaths = null;
        }

        return numberOfMissingLearningPaths;
    }

    private void checkNoCompetencies(@NotNull Course course, @NotNull Set<LearningPathHealthDTO.HealthStatus> status) {
        if (competencyRepository.countByCourse(course) == 0) {
            status.add(LearningPathHealthDTO.HealthStatus.NO_COMPETENCIES);
        }
    }

    private void checkNoRelations(@NotNull Course course, @NotNull Set<LearningPathHealthDTO.HealthStatus> status) {
        if (competencyRelationRepository.countByCourseId(course.getId()) == 0) {
            status.add(LearningPathHealthDTO.HealthStatus.NO_RELATIONS);
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

    /**
     * Generates Ngx path representation of the learning path.
     *
     * @param learningPath the learning path for which the Ngx representation should be created
     * @return Ngx path representation of the learning path
     * @see NgxLearningPathDTO
     */
    public NgxLearningPathDTO generateNgxPathRepresentation(@NotNull LearningPath learningPath) {
        Set<NgxLearningPathDTO.Node> nodes = new HashSet<>();
        Set<NgxLearningPathDTO.Edge> edges = new HashSet<>();

        var recommendedOrderOfCompetenciesById = getRecommendedOrderOfCompetencies(learningPath);
        var recommendedOrderOfCompetencies = recommendedOrderOfCompetenciesById.stream()
                .map(id -> learningPath.getCompetencies().stream().filter(competency -> competency.getId().equals(id)).findFirst().get()).toList();

        // generate ngx representation of recommended competencies
        // IMPORTANT generateNgxGraphRepresentationForCompetency will be replaced by future PR
        recommendedOrderOfCompetencies.forEach(competency -> generateNgxGraphRepresentationForCompetency(learningPath, competency, nodes, edges));
        // generate edges between competencies
        for (int i = 0; i < recommendedOrderOfCompetencies.size() - 1; i++) {
            var sourceNodeId = getCompetencyEndNodeId(recommendedOrderOfCompetenciesById.get(i));
            var targetNodeId = getCompetencyStartNodeId(recommendedOrderOfCompetenciesById.get(i + 1));
            edges.add(new NgxLearningPathDTO.Edge(getRelationEdgeId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
        }

        return new NgxLearningPathDTO(nodes, edges);
    }

    /**
     * Analyzes the current progress within the learning path and generates a recommended ordering of competencies.
     *
     * @param learningPath the learning path that should be analyzed
     * @return the recommended ordering of competencies
     */
    private List<Long> getRecommendedOrderOfCompetencies(LearningPath learningPath) {
        RecommendationState state = generateInitialRecommendationState(learningPath);
        var pendingCompetencies = getPendingCompetencies(learningPath.getCompetencies(), state);
        return simulateProgression(pendingCompetencies, state);
    }

    /**
     * Generates the initial state of the recommendation containing all necessary information for the prediction.
     *
     * @param learningPath the learning path that should be analyzed
     * @return the initial RecommendationState
     * @see RecommendationState
     */
    private RecommendationState generateInitialRecommendationState(LearningPath learningPath) {
        Map<Long, Set<Long>> matchingClusters = getMatchingCompetencyClusters(learningPath.getCompetencies());
        Map<Long, Set<Long>> priorsCompetencies = getPriorCompetencyMapping(learningPath.getCompetencies(), matchingClusters);
        Map<Long, Long> extendsCompetencies = getExtendsCompetencyMapping(learningPath.getCompetencies(), matchingClusters, priorsCompetencies);
        Map<Long, Long> assumesCompetencies = getAssumesCompetencyMapping(learningPath.getCompetencies(), matchingClusters, priorsCompetencies);
        Set<Long> masteredCompetencies = new HashSet<>();
        // map of non-mastered competencies to their normalized mastery score with respect to the associated threshold
        Map<Long, Double> competencyMastery = new HashMap<>();
        learningPath.getCompetencies().forEach(competency -> {
            // fetched learning path only contains data of the associated user
            final var progress = competency.getUserProgress().stream().findFirst();
            if (progress.isEmpty()) {
                competencyMastery.put(competency.getId(), 0d);
            }
            else if (CompetencyProgressService.isMastered(progress.get())) {
                // add competency to mastered set if mastered
                masteredCompetencies.add(competency.getId());
            }
            else {
                // calculate mastery progress if not completed yet
                competencyMastery.put(competency.getId(), CompetencyProgressService.getMasteryProgress(progress.get()));
            }
        });
        return new RecommendationState(masteredCompetencies, competencyMastery, matchingClusters, priorsCompetencies, extendsCompetencies, assumesCompetencies);
    }

    /**
     * Gets a map from competency ids to a set of all other competency ids that are connected via matching relations (transitive closure, including the competency itself).
     *
     * @param competencies the competencies for which the mapping should be generated
     * @return map representing the matching clusters
     */
    private Map<Long, Set<Long>> getMatchingCompetencyClusters(Set<Competency> competencies) {
        final Map<Long, Set<Long>> matchingClusters = new HashMap<>();
        for (var competency : competencies) {
            if (!matchingClusters.containsKey(competency.getId())) {
                final var matchingCompetencies = competencyRelationRepository.getMatchingCompetenciesByCompetencyId(competency.getId());
                // add for each in cluster to reduce database calls (once per cluster)
                matchingCompetencies.forEach(id -> matchingClusters.put(id, matchingCompetencies));
            }
        }
        return matchingClusters;
    }

    /**
     * Gets a map from competency ids to a set of all other competency ids that are connected via a non-matching relation.
     *
     * @param competencies     the competencies for which the mapping should be generated
     * @param matchingClusters the map representing the corresponding matching clusters
     * @return map to retrieve prior competencies
     */
    private Map<Long, Set<Long>> getPriorCompetencyMapping(Set<Competency> competencies, Map<Long, Set<Long>> matchingClusters) {
        Map<Long, Set<Long>> priorsMap = new HashMap<>();
        for (var competency : competencies) {
            if (!priorsMap.containsKey(competency.getId())) {
                final var priors = competencyRelationRepository.getPriorCompetenciesByCompetencyIds(matchingClusters.get(competency.getId()));
                // add for each in cluster to reduce database calls (once per cluster)
                matchingClusters.get(competency.getId()).forEach(id -> priorsMap.put(id, priors));
            }
        }
        return priorsMap;
    }

    /**
     * Gets a map from competency ids to number of competencies that the corresponding competency extends.
     *
     * @param competencies      the competencies for which the mapping should be generated
     * @param matchingClusters  the map representing the corresponding matching clusters
     * @param priorCompetencies the map to retrieve corresponding prior competencies
     * @return map to retrieve the number of competencies a competency extends
     */
    private Map<Long, Long> getExtendsCompetencyMapping(Set<Competency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies) {
        return getRelationsOfTypeCompetencyMapping(competencies, matchingClusters, priorCompetencies, CompetencyRelation.RelationType.EXTENDS);
    }

    /**
     * Gets a map from competency ids to number of competencies that the corresponding competency assumes.
     *
     * @param competencies      the competencies for which the mapping should be generated
     * @param matchingClusters  the map representing the corresponding matching clusters
     * @param priorCompetencies the map to retrieve corresponding prior competencies
     * @return map to retrieve the number of competencies a competency assumes
     */
    private Map<Long, Long> getAssumesCompetencyMapping(Set<Competency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies) {
        return getRelationsOfTypeCompetencyMapping(competencies, matchingClusters, priorCompetencies, CompetencyRelation.RelationType.ASSUMES);
    }

    /**
     * Gets a map from competency ids to number of competencies that the corresponding competency relates to with the specified type.
     *
     * @param competencies      the competencies for which the mapping should be generated
     * @param matchingClusters  the map representing the corresponding matching clusters
     * @param priorCompetencies the map to retrieve corresponding prior competencies
     * @param type              the relation type that should be counted
     * @return map to retrieve the number of competencies a competency extends
     */
    private Map<Long, Long> getRelationsOfTypeCompetencyMapping(Set<Competency> competencies, Map<Long, Set<Long>> matchingClusters, Map<Long, Set<Long>> priorCompetencies,
            CompetencyRelation.RelationType type) {
        Map<Long, Long> map = new HashMap<>();
        for (var competency : competencies) {
            if (!map.containsKey(competency.getId())) {
                long numberOfRelations = competencyRelationRepository.countRelationsOfTypeBetweenCompetencyGroups(matchingClusters.get(competency.getId()), type,
                        priorCompetencies.get(competency.getId()));
                // add for each in cluster to reduce database calls (once per cluster)
                matchingClusters.get(competency.getId()).forEach(id -> map.put(id, numberOfRelations));
            }
        }
        return map;
    }

    /**
     * Gets the set of competencies that are themselves not mastered and no matching competency is mastered.
     *
     * @param competencies the set of competencies that should be filtered
     * @param state        the current state of the recommendation system
     * @return set of pending competencies
     */
    private Set<Competency> getPendingCompetencies(Set<Competency> competencies, RecommendationState state) {
        Set<Competency> pendingCompetencies = new HashSet<>(competencies);
        pendingCompetencies.removeIf(competency -> state.masteredCompetencies.contains(competency.getId())
                || state.matchingClusters.get(competency.getId()).stream().anyMatch(state.masteredCompetencies::contains));
        return pendingCompetencies;
    }

    /**
     * Generates a recommended ordering of competencies.
     *
     * @param pendingCompetencies the set of pending competencies
     * @param state               the current state of the recommendation system
     * @return recommended ordering of competencies
     */
    private List<Long> simulateProgression(Set<Competency> pendingCompetencies, RecommendationState state) {
        List<Long> recommendedOrder = new ArrayList<>();
        while (!pendingCompetencies.isEmpty()) {
            Map<Long, Double> utilities = computeUtilities(pendingCompetencies, state);
            var maxEntry = utilities.entrySet().stream().max(Comparator.comparingDouble(Map.Entry::getValue));
            // is present since outstandingCompetencies is not empty
            Long competencyId = maxEntry.get().getKey();

            // add competency to recommended order
            recommendedOrder.add(competencyId);

            // simulate completion of competency
            state.masteredCompetencies.add(competencyId);
            pendingCompetencies
                    .removeIf(competency -> competency.getId().equals(competencyId) || state.matchingClusters.get(competency.getId()).stream().anyMatch(competencyId::equals));
        }
        return recommendedOrder;
    }

    /**
     * Generates a mapping from competency ids to their corresponding utility in the current state.
     *
     * @param competencies the set of competencies for which the mapping should be generated
     * @param state        the current state of the recommendation system
     * @return map to retrieve the utility of a competency
     */
    private Map<Long, Double> computeUtilities(Set<Competency> competencies, RecommendationState state) {
        Map<Long, Double> utilities = new HashMap<>();
        for (var competency : competencies) {
            utilities.put(competency.getId(), computeUtilityOfCompetency(competency, state));
        }
        return utilities;
    }

    /**
     * Gets the utility of a competency in the current state.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return the utility of the given competency
     */
    private double computeUtilityOfCompetency(Competency competency, RecommendationState state) {
        // if competency is already mastered there competency has no utility
        if (state.masteredCompetencies.contains(competency.getId())) {
            return 0;
        }
        double utility = 0;
        utility += computeDueDateUtility(competency);
        utility += computePriorUtility(competency, state);
        utility += computeExtendsOrAssumesUtility(competency, state);
        utility += computeMasteryUtility(competency, state);
        return utility;
    }

    /**
     * Gets the utility of the competency with respect to the earliest due date of the competency.
     *
     * @param competency the competency for which the utility should be computed
     * @return due date utility of the competency
     */
    private static double computeDueDateUtility(Competency competency) {
        final var earliestDueDate = getEarliestDueDate(competency);
        if (earliestDueDate.isEmpty()) {
            return 0;
        }
        double timeDelta = ChronoUnit.DAYS.between(ZonedDateTime.now(), earliestDueDate.get());

        if (timeDelta < 0) {
            // deadline has passed
            return (-timeDelta) * DUE_DATE_UTILITY;
        }
        else if (timeDelta > 0) {
            // deadline not passed yet
            return (1 / timeDelta) * DUE_DATE_UTILITY;
        }
        else {
            return DUE_DATE_UTILITY;
        }
    }

    /**
     * Gets the earliest due date of any learning object attached to the competency or the competency itself.
     *
     * @param competency the competency for which the earliest due date should be retrieved
     * @return earliest due date of the competency
     */
    private static Optional<ZonedDateTime> getEarliestDueDate(Competency competency) {
        final var lectureDueDates = competency.getLectureUnits().stream().map(LectureUnit::getLecture).map(Lecture::getEndDate);
        final var exerciseDueDates = competency.getExercises().stream().map(Exercise::getDueDate);
        return Stream.concat(Stream.concat(Stream.of(competency.getSoftDueDate()), lectureDueDates), exerciseDueDates).filter(Objects::nonNull).min(Comparator.naturalOrder());
    }

    /**
     * Gets the utility of the competency with respect to prior competencies.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return prior utility of the competency
     */
    private static double computePriorUtility(Competency competency, RecommendationState state) {
        // return max utility if no prior competencies are present
        if (state.priorCompetencies.get(competency.getId()).size() == 0) {
            return PRIOR_UTILITY;
        }
        final double masteredPriorCompetencies = state.priorCompetencies.get(competency.getId()).stream()
                .filter(id -> state.masteredCompetencies.contains(id) || state.matchingClusters.get(id).stream().anyMatch(state.masteredCompetencies::contains)).count();
        final double weight = masteredPriorCompetencies / state.priorCompetencies.get(competency.getId()).size();
        return weight * PRIOR_UTILITY;
    }

    /**
     * Gets the utility of the competency with respect to prior competencies that are extended or assumed by this competency.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return extends or assumes utility of the competency
     */
    private static double computeExtendsOrAssumesUtility(Competency competency, RecommendationState state) {
        final double weight = state.extendsCompetencies.get(competency.getId()) * EXTENDS_UTILITY_RATIO + state.assumesCompetencies.get(competency.getId()) * ASSUMES_UTILITY_RATIO;
        // return max utility if competency does not extend or assume other competencies
        if (weight == 0) {
            return EXTENDS_OR_ASSUMES_UTILITY;
        }
        return (1 / weight) * EXTENDS_OR_ASSUMES_UTILITY;

    }

    /**
     * Gets the utility of the competency with respect to users mastery progress within the competency.
     *
     * @param competency the competency for which the utility should be computed
     * @param state      the current state of the recommendation system
     * @return mastery utility of the competency
     */
    private static double computeMasteryUtility(Competency competency, RecommendationState state) {
        return state.competencyMastery.get(competency.getId()) * MASTERY_PROGRESS_UTILITY;
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

    private record RecommendationState(Set<Long> masteredCompetencies, Map<Long, Double> competencyMastery, Map<Long, Set<Long>> matchingClusters,
            Map<Long, Set<Long>> priorCompetencies, Map<Long, Long> extendsCompetencies, Map<Long, Long> assumesCompetencies) {
    }
}
