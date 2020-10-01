import { BaseEntity } from 'app/shared/model/base-entity';

export class StaticCodeAnalysisCategory implements BaseEntity {
    id: number;
    name: string;
    state: StaticCodeAnalysisCategoryState;
    penalty: number;
    maxPenalty: number;
}

export enum StaticCodeAnalysisCategoryState {
    Inactive = 'inactive',
    Feedback = 'feedback',
    Graded = 'graded',
}
