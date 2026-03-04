export type GenerationLanguage = 'en' | 'de';
export type GeneratedQuestionType = 'single-choice' | 'multiple-choice' | 'true-false';

export interface GeneratedOption {
    key: string;
    correct: boolean;
}

export interface GeneratedQuestionTemplate {
    key: string;
    type: GeneratedQuestionType;
    questionKey: string;
    options: GeneratedOption[];
}

export interface GeneratedQuestion {
    id: string;
    type: GeneratedQuestionType;
    questionKey: string;
    options: GeneratedOption[];
}
