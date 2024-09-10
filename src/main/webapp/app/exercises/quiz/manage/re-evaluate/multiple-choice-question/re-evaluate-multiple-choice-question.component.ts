import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { cloneDeep } from 'lodash-es';
import { generateExerciseHintExplanation, parseExerciseHintExplanation } from 'app/shared/util/markdown.util';
import { faAngleDown, faAngleRight, faArrowsAltV, faChevronDown, faChevronUp, faTrash, faUndo } from '@fortawesome/free-solid-svg-icons';
import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { MonacoCorrectMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/monaco-correct-multiple-choice-answer.action';
import { MonacoWrongMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/monaco-wrong-multiple-choice-answer.action';

@Component({
    selector: 'jhi-re-evaluate-multiple-choice-question',
    templateUrl: './re-evaluate-multiple-choice-question.component.html',
    styleUrls: ['./re-evaluate-multiple-choice-question.component.scss', '../../../shared/quiz.scss'],
})
export class ReEvaluateMultipleChoiceQuestionComponent implements OnInit {
    @Input() question: MultipleChoiceQuestion;
    @Input() questionIndex: number;

    @Output() questionDeleted = new EventEmitter<object>();
    @Output() questionUpdated = new EventEmitter<object>();
    @Output() questionMoveUp = new EventEmitter<object>();
    @Output() questionMoveDown = new EventEmitter<object>();

    markdownMap: Map<number, string>;
    questionText: string;

    // Create Backup Question for resets
    @Input() backupQuestion: MultipleChoiceQuestion;

    isQuestionCollapsed: boolean;

    // Icons
    faTrash = faTrash;
    faUndo = faUndo;
    faChevronUp = faChevronUp;
    faChevronDown = faChevronDown;
    faArrowsAltV = faArrowsAltV;
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    ngOnInit(): void {
        this.markdownMap = new Map<number, string>();
        for (const answer of this.question.answerOptions!) {
            this.markdownMap.set(
                answer.id!,
                (answer.isCorrect ? MonacoCorrectMultipleChoiceAnswerAction.IDENTIFIER : MonacoWrongMultipleChoiceAnswerAction.IDENTIFIER) +
                    ' ' +
                    generateExerciseHintExplanation(answer),
            );
        }
        this.questionText = this.getQuestionText(this.question);
    }

    /**
     * generate the question using the markdown service
     * @param {MultipleChoiceQuestion} question
     * @return string
     */
    getQuestionText(question: MultipleChoiceQuestion): string {
        const questionToSet = { text: question.text, hint: question.hint, explanation: question.explanation };
        return generateExerciseHintExplanation(questionToSet);
    }

    /**
     * parse the new question text, hint and explanation using the markdown service
     * @param {string} text
     */
    onQuestionChange(text: string): void {
        parseExerciseHintExplanation(text, this.question);
        this.questionUpdated.emit();
    }

    /**
     * parse the new text, hint and explanation using the markdown service and assign it to the passed answer
     * @param {string} text
     * @param {AnswerOption} answer
     */
    onAnswerOptionChange(text: string, answer: AnswerOption): void {
        const answerIndex = this.question.answerOptions!.findIndex((answerOption) => {
            return answerOption.id === answer.id;
        });
        this.parseAnswerMarkdown(text, this.question.answerOptions![answerIndex!]);
        this.questionUpdated.emit();
    }

    /**
     * Parse the answer Markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text starts with [correct] or [wrong]
     *    => Answer options are marked as isCorrect depending on [wrong] or [correct]
     * 2. The answer text is parsed with ArtemisMarkdown
     *
     * @param text {string} the Markdown text to parse
     * @param answer {AnswerOption} the answer, where to save the result
     */
    parseAnswerMarkdown(text: string, answer: AnswerOption) {
        const answerParts = ReEvaluateMultipleChoiceQuestionComponent.splitByCorrectIncorrectTag(text);
        // Find the box (text in-between the parts)
        const answerOptionText = answerParts[1];
        const startOfThisPart = text.indexOf(answerOptionText);
        const box = text.substring(0, startOfThisPart);
        // Check if box says this answer option is correct or not
        if (box === MonacoCorrectMultipleChoiceAnswerAction.IDENTIFIER) {
            answer.isCorrect = true;
        } else if (box === MonacoWrongMultipleChoiceAnswerAction.IDENTIFIER) {
            answer.isCorrect = false;
        } else {
            answer.isCorrect = undefined;
        }
        parseExerciseHintExplanation(answerOptionText, answer);
    }

    /**
     * Split text by [correct] and [wrong]
     * @param text
     */
    private static splitByCorrectIncorrectTag(text: string): string[] {
        const stringForSplit =
            escapeStringForUseInRegex(`${MonacoCorrectMultipleChoiceAnswerAction.IDENTIFIER}`) +
            '|' +
            escapeStringForUseInRegex(`${MonacoWrongMultipleChoiceAnswerAction.IDENTIFIER}`);
        const splitRegExp = new RegExp(stringForSplit, 'g');
        return text.split(splitRegExp);
    }

    /**
     * Delete this question from the quiz
     */
    delete() {
        this.questionDeleted.emit();
    }

    /**
     * Move this question one position up
     */
    moveUp() {
        this.questionMoveUp.emit();
    }

    /**
     * Move this question one position down
     */
    moveDown() {
        this.questionMoveDown.emit();
    }

    /**
     * Resets the question title
     */
    resetQuestionTitle() {
        this.question.title = this.backupQuestion.title;
    }

    /**
     * Resets the question text
     */
    resetQuestionText() {
        this.question.text = this.backupQuestion.text;
        this.question.explanation = this.backupQuestion.explanation;
        this.question.hint = this.backupQuestion.hint;
    }

    /**
     * Resets the whole question
     */
    resetQuestion() {
        this.resetQuestionTitle();
        this.resetQuestionText();
        this.question.answerOptions = cloneDeep(this.backupQuestion.answerOptions);
    }

    /**
     * Resets the whole answer
     * @param answer {AnswerOption} the answer, which will be reset
     */
    resetAnswer(answer: AnswerOption) {
        // Find correct answer if they have another order
        const answerIndex = this.question.answerOptions!.findIndex((answerOption) => {
            return answerOption.id === answer.id;
        });
        // Find correct backup answer
        const backupAnswerIndex = this.backupQuestion.answerOptions!.findIndex((answerBackup) => {
            return answer.id === answerBackup.id;
        });
        // Overwrite current answerOption at given index with the backup
        this.question.answerOptions![answerIndex] = cloneDeep(this.backupQuestion.answerOptions![backupAnswerIndex]);
    }

    /**
     * Delete the answer
     * @param answer {AnswerOption} the Answer which should be deleted
     */
    deleteAnswer(answer: AnswerOption) {
        const index = this.question.answerOptions!.indexOf(answer);
        this.question.answerOptions!.splice(index, 1);
    }

    /**
     * Set the answer invalid
     * @param  answer {AnswerOption} the Answer which should be deleted
     */
    setAnswerInvalid(answer: AnswerOption) {
        const answerIndex = this.question.answerOptions!.indexOf(answer);
        this.question.answerOptions![answerIndex].invalid = true;
        this.questionUpdated.emit();
    }

    /**
     * Checks if the given answer is invalid
     * @param  answer {AnswerOption} the Answer which should be checked
     * @return {boolean} true if the answer is invalid
     */
    isAnswerInvalid(answer: AnswerOption) {
        return answer.invalid;
    }

    onReorderAnswerOptionDrop(event: CdkDragDrop<AnswerOption[]>) {
        moveItemInArray(this.question.answerOptions || [], event.previousIndex, event.currentIndex);
    }
}
