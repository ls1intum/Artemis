import { Service, inject } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import {
    CompetencyGraphDTO,
    CompetencyGraphEdgeDTO,
    CompetencyGraphNodeDTO,
    CompetencyGraphNodeValueType,
    LearningObjectType,
    LearningPathAverageProgressDTO,
    LearningPathCompetencyDTO,
    LearningPathDTO,
    LearningPathInformationDTO,
    LearningPathNavigationDTO,
    LearningPathNavigationObjectDTO,
    LearningPathNavigationOverviewDTO,
} from 'app/atlas/shared/entities/learning-path.model';
import { HealthStatus, LearningPathHealthDTO } from 'app/atlas/shared/entities/learning-path-health.model';
import { CompetencyRelationType } from 'app/atlas/shared/entities/competency.model';
import { SearchResult, SearchTermPageableSearch } from 'app/foundation/pagination/pageable-table';
import { LearningPathApi } from 'app/openapi/api/learning-path-api';
import { CompetencyGraphEdge, CompetencyGraphEdgeRelationTypeEnum } from 'app/openapi/model/competency-graph-edge';
import { CompetencyGraphNode, CompetencyGraphNodeValueTypeEnum } from 'app/openapi/model/competency-graph-node';
import { LearningPathCompetencyGraph } from 'app/openapi/model/learning-path-competency-graph';
import { LearningPathHealthStatusEnum } from 'app/openapi/model/learning-path-health';
import { LearningPathNavigation } from 'app/openapi/model/learning-path-navigation';
import { LearningPathNavigationObject, LearningPathNavigationObjectTypeEnum } from 'app/openapi/model/learning-path-navigation-object';

// The generated literal unions carry the same values as the client enums. These maps are checked for completeness by the compiler.
const LEARNING_OBJECT_TYPES: Record<LearningPathNavigationObjectTypeEnum, LearningObjectType> = {
    LECTURE: LearningObjectType.LECTURE,
    EXERCISE: LearningObjectType.EXERCISE,
};

const NODE_VALUE_TYPES: Record<CompetencyGraphNodeValueTypeEnum, CompetencyGraphNodeValueType> = {
    MASTERY_PROGRESS: CompetencyGraphNodeValueType.MASTERY_PROGRESS,
    AVERAGE_MASTERY_PROGRESS: CompetencyGraphNodeValueType.AVERAGE_MASTERY_PROGRESS,
};

const RELATION_TYPES: Record<CompetencyGraphEdgeRelationTypeEnum, CompetencyRelationType> = {
    ASSUMES: CompetencyRelationType.ASSUMES,
    EXTENDS: CompetencyRelationType.EXTENDS,
    MATCHES: CompetencyRelationType.MATCHES,
};

const HEALTH_STATUSES: Record<LearningPathHealthStatusEnum, HealthStatus> = {
    MISSING: HealthStatus.MISSING,
    NO_COMPETENCIES: HealthStatus.NO_COMPETENCIES,
    NO_RELATIONS: HealthStatus.NO_RELATIONS,
};

/**
 * Promise-based access to the learning path endpoints, delegating to the generated {@link LearningPathApi}.
 *
 * The server omits empty lists and empty strings. This service restores the empty lists its callers expect, converts dates, and maps
 * the generated literal unions to the client enums. A field that the server always sends but the specification cannot mark as required
 * (it belongs to a DTO shared with other endpoints) is checked here and reported as an error when it is missing.
 */
@Service()
export class LearningPathApiService {
    private readonly learningPathApi = inject(LearningPathApi);

    async getLearningPathForCurrentUser(courseId: number): Promise<LearningPathDTO> {
        return await lastValueFrom(this.learningPathApi.getLearningPathForCurrentUser(courseId));
    }

    async startLearningPathForCurrentUser(learningPathId: number): Promise<void> {
        await lastValueFrom(this.learningPathApi.startLearningPathForCurrentUser(learningPathId));
    }

    async getLearningPathNavigation(learningPathId: number): Promise<LearningPathNavigationDTO> {
        return toNavigation(await lastValueFrom(this.learningPathApi.getLearningPathNavigation(learningPathId)));
    }

    /**
     * Gets the navigation relative to a learning object. repeatedTest is not sent, so the server uses its default (false).
     */
    async getRelativeLearningPathNavigation(
        learningPathId: number,
        learningObjectId: number,
        learningObjectType: LearningObjectType,
        competencyId: number,
    ): Promise<LearningPathNavigationDTO> {
        return toNavigation(await lastValueFrom(this.learningPathApi.getRelativeLearningPathNavigation(learningPathId, learningObjectId, learningObjectType, competencyId)));
    }

    /**
     * Generates the learning path of the current user. The server answers 201 with the new learning path in the body.
     */
    async generateLearningPathForCurrentUser(courseId: number): Promise<LearningPathDTO> {
        const response = await lastValueFrom(this.learningPathApi.generateLearningPathForCurrentUser(courseId));
        if (!response.body) {
            throw new Error('The server created a learning path but sent no body');
        }
        return response.body;
    }

    async getLearningPathNavigationOverview(learningPathId: number): Promise<LearningPathNavigationOverviewDTO> {
        const overview = await lastValueFrom(this.learningPathApi.getLearningPathNavigationOverview(learningPathId));
        return { learningObjects: (overview.learningObjects ?? []).map(toNavigationObject) };
    }

    async getLearningPathCompetencyGraph(learningPathId: number): Promise<CompetencyGraphDTO> {
        return toCompetencyGraph(await lastValueFrom(this.learningPathApi.getLearningPathCompetencyGraph(learningPathId)));
    }

    async getLearningPathInstructorCompetencyGraph(courseId: number): Promise<CompetencyGraphDTO> {
        return toCompetencyGraph(await lastValueFrom(this.learningPathApi.getLearningPathCompetencyInstructorGraph(courseId)));
    }

    async getLearningPathCompetencies(learningPathId: number): Promise<LearningPathCompetencyDTO[]> {
        return await lastValueFrom(this.learningPathApi.getCompetencyOrderForLearningPath(learningPathId));
    }

    async getLearningPathCompetencyLearningObjects(learningPathId: number, competencyId: number): Promise<LearningPathNavigationObjectDTO[]> {
        const learningObjects = await lastValueFrom(this.learningPathApi.getLearningObjectsForCompetency(learningPathId, competencyId));
        return learningObjects.map(toNavigationObject);
    }

    async getLearningPathHealthStatus(courseId: number): Promise<LearningPathHealthDTO> {
        const health = await lastValueFrom(this.learningPathApi.getHealthStatusForCourse(courseId));
        // A healthy course has no status, which the server omits.
        const learningPathHealth = new LearningPathHealthDTO((health.status ?? []).map((status) => HEALTH_STATUSES[status]));
        learningPathHealth.missingLearningPaths = health.missingLearningPaths;
        return learningPathHealth;
    }

    async enableLearningPaths(courseId: number): Promise<void> {
        await lastValueFrom(this.learningPathApi.enableLearningPathsForCourse(courseId));
    }

    async generateMissingLearningPaths(courseId: number): Promise<void> {
        await lastValueFrom(this.learningPathApi.generateMissingLearningPathsForCourse(courseId));
    }

    async getLearningPathInformation(courseId: number, pageable: SearchTermPageableSearch): Promise<SearchResult<LearningPathInformationDTO>> {
        const page = await lastValueFrom(
            this.learningPathApi.getLearningPathsOnPage(courseId, pageable.page, pageable.pageSize, pageable.sortedColumn, pageable.sortingOrder, pageable.searchTerm),
        );
        // The server omits the results of an empty page.
        return { resultsOnPage: page.resultsOnPage ?? [], numberOfPages: requireField(page.numberOfPages, 'numberOfPages') };
    }

    async getAverageProgressForCourse(courseId: number): Promise<LearningPathAverageProgressDTO> {
        return await lastValueFrom(this.learningPathApi.getAverageProgressForCourse(courseId));
    }
}

function requireField<T>(value: T | undefined, field: string): T {
    if (value === undefined) {
        throw new Error(`The server response has no ${field}`);
    }
    return value;
}

function toNavigationObject(learningObject: LearningPathNavigationObject): LearningPathNavigationObjectDTO {
    return {
        id: learningObject.id,
        completed: learningObject.completed,
        name: learningObject.name,
        competencyId: learningObject.competencyId,
        type: LEARNING_OBJECT_TYPES[learningObject.type],
        repeatedTest: learningObject.repeatedTest,
        unreleased: learningObject.unreleased,
    };
}

function toNavigation(navigation: LearningPathNavigation): LearningPathNavigationDTO {
    return {
        predecessorLearningObject: navigation.predecessorLearningObject && toNavigationObject(navigation.predecessorLearningObject),
        currentLearningObject: navigation.currentLearningObject && toNavigationObject(navigation.currentLearningObject),
        successorLearningObject: navigation.successorLearningObject && toNavigationObject(navigation.successorLearningObject),
        progress: navigation.progress,
    };
}

// Learning path graphs always carry a value and its type on every node, and the endpoints and type on every edge. The node and edge
// DTOs are shared with graphs of other endpoints that leave them out, so the specification marks them optional.
function toCompetencyGraphNode(node: CompetencyGraphNode): CompetencyGraphNodeDTO {
    return {
        id: node.id,
        label: node.label,
        softDueDate: node.softDueDate === undefined ? undefined : new Date(node.softDueDate),
        value: requireField(node.value, 'node value'),
        valueType: NODE_VALUE_TYPES[requireField(node.valueType, 'node value type')],
    };
}

function toCompetencyGraphEdge(edge: CompetencyGraphEdge): CompetencyGraphEdgeDTO {
    return {
        id: edge.id,
        source: requireField(edge.source, 'edge source'),
        target: requireField(edge.target, 'edge target'),
        relationType: RELATION_TYPES[requireField(edge.relationType, 'edge relation type')],
    };
}

function toCompetencyGraph(graph: LearningPathCompetencyGraph): CompetencyGraphDTO {
    // The server omits empty lists. The graph renderer needs the nodes; edges stay optional as in the view model.
    return {
        nodes: (graph.nodes ?? []).map(toCompetencyGraphNode),
        edges: graph.edges?.map(toCompetencyGraphEdge),
    };
}
