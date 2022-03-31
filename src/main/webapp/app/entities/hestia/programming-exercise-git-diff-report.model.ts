import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseGitDiffEntry } from 'app/entities/hestia/programming-exercise-git-diff-entry.model';

export class ProgrammingExerciseGitDiffReport implements BaseEntity {
    public id?: number;

    public programmingExercise: ProgrammingExercise;
    public templateRepositoryCommitHash: string;
    public solutionRepositoryCommitHash: string;
    public entries: ProgrammingExerciseGitDiffEntry[];

    constructor() {}
}
