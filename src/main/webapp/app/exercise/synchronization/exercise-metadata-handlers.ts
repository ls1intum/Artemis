import dayjs from 'dayjs/esm';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { CompetencyExerciseLink, CourseCompetency, MEDIUM_COMPETENCY_LINK_WEIGHT } from 'app/atlas/shared/entities/competency.model';
import { DifficultyLevel, Exercise, ExerciseMode, ExerciseType, IncludedInOverallScore, PlagiarismDetectionConfig } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { CompetencyExerciseLinkSnapshotDTO, ExerciseSnapshotDTO, TeamAssignmentConfigSnapshot } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';
import { toExerciseCategories, toTeamAssignmentConfig } from 'app/exercise/synchronization/exercise-metadata-snapshot-shared.mapper';
import { toBuildConfig } from 'app/exercise/synchronization/exercise-metadata-snapshot-programming.mapper';
import { toQuizQuestions } from 'app/exercise/synchronization/exercise-metadata-snapshot-quiz.mapper';

/**
 * Defines how a specific exercise field is read, compared, and applied.
 */
export interface ExerciseMetadataFieldHandler<T extends Exercise> {
    key: string;
    labelKey: string;
    getCurrentValue: (exercise: T) => unknown;
    getBaselineValue: (exercise: T) => unknown;
    getIncomingValue: (snapshot: ExerciseSnapshotDTO) => unknown;
    applyValue: (exercise: T, value: unknown) => void;
}

type ExerciseResolver = () => Exercise;

/**
 * Creates a basic handler for scalar exercise fields stored directly on the snapshot.
 */
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

/**
 * Maps competency link snapshots onto the current exercise's resolved competencies.
 */
const toCompetencyLinks = (exercise: Exercise, snapshotLinks: CompetencyExerciseLinkSnapshotDTO[] | undefined): CompetencyExerciseLink[] | undefined => {
    if (!snapshotLinks) {
        return undefined;
    }
    const competencyById = new Map<number, CourseCompetency>();
    const existingLinks = exercise.competencyLinks ?? [];
    for (const link of existingLinks) {
        if (link.competency?.id != undefined) {
            competencyById.set(link.competency.id, link.competency);
        }
    }
    const courseCompetencies = exercise.course?.competencies ?? [];
    const coursePrerequisites = exercise.course?.prerequisites ?? [];
    for (const competency of [...courseCompetencies, ...coursePrerequisites]) {
        if (competency.id != undefined) {
            competencyById.set(competency.id, competency);
        }
    }
    const mapped: CompetencyExerciseLink[] = [];
    for (const link of snapshotLinks) {
        const competencyId = link.competencyId?.competencyId;
        if (competencyId == undefined) {
            continue;
        }
        const competency = competencyById.get(competencyId);
        if (!competency) {
            continue;
        }
        mapped.push(new CompetencyExerciseLink(competency, exercise, link.weight ?? MEDIUM_COMPETENCY_LINK_WEIGHT));
    }
    return mapped.length > 0 ? mapped : undefined;
};

/**
 * Creates a handler for date fields with server-date normalization.
 */
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

/**
 * Builds handlers for exercise fields shared across all exercise types.
 */
export const createBaseHandlers = (resolveExercise?: ExerciseResolver): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        baseHandler(
            'title',
            'artemisApp.exercise.title',
            (exercise) => exercise.title,
            (exercise, value) => (exercise.title = value as string | undefined),
        ),
        baseHandler(
            'shortName',
            'artemisApp.exercise.shortName',
            (exercise) => exercise.shortName,
            (exercise, value) => (exercise.shortName = value as string | undefined),
        ),
        {
            key: 'competencyLinks',
            labelKey: 'artemisApp.competency.competencies',
            getCurrentValue: (exercise) => exercise.competencyLinks,
            getBaselineValue: (exercise) => exercise.competencyLinks,
            getIncomingValue: (snapshot) => (resolveExercise ? toCompetencyLinks(resolveExercise(), snapshot.competencyLinks) : snapshot.competencyLinks),
            applyValue: (exercise, value) => (exercise.competencyLinks = value as CompetencyExerciseLink[] | undefined),
        },
        baseHandler(
            'channelName',
            'artemisApp.lecture.channelName',
            (exercise) => exercise.channelName,
            (exercise, value) => (exercise.channelName = value as string | undefined),
        ),
        baseHandler(
            'maxPoints',
            'artemisApp.gradingSystem.maxPoints',
            (exercise) => exercise.maxPoints,
            (exercise, value) => (exercise.maxPoints = value as number | undefined),
        ),
        baseHandler(
            'bonusPoints',
            'artemisApp.exercise.bonusPoints',
            (exercise) => exercise.bonusPoints,
            (exercise, value) => (exercise.bonusPoints = value as number | undefined),
        ),
        baseHandler(
            'assessmentType',
            'artemisApp.assessment.assessmentType',
            (exercise) => exercise.assessmentType,
            (exercise, value) => (exercise.assessmentType = value as AssessmentType | undefined),
        ),
        dateHandler(
            'releaseDate',
            'artemisApp.exercise.releaseDate',
            (exercise) => exercise.releaseDate,
            (exercise, value) => (exercise.releaseDate = value as dayjs.Dayjs | undefined),
        ),
        dateHandler(
            'startDate',
            'artemisApp.exercise.startDate',
            (exercise) => exercise.startDate,
            (exercise, value) => (exercise.startDate = value as dayjs.Dayjs | undefined),
        ),
        dateHandler(
            'dueDate',
            'artemisApp.exercise.dueDate',
            (exercise) => exercise.dueDate,
            (exercise, value) => (exercise.dueDate = value as dayjs.Dayjs | undefined),
        ),
        dateHandler(
            'assessmentDueDate',
            'artemisApp.exercise.assessmentDueDate',
            (exercise) => exercise.assessmentDueDate,
            (exercise, value) => (exercise.assessmentDueDate = value as dayjs.Dayjs | undefined),
        ),
        dateHandler(
            'exampleSolutionPublicationDate',
            'artemisApp.exercise.exampleSolutionPublicationDate',
            (exercise) => exercise.exampleSolutionPublicationDate,
            (exercise, value) => (exercise.exampleSolutionPublicationDate = value as dayjs.Dayjs | undefined),
        ),
        baseHandler(
            'difficulty',
            'artemisApp.exercise.difficulty',
            (exercise) => exercise.difficulty,
            (exercise, value) => (exercise.difficulty = value as DifficultyLevel | undefined),
        ),
        baseHandler(
            'mode',
            'artemisApp.exercise.mode',
            (exercise) => exercise.mode,
            (exercise, value) => (exercise.mode = value as ExerciseMode | undefined),
        ),
        baseHandler(
            'allowComplaintsForAutomaticAssessments',
            'artemisApp.course.complaintsEnabled.title',
            (exercise) => exercise.allowComplaintsForAutomaticAssessments,
            (exercise, value) => (exercise.allowComplaintsForAutomaticAssessments = value as boolean | undefined),
        ),
        baseHandler(
            'allowFeedbackRequests',
            'artemisApp.course.requestMoreFeedbackEnabled.title',
            (exercise) => exercise.allowFeedbackRequests,
            (exercise, value) => (exercise.allowFeedbackRequests = value as boolean | undefined),
        ),
        baseHandler(
            'includedInOverallScore',
            'artemisApp.exercise.includedInOverallScore',
            (exercise) => exercise.includedInOverallScore,
            (exercise, value) => (exercise.includedInOverallScore = value as IncludedInOverallScore | undefined),
        ),
        baseHandler(
            'problemStatement',
            'artemisApp.exercise.problemStatement',
            (exercise) => exercise.problemStatement,
            (exercise, value) => (exercise.problemStatement = value as string | undefined),
        ),
        baseHandler(
            'gradingInstructions',
            'artemisApp.assessmentInstructions.assessmentInstructions',
            (exercise) => exercise.gradingInstructions,
            (exercise, value) => (exercise.gradingInstructions = value as string | undefined),
        ),
        baseHandler(
            'categories',
            'artemisApp.exercise.categories',
            (exercise) => exercise.categories,
            (exercise, value) => (exercise.categories = toExerciseCategories(value as string[] | undefined)),
        ),
        baseHandler(
            'teamAssignmentConfig',
            'artemisApp.exercise.teamAssignmentConfig.teamSize',
            (exercise) => exercise.teamAssignmentConfig,
            (exercise, value) => (exercise.teamAssignmentConfig = toTeamAssignmentConfig(value as TeamAssignmentConfigSnapshot | undefined)),
        ),
        baseHandler(
            'presentationScoreEnabled',
            'artemisApp.exercise.presentationScoreEnabled.title',
            (exercise) => exercise.presentationScoreEnabled,
            (exercise, value) => (exercise.presentationScoreEnabled = value as boolean | undefined),
        ),
        baseHandler(
            'secondCorrectionEnabled',
            'artemisApp.exam.secondCorrectionColumn',
            (exercise) => exercise.secondCorrectionEnabled,
            (exercise, value) => (exercise.secondCorrectionEnabled = (value as boolean | undefined) ?? false),
        ),
        baseHandler(
            'feedbackSuggestionModule',
            'artemisApp.exercise.feedbackSuggestionsEnabled',
            (exercise) => exercise.feedbackSuggestionModule,
            (exercise, value) => (exercise.feedbackSuggestionModule = value as string | undefined),
        ),
        baseHandler(
            'gradingCriteria',
            'artemisApp.assessmentInstructions.structuredGradingInstructions',
            (exercise) => exercise.gradingCriteria,
            (exercise, value) => (exercise.gradingCriteria = value as GradingCriterion[] | undefined),
        ),
        baseHandler(
            'plagiarismDetectionConfig',
            'artemisApp.plagiarism.plagiarismDetection',
            (exercise) => exercise.plagiarismDetectionConfig,
            (exercise, value) => (exercise.plagiarismDetectionConfig = value as PlagiarismDetectionConfig | undefined),
        ),
    ];
};

/**
 * Builds handlers for text exercise-specific fields.
 */
const createTextHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'textData.exampleSolution',
            labelKey: 'artemisApp.exercise.exampleSolution',
            getCurrentValue: (exercise) => (exercise as TextExercise).exampleSolution,
            getBaselineValue: (exercise) => (exercise as TextExercise).exampleSolution,
            getIncomingValue: (snapshot) => snapshot.textData?.exampleSolution,
            applyValue: (exercise, value) => ((exercise as TextExercise).exampleSolution = value as string | undefined),
        },
    ];
};

/**
 * Builds handlers for modeling exercise-specific fields.
 */
const createModelingHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'modelingData.diagramType',
            labelKey: 'artemisApp.modelingExercise.diagramType',
            getCurrentValue: (exercise) => (exercise as ModelingExercise).diagramType,
            getBaselineValue: (exercise) => (exercise as ModelingExercise).diagramType,
            getIncomingValue: (snapshot) => snapshot.modelingData?.diagramType,
            applyValue: (exercise, value) => ((exercise as ModelingExercise).diagramType = value as any),
        },
        {
            key: 'modelingData.exampleSolutionModel',
            labelKey: 'artemisApp.modelingExercise.exampleSolution',
            getCurrentValue: (exercise) => (exercise as ModelingExercise).exampleSolutionModel,
            getBaselineValue: (exercise) => (exercise as ModelingExercise).exampleSolutionModel,
            getIncomingValue: (snapshot) => snapshot.modelingData?.exampleSolutionModel,
            applyValue: (exercise, value) => ((exercise as ModelingExercise).exampleSolutionModel = value as string | undefined),
        },
        {
            key: 'modelingData.exampleSolutionExplanation',
            labelKey: 'artemisApp.modelingExercise.exampleSolutionExplanation',
            getCurrentValue: (exercise) => (exercise as ModelingExercise).exampleSolutionExplanation,
            getBaselineValue: (exercise) => (exercise as ModelingExercise).exampleSolutionExplanation,
            getIncomingValue: (snapshot) => snapshot.modelingData?.exampleSolutionExplanation,
            applyValue: (exercise, value) => ((exercise as ModelingExercise).exampleSolutionExplanation = value as string | undefined),
        },
    ];
};

/**
 * Builds handlers for file upload exercise-specific fields.
 */
const createFileUploadHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'fileUploadData.exampleSolution',
            labelKey: 'artemisApp.exercise.exampleSolution',
            getCurrentValue: (exercise) => (exercise as FileUploadExercise).exampleSolution,
            getBaselineValue: (exercise) => (exercise as FileUploadExercise).exampleSolution,
            getIncomingValue: (snapshot) => snapshot.fileUploadData?.exampleSolution,
            applyValue: (exercise, value) => ((exercise as FileUploadExercise).exampleSolution = value as string | undefined),
        },
        {
            key: 'fileUploadData.filePattern',
            labelKey: 'artemisApp.fileUploadExercise.filePattern',
            getCurrentValue: (exercise) => (exercise as FileUploadExercise).filePattern,
            getBaselineValue: (exercise) => (exercise as FileUploadExercise).filePattern,
            getIncomingValue: (snapshot) => snapshot.fileUploadData?.filePattern,
            applyValue: (exercise, value) => ((exercise as FileUploadExercise).filePattern = value as string | undefined),
        },
    ];
};

/**
 * Builds handlers for quiz exercise-specific fields.
 */
const createQuizHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'quizData.randomizeQuestionOrder',
            labelKey: 'artemisApp.quizExercise.randomizeQuestionOrder',
            getCurrentValue: (exercise) => (exercise as QuizExercise).randomizeQuestionOrder,
            getBaselineValue: (exercise) => (exercise as QuizExercise).randomizeQuestionOrder,
            getIncomingValue: (snapshot) => snapshot.quizData?.randomizeQuestionOrder,
            applyValue: (exercise, value) => ((exercise as QuizExercise).randomizeQuestionOrder = value as boolean | undefined),
        },
        {
            key: 'quizData.allowedNumberOfAttempts',
            labelKey: 'artemisApp.quizExercise.allowedNumberOfAttempts',
            getCurrentValue: (exercise) => (exercise as QuizExercise).allowedNumberOfAttempts,
            getBaselineValue: (exercise) => (exercise as QuizExercise).allowedNumberOfAttempts,
            getIncomingValue: (snapshot) => snapshot.quizData?.allowedNumberOfAttempts,
            applyValue: (exercise, value) => ((exercise as QuizExercise).allowedNumberOfAttempts = value as number | undefined),
        },
        {
            key: 'quizData.quizMode',
            labelKey: 'artemisApp.quizExercise.quizMode.title',
            getCurrentValue: (exercise) => (exercise as QuizExercise).quizMode,
            getBaselineValue: (exercise) => (exercise as QuizExercise).quizMode,
            getIncomingValue: (snapshot) => snapshot.quizData?.quizMode,
            applyValue: (exercise, value) => ((exercise as QuizExercise).quizMode = value as QuizMode | undefined),
        },
        {
            key: 'quizData.duration',
            labelKey: 'artemisApp.quizExercise.duration',
            getCurrentValue: (exercise) => (exercise as QuizExercise).duration,
            getBaselineValue: (exercise) => (exercise as QuizExercise).duration,
            getIncomingValue: (snapshot) => snapshot.quizData?.duration,
            applyValue: (exercise, value) => ((exercise as QuizExercise).duration = value as number | undefined),
        },
        {
            key: 'quizData.quizQuestions',
            labelKey: 'artemisApp.quizExercise.questions',
            getCurrentValue: (exercise) => (exercise as QuizExercise).quizQuestions,
            getBaselineValue: (exercise) => (exercise as QuizExercise).quizQuestions,
            getIncomingValue: (snapshot) => toQuizQuestions(snapshot.quizData?.quizQuestions),
            applyValue: (exercise, value) => ((exercise as QuizExercise).quizQuestions = value as QuizQuestion[] | undefined),
        },
    ];
};

/**
 * Builds handlers for programming exercise-specific fields.
 */
const createProgrammingHandlers = (): ExerciseMetadataFieldHandler<Exercise>[] => {
    return [
        {
            key: 'programmingData.allowOnlineEditor',
            labelKey: 'artemisApp.programmingExercise.allowOnlineEditor.title',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineEditor,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineEditor,
            getIncomingValue: (snapshot) => snapshot.programmingData?.allowOnlineEditor,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).allowOnlineEditor = value as boolean | undefined),
        },
        {
            key: 'programmingData.allowOfflineIde',
            labelKey: 'artemisApp.programmingExercise.allowOfflineIde.title',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).allowOfflineIde,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).allowOfflineIde,
            getIncomingValue: (snapshot) => snapshot.programmingData?.allowOfflineIde,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).allowOfflineIde = value as boolean | undefined),
        },
        {
            key: 'programmingData.allowOnlineIde',
            labelKey: 'artemisApp.programmingExercise.allowOnlineIde.title',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineIde,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineIde,
            getIncomingValue: (snapshot) => snapshot.programmingData?.allowOnlineIde,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).allowOnlineIde = value as boolean | undefined),
        },
        {
            key: 'programmingData.maxStaticCodeAnalysisPenalty',
            labelKey: 'artemisApp.programmingExercise.maxStaticCodeAnalysisPenalty.title',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty,
            getIncomingValue: (snapshot) => snapshot.programmingData?.maxStaticCodeAnalysisPenalty,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty = value as number | undefined),
        },
        {
            key: 'programmingData.showTestNamesToStudents',
            labelKey: 'artemisApp.programmingExercise.showTestNamesToStudents',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).showTestNamesToStudents,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).showTestNamesToStudents,
            getIncomingValue: (snapshot) => snapshot.programmingData?.showTestNamesToStudents,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).showTestNamesToStudents = value as boolean | undefined),
        },
        {
            key: 'programmingData.buildAndTestStudentSubmissionsAfterDueDate',
            labelKey: 'artemisApp.programmingExercise.timeline.afterDueDate',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate,
            getIncomingValue: (snapshot) => convertDateFromServer(snapshot.programmingData?.buildAndTestStudentSubmissionsAfterDueDate as any),
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate = value as dayjs.Dayjs | undefined),
        },
        {
            key: 'programmingData.releaseTestsWithExampleSolution',
            labelKey: 'artemisApp.programmingExercise.releaseTestsWithExampleSolution',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).releaseTestsWithExampleSolution,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).releaseTestsWithExampleSolution,
            getIncomingValue: (snapshot) => snapshot.programmingData?.releaseTestsWithExampleSolution,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).releaseTestsWithExampleSolution = value as boolean | undefined),
        },
        {
            key: 'programmingData.buildConfig',
            labelKey: 'artemisApp.programmingExercise.buildConfiguration',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).buildConfig,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).buildConfig,
            getIncomingValue: (snapshot) => toBuildConfig(snapshot.programmingData?.buildConfig),
            applyValue: (exercise, value) => {
                const programmingExercise = exercise as ProgrammingExercise;
                const incoming = value as ProgrammingExerciseBuildConfig | undefined;
                if (incoming && programmingExercise.buildConfig) {
                    incoming.sequentialTestRuns = programmingExercise.buildConfig.sequentialTestRuns;
                }
                programmingExercise.buildConfig = incoming;
            },
        },
    ];
};

/**
 * Returns handlers for the given exercise type.
 */
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

/**
 * Builds the full set of handlers for a specific exercise type.
 */
export const createExerciseMetadataHandlers = (exerciseType: ExerciseType, resolveExercise?: ExerciseResolver): ExerciseMetadataFieldHandler<Exercise>[] => {
    return createBaseHandlers(resolveExercise).concat(createHandlersByType(exerciseType));
};
