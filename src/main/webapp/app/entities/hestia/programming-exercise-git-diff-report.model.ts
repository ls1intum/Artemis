import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';

export class ProgrammingExerciseGitDiffReport implements BaseEntity {
    public id?: number;

    public programmingExercise: ProgrammingExercise;
    public leftCommitHash?: string;
    public rightCommitHash?: string;
    public entries?: ProgrammingExerciseGitDiffEntry[];
    public participationIdForLeftCommit?: number;
    public participationIdForRightCommit?: number;
    public templateRepositoryCommitHash?: string;
    public solutionRepositoryCommitHash?: string;

    constructor() {}
}
