import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise';
import { StudentQuestion, StudentQuestionService } from 'app/entities/student-question';
import { AccountService, User } from 'app/core';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-student-questions',
    templateUrl: './student-questions.component.html',
    styleUrls: ['student-questions.scss'],
})
export class StudentQuestionsComponent implements OnInit, OnDestroy {
    @Input() exercise: Exercise;
    studentQuestions: StudentQuestion[];
    isEditMode: boolean;
    isAddMode: boolean;
    studentQuestionText: string;
    questionAnswerText: string;
    selectedStudentQuestion: StudentQuestion;
    currentUser: User;

    constructor(private accountService: AccountService, private studentQuestionService: StudentQuestionService) {}

    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
        });
        this.studentQuestionService.query({ exercise: this.exercise.id }).subscribe((res: HttpResponse<StudentQuestion[]>) => {
            this.studentQuestions = res.body;
        });
    }

    ngOnDestroy(): void {}

    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.selectedStudentQuestion = null;
    }

    toggleAnswerMode(studentQuestion: StudentQuestion): void {
        this.isAddMode = !this.isAddMode;
        if (studentQuestion) {
            this.selectedStudentQuestion = studentQuestion;
        }
    }

    deleteQuestion(studentQuestion: StudentQuestion) {
        this.studentQuestionService.delete(studentQuestion.id).subscribe((res: HttpResponse<any>) => {
            this.studentQuestions = this.studentQuestions.filter(el => el.id !== studentQuestion.id);
        });
    }

    editQuestion(studentQuestion: StudentQuestion) {
        this.selectedStudentQuestion = studentQuestion;
        this.studentQuestionText = studentQuestion.questionText;
        this.isEditMode = true;
    }

    addAnswer(): void {}

    saveQuestion() {
        debugger;
        this.selectedStudentQuestion.questionText = this.studentQuestionText;
        this.studentQuestionService.update(this.selectedStudentQuestion).subscribe((studentQuestionResponse: HttpResponse<StudentQuestion>) => {
            this.studentQuestionText = undefined;
            this.isEditMode = false;
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
