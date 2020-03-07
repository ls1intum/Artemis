import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { QuestionActionName, StudentQuestionAction } from 'app/overview/student-questions/student-question-row.component';
import { Lecture } from 'app/entities/lecture.model';
import { AccountService } from 'app/core/auth/account.service';
import { StudentQuestion } from 'app/entities/student-question.model';
import { StudentQuestionService } from 'app/overview/student-questions/student-question.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

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
    studentQuestionText: string | null;
    selectedStudentQuestion: StudentQuestion | null;
    currentUser: User;
    isAtLeastTutorInCourse: boolean;

    constructor(private accountService: AccountService, private studentQuestionService: StudentQuestionService, private exerciseService: ExerciseService) {}

    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
        });
        if (this.exercise) {
            // in this case the student questions are preloaded
            this.studentQuestions = this.exercise.studentQuestions;
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.exercise.course!);
        } else {
            // in this case the student questions are preloaded
            this.studentQuestions = this.lecture.studentQuestions;
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
            studentQuestion.exercise = Object.assign({}, this.exerciseService.convertExerciseForServer(this.exercise), {});
        } else {
            studentQuestion.lecture = Object.assign({}, this.lecture, {});
            delete studentQuestion.lecture.attachments;
        }
        studentQuestion.creationDate = moment();
        this.studentQuestionService.create(studentQuestion).subscribe((studentQuestionResponse: HttpResponse<StudentQuestion>) => {
            this.studentQuestions.push(studentQuestionResponse.body!);
            this.studentQuestionText = null;
            this.isEditMode = false;
        });
    }
}
