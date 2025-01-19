import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { User, UserNameAndLoginDTO } from 'app/core/user/user.model';
import { CompetencyRelationType, CourseCompetency } from 'app/entities/competency.model';
import { NodeDimension } from '@swimlane/ngx-graph';

export class LearningPath implements BaseEntity {
    public id?: number;
    public progress?: number;
    public user?: User;
    public course?: Course;
    public competencies?: CourseCompetency[];
}

export class LearningPathInformationDTO {
    public id: number;
    public user: UserNameAndLoginDTO;
    public progress: number;
}

export enum LearningObjectType {
    LECTURE = 'LECTURE',
    EXERCISE = 'EXERCISE',
}

export interface LearningPathCompetencyDTO {
    id: number;
    title: string;
    masteryProgress: number;
}

export interface LearningPathDTO {
    id: number;
    progress: number;
    startedByStudent: boolean;
}

export interface LearningPathNavigationObjectDTO {
    id: number;
    completed: boolean;
    name?: string;
    competencyId: number;
    type: LearningObjectType;
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

export interface LearningPathsConfigurationDTO {
    includeAllGradedExercises: boolean;
}

export enum CompetencyGraphNodeValueType {
    MASTERY_PROGRESS = 'MASTERY_PROGRESS',
    AVERAGE_MASTERY_PROGRESS = 'AVERAGE_MASTERY_PROGRESS',
}

export interface CompetencyGraphNodeDTO {
    id: string;
    label: string;
    softDueDate: Date;
    value: number;
    valueType: CompetencyGraphNodeValueType;
    dimension?: NodeDimension;
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

export enum NodeType {
    COMPETENCY_START = 'COMPETENCY_START',
    COMPETENCY_END = 'COMPETENCY_END',
    MATCH_START = 'MATCH_START',
    MATCH_END = 'MATCH_END',
    EXERCISE = 'EXERCISE',
    LECTURE_UNIT = 'LECTURE_UNIT',
}
