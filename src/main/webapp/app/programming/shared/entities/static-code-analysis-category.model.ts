import { BaseEntity } from 'app/foundation/model/base-entity';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class StaticCodeAnalysisCategory implements BaseEntity {
    id!: number;
    name!: string;
    state!: StaticCodeAnalysisCategoryState;
    penalty!: number;
    maxPenalty!: number;
}

export enum StaticCodeAnalysisCategoryState {
    Inactive = 'INACTIVE',
    Feedback = 'FEEDBACK',
    Graded = 'GRADED',
}
