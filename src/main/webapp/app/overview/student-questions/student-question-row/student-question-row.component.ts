import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { StudentQuestion } from 'app/entities/student-question.model';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { StudentQuestionAnswerService } from 'app/overview/student-questions/student-question-answer/student-question-answer.service';
import { LocalStorageService } from 'ngx-webstorage';
import { QuestionAnswerActionName, StudentQuestionAnswerAction } from 'app/overview/student-questions/student-question-answer/student-question-answer.component';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { QuestionActionName, StudentQuestionAction } from 'app/overview/student-questions/student-question/student-question.component';

export interface StudentQuestionRowAction {
    name: QuestionRowActionName;
    studentQuestion: StudentQuestion;
}

export enum QuestionRowActionName {
    DELETE,
    VOTE_CHANGE,
}

@Component({
    selector: 'jhi-student-question-row',
    templateUrl: './student-question-row.component.html',
    styleUrls: ['./../student-questions.scss'],
})
export class StudentQuestionRowComponent implements OnInit {
    @Input() studentQuestion: StudentQuestion;
    @Input() selectedStudentQuestion: StudentQuestion;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactQuestionRow = new EventEmitter<StudentQuestionRowAction>();
    isExpanded = true;
    isAnswerMode: boolean;
    showOtherAnswers = false;
    questionAnswerText?: string;
    sortedQuestionAnswers: StudentQuestionAnswer[];
    approvedQuestionAnswers: StudentQuestionAnswer[];
    EditorMode = EditorMode;

    constructor(
        private studentQuestionAnswerService: StudentQuestionAnswerService,
        private studentQuestionService: StudentQuestionService,
        private localStorage: LocalStorageService,
    ) {}

    /**
     * sort answers when component is initialized
     */
    ngOnInit(): void {
        this.sortQuestionAnswers();
    }

    /**
     * interact with asnwer component
     * @param {StudentQuestionAnswerAction} action
     */
    interactAnswer(action: StudentQuestionAnswerAction) {
        switch (action.name) {
            case QuestionAnswerActionName.DELETE:
                this.deleteAnswerFromList(action.studentQuestionAnswer);
                break;
            case QuestionAnswerActionName.ADD:
                this.addAnswerToList(action.studentQuestionAnswer);
                break;
            case QuestionAnswerActionName.APPROVE:
                this.sortQuestionAnswers();
                break;
        }
    }

    /**
     * interact with question component
     * @param {StudentQuestionAction} action
     */
    interactQuestion(action: StudentQuestionAction): void {
        switch (action.name) {
            case QuestionActionName.DELETE:
                this.deleteQuestion();
                break;
            case QuestionActionName.EXPAND:
                this.isExpanded = !this.isExpanded;
                break;
            case QuestionActionName.VOTE_CHANGE:
                this.interactQuestionRow.emit({
                    name: QuestionRowActionName.VOTE_CHANGE,
                    studentQuestion: action.studentQuestion,
                });
                break;
        }
    }

    /**
     * sorts the answers of a question into approved and not approved and then by date
     */
    sortQuestionAnswers(): void {
        if (!this.studentQuestion.answers) {
            this.sortedQuestionAnswers = [];
            this.approvedQuestionAnswers = [];
            return;
        }
        this.approvedQuestionAnswers = this.studentQuestion.answers
            .filter((ans) => ans.tutorApproved)
            .sort((a, b) => {
                const aValue = moment(a.answerDate!).valueOf();
                const bValue = moment(b.answerDate!).valueOf();

                return aValue - bValue;
            });
        this.sortedQuestionAnswers = this.studentQuestion.answers
            .filter((ans) => !ans.tutorApproved)
            .sort((a, b) => {
                const aValue = moment(a.answerDate!).valueOf();
                const bValue = moment(b.answerDate!).valueOf();

                return aValue - bValue;
            });
    }

    /**
     * deletes the studentQuestion
     */
    deleteQuestion(): void {
        this.studentQuestionService.delete(this.studentQuestion.id!).subscribe(() => {
            this.localStorage.clear(`q${this.studentQuestion.id}u${this.user.id}`);
            this.interactQuestionRow.emit({
                name: QuestionRowActionName.DELETE,
                studentQuestion: this.studentQuestion,
            });
        });
    }

    /**
     * Creates a new studentAnswer
     */
    addAnswer(): void {
        const studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.answerText = this.questionAnswerText;
        studentQuestionAnswer.author = this.user;
        studentQuestionAnswer.verified = true;
        studentQuestionAnswer.question = this.studentQuestion;
        studentQuestionAnswer.tutorApproved = false;
        studentQuestionAnswer.answerDate = moment();
        this.studentQuestionAnswerService.create(studentQuestionAnswer).subscribe((studentQuestionResponse: HttpResponse<StudentQuestionAnswer>) => {
            if (!this.studentQuestion.answers) {
                this.studentQuestion.answers = [];
            }
            this.studentQuestion.answers.push(studentQuestionResponse.body!);
            this.sortQuestionAnswers();
            this.questionAnswerText = undefined;
            this.isAnswerMode = false;
        });
    }

    /**
     * Takes a studentAnswer and deletes it
     * @param   {studentQuestionAnswer} studentQuestionAnswer
     */
    deleteAnswerFromList(studentQuestionAnswer: StudentQuestionAnswer): void {
        this.studentQuestion.answers = this.studentQuestion.answers?.filter((el: StudentQuestionAnswer) => el.id !== studentQuestionAnswer.id);
        this.sortQuestionAnswers();
    }

    /**
     * Takes a studentAnswer and adds it to the others
     * @param   {StudentQuestionAnswer} studentQuestionAnswer
     */
    addAnswerToList(studentQuestionAnswer: StudentQuestionAnswer): void {
        this.studentQuestion.answers!.push(studentQuestionAnswer);
        this.sortQuestionAnswers();
    }
}
