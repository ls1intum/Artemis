import type { QuizQuestionWithoutSolution } from 'app/openapi/model/quiz-question-without-solution';
import type { QuizQuestionWithSolution } from 'app/openapi/model/quiz-question-with-solution';

export interface EntityIdRefDTO {
    id: number;
}

export interface PracticeSessionDTO {
    id: number;
    courseId: number;
    startTime: string;
    questions: QuizQuestionWithoutSolution[];
}

export interface SubmittedAnswerFromLiveClientDTO {
    type: 'multiple-choice' | 'drag-and-drop' | 'short-answer' | string;
    quizQuestion: EntityIdRefDTO;
    selectedOptions?: EntityIdRefDTO[];
}

export interface PracticeSubmissionDTO {
    answers: SubmittedAnswerFromLiveClientDTO[];
}

export interface PracticeQuestionReviewDTO {
    question: QuizQuestionWithSolution;
    score?: number;
    submittedAnswer?: string;
}

export interface PracticeSessionReviewDTO {
    id: number;
    courseId: number;
    startTime: string;
    completedAt?: string;
    score?: number;
    maxPoints?: number;
    questions: PracticeQuestionReviewDTO[];
}

export interface PracticeSessionSummaryDTO {
    id: number;
    courseId: number;
    startTime: string;
    completedAt?: string;
    score?: number;
    maxPoints?: number;
    completed: boolean;
}
