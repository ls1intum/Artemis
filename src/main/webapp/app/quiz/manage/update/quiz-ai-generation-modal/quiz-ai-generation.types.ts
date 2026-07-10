import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizQuestionRefinementSuccess } from 'app/openapi/model/quizQuestionRefinementSuccess';

export type GenerationLanguage = 'en' | 'de';

export type GeneratedQuestionType = 'single-choice' | 'multiple-choice' | 'true-false';

/** A successful refinement response, narrowed to guarantee the refined question payload is present. */
export type SuccessfulRefinementResponse = QuizQuestionRefinementSuccess & { question: Omit<GeneratedQuestion, 'id'> };

export interface QuizQuestionRefinementResult {
    refinedQuestion: MultipleChoiceQuestion;
    reasoning: string;
    previousQuestion: MultipleChoiceQuestion;
}

export interface QuizQuestionBulkRefinementResult {
    results: Map<MultipleChoiceQuestion, string>;
    previousSnapshots: Map<MultipleChoiceQuestion, MultipleChoiceQuestion>;
}

export interface GeneratedOption {
    text: string;
    correct: boolean;
    hint?: string;
    explanation?: string;
}

export interface GeneratedQuestion {
    id: string;
    type: GeneratedQuestionType;
    title: string;
    questionText: string;
    options: GeneratedOption[];
    hint?: string;
    explanation?: string;
}
