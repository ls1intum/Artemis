import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { User, UserNameAndLoginDTO } from 'app/core/user/user.model';
import { CompetencyRelationType, CourseCompetency } from 'app/entities/competency.model';
import { Edge, Node, NodeDimension } from '@swimlane/ngx-graph';
import { faCheckCircle, faCircle, faFlag, faFlagCheckered, faPlayCircle, faSignsPost } from '@fortawesome/free-solid-svg-icons';

export class LearningPath implements BaseEntity {
    public id?: number;
    public progress?: number;
    public user?: User;
    public course?: Course;
    public competencies?: CourseCompetency[];

    constructor() {}
}

export class LearningPathInformationDTO {
    public id?: number;
    public user?: UserNameAndLoginDTO;
    public progress?: number;
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

export enum CompetencyGraphNodeValueType {
    MASTERY_PROGRESS = 'MASTERY_PROGRESS',
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

export class NgxLearningPathDTO {
    public nodes: NgxLearningPathNode[];
    public edges: NgxLearningPathEdge[];
}

export class NgxLearningPathNode implements Node {
    public id: string;
    public type?: NodeType;
    public linkedResource?: number;
    public linkedResourceParent?: number;
    public completed?: boolean;
    public label?: string;
    dimension?: NodeDimension;
}

export function getIcon(node: NgxLearningPathNode) {
    // return generic icon if no type present
    if (!node.type) {
        return faCircle;
    }

    // return different icons for completed learning objects
    if (node.type === NodeType.EXERCISE || node.type === NodeType.LECTURE_UNIT) {
        if (node.completed) {
            return faCheckCircle;
        } else {
            return faPlayCircle;
        }
    }

    const icons = {
        [NodeType.COMPETENCY_START]: faFlag,
        [NodeType.COMPETENCY_END]: faFlagCheckered,
        [NodeType.MATCH_START]: faSignsPost,
        [NodeType.MATCH_END]: faCircle,
    };
    return icons[node.type];
}

export class NgxLearningPathEdge implements Edge {
    public id?: string;
    public source: string;
    public target: string;
}

export enum NodeType {
    COMPETENCY_START = 'COMPETENCY_START',
    COMPETENCY_END = 'COMPETENCY_END',
    MATCH_START = 'MATCH_START',
    MATCH_END = 'MATCH_END',
    EXERCISE = 'EXERCISE',
    LECTURE_UNIT = 'LECTURE_UNIT',
}

export class CompetencyProgressForLearningPathDTO {
    public competencyId?: number;
    public masteryThreshold?: number;
    public progress?: number;
    public confidence?: number;
}
