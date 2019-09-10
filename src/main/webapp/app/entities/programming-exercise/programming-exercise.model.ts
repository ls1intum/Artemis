import { Course } from '../course';
import { Moment } from 'moment';
import { Exercise, ExerciseType } from '../exercise';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingExerciseOptions } from 'app/entities/programming-exercise/programming-exercise-options.model';

export const enum ProgrammingLanguage {
    JAVA = 'JAVA',
    PYTHON = 'PYTHON',
    C = 'C',
}

export class ProgrammingExercise extends Exercise {
    public templateParticipation: TemplateProgrammingExerciseParticipation;
    public solutionParticipation: SolutionProgrammingExerciseParticipation;
    public testRepositoryUrl: string;
    public publishBuildPlanUrl = false; // default value
    public allowOnlineEditor = false; // default value
    public programmingLanguage = ProgrammingLanguage.JAVA; // default value
    public packageName: string;
    public problemStatement: string;
    public sequentialTestRuns: boolean | null;

    public buildAndTestStudentSubmissionsAfterDueDate: Moment | null;

    constructor(course?: Course) {
        super(ExerciseType.PROGRAMMING);
        this.course = course || null;
        this.templateParticipation = new TemplateProgrammingExerciseParticipation();
        this.solutionParticipation = new SolutionProgrammingExerciseParticipation();
    }
}
