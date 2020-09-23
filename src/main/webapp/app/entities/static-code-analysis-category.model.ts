import { BaseEntity } from 'app/shared/model/base-entity';

export class StaticCodeAnalysisCategory implements BaseEntity {
    id: number;
    name: string;
    description: string;
    state: StaticCodeAnalysisCategoryState;
    checks: { tool: string; check: string }[];
    penalty: number;
    maxPenalty: number;
}

export enum StaticCodeAnalysisCategoryState {
    INACTIVE = 'INACTIVE',
    VISIBLE = 'VISIBLE',
    GRADED = 'GRADED',
}
