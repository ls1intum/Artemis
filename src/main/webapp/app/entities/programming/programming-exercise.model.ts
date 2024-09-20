import { AssessmentType } from 'app/entities/assessment-type.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType, resetForImport } from 'app/entities/exercise.model';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { BuildLogStatisticsDTO } from 'app/entities/programming/build-log-statistics-dto';
import { AuxiliaryRepository } from 'app/entities/programming/programming-exercise-auxiliary-repository-model';
import { ProgrammingExerciseBuildConfig } from 'app/entities/programming/programming-exercise-build.config';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import dayjs from 'dayjs/esm';

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
    RUST = 'RUST',
    JAVASCRIPT = 'JAVASCRIPT',
    C_PLUS_PLUS = 'C_PLUS_PLUS',
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
    public exerciseHints?: ExerciseHint[];
    public gitDiffReport?: ProgrammingExerciseGitDiffReport;
    public buildLogStatistics?: BuildLogStatisticsDTO;
    public buildConfig?: ProgrammingExerciseBuildConfig;
    public releaseTestsWithExampleSolution?: boolean;

    public buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs;
    public testCasesChanged?: boolean;

    public projectType?: ProjectType;

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
    exercise.assessmentType = AssessmentType.AUTOMATIC;
}

/**
 * Copy the build configuration from the given exerciseJson to this build configuration. This is to ensure compatibility with old exported programming exercises.
 */
export function copyBuildConfigFromExerciseJson(exerciseJson: ProgrammingExerciseBuildConfig): ProgrammingExerciseBuildConfig {
    const buildConfig = new ProgrammingExerciseBuildConfig();
    buildConfig.sequentialTestRuns = exerciseJson.sequentialTestRuns ?? false;
    buildConfig.checkoutPath = exerciseJson.checkoutPath ?? '';
    buildConfig.buildPlanConfiguration = exerciseJson.buildPlanConfiguration ?? '';
    buildConfig.checkoutSolutionRepository = exerciseJson.checkoutSolutionRepository ?? false;
    buildConfig.timeoutSeconds = exerciseJson.timeoutSeconds ?? 0;
    buildConfig.windfile = exerciseJson.windfile ?? undefined;
    buildConfig.buildScript = exerciseJson.buildScript ?? '';
    buildConfig.testwiseCoverageEnabled = exerciseJson.testwiseCoverageEnabled ?? false;
    buildConfig.dockerFlags = exerciseJson.dockerFlags ?? '';

    return buildConfig;
}
