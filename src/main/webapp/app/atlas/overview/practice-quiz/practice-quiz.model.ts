export interface EntityIdRefDTO {
    id: number;
}

export interface AnswerOptionDTO {
    id: number;
    text?: string;
    hint?: string;
    isCorrect?: boolean;
    explanation?: string;
}

export interface QuizQuestionDTO {
    id: number;
    title?: string;
    text?: string;
    points?: number;
    scoringType?: string;
    type: 'multiple-choice' | 'drag-and-drop' | 'short-answer' | string;
    answerOptions?: AnswerOptionDTO[];
    singleChoice?: boolean;
    competencyId?: number;
    difficulty?: string;
}

export type QuizQuestionWithoutSolutionDTO = QuizQuestionDTO;
export type QuizQuestionWithSolutionDTO = QuizQuestionDTO;

export interface PracticeSessionDTO {
    id: number;
    courseId: number;
    startTime: string;
    completedAt?: string;
    questions: QuizQuestionWithoutSolutionDTO[];
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
    question: QuizQuestionWithSolutionDTO;
    isAdaptive: boolean;
    score?: number;
    submittedAnswer?: string;
    answeredAt?: string;
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
