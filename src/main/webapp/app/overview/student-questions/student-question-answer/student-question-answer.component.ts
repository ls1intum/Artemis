import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestionAnswerService } from 'app/overview/student-questions/student-question-answer/student-question-answer.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';

export interface StudentQuestionAnswerAction {
    name: QuestionAnswerActionName;
    studentQuestionAnswer: StudentQuestionAnswer;
}

export enum QuestionAnswerActionName {
    DELETE,
    ADD,
    APPROVE,
}

@Component({
    selector: 'jhi-student-question-answer',
    templateUrl: './student-question-answer.component.html',
    styleUrls: ['./../student-questions.scss'],
})
export class StudentQuestionAnswerComponent implements OnInit {
    @Input() studentQuestionAnswer: StudentQuestionAnswer;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactAnswer = new EventEmitter<StudentQuestionAnswerAction>();
    editText: string | null;
    isEditMode: boolean;
    EditorMode = EditorMode;

    constructor(private studentQuestionAnswerService: StudentQuestionAnswerService) {}

    /**
     * Sets the text of the answer as the editor text
     */
    ngOnInit(): void {
        this.editText = this.studentQuestionAnswer.answerText;
    }

    /**
     * Takes a studentQuestionAnswer and determines if the user is the author of it
     * @param {StudentQuestionAnswer} studentQuestionAnswer
     * @returns {boolean}
     */
    isAuthorOfAnswer(studentQuestionAnswer: StudentQuestionAnswer): boolean {
        if (this.user) {
            return studentQuestionAnswer.author.id === this.user.id;
        } else {
            return false;
        }
    }

    /**
     * Deletes this studentQuestionAnswer
     */
    deleteAnswer(): void {
        this.studentQuestionAnswerService.delete(this.studentQuestionAnswer.id).subscribe(() => {
            this.interactAnswer.emit({
                name: QuestionAnswerActionName.DELETE,
                studentQuestionAnswer: this.studentQuestionAnswer,
            });
        });
    }

    /**
     * Updates the text of the selected studentAnswer
     */
    saveAnswer(): void {
        this.studentQuestionAnswer.answerText = this.editText;
        this.studentQuestionAnswerService.update(this.studentQuestionAnswer).subscribe(() => {
            this.isEditMode = false;
        });
    }

    /**
     * Toggles the tutorApproved field for this studentQuestionAnswer
     */
    toggleAnswerTutorApproved(): void {
        this.studentQuestionAnswer.tutorApproved = !this.studentQuestionAnswer.tutorApproved;
        this.studentQuestionAnswerService.update(this.studentQuestionAnswer).subscribe(() => {
            this.interactAnswer.emit({
                name: QuestionAnswerActionName.APPROVE,
                studentQuestionAnswer: this.studentQuestionAnswer,
            });
        });
    }

    /**
     * toggles the edit Mode
     * set the editor text to the answer text
     */
    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.editText = this.studentQuestionAnswer.answerText;
    }
}
