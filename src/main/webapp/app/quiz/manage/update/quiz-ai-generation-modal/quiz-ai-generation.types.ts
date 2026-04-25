export type GenerationLanguage = 'en' | 'de';
export type GeneratedQuestionType = 'single-choice' | 'multiple-choice' | 'true-false';

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
