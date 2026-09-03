import { BaseEntity } from 'app/foundation/model/base-entity';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class StaticCodeAnalysisCategory implements BaseEntity {
    id!: number;
    name!: string;
    state!: StaticCodeAnalysisCategoryState;
    penalty!: number;
    maxPenalty!: number;
}

/**
 * How a category's issues affect a student's score.
 *
 * `Blocking` is the strongest: a single issue in such a category zeroes the exercise it was configured on. On a
 * milestone exercise, which carries the points of its whole user story group, that means one violation anywhere in the
 * shared codebase costs the entire group. It therefore has no per-issue penalty of its own.
 */
export enum StaticCodeAnalysisCategoryState {
    Inactive = 'INACTIVE',
    Feedback = 'FEEDBACK',
    Graded = 'GRADED',
    Blocking = 'BLOCKING',
}
