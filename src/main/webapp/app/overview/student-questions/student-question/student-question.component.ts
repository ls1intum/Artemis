import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';

export interface StudentQuestionAction {
    name: QuestionActionName;
    studentQuestion: StudentQuestion;
}

export enum QuestionActionName {
    DELETE,
    EXPAND,
}

@Component({
    selector: 'jhi-student-question',
    templateUrl: './student-question.component.html',
    styleUrls: ['./../student-questions.scss'],
})
export class StudentQuestionComponent implements OnInit {
    @Input() studentQuestion: StudentQuestion;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactQuestion = new EventEmitter<StudentQuestionAction>();
    isQuestionAuthor = false;
    editText: string | null;
    isEditMode: boolean;
    EditorMode = EditorMode;

    constructor(private studentQuestionService: StudentQuestionService) {}

    /**
     * checks if the user is the author of the question
     * sets the question text as the editor text
     */
    ngOnInit(): void {
        if (this.user) {
            this.isQuestionAuthor = this.studentQuestion.author.id === this.user.id;
        }
        this.editText = this.studentQuestion.questionText;
    }

    /**
     * pass the studentQuestion to the row to delete
     */
    deleteQuestion(): void {
        this.interactQuestion.emit({
            name: QuestionActionName.DELETE,
            studentQuestion: this.studentQuestion,
        });
    }

    /**
     * Changes the question text
     */
    saveQuestion(): void {
        this.studentQuestion.questionText = this.editText;
        this.studentQuestionService.update(this.studentQuestion).subscribe(() => {
            this.isEditMode = false;
        });
    }

    /**
     * toggles the edit Mode
     * set the editor text to the question text
     */
    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.editText = this.studentQuestion.questionText;
    }
}
