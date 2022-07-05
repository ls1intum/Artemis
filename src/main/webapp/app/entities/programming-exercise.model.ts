import dayjs from 'dayjs/esm';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { BuildLogStatisticsDTO } from 'app/exercises/programming/manage/build-log-statistics-dto';

export enum ProgrammingLanguage {
    JAVA = 'JAVA',
    PYTHON = 'PYTHON',
    C = 'C',
    HASKELL = 'HASKELL',
    KOTLIN = 'KOTLIN',
    VHDL = 'VHDL',
    ASSEMBLER = 'ASSEMBLER',
    SWIFT = 'SWIFT',
    OCAML = 'OCAML',
    EMPTY = 'EMPTY',
}

export enum ProjectType {
    MAVEN_MAVEN = 'MAVEN_MAVEN',
    PLAIN_MAVEN = 'PLAIN_MAVEN',
    PLAIN_GRADLE = 'PLAIN_GRADLE',
    GRADLE_GRADLE = 'GRADLE_GRADLE',
    PLAIN = 'PLAIN',
    XCODE = 'XCODE',
    FACT = 'FACT',
    GCC = 'GCC',
}

export class ProgrammingExercise extends Exercise {
    public projectKey?: string;
    public templateParticipation?: TemplateProgrammingExerciseParticipation;
    public solutionParticipation?: SolutionProgrammingExerciseParticipation;
    public testRepositoryUrl?: string;
    public publishBuildPlanUrl?: boolean;
    public allowOnlineEditor?: boolean;
    public staticCodeAnalysisEnabled?: boolean;
    public maxStaticCodeAnalysisPenalty?: number;
    public allowOfflineIde?: boolean;
    public programmingLanguage?: ProgrammingLanguage;
    public packageName?: string;
    public problemStatement?: string;
    public sequentialTestRuns?: boolean;
    public showTestNamesToStudents?: boolean;
    public checkoutSolutionRepository?: boolean;
    public auxiliaryRepositories?: AuxiliaryRepository[];
    public submissionPolicy?: SubmissionPolicy;
    public exerciseHints?: ExerciseHint[];
    public gitDiffReport?: ProgrammingExerciseGitDiffReport;
    public buildLogStatistics?: BuildLogStatisticsDTO;

    public buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
    public testCasesChanged?: boolean;

    public projectType?: ProjectType;

    public testwiseCoverageEnabled?: boolean;

    // helper attributes

    // this attribute is used to display the covered lines ratio
    public coveredLinesRatio?: number;

    /**
     * This attribute is used to generate a programming exercise with no connection to the VCS and CI.
     * This functionality is only for testing purposes.
     */
    public noVersionControlAndContinuousIntegrationAvailable?: boolean;
    public isLocalSimulation?: boolean;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.PROGRAMMING);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        this.templateParticipation = new TemplateProgrammingExerciseParticipation();
        this.solutionParticipation = new SolutionProgrammingExerciseParticipation();
        this.publishBuildPlanUrl = false; // default value
        this.allowOnlineEditor = false; // default value
        this.staticCodeAnalysisEnabled = false; // default value
        this.allowOfflineIde = true; // default value
        this.programmingLanguage = ProgrammingLanguage.JAVA; // default value
        this.noVersionControlAndContinuousIntegrationAvailable = false; // default value
        this.checkoutSolutionRepository = false; // default value
        this.projectType = ProjectType.PLAIN_MAVEN; // default value
        this.showTestNamesToStudents = false; // default value
        this.testwiseCoverageEnabled = false; // default value
    }
}
