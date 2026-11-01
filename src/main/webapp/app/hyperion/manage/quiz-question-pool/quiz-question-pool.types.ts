import { GeneratedQuestion, GeneratedQuestionType, GenerationLanguage } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation.types';

export type PoolFailureMode = 'FACTUAL_ERROR' | 'AMBIGUOUS_CORRECT_ANSWER' | 'OFF_TOPIC' | 'NEAR_DUPLICATE' | 'ILL_FORMED_DISTRACTORS';

export type PoolItemState = 'PENDING' | 'GENERATING' | 'GENERATED' | 'FILTERING' | 'FILTERED' | 'FAILED_GENERATION' | 'FAILED_FILTER';

export type PoolReviewState = 'PENDING' | 'APPROVED' | 'EXCLUDED';

export type PoolDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

/** The filter's judgement on one failure mode; severity runs from 0 (absent) to 1 (severe). */
export interface PoolFilterVerdict {
    mode: PoolFailureMode;
    severity: number;
    triggered: boolean;
    justification?: string;
    /** Whether this verdict asks a reviewer to look at the question; decided by the server. */
    flagged: boolean;
}

/** The filter's decision on one pool question; aggregateScore is 1 minus the worst gating severity. */
export interface PoolFilterDecision {
    accepted: boolean;
    aggregateScore: number;
    verdicts: PoolFilterVerdict[];
    rationale?: string;
}

export interface PoolQuestion {
    id: number;
    competencyId: number;
    competencyTitle: string;
    lectureUnitId: number;
    lectureUnitName: string;
    language: GenerationLanguage;
    questionType: GeneratedQuestionType;
    difficulty: PoolDifficulty;
    state: PoolItemState;
    reviewState: PoolReviewState;
    /** Whether the filter's verdicts ask for a review; decided by the server once the question is judged. */
    flagged?: boolean;
    question?: GeneratedQuestion;
    decision?: PoolFilterDecision;
    failure?: string;
    generatorModel?: string;
    judgeModel?: string;
    updatedAt: string;
}

export interface PoolProgress {
    running: boolean;
    total: number;
    toGenerate: number;
    toJudge: number;
    judged: number;
    accepted: number;
    rejected: number;
    failed: number;
    /** The settings the running build planned its questions with; absent while no build is running. */
    buildSettings?: PoolSettings;
}

export interface PoolSettings {
    poolGenerationEnabled: boolean;
    singleChoiceQuestionsPerLectureUnit: number;
    multipleChoiceEnabled: boolean;
    multipleChoiceQuestionsPerLectureUnit: number;
    /** The fewest answer options a multiple-choice question marks correct. */
    multipleChoiceMinCorrectOptions: number;
    /** The most answer options a multiple-choice question marks correct; not below the fewest. */
    multipleChoiceMaxCorrectOptions: number;
    easyPercent: number;
    mediumPercent: number;
    hardPercent: number;
}

/** One fact of the settings summary: its label key, the label's values and whether the running build was planned with another value. */
export interface PoolSettingsFact {
    key: string;
    values: Record<string, number>;
    changed: boolean;
}

export interface PoolBuildStart {
    started: boolean;
    enqueued: number;
}

/** A reviewer's decision on one pool question. */
export interface PoolReviewRequest {
    questionId: number;
    reviewState: PoolReviewState;
}

/** One verdict as the list shows it: its label key and its severity as an integer percentage. */
export interface PoolVerdictView {
    verdict: PoolFilterVerdict;
    labelKey: string;
    percent: number;
}

/** One listed question with everything the list shows, computed once per change. */
export interface PoolQuestionView {
    question: PoolQuestion;
    /** Position across all three sections, starting at 0. */
    index: number;
    /** The verdicts worth a badge, worst first. */
    flaggedVerdicts: PoolVerdictView[];
    verdicts: PoolVerdictView[];
    scorePercent: number;
    expanded: boolean;
    busy: boolean;
    reviewSeverity: 'success' | 'secondary' | 'contrast';
    reviewLabelKey: string;
}

/** One competency that has questions in the pool. */
export interface PoolCompetency {
    id: number;
    title: string;
}

/** One page of the pool's question list. */
export interface PoolQuestionPage {
    items: PoolQuestion[];
    totalCount: number;
    competencies: PoolCompetency[];
}

/** The request for one page of the question list. */
export interface PoolQuestionQuery {
    competencyId?: number;
    /** Only questions flagged for review, those awaiting a decision first. */
    flagged?: boolean;
    page: number;
    size: number;
}
