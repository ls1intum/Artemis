import { Moment } from 'moment';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';

export enum ProgrammingLanguage {
    JAVA = 'JAVA',
    PYTHON = 'PYTHON',
    C = 'C',
    HASKELL = 'HASKELL',
}

export class ProgrammingExercise extends Exercise {
    public projectKey: string | null;
    public templateParticipation: TemplateProgrammingExerciseParticipation;
    public solutionParticipation: SolutionProgrammingExerciseParticipation;
    public testRepositoryUrl: string;
    public publishBuildPlanUrl = false; // default value
    public allowOnlineEditor = false; // default value
    public allowOfflineIde = true; // default value
    public programmingLanguage = ProgrammingLanguage.JAVA; // default value
    public packageName: string;
    public problemStatement: string;
    public sequentialTestRuns: boolean | null;

    public buildAndTestStudentSubmissionsAfterDueDate: Moment | null;
    public testCasesChanged: boolean;

    // helper attributes
    /**
     * This attribute is used to generate a programming exercise with no connection to
     * the VCS and CI
     * This functionality is only for testing purposes
     */
    public noVersionControlAndContinuousIntegrationAvailable = false; // default value
    public isLocalSimulation?: boolean;

    constructor(course?: Course) {
        super(ExerciseType.PROGRAMMING);
        this.course = course || null;
        this.templateParticipation = new TemplateProgrammingExerciseParticipation();
        this.solutionParticipation = new SolutionProgrammingExerciseParticipation();
    }
}
