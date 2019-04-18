import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise';
import { StudentQuestion, StudentQuestionService } from 'app/entities/student-question';
import { AccountService, User } from 'app/core';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { QuestionActionName, StudentQuestionAction } from 'app/student-questions/student-question-row.component';
import { Lecture } from 'app/entities/lecture';

@Component({
    selector: 'jhi-student-questions',
    templateUrl: './student-questions.component.html',
    styleUrls: ['./student-questions.scss'],
})
export class StudentQuestionsComponent implements OnInit, OnDestroy {
    @Input() exercise: Exercise;
    @Input() lecture: Lecture;
    studentQuestions: StudentQuestion[];
    isEditMode: boolean;
    studentQuestionText: string;
    selectedStudentQuestion: StudentQuestion;
    currentUser: User;
    isAtLeastTutorInCourse: boolean;

    constructor(private accountService: AccountService, private studentQuestionService: StudentQuestionService) {}

    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
        });
        if (this.exercise) {
            this.studentQuestionService.query({ exercise: this.exercise.id }).subscribe((res: HttpResponse<StudentQuestion[]>) => {
                this.studentQuestions = res.body;
            });
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.exercise.course);
        } else {
            this.studentQuestionService.query({ lecture: this.lecture.id }).subscribe((res: HttpResponse<StudentQuestion[]>) => {
                this.studentQuestions = res.body;
            });
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.lecture.course);
        }
    }

    ngOnDestroy(): void {}

    interactQuestion(action: StudentQuestionAction) {
        switch (action.name) {
            case QuestionActionName.DELETE:
                this.deleteQuestionFromList(action.studentQuestion);
                break;
        }
    }

    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.selectedStudentQuestion = null;
    }

    deleteQuestionFromList(studentQuestion: StudentQuestion) {
        this.studentQuestions = this.studentQuestions.filter(el => el.id !== studentQuestion.id);
    }

    addQuestion(): void {
        const studentQuestion = new StudentQuestion();
        studentQuestion.questionText = this.studentQuestionText;
        studentQuestion.author = this.currentUser;
        studentQuestion.visibleForStudents = true;
        if (this.exercise) {
            studentQuestion.exercise = this.exercise;
        } else {
            studentQuestion.lecture = this.lecture;
        }
        studentQuestion.creationDate = moment();
        this.studentQuestionService.create(studentQuestion).subscribe((studentQuestionResponse: HttpResponse<StudentQuestion>) => {
            this.studentQuestions.push(studentQuestionResponse.body);
            this.studentQuestionText = undefined;
            this.isEditMode = false;
        });
    }
}
