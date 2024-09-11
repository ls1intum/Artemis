package de.tum.cit.aet.artemis.service.learningpath;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.jgrapht.alg.util.UnionFind;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.LearningObject;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.domain.competency.RelationType;
import de.tum.cit.aet.artemis.domain.lecture.LectureUnit;
import de.tum.cit.aet.artemis.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.web.rest.dto.competency.NgxLearningPathDTO;

/**
 * Service Implementation for the generation of ngx representations of learning paths.
 */
@Profile(PROFILE_CORE)
@Service
public class LearningPathNgxService {

    private final CompetencyRelationRepository competencyRelationRepository;

    private final LearningPathRecommendationService learningPathRecommendationService;

    protected LearningPathNgxService(CompetencyRelationRepository competencyRelationRepository, LearningPathRecommendationService learningPathRecommendationService) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.learningPathRecommendationService = learningPathRecommendationService;
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
    private void generateNgxGraphRepresentationForCompetency(LearningPath learningPath, CourseCompetency competency, Set<NgxLearningPathDTO.Node> nodes,
            Set<NgxLearningPathDTO.Edge> edges) {
        Set<NgxLearningPathDTO.Node> currentCluster = new HashSet<>();
        // generates start and end node
        final var startNodeId = getCompetencyStartNodeId(competency.getId());
        final var endNodeId = getCompetencyEndNodeId(competency.getId());
        currentCluster.add(NgxLearningPathDTO.Node.of(startNodeId, NgxLearningPathDTO.NodeType.COMPETENCY_START, competency.getId()));
        currentCluster.add(NgxLearningPathDTO.Node.of(endNodeId, NgxLearningPathDTO.NodeType.COMPETENCY_END, competency.getId()));

        // generate nodes and edges for lecture units
        competency.getLectureUnits().forEach(lectureUnit -> {
            currentCluster.add(NgxLearningPathDTO.Node.of(getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), NgxLearningPathDTO.NodeType.LECTURE_UNIT,
                    lectureUnit.getId(), lectureUnit.getLecture().getId(), lectureUnit.isCompletedFor(learningPath.getUser()), lectureUnit.getName()));
            edges.add(new NgxLearningPathDTO.Edge(getLectureUnitInEdgeId(competency.getId(), lectureUnit.getId()), startNodeId,
                    getLectureUnitNodeId(competency.getId(), lectureUnit.getId())));
            edges.add(new NgxLearningPathDTO.Edge(getLectureUnitOutEdgeId(competency.getId(), lectureUnit.getId()), getLectureUnitNodeId(competency.getId(), lectureUnit.getId()),
                    endNodeId));
        });
        // generate nodes and edges for exercises
        competency.getExercises().forEach(exercise -> {
            currentCluster.add(NgxLearningPathDTO.Node.of(getExerciseNodeId(competency.getId(), exercise.getId()), NgxLearningPathDTO.NodeType.EXERCISE, exercise.getId(), false,
                    exercise.getTitle()));
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
        final var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(learningPath.getCourse().getId());

        // compute match clusters
        Map<Long, Integer> competencyToMatchCluster = new HashMap<>();
        final var competenciesInMatchRelation = relations.stream().filter(relation -> relation.getType().equals(RelationType.MATCHES))
                .flatMap(relation -> Stream.of(relation.getHeadCompetency().getId(), relation.getTailCompetency().getId())).collect(Collectors.toSet());
        if (!competenciesInMatchRelation.isEmpty()) {
            UnionFind<Long> matchClusters = new UnionFind<>(competenciesInMatchRelation);
            relations.stream().filter(relation -> relation.getType().equals(RelationType.MATCHES))
                    .forEach(relation -> matchClusters.union(relation.getHeadCompetency().getId(), relation.getTailCompetency().getId()));

            // generate map between competencies and cluster node
            AtomicInteger matchClusterId = new AtomicInteger();
            relations.stream().filter(relation -> relation.getType().equals(RelationType.MATCHES))
                    .flatMapToLong(relation -> LongStream.of(relation.getHeadCompetency().getId(), relation.getTailCompetency().getId())).distinct().forEach(competencyId -> {
                        var parentId = matchClusters.find(competencyId);
                        var clusterId = competencyToMatchCluster.computeIfAbsent(parentId, (key) -> matchClusterId.getAndIncrement());
                        competencyToMatchCluster.put(competencyId, clusterId);
                    });

            // generate match cluster start and end nodes
            for (int i = 0; i < matchClusters.numberOfSets(); i++) {
                nodes.add(NgxLearningPathDTO.Node.of(getMatchingClusterStartNodeId(i), NgxLearningPathDTO.NodeType.MATCH_START));
                nodes.add(NgxLearningPathDTO.Node.of(getMatchingClusterEndNodeId(i), NgxLearningPathDTO.NodeType.MATCH_END));
            }

            // generate edges between match cluster nodes and corresponding competencies
            competencyToMatchCluster.forEach((competency, cluster) -> {
                edges.add(new NgxLearningPathDTO.Edge(getInEdgeId(competency), getMatchingClusterStartNodeId(cluster), getCompetencyStartNodeId(competency)));
                edges.add(new NgxLearningPathDTO.Edge(getOutEdgeId(competency), getCompetencyEndNodeId(competency), getMatchingClusterEndNodeId(cluster)));
            });
        }

        // generate edges for remaining relations
        final Set<String> createdRelations = new HashSet<>();
        relations.stream().filter(relation -> !relation.getType().equals(RelationType.MATCHES))
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
        final String relationEdgeId = getEdgeFromToId(sourceNodeId, targetNodeId);
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

        var recommendationState = learningPathRecommendationService.getRecommendedOrderOfNotMasteredCompetencies(learningPath);
        var recommendedOrderOfCompetencies = recommendationState.recommendedOrderOfCompetencies().stream()
                .map(id -> learningPath.getCompetencies().stream().filter(competency -> competency.getId().equals(id)).findFirst().get()).toList();

        // generate ngx representation of recommended competencies
        recommendedOrderOfCompetencies.forEach(competency -> generateNgxPathRepresentationForCompetency(learningPath.getUser(), competency, nodes, edges, recommendationState));
        // generate edges between competencies
        for (int i = 0; i < recommendedOrderOfCompetencies.size() - 1; i++) {
            var sourceNodeId = getCompetencyEndNodeId(recommendationState.recommendedOrderOfCompetencies().get(i));
            var targetNodeId = getCompetencyStartNodeId(recommendationState.recommendedOrderOfCompetencies().get(i + 1));
            edges.add(new NgxLearningPathDTO.Edge(getEdgeFromToId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
        }

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
     * @param user       the user for which the representation should be created
     * @param competency the competency for which the representation will be created
     * @param nodes      set of nodes to store the new nodes
     * @param edges      set of edges to store the new edges
     */
    private void generateNgxPathRepresentationForCompetency(User user, CourseCompetency competency, Set<NgxLearningPathDTO.Node> nodes, Set<NgxLearningPathDTO.Edge> edges,
            LearningPathRecommendationService.RecommendationState state) {
        Set<NgxLearningPathDTO.Node> currentCluster = new HashSet<>();
        // generates start and end node
        final var startNodeId = getCompetencyStartNodeId(competency.getId());
        final var endNodeId = getCompetencyEndNodeId(competency.getId());
        currentCluster.add(NgxLearningPathDTO.Node.of(startNodeId, NgxLearningPathDTO.NodeType.COMPETENCY_START, competency.getId()));
        currentCluster.add(NgxLearningPathDTO.Node.of(endNodeId, NgxLearningPathDTO.NodeType.COMPETENCY_END, competency.getId()));

        final var recommendedLearningObjects = learningPathRecommendationService.getRecommendedOrderOfLearningObjects(user, competency, state);
        for (int i = 0; i < recommendedLearningObjects.size(); i++) {

            // add node for learning object
            addNodeForLearningObject(competency, recommendedLearningObjects.get(i), currentCluster);

            // add edges between learning objects
            if (i != 0) {
                addEdgeBetweenLearningObjects(competency, recommendedLearningObjects.get(i - 1), recommendedLearningObjects.get(i), edges);
            }
        }

        // if no linked learning units exist directly link start to end (can't happen for valid recommendations)
        if (currentCluster.size() == 2) {
            edges.add(new NgxLearningPathDTO.Edge(getDirectEdgeId(competency.getId()), startNodeId, endNodeId));
        }
        else {
            // add edge from competency start to first learning object
            addEdgeFromCompetencyStartToLearningObject(competency, recommendedLearningObjects.getFirst(), edges);

            // add edge from last learning object to competency end
            addEdgeFromLearningObjectToCompetencyEnd(competency, recommendedLearningObjects.getLast(), edges);
        }

        nodes.addAll(currentCluster);
    }

    private static void addNodeForLearningObject(CourseCompetency competency, LearningObject learningObject, Set<NgxLearningPathDTO.Node> nodes) {
        if (learningObject instanceof LectureUnit lectureUnit) {
            nodes.add(NgxLearningPathDTO.Node.of(getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), NgxLearningPathDTO.NodeType.LECTURE_UNIT, lectureUnit.getId(),
                    lectureUnit.getLecture().getId(), false, lectureUnit.getName()));
        }
        else if (learningObject instanceof Exercise exercise) {
            nodes.add(NgxLearningPathDTO.Node.of(getExerciseNodeId(competency.getId(), exercise.getId()), NgxLearningPathDTO.NodeType.EXERCISE, exercise.getId(), false,
                    exercise.getTitle()));
        }
    }

    private static void addEdgeBetweenLearningObjects(CourseCompetency competency, LearningObject source, LearningObject target, Set<NgxLearningPathDTO.Edge> edges) {
        final var sourceId = getLearningObjectNodeId(competency.getId(), source);
        final var targetId = getLearningObjectNodeId(competency.getId(), target);
        edges.add(new NgxLearningPathDTO.Edge(getEdgeFromToId(sourceId, targetId), sourceId, targetId));
    }

    private static void addEdgeFromCompetencyStartToLearningObject(CourseCompetency competency, LearningObject learningObject, Set<NgxLearningPathDTO.Edge> edges) {
        addEdgeFromSourceToTarget(getCompetencyStartNodeId(competency.getId()), getLearningObjectNodeId(competency.getId(), learningObject), edges);
    }

    private static void addEdgeFromLearningObjectToCompetencyEnd(CourseCompetency competency, LearningObject learningObject, Set<NgxLearningPathDTO.Edge> edges) {
        addEdgeFromSourceToTarget(getLearningObjectNodeId(competency.getId(), learningObject), getCompetencyEndNodeId(competency.getId()), edges);
    }

    private static void addEdgeFromSourceToTarget(String sourceId, String targetId, Set<NgxLearningPathDTO.Edge> edges) {
        edges.add(new NgxLearningPathDTO.Edge(getEdgeFromToId(sourceId, targetId), sourceId, targetId));
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

    public static String getEdgeFromToId(String sourceNodeId, String targetNodeId) {
        return "edge-" + sourceNodeId + "-" + targetNodeId;
    }

    public static String getDirectEdgeId(long competencyId) {
        return "edge-" + competencyId + "-direct";
    }

    /**
     * Gets the node id of the given lecture unit or exercise.
     *
     * @param competencyId   the id of the competency that the learning object is linked to
     * @param learningObject the lecture unit or exercise
     * @return the ngx node id of the given lecture unit or exercise
     */
    public static String getLearningObjectNodeId(long competencyId, LearningObject learningObject) {
        if (learningObject instanceof LectureUnit) {
            return getLectureUnitNodeId(competencyId, learningObject.getId());
        }
        else if (learningObject instanceof Exercise) {
            return getExerciseNodeId(competencyId, learningObject.getId());
        }
        return null;
    }
}
