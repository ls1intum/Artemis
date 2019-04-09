import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { StudentQuestion, StudentQuestionService } from 'app/entities/student-question';
import { User } from 'app/core';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer';

export interface StudentQuestionAction {
    name: QuestionActionName;
    studentQuestion: StudentQuestion;
}

export interface StudentAnswerAction {
    name: QuestionActionName;
    studentAnswer: StudentQuestionAnswer;
    studentQuestion: StudentQuestion;
}

export enum QuestionActionName {
    ANSWER,
    DELETE,
    EDIT,
}

@Component({
    selector: 'jhi-student-question-row',
    templateUrl: './student-question-row.component.html',
    styleUrls: ['student-questions.scss'],
})
export class StudentQuestionRowComponent implements OnInit, OnDestroy {
    // @Input() exercise: Exercise;
    @Input() studentQuestion: StudentQuestion;
    @Input() selectedStudentQuestion: StudentQuestion;
    @Input() user: User;
    @Output() interactQuestion = new EventEmitter<StudentQuestionAction>();
    @Output() interactAnswer = new EventEmitter<StudentAnswerAction>();
    isExpanded: boolean;
    isQuestionAuthor: boolean;
    shortQuestionText: string;

    constructor(private studentQuestionService: StudentQuestionService) {}

    ngOnInit(): void {
        this.shortQuestionText = this.studentQuestion.questionText.substr(0, 25);
        if (this.studentQuestion.questionText.length > 25) {
            this.shortQuestionText += '...';
        }
        if (this.selectedStudentQuestion) {
            this.isExpanded = this.studentQuestion.id === this.selectedStudentQuestion.id;
        }
        if (this.user) {
            this.isQuestionAuthor = this.studentQuestion.author.id === this.user.id;
        }
    }

    ngOnDestroy(): void {}

    initAnswer(): void {
        this.interactQuestion.emit({
            name: QuestionActionName.ANSWER,
            studentQuestion: this.studentQuestion,
        });
    }

    initDelete() {
        this.interactQuestion.emit({
            name: QuestionActionName.DELETE,
            studentQuestion: this.studentQuestion,
        });
    }

    initEdit() {
        this.interactQuestion.emit({
            name: QuestionActionName.EDIT,
            studentQuestion: this.studentQuestion,
        });
    }

    initDeleteAnswer(studentAnswer: StudentQuestionAnswer) {
        this.interactAnswer.emit({
            name: QuestionActionName.DELETE,
            studentAnswer,
            studentQuestion: null,
        });
    }

    initEditAnswer(studentAnswer: StudentQuestionAnswer) {
        this.interactAnswer.emit({
            name: QuestionActionName.EDIT,
            studentAnswer,
            studentQuestion: this.studentQuestion,
        });
    }
}
