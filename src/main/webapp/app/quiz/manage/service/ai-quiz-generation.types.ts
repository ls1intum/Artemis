import { AiDifficultyLevel, AiLanguage, AiRequestedSubtype } from './ai-quiz-generation.enums';

export interface AiQuizGenerationRequest {
    numberOfQuestions: number;
    language: AiLanguage;
    topic: string;
    promptHint?: string | null;
    difficultyLevel: AiDifficultyLevel;
    requestedSubtype: AiRequestedSubtype;
}

export interface AiGeneratedOptionDTO {
    text: string;
    correct: boolean;
    feedback?: string | null;
}

export interface AiGeneratedQuestionDTO {
    title: string;
    text: string;
    explanation?: string | null;
    hint?: string | null;
    difficulty?: number | null;
    tags: string[];
    subtype: AiRequestedSubtype;
    competencyIds: number[];
    options: AiGeneratedOptionDTO[];
}

export interface AiQuizGenerationResponse {
    questions: AiGeneratedQuestionDTO[];
}
