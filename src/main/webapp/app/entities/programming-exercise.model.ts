import dayjs from 'dayjs/esm';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { Exercise, ExerciseType, resetDates } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { BuildLogStatisticsDTO } from 'app/entities/build-log-statistics-dto';

export class BuildAction {
    name: string;
    runAlways: boolean;
    workdir: string;
    results?: AeolusResult[];
    platform?: string;
    parameters: Map<string, string | boolean | number> = new Map<string, string | boolean | number>();
}

export class AeolusResult {
    name: string;
    path: string;
    ignore: string;
    type?: string;
    before?: boolean;
}

export class ScriptAction extends BuildAction {
    script: string;
}

export class PlatformAction extends BuildAction {
    type: string;
    kind: string;
}

export class WindMetadata {
    author: string | any;
    description: string;
    id: string;
    name: string;
    docker: DockerConfiguration;
}

export class DockerConfiguration {
    image: string;
    tag?: string;
    volumes: Map<string, string>;
    parameters: Map<string, string>;
}

export class WindFile {
    api: string;
    metadata: WindMetadata;
    actions: BuildAction[];
}

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
    MAVEN_BLACKBOX = 'MAVEN_BLACKBOX',
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
    public testRepositoryUri?: string;
    public customizeBuildPlanWithAeolus?: boolean;
    public allowOnlineEditor?: boolean;
    public staticCodeAnalysisEnabled?: boolean;
    /**
     * Max penalty from SCA in percentage
     */
    public maxStaticCodeAnalysisPenalty?: number;
    public allowOfflineIde?: boolean;
    public programmingLanguage?: ProgrammingLanguage;
    public packageName?: string;
    public sequentialTestRuns?: boolean;
    public showTestNamesToStudents?: boolean;
    public checkoutSolutionRepository?: boolean;
    public auxiliaryRepositories?: AuxiliaryRepository[];
    public submissionPolicy?: SubmissionPolicy;
    public exerciseHints?: ExerciseHint[];
    public gitDiffReport?: ProgrammingExerciseGitDiffReport;
    public buildLogStatistics?: BuildLogStatisticsDTO;
    public releaseTestsWithExampleSolution?: boolean;

    public buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
    public testCasesChanged?: boolean;

    public projectType?: ProjectType;
    public windFile?: WindFile;
    public buildScript?: string;
    public buildPlanConfiguration?: string;

    public testwiseCoverageEnabled?: boolean;

    // helper attributes

    // this attribute is used to display the covered lines ratio
    public coveredLinesRatio?: number;

    /**
     * This attribute is used to generate a programming exercise with no connection to the VCS and CI.
     * This functionality is only for testing purposes.
     */
    public noVersionControlAndContinuousIntegrationAvailable?: boolean;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.PROGRAMMING);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        this.templateParticipation = new TemplateProgrammingExerciseParticipation();
        this.solutionParticipation = new SolutionProgrammingExerciseParticipation();
        this.allowOnlineEditor = false; // default value
        this.staticCodeAnalysisEnabled = false; // default value
        this.allowOfflineIde = true; // default value
        this.programmingLanguage = ProgrammingLanguage.JAVA; // default value
        this.noVersionControlAndContinuousIntegrationAvailable = false; // default value
        this.checkoutSolutionRepository = false; // default value
        this.projectType = ProjectType.PLAIN_GRADLE; // default value
        this.showTestNamesToStudents = false; // default value
        this.testwiseCoverageEnabled = false; // default value
    }
}

export function resetProgrammingDates(exercise: ProgrammingExercise) {
    resetDates(exercise);

    // without dates set, they have to be reset as well
    exercise.releaseTestsWithExampleSolution = false;
    exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
}
