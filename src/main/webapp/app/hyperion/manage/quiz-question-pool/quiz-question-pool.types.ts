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
}

export interface PoolSettings {
    poolGenerationEnabled: boolean;
    singleChoiceQuestionsPerLectureUnit: number;
    multipleChoiceEnabled: boolean;
    multipleChoiceQuestionsPerLectureUnit: number;
    easyPercent: number;
    mediumPercent: number;
    hardPercent: number;
}

export interface PoolBuildStart {
    started: boolean;
    enqueued: number;
}

/**
 * Whether a decision deserves the reviewer's attention although the question passed the filter:
 * the filter triggered on at least one failure mode or reported a non-zero severity.
 */
export function isFlagged(decision: PoolFilterDecision): boolean {
    return decision.verdicts.some((verdict) => verdict.triggered || verdict.severity > 0);
}
