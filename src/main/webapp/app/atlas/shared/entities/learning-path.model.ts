import { BaseEntity } from 'app/foundation/model/base-entity';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { CompetencyRelationType, CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { GraphNodeDimension } from 'app/atlas/shared/dag-graph/dag-graph.model';
import { CompetencyName } from 'app/openapi/model/competency-name';
import { LearningPath as GeneratedLearningPath } from 'app/openapi/model/learning-path';
import { LearningPathAverageProgress } from 'app/openapi/model/learning-path-average-progress';
import { LearningPathInformation } from 'app/openapi/model/learning-path-information';

export interface LearningPath extends BaseEntity {
    id?: number;
    progress?: number;
    user?: User;
    course?: Course;
    competencies?: CourseCompetency[];
}

// The wire types of the learning path endpoints are generated from the server. The names below are kept for their callers.
export type LearningPathInformationDTO = LearningPathInformation;
export type LearningPathCompetencyDTO = CompetencyName;
export type LearningPathDTO = GeneratedLearningPath;
export type LearningPathAverageProgressDTO = LearningPathAverageProgress;

export enum LearningObjectType {
    LECTURE = 'LECTURE',
    EXERCISE = 'EXERCISE',
}

export interface LearningPathNavigationObjectDTO {
    id: number;
    completed: boolean;
    name?: string;
    competencyId: number;
    type: LearningObjectType;
    repeatedTest: boolean;
    unreleased: boolean;
}

export interface LearningPathNavigationDTO {
    predecessorLearningObject?: LearningPathNavigationObjectDTO;
    currentLearningObject?: LearningPathNavigationObjectDTO;
    successorLearningObject?: LearningPathNavigationObjectDTO;
    progress: number;
}

export interface LearningPathNavigationOverviewDTO {
    learningObjects: LearningPathNavigationObjectDTO[];
}

export enum CompetencyGraphNodeValueType {
    MASTERY_PROGRESS = 'MASTERY_PROGRESS',
    AVERAGE_MASTERY_PROGRESS = 'AVERAGE_MASTERY_PROGRESS',
}

export interface CompetencyGraphNodeDTO {
    id: string;
    label?: string;
    softDueDate?: Date;
    value: number;
    valueType: CompetencyGraphNodeValueType;
    dimension?: GraphNodeDimension;
}

export interface CompetencyGraphEdgeDTO {
    id: string;
    source: string;
    target: string;
    relationType: CompetencyRelationType;
}

export interface CompetencyGraphDTO {
    nodes: CompetencyGraphNodeDTO[];
    edges?: CompetencyGraphEdgeDTO[];
}
