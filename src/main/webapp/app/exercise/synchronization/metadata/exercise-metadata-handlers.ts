import dayjs from 'dayjs/esm';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { DifficultyLevel, Exercise, ExerciseMode, ExerciseType, IncludedInOverallScore, PlagiarismDetectionConfig } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { ExerciseSnapshotDTO, TeamAssignmentConfigSnapshot } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { normalizeCategoryArray, toCompetencyLinks, toTeamAssignmentConfig } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot-shared.mapper';
import { toAuxiliaryRepositories, toBuildConfig } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot-programming.mapper';

/**
 * Defines how a specific exercise field is read, compared, and applied.
 */
export interface ExerciseMetadataFieldHandler<T extends Exercise, V = unknown> {
    key: string;
    labelKey: string;
    getCurrentValue: (exercise: T) => V;
    getBaselineValue: (exercise: T) => V;
    getIncomingValue: (snapshot: ExerciseSnapshotDTO) => V;
    applyValue: (exercise: T, value: V) => void;
}

type ExerciseResolver = () => Exercise;

const normalizeCategories = (value: unknown): ExerciseCategory[] | undefined => {
    if (!Array.isArray(value)) {
        return undefined;
    }
    return normalizeCategoryArray(value);
};

const applyCategories = (exercise: Exercise, value: unknown): void => {
    const incomingCategories = normalizeCategories(value);
    const existingCategories = exercise.categories;

    if (existingCategories) {
        existingCategories.length = 0;
        if (incomingCategories) {
            existingCategories.push(...incomingCategories);
        }
        return;
    }

    exercise.categories = incomingCategories;
};

const applyAuxiliaryRepositories = (exercise: ProgrammingExercise, value: unknown): void => {
    exercise.auxiliaryRepositories = (value as AuxiliaryRepository[] | undefined) ?? [];
};

/**
 * Creates a basic handler for scalar exercise fields stored directly on the snapshot.
 *
 * The `as V` cast in `getIncomingValue` is necessary because the snapshot field type
 * (`ExerciseSnapshotDTO[K]`) may not exactly match the exercise field type `V` (e.g.
 * categories are `string[]` in the snapshot but `ExerciseCategory[]` on the exercise).
 * Type correctness is ensured by matching getter/setter pairs and verified by the
 * reflection-based coverage tests in {@link ExerciseVersionServiceTest}.
 */
const baseHandler = <K extends keyof ExerciseSnapshotDTO, V>(
    key: K,
    labelKey: string,
    getter: (exercise: Exercise) => V,
    setter: (exercise: Exercise, value: V) => void,
): ExerciseMetadataFieldHandler<Exercise, V> => {
    return {
        key,
        labelKey,
        getCurrentValue: getter,
        getBaselineValue: getter,
        getIncomingValue: (snapshot) => snapshot[key] as V,
        applyValue: setter,
    };
};

/**
 * Creates a handler for date fields with server-date normalization.
 */
const dateHandler = (
    key: keyof ExerciseSnapshotDTO,
    labelKey: string,
    getter: (exercise: Exercise) => dayjs.Dayjs | undefined,
    setter: (exercise: Exercise, value: dayjs.Dayjs | undefined) => void,
): ExerciseMetadataFieldHandler<Exercise, dayjs.Dayjs | undefined> => {
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
        // Note: problemStatement is handled by live synchronization with Yjs, not by metadata sync
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
            (exercise, value) => applyCategories(exercise, value),
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
        } satisfies ExerciseMetadataFieldHandler<Exercise, boolean | undefined>,
        {
            key: 'programmingData.allowOfflineIde',
            labelKey: 'artemisApp.programmingExercise.allowOfflineIde.title',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).allowOfflineIde,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).allowOfflineIde,
            getIncomingValue: (snapshot) => snapshot.programmingData?.allowOfflineIde,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).allowOfflineIde = value as boolean | undefined),
        } satisfies ExerciseMetadataFieldHandler<Exercise, boolean | undefined>,
        {
            key: 'programmingData.allowOnlineIde',
            labelKey: 'artemisApp.programmingExercise.allowOnlineIde.title',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineIde,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).allowOnlineIde,
            getIncomingValue: (snapshot) => snapshot.programmingData?.allowOnlineIde,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).allowOnlineIde = value as boolean | undefined),
        } satisfies ExerciseMetadataFieldHandler<Exercise, boolean | undefined>,
        {
            key: 'programmingData.auxiliaryRepositories',
            labelKey: 'artemisApp.programmingExercise.auxiliaryRepositories',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).auxiliaryRepositories,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).auxiliaryRepositories,
            getIncomingValue: (snapshot) => toAuxiliaryRepositories(snapshot.programmingData?.auxiliaryRepositories),
            applyValue: (exercise, value) => applyAuxiliaryRepositories(exercise as ProgrammingExercise, value),
        } satisfies ExerciseMetadataFieldHandler<Exercise, unknown>,
        {
            key: 'programmingData.maxStaticCodeAnalysisPenalty',
            labelKey: 'artemisApp.programmingExercise.maxStaticCodeAnalysisPenalty.title',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty,
            getIncomingValue: (snapshot) => snapshot.programmingData?.maxStaticCodeAnalysisPenalty,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).maxStaticCodeAnalysisPenalty = value as number | undefined),
        } satisfies ExerciseMetadataFieldHandler<Exercise, number | undefined>,
        {
            key: 'programmingData.showTestNamesToStudents',
            labelKey: 'artemisApp.programmingExercise.showTestNamesToStudents',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).showTestNamesToStudents,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).showTestNamesToStudents,
            getIncomingValue: (snapshot) => snapshot.programmingData?.showTestNamesToStudents,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).showTestNamesToStudents = value as boolean | undefined),
        } satisfies ExerciseMetadataFieldHandler<Exercise, boolean | undefined>,
        {
            key: 'programmingData.buildAndTestStudentSubmissionsAfterDueDate',
            labelKey: 'artemisApp.programmingExercise.timeline.afterDueDate',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate,
            getIncomingValue: (snapshot) => convertDateFromServer(snapshot.programmingData?.buildAndTestStudentSubmissionsAfterDueDate as any),
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate = value as dayjs.Dayjs | undefined),
        } satisfies ExerciseMetadataFieldHandler<Exercise, dayjs.Dayjs | undefined>,
        {
            key: 'programmingData.releaseTestsWithExampleSolution',
            labelKey: 'artemisApp.programmingExercise.releaseTestsWithExampleSolution',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).releaseTestsWithExampleSolution,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).releaseTestsWithExampleSolution,
            getIncomingValue: (snapshot) => snapshot.programmingData?.releaseTestsWithExampleSolution,
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).releaseTestsWithExampleSolution = value as boolean | undefined),
        } satisfies ExerciseMetadataFieldHandler<Exercise, boolean | undefined>,
        {
            key: 'programmingData.buildConfig',
            labelKey: 'artemisApp.programmingExercise.buildConfiguration',
            getCurrentValue: (exercise) => (exercise as ProgrammingExercise).buildConfig,
            getBaselineValue: (exercise) => (exercise as ProgrammingExercise).buildConfig,
            getIncomingValue: (snapshot) => toBuildConfig(snapshot.programmingData?.buildConfig),
            applyValue: (exercise, value) => ((exercise as ProgrammingExercise).buildConfig = value as ProgrammingExerciseBuildConfig | undefined),
        } satisfies ExerciseMetadataFieldHandler<Exercise, ProgrammingExerciseBuildConfig | undefined>,
    ];
};

/**
 * Returns handlers for the given exercise type.
 */
export const createHandlersByType = (exerciseType: ExerciseType): ExerciseMetadataFieldHandler<Exercise>[] => {
    switch (exerciseType) {
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
