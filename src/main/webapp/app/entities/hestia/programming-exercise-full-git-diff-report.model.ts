import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseFullGitDiffEntry } from 'app/entities/hestia/programming-exercise-full-git-diff-entry.model';

export class ProgrammingExerciseFullGitDiffReport {
    public programmingExercise: ProgrammingExercise;
    public templateRepositoryCommitHash: string;
    public solutionRepositoryCommitHash: string;
    public entries: ProgrammingExerciseFullGitDiffEntry[];

    constructor() {}
}
