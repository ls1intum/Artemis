import { signal } from '@angular/core';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';

/** Selections displayed by course and exam quizzes. Exam views detach them from the saved submission. */
export class QuizAnswerSelection {
    readonly selectedAnswerOptions = signal(new Map<number, AnswerOption[]>());
    readonly dragAndDropMappings = signal(new Map<number, DragAndDropMapping[]>());
    readonly shortAnswerSubmittedTexts = signal(new Map<number, ShortAnswerSubmittedText[]>());

    readonly onSelectedAnswerOptionsChange = (questionId: number, options: AnswerOption[]) => {
        this.selectedAnswerOptions.update((map) => new Map(map).set(questionId, options));
    };
    readonly onDragAndDropMappingsChange = (questionId: number, mappings: DragAndDropMapping[]) => {
        this.dragAndDropMappings.update((map) => new Map(map).set(questionId, mappings));
    };
    readonly onShortAnswerSubmittedTextsChange = (questionId: number, texts: ShortAnswerSubmittedText[]) => {
        this.shortAnswerSubmittedTexts.update((map) => new Map(map).set(questionId, texts));
    };

    reset(questions: QuizQuestion[] | undefined, submittedAnswers?: SubmittedAnswer[], cloneAnswers = false): void {
        const options = new Map<number, AnswerOption[]>();
        const mappings = new Map<number, DragAndDropMapping[]>();
        const texts = new Map<number, ShortAnswerSubmittedText[]>();
        for (const question of questions ?? []) {
            const answer = submittedAnswers?.find((submittedAnswer) => submittedAnswer.quizQuestion?.id === question.id);
            switch (question.type) {
                case QuizQuestionType.MULTIPLE_CHOICE:
                    options.set(question.id!, (answer as MultipleChoiceSubmittedAnswer)?.selectedOptions ?? []);
                    break;
                case QuizQuestionType.DRAG_AND_DROP:
                    mappings.set(question.id!, (answer as DragAndDropSubmittedAnswer)?.mappings ?? []);
                    break;
                case QuizQuestionType.SHORT_ANSWER:
                    texts.set(question.id!, (answer as ShortAnswerSubmittedAnswer)?.submittedTexts ?? []);
                    break;
                default:
                    captureException('Unknown question type: ' + question.type);
            }
        }
        this.selectedAnswerOptions.set(cloneAnswers ? deepClone(options) : options);
        this.dragAndDropMappings.set(cloneAnswers ? deepClone(mappings) : mappings);
        this.shortAnswerSubmittedTexts.set(cloneAnswers ? deepClone(texts) : texts);
    }
}
