import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { BaseEntity } from 'app/shared/model/base-entity';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';

export class ProgrammingExerciseFullGitDiffReport implements BaseEntity {
    public id?: number;

    public programmingExercise: ProgrammingExercise;
    public templateRepositoryCommitHash: string;
    public solutionRepositoryCommitHash: string;
    public entries: ProgrammingExerciseFullGitDiffEntry[];

    constructor() {}
}
