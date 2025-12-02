import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import dayjs from 'dayjs/esm';
/*import {
    DockerContainerConfig
} from 'app/programming/shared/entities/docker-container.config'; TODO*/

export enum ProgrammingLanguage {
    EMPTY = 'EMPTY',
    ASSEMBLER = 'ASSEMBLER',
    BASH = 'BASH',
    C = 'C',
    C_PLUS_PLUS = 'C_PLUS_PLUS',
    C_SHARP = 'C_SHARP',
    DART = 'DART',
    GO = 'GO',
    HASKELL = 'HASKELL',
    JAVA = 'JAVA',
    JAVASCRIPT = 'JAVASCRIPT',
    KOTLIN = 'KOTLIN',
    MATLAB = 'MATLAB',
    OCAML = 'OCAML',
    PYTHON = 'PYTHON',
    R = 'R',
    RUBY = 'RUBY',
    RUST = 'RUST',
    SWIFT = 'SWIFT',
    TYPESCRIPT = 'TYPESCRIPT',
    VHDL = 'VHDL',
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
    public allowOnlineIde?: boolean;
    public programmingLanguage?: ProgrammingLanguage;
    public packageName?: string;
    public showTestNamesToStudents?: boolean;
    public auxiliaryRepositories?: AuxiliaryRepository[];
    public submissionPolicy?: SubmissionPolicy;
    public buildConfig?: ProgrammingExerciseBuildConfig;
    public releaseTestsWithExampleSolution?: boolean;

    public buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
    public testCasesChanged?: boolean;

    public projectType?: ProjectType;

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
        this.allowOnlineIde = false; // default value
        this.staticCodeAnalysisEnabled = false; // default value
        this.allowOfflineIde = true; // default value
        this.programmingLanguage = ProgrammingLanguage.JAVA; // default value
        this.noVersionControlAndContinuousIntegrationAvailable = false; // default value
        this.projectType = ProjectType.PLAIN_GRADLE; // default value
        this.showTestNamesToStudents = false; // default value
        this.buildConfig = new ProgrammingExerciseBuildConfig();
    }
}

export function resetProgrammingForImport(exercise: ProgrammingExercise) {
    resetForImport(exercise);

    // without dates set, they have to be reset as well
    exercise.releaseTestsWithExampleSolution = false;
    exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
}

/**
 * Copy the build configuration from the given exerciseJson to this build configuration. This is to ensure compatibility with old exported programming exercises.
 */
export function copyBuildConfigFromExerciseJson(exerciseJson: ProgrammingExerciseBuildConfig): ProgrammingExerciseBuildConfig {
    const buildConfig = new ProgrammingExerciseBuildConfig();
    buildConfig.sequentialTestRuns = exerciseJson.sequentialTestRuns ?? false;
    buildConfig.assignmentCheckoutPath = exerciseJson.assignmentCheckoutPath ?? '';
    buildConfig.solutionCheckoutPath = exerciseJson.solutionCheckoutPath ?? '';
    buildConfig.testCheckoutPath = exerciseJson.testCheckoutPath ?? '';
    buildConfig.checkoutSolutionRepository = exerciseJson.checkoutSolutionRepository ?? false;
    buildConfig.timeoutSeconds = exerciseJson.timeoutSeconds ?? 0;

    // TODO: Ensure the old compatability is not broken!
    /*const containerConfig: DockerContainerConfig = {
        buildPlanConfiguration: exerciseJson.buildPlanConfiguration ?? '',
        buildScript: exerciseJson.buildScript ?? '',
        dockerFlags: exerciseJson.dockerFlags ?? '',
        name: 'Container 1',
    }; TODO I don't really understand. Fix it later. */
    buildConfig.containerConfigs = exerciseJson.containerConfigs; //  [containerConfig];

    return buildConfig;
}
