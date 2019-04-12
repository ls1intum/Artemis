import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { StudentQuestion, StudentQuestionService } from 'app/entities/student-question';
import { User } from 'app/core';
import { StudentQuestionAnswer, StudentQuestionAnswerService } from 'app/entities/student-question-answer';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';

export interface StudentQuestionAction {
    name: QuestionActionName;
    studentQuestion: StudentQuestion;
}

export enum QuestionActionName {
    DELETE,
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
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactQuestion = new EventEmitter<StudentQuestionAction>();
    isExpanded = true;
    isAnswerMode: boolean;
    isEditMode: boolean;
    isQuestionAuthor: boolean;
    selectedQuestionAnswer: StudentQuestionAnswer;
    questionAnswerText: string;
    studentQuestionText: string;
    sortedQuestionAnswers: StudentQuestionAnswer[];

    constructor(private studentQuestionAnswerService: StudentQuestionAnswerService, private studentQuestionService: StudentQuestionService) {}

    ngOnInit(): void {
        if (this.user) {
            this.isQuestionAuthor = this.studentQuestion.author.id === this.user.id;
        }
        this.sortQuestionAnswers();
    }

    sortQuestionAnswers() {
        this.sortedQuestionAnswers = this.studentQuestion.answers.sort((a, b) => {
            const aValue = moment(a.answerDate).valueOf();
            const bValue = moment(b.answerDate).valueOf();

            return aValue - bValue;
        });
    }

    ngOnDestroy(): void {}

    toggleQuestionEditMode() {
        this.studentQuestionText = this.studentQuestion.questionText;
        this.isEditMode = !this.isEditMode;
    }

    toggleAnswerMode(questionAnswer: StudentQuestionAnswer): void {
        this.isAnswerMode = !this.isAnswerMode;
        this.questionAnswerText = questionAnswer ? questionAnswer.answerText : '';
        this.selectedQuestionAnswer = questionAnswer;
    }

    saveQuestion() {
        this.studentQuestion.questionText = this.studentQuestionText;
        this.studentQuestionService.update(this.studentQuestion).subscribe((studentQuestionResponse: HttpResponse<StudentQuestion>) => {
            this.studentQuestionText = undefined;
            this.isEditMode = false;
        });
    }

    deleteQuestion() {
        this.studentQuestionService.delete(this.studentQuestion.id).subscribe((res: HttpResponse<any>) => {
            this.interactQuestion.emit({
                name: QuestionActionName.DELETE,
                studentQuestion: this.studentQuestion,
            });
        });
    }

    addAnswer(): void {
        const studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.answerText = this.questionAnswerText;
        studentQuestionAnswer.author = this.user;
        studentQuestionAnswer.verified = true;
        studentQuestionAnswer.question = this.studentQuestion;
        studentQuestionAnswer.answerDate = moment();
        this.studentQuestionAnswerService.create(studentQuestionAnswer).subscribe((studentQuestionResponse: HttpResponse<StudentQuestionAnswer>) => {
            this.studentQuestion.answers.push(studentQuestionResponse.body);
            this.sortQuestionAnswers();
            this.questionAnswerText = undefined;
            this.isAnswerMode = false;
        });
    }

    saveAnswer() {
        this.selectedQuestionAnswer.answerText = this.questionAnswerText;
        this.studentQuestionAnswerService.update(this.selectedQuestionAnswer).subscribe((studentAnswerResponse: HttpResponse<StudentQuestionAnswer>) => {
            this.questionAnswerText = undefined;
            this.selectedQuestionAnswer = undefined;
            this.isAnswerMode = false;
        });
    }

    deleteAnswer(studentAnswer: StudentQuestionAnswer) {
        this.studentQuestionAnswerService.delete(studentAnswer.id).subscribe((res: HttpResponse<any>) => {
            this.studentQuestion.answers = this.studentQuestion.answers.filter(el => el.id !== studentAnswer.id);
            this.sortQuestionAnswers();
        });
    }
}
