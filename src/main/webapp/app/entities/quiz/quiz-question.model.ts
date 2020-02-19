import { BaseEntity } from 'app/shared/model/base-entity';
import { SafeHtml } from '@angular/platform-browser';
import { Exercise } from 'app/entities/exercise.model';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';

export const enum ScoringType {
    ALL_OR_NOTHING = 'ALL_OR_NOTHING',
    PROPORTIONAL_WITH_PENALTY = 'PROPORTIONAL_WITH_PENALTY',
    TRUE_FALSE_NEUTRAL = 'TRUE_FALSE_NEUTRAL',
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Question.java
export const enum QuizQuestionType {
    MULTIPLE_CHOICE = 'multiple-choice',
    DRAG_AND_DROP = 'drag-and-drop',
    SHORT_ANSWER = 'short-answer',
}

export interface TextHintExplanationInterface {
    text: string | null;
    hint: string | null;
    explanation: string | null;
}

export class RenderedQuizQuestionMarkDownElement {
    text: SafeHtml;
    hint: SafeHtml;
    explanation: SafeHtml;
    renderedSubElements: RenderedQuizQuestionMarkDownElement[] = [];
}

export abstract class QuizQuestion implements BaseEntity, TextHintExplanationInterface {
    public id: number;
    public title: string;
    public text: string | null;
    public hint: string | null;
    public explanation: string | null;
    public score: number;
    public scoringType: ScoringType;
    public randomizeOrder = true; // default value
    public invalid = false; // default value
    public quizQuestionStatistic: QuizQuestionStatistic;
    public exercise: Exercise;
    public exportQuiz = false; // default value
    public type: QuizQuestionType;

    protected constructor(type: QuizQuestionType) {
        this.type = type;
    }
}
