import { BaseEntity } from 'app/shared/model/base-entity';
import { SafeHtml } from '@angular/platform-browser';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';

export enum ScoringType {
    ALL_OR_NOTHING = 'ALL_OR_NOTHING',
    PROPORTIONAL_WITH_PENALTY = 'PROPORTIONAL_WITH_PENALTY',
    PROPORTIONAL_WITHOUT_PENALTY = 'PROPORTIONAL_WITHOUT_PENALTY',
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Question.java
export enum QuizQuestionType {
    MULTIPLE_CHOICE = 'multiple-choice',
    DRAG_AND_DROP = 'drag-and-drop',
    SHORT_ANSWER = 'short-answer',
}

export interface ExerciseHintExplanationInterface {
    text?: string;
    hint?: string;
    explanation?: string;
}

export class RenderedQuizQuestionMarkDownElement {
    text: SafeHtml;
    hint: SafeHtml;
    explanation: SafeHtml;
    renderedSubElements: RenderedQuizQuestionMarkDownElement[] = [];
}

export abstract class QuizQuestion implements BaseEntity, CanBecomeInvalid, ExerciseHintExplanationInterface {
    public id?: number;
    public title?: string;
    public text?: string;
    public hint?: string;
    public explanation?: string;
    public points?: number;
    public scoringType?: ScoringType;
    public randomizeOrder = true; // default value
    public invalid = false; // default value
    public quizQuestionStatistic?: QuizQuestionStatistic;
    public exercise?: QuizExercise;
    public exportQuiz = false; // default value
    public type?: QuizQuestionType;

    protected constructor(type: QuizQuestionType) {
        this.type = type;
    }
}
