import dayjs from 'dayjs/esm';

/** Where one student's comprehension check stands; the names are the server's constants. */
export enum MetisCheckStatus {
    PENDING = 'PENDING',
    GENERATING = 'GENERATING',
    IN_REVIEW = 'IN_REVIEW',
    READY = 'READY',
    IN_PROGRESS = 'IN_PROGRESS',
    COMPLETED = 'COMPLETED',
    FAILED = 'FAILED',
    SKIPPED = 'SKIPPED',
}

/** How the comprehension check of one programming exercise is set up. */
export interface MetisExerciseConfigDTO {
    exerciseId: number;
    enabled: boolean;
    /** When students may answer; an empty start opens the check right after the due date. */
    windowStart?: dayjs.Dayjs;
    windowEnd?: dayjs.Dayjs;
    mainQuestionCount: number;
    maxFollowUpsPerQuestion: number;
    minSecondsPerQuestion: number;
    maxSecondsPerQuestion: number;
    /** Five-step scale around the default (3): how much the model expects in an answer. */
    answerLength: number;
    /** Five-step scale around the default (3): how demanding the questions are. */
    complexity: number;
    /** Points of the Likert scale the answers are rated on (5 or 7). */
    likertScalePoints: number;
    /** A follow-up is asked when the rating is at most this value. */
    followUpThreshold: number;
    /** Whether the generated questions wait for an instructor's approval before students see them. */
    reviewRequired: boolean;
    instructorHint?: string;
}

/** An answer window of its own for one tutorial group; groups without one use the exercise's window. */
export interface MetisGroupWindowDTO {
    id?: number;
    exerciseId: number;
    tutorialGroupId: number;
    windowStart: dayjs.Dayjs;
    windowEnd: dayjs.Dayjs;
}

export interface MetisTutorialGroupDTO {
    id: number;
    title: string;
}

/** One question of a check: a main question generated up front, or a follow-up (`parentQuestionId`) generated live. */
export interface MetisQuestionDTO {
    id: number;
    checkId: number;
    questionOrder: number;
    parentQuestionId?: number;
    focusFilePath?: string;
    focusFileChangedLines?: number;
    questionText: string;
    timeLimitSeconds: number;
    regenerationCount: number;
    approvedAt?: dayjs.Dayjs;
    shownAt?: dayjs.Dayjs;
    answerText?: string;
    answeredAt?: dayjs.Dayjs;
    llmScore?: number;
    llmRationale?: string;
    llmRatedAt?: dayjs.Dayjs;
}

export interface MetisCheckDTO {
    id: number;
    participationId: number;
    status: MetisCheckStatus;
    commitHash?: string;
    llmModel?: string;
    promptVersion?: string;
    failureReason?: string;
    generatedAt?: dayjs.Dayjs;
    startedAt?: dayjs.Dayjs;
    completedAt?: dayjs.Dayjs;
}

/** What the student endpoint sends to a student: the state of their own check, without the questions not asked yet. */
export interface MetisStudentCheckDTO {
    status: MetisCheckStatus;
    windowStart?: dayjs.Dayjs;
    windowEnd?: dayjs.Dayjs;
    tutorialGroupTitle?: string;
    mainQuestionCount: number;
    likertScalePoints: number;
    currentQuestion?: MetisQuestionDTO;
    answeredQuestions: MetisQuestionDTO[];
    /** Whether the student may see the ratings once the window has closed. */
    ratingsVisible: boolean;
    /** Why the check is skipped or failed, as a code of the server's MetisFailureReason. */
    failureReason?: string;
}

/** One participation on the instructor dashboard; `questions` are in thread order and main questions have no `parentQuestionId`. */
export interface MetisDashboardRowDTO {
    participationId: number;
    studentName: string;
    studentLogin: string;
    tutorialGroupTitle?: string;
    check: MetisCheckDTO;
    questions: MetisQuestionDTO[];
}

export interface MetisDashboardSummaryDTO {
    participants: number;
    completed: number;
    inProgress: number;
    notStarted: number;
    skippedOrFailed: number;
    awaitingReview: number;
    averageScore?: number;
    followUps: number;
    windowEnd?: dayjs.Dayjs;
}

/** What the dashboard endpoint answers with: the summary tiles and one row per participation. */
export interface MetisDashboardDTO {
    summary: MetisDashboardSummaryDTO;
    rows: MetisDashboardRowDTO[];
}

/** What "generate now" did: the checks it created and the skipped or failed checks it put back into the queue. */
export interface MetisGenerateResultDTO {
    createdChecks: number;
    requeuedChecks: number;
}

/** Lowest and highest step of the five-step answer-length and complexity scales. */
export const METIS_SCALE_MIN = 1;
export const METIS_SCALE_MAX = 5;

/** The configuration of an exercise that has none yet. */
export const METIS_DEFAULT_CONFIG: Omit<MetisExerciseConfigDTO, 'exerciseId'> = {
    enabled: false,
    mainQuestionCount: 3,
    maxFollowUpsPerQuestion: 2,
    minSecondsPerQuestion: 60,
    maxSecondsPerQuestion: 300,
    answerLength: 3,
    complexity: 3,
    likertScalePoints: 5,
    followUpThreshold: 3,
    reviewRequired: false,
};
