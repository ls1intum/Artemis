import { BaseEntity } from 'app/shared/model/base-entity';
import { SafeHtml } from '@angular/platform-browser';
import { QuizQuestionStatistic } from 'app/quiz/shared/entities/quiz-question-statistic.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { CanBecomeInvalid, DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { generate } from 'app/quiz/manage/util/temp-id';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';

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
    public exerciseId?: number;
    public isHighlighted? = false;

    protected constructor(type: QuizQuestionType) {
        this.type = type;
    }
}

export function resetQuizQuestionForImport(question: QuizQuestion): void {
    question.id = undefined;
    if (question.type === QuizQuestionType.DRAG_AND_DROP) {
        const dragAndDropQuestion = question as DragAndDropQuestion;
        const idToDragItemMap = new Map<number, DragItem>();
        const idToDropLocationMap = new Map<number, DropLocation>();
        for (const dragItem of dragAndDropQuestion.dragItems || []) {
            const oldId = dragItem.id;
            if (oldId !== undefined) {
                dragItem.id = undefined;
                dragItem.tempID = generate();
                idToDragItemMap.set(oldId, dragItem);
            }
        }
        for (const dropLocation of dragAndDropQuestion.dropLocations || []) {
            const oldId = dropLocation.id;
            if (oldId !== undefined) {
                dropLocation.id = undefined;
                dropLocation.tempID = generate();
                idToDropLocationMap.set(oldId, dropLocation);
            }
        }
        for (const mapping of dragAndDropQuestion.correctMappings || []) {
            const dragItem = mapping.dragItem;
            const dropLocation = mapping.dropLocation;
            if (dragItem && dragItem.id !== undefined) {
                mapping.dragItem = idToDragItemMap.get(dragItem.id);
            }
            if (dropLocation && dropLocation.id !== undefined) {
                mapping.dropLocation = idToDropLocationMap.get(dropLocation.id);
            }
        }
    } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
        const shortAnswerQuestion = question as ShortAnswerQuestion;
        const idToSolutionMap = new Map<number, ShortAnswerSolution>();
        const idToSpotMap = new Map<number, ShortAnswerSpot>();
        for (const solution of shortAnswerQuestion.solutions || []) {
            const oldId = solution.id;
            if (oldId !== undefined) {
                solution.id = undefined;
                solution.tempID = generate();
                idToSolutionMap.set(oldId, solution);
            }
        }
        for (const spot of shortAnswerQuestion.spots || []) {
            const oldId = spot.id;
            if (oldId !== undefined) {
                spot.id = undefined;
                spot.tempID = generate();
                idToSpotMap.set(oldId, spot);
            }
        }
        for (const mapping of shortAnswerQuestion.correctMappings || []) {
            const solution = mapping.solution;
            const spot = mapping.spot;
            if (solution && solution.id !== undefined) {
                mapping.solution = idToSolutionMap.get(solution.id);
            }
            if (spot && spot.id !== undefined) {
                mapping.spot = idToSpotMap.get(spot.id);
            }
        }
    }
}
