import { Moment } from 'moment';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise/exercise.model';
import { Course } from 'app/entities/course/course.model';

export const enum ProgrammingLanguage {
    JAVA = 'JAVA',
    PYTHON = 'PYTHON',
    C = 'C',
}

export class ProgrammingExercise extends Exercise {
    public projectKey: string | null;
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
    public testCasesChanged: boolean;

    constructor(course?: Course) {
        super(ExerciseType.PROGRAMMING);
        this.course = course || null;
        this.templateParticipation = new TemplateProgrammingExerciseParticipation();
        this.solutionParticipation = new SolutionProgrammingExerciseParticipation();
    }
}
