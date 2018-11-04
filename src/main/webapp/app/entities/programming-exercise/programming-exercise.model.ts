import { Course } from '../course';
import { Exercise, ExerciseType } from '../exercise';

export const enum ProgrammingLanguage {
    JAVA = 'JAVA',
    PYTHON = 'PYTHON'
}

export class ProgrammingExercise extends Exercise {
    public baseRepositoryUrl: string;
    public solutionRepositoryUrl: string;
    public testRepositoryUrl: string;
    public baseBuildPlanId: string;
    public solutionBuildPlanId: string;
    public publishBuildPlanUrl = false; // default value
    public allowOnlineEditor = false; // default value
    public programmingLanguage = ProgrammingLanguage.JAVA; // default value
    public packageName: string;

    constructor(course?: Course) {
        super(ExerciseType.PROGRAMMING);
        this.course = course;
    }
}
