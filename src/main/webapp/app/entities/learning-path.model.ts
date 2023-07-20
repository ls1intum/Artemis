import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { Competency } from 'app/entities/competency.model';
import { ClusterNode, Edge, Node } from '@swimlane/ngx-graph';

export class LearningPath implements BaseEntity {
    public id?: number;
    public progress?: number;
    public user?: User;
    public course?: Course;
    public competencies?: Competency[];

    constructor() {}
}

export class NgxLearningPathDTO {
    public nodes: NgxLearningPathNode[];
    public edges: NgxLearningPathEdge[];
    public clusters: NgxLearningPathCluster[];
}

export class NgxLearningPathNode implements Node {
    public id: string;
    public type?: NodeType;
    public linkedResource?: number;
    public completed?: boolean;
    public label?: string;
}

export class NgxLearningPathEdge implements Edge {
    public id?: string;
    public source: string;
    public target: string;
}

export class NgxLearningPathCluster implements ClusterNode {
    public id: string;
    public label?: string;
    public childNodeIds?: string[];
}
export enum NodeType {
    COMPETENCY_START = 'COMPETENCY_START',
    COMPETENCY_END = 'COMPETENCY_END',
    MATCH_START = 'MATCH_START',
    MATCH_END = 'MATCH_END',
    EXERCISE = 'EXERCISE',
    LECTURE_UNIT = 'LECTURE_UNIT',
}

export class LearningPathRecommendation {
    public learningObjectId: number;
    public lectureId?: number;
    public type: RecommendationType;
}

export enum RecommendationType {
    EMPTY = 'EMPTY',
    LECTURE_UNIT = 'LECTURE_UNIT',
    EXERCISE = 'EXERCISE',
}
