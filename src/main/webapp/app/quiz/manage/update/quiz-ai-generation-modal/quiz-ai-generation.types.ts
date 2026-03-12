export type GenerationLanguage = 'en' | 'de';
export type GeneratedQuestionType = 'single-choice' | 'multiple-choice' | 'true-false';

export interface GeneratedOption {
    text: string;
    correct: boolean;
}

export interface GeneratedQuestion {
    id: string;
    type: GeneratedQuestionType;
    title: string;
    questionText: string;
    options: GeneratedOption[];
}
