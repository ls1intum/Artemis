import dayjs from 'dayjs/esm';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { DifficultyLevel, Exercise, ExerciseMode, ExerciseType, IncludedInOverallScore, PlagiarismDetectionConfig } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';

import { convertDateFromServer } from 'app/shared/util/date.utils';
import { ExerciseSnapshotDTO, ParticipationSnapshotDTO, TeamAssignmentConfigSnapshot } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';
import { toExerciseCategories, toTeamAssignmentConfig } from 'app/exercise/synchronization/exercise-metadata-snapshot-shared.mapper';
import {
    toAuxiliaryRepositories,
    toBuildConfig,
    toSolutionParticipation,
    toSubmissionPolicy,
    toTemplateParticipation,
} from 'app/exercise/synchronization/exercise-metadata-snapshot-programming.mapper';
import { toQuizQuestions } from 'app/exercise/synchronization/exercise-metadata-snapshot-quiz.mapper';

export interface ExerciseMetadataFieldHandler<T extends Exercise> {
    key: string;
    labelKey: string;
    getCurrentValue: (exercise: T) => unknown;
    getBaselineValue: (exercise: T) => unknown;
    getIncomingValue: (snapshot: ExerciseSnapshotDTO) => unknown;
    applyValue: (exercise: T, value: unknown) => void;
}

const baseHandler = (
    key: string,
    labelKey: string,
    getter: (exercise: Exercise) => unknown,
    setter: (exercise: Exercise, value: unknown) => void,
): ExerciseMetadataFieldHandler<Exercise> => {
    return {
        key,
        labelKey,
        getCurrentValue: getter,
        getBaselineValue: getter,
        getIncomingValue: (snapshot) => snapshot[key as keyof ExerciseSnapshotDTO],
        applyValue: setter,
    };
};

const dateHandler = (
    key: string,
    labelKey: string,
    getter: (exercise: Exercise) => dayjs.Dayjs | undefined,
    setter: (exercise: Exercise, value: dayjs.Dayjs | undefined) => void,
): ExerciseMetadataFieldHandler<Exercise> => {
    return {
        key,
        labelKey,
        getCurrentValue: getter,
        getBaselineValue: getter,
        getIncomingValue: (snapshot) => convertDateFromServer(snapshot[key as keyof ExerciseSnapshotDTO] as any),
        applyValue: (exercise, value) => setter(exercise, value as dayjs.Dayjs | undefined),
    };
};

export const createBaseHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        baseHandler(
            'title',
            'artemisApp.exercise.metadataSync.fields.title',
            (exercise) => exercise.title,
            (exercise, value) => (exercise.title = value as string | undefined),
        ),
        baseHandler(
            'shortName',
            'artemisApp.exercise.metadataSync.fields.shortName',
            (exercise) => exercise.shortName,
            (exercise, value) => (exercise.shortName = value as string | undefined),
        ),
        baseHandler(
            'maxPoints',
            'artemisApp.exercise.metadataSync.fields.maxPoints',
            (exercise) => exercise.maxPoints,
            (exercise, value) => (exercise.maxPoints = value as number | undefined),
        ),
        baseHandler(
            'bonusPoints',
            'artemisApp.exercise.metadataSync.fields.bonusPoints',
            (exercise) => exercise.bonusPoints,
            (exercise, value) => (exercise.bonusPoints = value as number | undefined),
        ),
        baseHandler(
            'assessmentType',
            'artemisApp.exercise.metadataSync.fields.assessmentType',
            (exercise) => exercise.assessmentType,
            (exercise, value) => (exercise.assessmentType = value as AssessmentType | undefined),
        ),
        dateHandler(
            'releaseDate',
            'artemisApp.exercise.metadataSync.fields.releaseDate',
            (exercise) => exercise.releaseDate,
            (exercise, value) => (exercise.releaseDate = value as dayjs.Dayjs | undefined),
        ),
        dateHandler(
            'startDate',
            'artemisApp.exercise.metadataSync.fields.startDate',
            (exercise) => exercise.startDate,
            (exercise, value) => (exercise.startDate = value as dayjs.Dayjs | undefined),
        ),
        dateHandler(
            'dueDate',
            'artemisApp.exercise.metadataSync.fields.dueDate',
            (exercise) => exercise.dueDate,
            (exercise, value) => (exercise.dueDate = value as dayjs.Dayjs | undefined),
        ),
        dateHandler(
            'assessmentDueDate',
            'artemisApp.exercise.metadataSync.fields.assessmentDueDate',
            (exercise) => exercise.assessmentDueDate,
            (exercise, value) => (exercise.assessmentDueDate = value as dayjs.Dayjs | undefined),
        ),
        dateHandler(
            'exampleSolutionPublicationDate',
            'artemisApp.exercise.metadataSync.fields.exampleSolutionPublicationDate',
            (exercise) => exercise.exampleSolutionPublicationDate,
            (exercise, value) => (exercise.exampleSolutionPublicationDate = value as dayjs.Dayjs | undefined),
        ),
        baseHandler(
            'difficulty',
            'artemisApp.exercise.metadataSync.fields.difficulty',
            (exercise) => exercise.difficulty,
            (exercise, value) => (exercise.difficulty = value as DifficultyLevel | undefined),
        ),
        baseHandler(
            'mode',
            'artemisApp.exercise.metadataSync.fields.mode',
            (exercise) => exercise.mode,
            (exercise, value) => (exercise.mode = value as ExerciseMode | undefined),
        ),
        baseHandler(
            'allowComplaintsForAutomaticAssessments',
            'artemisApp.exercise.metadataSync.fields.allowComplaintsForAutomaticAssessments',
            (exercise) => exercise.allowComplaintsForAutomaticAssessments,
            (exercise, value) => (exercise.allowComplaintsForAutomaticAssessments = value as boolean | undefined),
        ),
        baseHandler(
            'allowFeedbackRequests',
            'artemisApp.exercise.metadataSync.fields.allowFeedbackRequests',
            (exercise) => exercise.allowFeedbackRequests,
            (exercise, value) => (exercise.allowFeedbackRequests = value as boolean | undefined),
        ),
        baseHandler(
            'includedInOverallScore',
            'artemisApp.exercise.metadataSync.fields.includedInOverallScore',
            (exercise) => exercise.includedInOverallScore,
            (exercise, value) => (exercise.includedInOverallScore = value as IncludedInOverallScore | undefined),
        ),
        baseHandler(
            'problemStatement',
            'artemisApp.exercise.metadataSync.fields.problemStatement',
            (exercise) => exercise.problemStatement,
            (exercise, value) => (exercise.problemStatement = value as string | undefined),
        ),
        baseHandler(
            'gradingInstructions',
            'artemisApp.exercise.metadataSync.fields.gradingInstructions',
            (exercise) => exercise.gradingInstructions,
            (exercise, value) => (exercise.gradingInstructions = value as string | undefined),
        ),
        baseHandler(
            'categories',
            'artemisApp.exercise.metadataSync.fields.categories',
            (exercise) => exercise.categories,
            (exercise, value) => (exercise.categories = toExerciseCategories(value as string[] | undefined)),
        ),
        baseHandler(
            'teamAssignmentConfig',
            'artemisApp.exercise.metadataSync.fields.teamAssignmentConfig',
            (exercise) => exercise.teamAssignmentConfig,
            (exercise, value) => (exercise.teamAssignmentConfig = toTeamAssignmentConfig(value as TeamAssignmentConfigSnapshot | undefined)),
        ),
        baseHandler(
            'presentationScoreEnabled',
            'artemisApp.exercise.metadataSync.fields.presentationScoreEnabled',
            (exercise) => exercise.presentationScoreEnabled,
            (exercise, value) => (exercise.presentationScoreEnabled = value as boolean | undefined),
        ),
        baseHandler(
            'secondCorrectionEnabled',
            'artemisApp.exercise.metadataSync.fields.secondCorrectionEnabled',
            (exercise) => exercise.secondCorrectionEnabled,
            (exercise, value) => (exercise.secondCorrectionEnabled = (value as boolean | undefined) ?? false),
        ),
        baseHandler(
            'feedbackSuggestionModule',
            'artemisApp.exercise.metadataSync.fields.feedbackSuggestionModule',
            (exercise) => exercise.feedbackSuggestionModule,
            (exercise, value) => (exercise.feedbackSuggestionModule = value as string | undefined),
        ),
        baseHandler(
            'gradingCriteria',
            'artemisApp.exercise.metadataSync.fields.gradingCriteria',
            (exercise) => exercise.gradingCriteria,
            (exercise, value) => (exercise.gradingCriteria = value as GradingCriterion[] | undefined),
        ),
        baseHandler(
            'plagiarismDetectionConfig',
            'artemisApp.exercise.metadataSync.fields.plagiarismDetectionConfig',
            (exercise) => exercise.plagiarismDetectionConfig,
            (exercise, value) => (exercise.plagiarismDetectionConfig = value as PlagiarismDetectionConfig | undefined),
        ),
    ];
};

const createTextHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'textData.exampleSolution',
            labelKey: 'artemisApp.exercise.metadataSync.fields.textExampleSolution',
            getCurrentValue: (exercise) => (exercise as TextExercise).exampleSolution,
            getBaselineValue: (exercise) => (exercise as TextExercise).exampleSolution,
            getIncomingValue: (snapshot) => snapshot.textData?.exampleSolution,
            applyValue: (exercise, value) => ((exercise as TextExercise).exampleSolution = value as string | undefined),
        },
    ];
};

const createModelingHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'modelingData.diagramType',
            labelKey: 'artemisApp.exercise.metadataSync.fields.modelingDiagramType',
            getCurrentValue: (exercise) => (exercise as ModelingExercise).diagramType,
            getBaselineValue: (exercise) => (exercise as ModelingExercise).diagramType,
            getIncomingValue: (snapshot) => snapshot.modelingData?.diagramType,
            applyValue: (exercise, value) => ((exercise as ModelingExercise).diagramType = value as any),
        },
        {
            key: 'modelingData.exampleSolutionModel',
            labelKey: 'artemisApp.exercise.metadataSync.fields.modelingExampleSolutionModel',
            getCurrentValue: (exercise) => (exercise as ModelingExercise).exampleSolutionModel,
            getBaselineValue: (exercise) => (exercise as ModelingExercise).exampleSolutionModel,
            getIncomingValue: (snapshot) => snapshot.modelingData?.exampleSolutionModel,
            applyValue: (exercise, value) => ((exercise as ModelingExercise).exampleSolutionModel = value as string | undefined),
        },
        {
            key: 'modelingData.exampleSolutionExplanation',
            labelKey: 'artemisApp.exercise.metadataSync.fields.modelingExampleSolutionExplanation',
            getCurrentValue: (exercise) => (exercise as ModelingExercise).exampleSolutionExplanation,
            getBaselineValue: (exercise) => (exercise as ModelingExercise).exampleSolutionExplanation,
            getIncomingValue: (snapshot) => snapshot.modelingData?.exampleSolutionExplanation,
            applyValue: (exercise, value) => ((exercise as ModelingExercise).exampleSolutionExplanation = value as string | undefined),
        },
    ];
};

const createFileUploadHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'fileUploadData.exampleSolution',
            labelKey: 'artemisApp.exercise.metadataSync.fields.fileUploadExampleSolution',
            getCurrentValue: (exercise) => (exercise as FileUploadExercise).exampleSolution,
            getBaselineValue: (exercise) => (exercise as FileUploadExercise).exampleSolution,
            getIncomingValue: (snapshot) => snapshot.fileUploadData?.exampleSolution,
            applyValue: (exercise, value) => ((exercise as FileUploadExercise).exampleSolution = value as string | undefined),
        },
        {
            key: 'fileUploadData.filePattern',
            labelKey: 'artemisApp.exercise.metadataSync.fields.fileUploadFilePattern',
            getCurrentValue: (exercise) => (exercise as FileUploadExercise).filePattern,
            getBaselineValue: (exercise) => (exercise as FileUploadExercise).filePattern,
            getIncomingValue: (snapshot) => snapshot.fileUploadData?.filePattern,
            applyValue: (exercise, value) => ((exercise as FileUploadExercise).filePattern = value as string | undefined),
        },
    ];
};

const createQuizHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'quizData.randomizeQuestionOrder',
            labelKey: 'artemisApp.exercise.metadataSync.fields.quizRandomizeQuestionOrder',
            getCurrentValue: (exercise) => (exercise as QuizExercise).randomizeQuestionOrder,
            getBaselineValue: (exercise) => (exercise as QuizExercise).randomizeQuestionOrder,
            getIncomingValue: (snapshot) => snapshot.quizData?.randomizeQuestionOrder,
            applyValue: (exercise, value) => ((exercise as QuizExercise).randomizeQuestionOrder = value as boolean | undefined),
        },
        {
            key: 'quizData.allowedNumberOfAttempts',
            labelKey: 'artemisApp.exercise.metadataSync.fields.quizAllowedNumberOfAttempts',
            getCurrentValue: (exercise) => (exercise as QuizExercise).allowedNumberOfAttempts,
            getBaselineValue: (exercise) => (exercise as QuizExercise).allowedNumberOfAttempts,
            getIncomingValue: (snapshot) => snapshot.quizData?.allowedNumberOfAttempts,
            applyValue: (exercise, value) => ((exercise as QuizExercise).allowedNumberOfAttempts = value as number | undefined),
        },
        {
            key: 'quizData.quizMode',
            labelKey: 'artemisApp.exercise.metadataSync.fields.quizMode',
            getCurrentValue: (exercise) => (exercise as QuizExercise).quizMode,
            getBaselineValue: (exercise) => (exercise as QuizExercise).quizMode,
            getIncomingValue: (snapshot) => snapshot.quizData?.quizMode,
            applyValue: (exercise, value) => ((exercise as QuizExercise).quizMode = value as QuizMode | undefined),
        },
        {
            key: 'quizData.duration',
            labelKey: 'artemisApp.exercise.metadataSync.fields.quizDuration',
            getCurrentValue: (exercise) => (exercise as QuizExercise).duration,
            getBaselineValue: (exercise) => (exercise as QuizExercise).duration,
            getIncomingValue: (snapshot) => snapshot.quizData?.duration,
            applyValue: (exercise, value) => ((exercise as QuizExercise).duration = value as number | undefined),
        },
        {
            key: 'quizData.quizQuestions',
            labelKey: 'artemisApp.exercise.metadataSync.fields.quizQuestions',
            getCurrentValue: (exercise) => (exercise as QuizExercise).quizQuestions,
            getBaselineValue: (exercise) => (exercise as QuizExercise).quizQuestions,
            getIncomingValue: (snapshot) => toQuizQuestions(snapshot.quizData?.quizQuestions),
            applyValue: (exercise, value) => ((exercise as QuizExercise).quizQuestions = value as QuizQuestion[] | undefined),
        },
    ];
};

const createProgrammingHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'programmingData.testRepositoryUri',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingTestRepositoryUri',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).testRepositoryUri,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).testRepositoryUri,
            getIncomingValue: (snapshot) => snapshot.programmingData?.testRepositoryUri,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).testRepositoryUri = value as string | undefined),
        },
        {
            key: 'programmingData.auxiliaryRepositories',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingAuxiliaryRepositories',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).auxiliaryRepositories,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).auxiliaryRepositories,
            getIncomingValue: (snapshot) => toAuxiliaryRepositories(snapshot.programmingData?.auxiliaryRepositories),
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).auxiliaryRepositories = value as AuxiliaryRepository[] | undefined),
        },
        {
            key: 'programmingData.allowOnlineEditor',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingAllowOnlineEditor',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineEditor,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineEditor,
            getIncomingValue: (snapshot) => snapshot.programmingData?.allowOnlineEditor,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).allowOnlineEditor = value as boolean | undefined),
        },
        {
            key: 'programmingData.allowOfflineIde',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingAllowOfflineIde',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).allowOfflineIde,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).allowOfflineIde,
            getIncomingValue: (snapshot) => snapshot.programmingData?.allowOfflineIde,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).allowOfflineIde = value as boolean | undefined),
        },
        {
            key: 'programmingData.allowOnlineIde',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingAllowOnlineIde',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineIde,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineIde,
            getIncomingValue: (snapshot) => snapshot.programmingData?.allowOnlineIde,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).allowOnlineIde = value as boolean | undefined),
        },
        {
            key: 'programmingData.staticCodeAnalysisEnabled',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingStaticCodeAnalysisEnabled',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).staticCodeAnalysisEnabled,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).staticCodeAnalysisEnabled,
            getIncomingValue: (snapshot) => snapshot.programmingData?.staticCodeAnalysisEnabled,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).staticCodeAnalysisEnabled = value as boolean | undefined),
        },
        {
            key: 'programmingData.maxStaticCodeAnalysisPenalty',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingMaxStaticCodeAnalysisPenalty',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty,
            getIncomingValue: (snapshot) => snapshot.programmingData?.maxStaticCodeAnalysisPenalty,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty = value as number | undefined),
        },
        {
            key: 'programmingData.programmingLanguage',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingLanguage',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).programmingLanguage,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).programmingLanguage,
            getIncomingValue: (snapshot) => snapshot.programmingData?.programmingLanguage,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).programmingLanguage = value as ProgrammingLanguage | undefined),
        },
        {
            key: 'programmingData.packageName',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingPackageName',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).packageName,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).packageName,
            getIncomingValue: (snapshot) => snapshot.programmingData?.packageName,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).packageName = value as string | undefined),
        },
        {
            key: 'programmingData.showTestNamesToStudents',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingShowTestNamesToStudents',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).showTestNamesToStudents,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).showTestNamesToStudents,
            getIncomingValue: (snapshot) => snapshot.programmingData?.showTestNamesToStudents,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).showTestNamesToStudents = value as boolean | undefined),
        },
        {
            key: 'programmingData.buildAndTestStudentSubmissionsAfterDueDate',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingBuildAndTestAfterDueDate',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate,
            getIncomingValue: (snapshot) => convertDateFromServer(snapshot.programmingData?.buildAndTestStudentSubmissionsAfterDueDate as any),
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate = value as dayjs.Dayjs | undefined),
        },
        {
            key: 'programmingData.projectKey',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingProjectKey',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).projectKey,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).projectKey,
            getIncomingValue: (snapshot) => snapshot.programmingData?.projectKey,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).projectKey = value as string | undefined),
        },
        {
            key: 'programmingData.templateParticipation',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingTemplateParticipation',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).templateParticipation,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).templateParticipation,
            getIncomingValue: (snapshot) => snapshot.programmingData?.templateParticipation,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).templateParticipation = toTemplateParticipation(value as ParticipationSnapshotDTO | undefined)),
        },
        {
            key: 'programmingData.solutionParticipation',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingSolutionParticipation',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).solutionParticipation,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).solutionParticipation,
            getIncomingValue: (snapshot) => snapshot.programmingData?.solutionParticipation,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).solutionParticipation = toSolutionParticipation(value as ParticipationSnapshotDTO | undefined)),
        },
        {
            key: 'programmingData.submissionPolicy',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingSubmissionPolicy',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).submissionPolicy,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).submissionPolicy,
            getIncomingValue: (snapshot) => toSubmissionPolicy(snapshot.programmingData?.submissionPolicy),
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).submissionPolicy = value as SubmissionPolicy | undefined),
        },
        {
            key: 'programmingData.projectType',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingProjectType',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).projectType,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).projectType,
            getIncomingValue: (snapshot) => snapshot.programmingData?.projectType,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).projectType = value as ProjectType | undefined),
        },
        {
            key: 'programmingData.releaseTestsWithExampleSolution',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingReleaseTestsWithExampleSolution',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).releaseTestsWithExampleSolution,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).releaseTestsWithExampleSolution,
            getIncomingValue: (snapshot) => snapshot.programmingData?.releaseTestsWithExampleSolution,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).releaseTestsWithExampleSolution = value as boolean | undefined),
        },
        {
            key: 'programmingData.buildConfig',
            labelKey: 'artemisApp.exercise.metadataSync.fields.programmingBuildConfig',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).buildConfig,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).buildConfig,
            getIncomingValue: (snapshot) => toBuildConfig(snapshot.programmingData?.buildConfig),
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).buildConfig = value as ProgrammingExerciseBuildConfig | undefined),
        },
    ];
};

export const createHandlersByType = (exerciseType: ExerciseType): ExerciseMetadataFieldHandler<Exercise>[] => {
    switch (exerciseType) {
        case ExerciseType.TEXT:
            return createTextHandlers();
        case ExerciseType.MODELING:
            return createModelingHandlers();
        case ExerciseType.FILE_UPLOAD:
            return createFileUploadHandlers();
        case ExerciseType.QUIZ:
            return createQuizHandlers();
        case ExerciseType.PROGRAMMING:
            return createProgrammingHandlers();
        default:
            return [];
    }
};

export const createExerciseMetadataHandlers = (exerciseType: ExerciseType): ExerciseMetadataFieldHandler<Exercise>[] => {
    return createBaseHandlers().concat(createHandlersByType(exerciseType));
};
