import { BaseEntity } from 'app/foundation/model/base-entity';

export class StaticCodeAnalysisCategory implements BaseEntity {
    id: number;
    name: string;
    state: StaticCodeAnalysisCategoryState;
    penalty: number;
    maxPenalty: number;
}

export enum StaticCodeAnalysisCategoryState {
    Inactive = 'INACTIVE',
    Feedback = 'FEEDBACK',
    Graded = 'GRADED',
}
