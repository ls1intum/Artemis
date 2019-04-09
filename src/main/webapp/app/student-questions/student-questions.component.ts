import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise';
import { StudentQuestion, StudentQuestionService } from 'app/entities/student-question';
import { AccountService, User } from 'app/core';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { StudentQuestionAnswer, StudentQuestionAnswerService } from 'app/entities/student-question-answer';
import { QuestionActionName, StudentAnswerAction, StudentQuestionAction } from 'app/student-questions/student-question-row.component';

@Component({
    selector: 'jhi-student-questions',
    templateUrl: './student-questions.component.html',
    styleUrls: ['student-questions.scss'],
})
export class StudentQuestionsComponent implements OnInit, OnDestroy {
    @Input() exercise: Exercise;
    studentQuestions: StudentQuestion[];
    isEditMode: boolean;
    isAnswerMode: boolean;
    studentQuestionText: string;
    questionAnswerText: string;
    selectedStudentQuestion: StudentQuestion;
    selectedStudentAnswer: StudentQuestionAnswer;
    currentUser: User;

    constructor(
        private accountService: AccountService,
        private studentQuestionService: StudentQuestionService,
        private studentQuestionAnswerService: StudentQuestionAnswerService,
    ) {}

    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
        });
        this.studentQuestionService.query({ exercise: this.exercise.id }).subscribe((res: HttpResponse<StudentQuestion[]>) => {
            this.studentQuestions = res.body;
        });
    }

    ngOnDestroy(): void {}

    interactQuestion(action: StudentQuestionAction) {
        switch (action.name) {
            case QuestionActionName.ANSWER:
                this.toggleAnswerMode(action.studentQuestion);
                break;
            case QuestionActionName.DELETE:
                this.deleteQuestion(action.studentQuestion);
                break;
            case QuestionActionName.EDIT:
                this.editQuestion(action.studentQuestion);
                break;
        }
    }

    interactAnswer(action: StudentAnswerAction) {
        switch (action.name) {
            case QuestionActionName.DELETE:
                this.deleteAnswer(action.studentAnswer);
                break;
            case QuestionActionName.EDIT:
                this.editAnswer(action.studentAnswer, action.studentQuestion);
                break;
        }
    }

    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.selectedStudentQuestion = null;
    }

    toggleAnswerMode(studentQuestion: StudentQuestion): void {
        this.isAnswerMode = !this.isAnswerMode;
        if (studentQuestion) {
            this.selectedStudentQuestion = studentQuestion;
        }
    }

    deleteQuestion(studentQuestion: StudentQuestion) {
        this.studentQuestionService.delete(studentQuestion.id).subscribe((res: HttpResponse<any>) => {
            this.studentQuestions = this.studentQuestions.filter(el => el.id !== studentQuestion.id);
        });
    }

    deleteAnswer(studentAnswer: StudentQuestionAnswer) {
        this.studentQuestionAnswerService.delete(studentAnswer.id).subscribe((res: HttpResponse<any>) => {
            this.studentQuestions = this.studentQuestions.map(question => {
                question.answers = question.answers.filter(el => el.id !== studentAnswer.id);
                return question;
            });
        });
    }

    editQuestion(studentQuestion: StudentQuestion) {
        this.selectedStudentQuestion = studentQuestion;
        this.studentQuestionText = studentQuestion.questionText;
        this.isEditMode = true;
    }

    editAnswer(studentAnswer: StudentQuestionAnswer, studentQuestion: StudentQuestion) {
        this.questionAnswerText = studentAnswer.answerText;
        if (studentQuestion) {
            this.selectedStudentQuestion = studentQuestion;
        }
        if (studentAnswer) {
            this.selectedStudentAnswer = studentAnswer;
        }
        this.isAnswerMode = true;
    }

    addAnswer(): void {
        const studentQuestionAnswer = new StudentQuestionAnswer();
        studentQuestionAnswer.answerText = this.questionAnswerText;
        studentQuestionAnswer.author = this.currentUser;
        studentQuestionAnswer.verified = true;
        studentQuestionAnswer.question = this.selectedStudentQuestion;
        studentQuestionAnswer.answerDate = moment();
        this.studentQuestionAnswerService.create(studentQuestionAnswer).subscribe((studentQuestionResponse: HttpResponse<StudentQuestionAnswer>) => {
            this.selectedStudentQuestion.answers.push(studentQuestionResponse.body);
            this.studentQuestionText = undefined;
            this.isAnswerMode = false;
        });
    }

    saveQuestion() {
        this.selectedStudentQuestion.questionText = this.studentQuestionText;
        this.studentQuestionService.update(this.selectedStudentQuestion).subscribe((studentQuestionResponse: HttpResponse<StudentQuestion>) => {
            this.studentQuestionText = undefined;
            this.isEditMode = false;
        });
    }

    saveAnswer() {
        this.selectedStudentAnswer.answerText = this.questionAnswerText;
        this.studentQuestionAnswerService.update(this.selectedStudentAnswer).subscribe((studentAnswerResponse: HttpResponse<StudentQuestionAnswer>) => {
            this.questionAnswerText = undefined;
            this.isAnswerMode = false;
        });
    }

    addQuestion(): void {
        const studentQuestion = new StudentQuestion();
        studentQuestion.questionText = this.studentQuestionText;
        studentQuestion.author = this.currentUser;
        studentQuestion.visibleForStudents = true;
        studentQuestion.exercise = this.exercise;
        studentQuestion.creationDate = moment();
        this.studentQuestionService.create(studentQuestion).subscribe((studentQuestionResponse: HttpResponse<StudentQuestion>) => {
            this.studentQuestions.push(studentQuestionResponse.body);
            this.studentQuestionText = undefined;
            this.isEditMode = false;
        });
    }
}
