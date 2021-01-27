import { AfterViewInit, Component, Input, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { QuestionRowActionName, StudentQuestionRowAction } from 'app/overview/student-questions/student-question-row/student-question-row.component';
import { Lecture } from 'app/entities/lecture.model';
import { AccountService } from 'app/core/auth/account.service';
import { StudentQuestion } from 'app/entities/student-question.model';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import interact from 'interactjs';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-student-questions',
    templateUrl: './student-questions.component.html',
    styleUrls: ['./student-questions.scss'],
})
export class StudentQuestionsComponent implements OnInit, AfterViewInit {
    @Input() exercise: Exercise;
    @Input() lecture: Lecture;

    studentQuestions: StudentQuestion[];
    isEditMode: boolean;
    collapsed = false;
    studentQuestionText?: string;
    selectedStudentQuestion?: StudentQuestion;
    currentUser: User;
    isAtLeastTutorInCourse: boolean;
    EditorMode = EditorMode;
    domainCommands = [new KatexCommand()];
    courseId: number;

    constructor(
        private route: ActivatedRoute,
        private accountService: AccountService,
        private studentQuestionService: StudentQuestionService,
        private exerciseService: ExerciseService,
    ) {}

    /**
     * get the current user and check if he is at least a tutor for this course
     */
    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
        });
        this.loadQuestions();
    }

    loadQuestions() {
        if (this.exercise) {
            // in this case the student questions are preloaded
            this.studentQuestions = StudentQuestionsComponent.sortStudentQuestionsByVote(this.exercise.studentQuestions!);
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.exercise.course!);
            this.courseId = this.exercise.course!.id!;
        } else if (this.lecture) {
            // in this case the student questions are preloaded
            this.studentQuestions = StudentQuestionsComponent.sortStudentQuestionsByVote(this.lecture.studentQuestions!);
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.lecture.course!);
            this.courseId = this.lecture.course!.id!;
        }
    }

    /**
     * Configures interact to make instructions expandable
     */
    ngAfterViewInit(): void {
        interact('.expanded-questions')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 300, height: 0 },
                        max: { width: 600, height: 4000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }

    /**
     * interact with actions send from studentQuestionRow
     * @param {StudentQuestionRowAction} action
     */
    interactQuestion(action: StudentQuestionRowAction) {
        switch (action.name) {
            case QuestionRowActionName.DELETE:
                this.deleteQuestionFromList(action.studentQuestion);
                break;
            case QuestionRowActionName.VOTE_CHANGE:
                this.updateQuestionAfterVoteChange(action.studentQuestion);
                break;
        }
    }

    /**
     * takes a studentQuestion and removes it from the list
     * @param {StudentQuestion} studentQuestion
     */
    deleteQuestionFromList(studentQuestion: StudentQuestion): void {
        this.studentQuestions = this.studentQuestions.filter((el) => el.id !== studentQuestion.id);
    }

    /**
     * create a new studentQuestion
     */
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
            delete studentQuestion.lecture.lectureUnits;
        }
        studentQuestion.creationDate = moment();
        this.studentQuestionService.create(this.courseId, studentQuestion).subscribe((studentQuestionResponse: HttpResponse<StudentQuestion>) => {
            this.studentQuestions.push(studentQuestionResponse.body!);
            this.studentQuestionText = undefined;
            this.isEditMode = false;
        });
    }

    private static sortStudentQuestionsByVote(studentQuestions: StudentQuestion[]): StudentQuestion[] {
        return studentQuestions.sort((a, b) => {
            return b.votes! - a.votes!;
        });
    }

    private updateQuestionAfterVoteChange(studentQuestion: StudentQuestion): void {
        const indexToUpdate = this.studentQuestions.findIndex((question) => {
            return question.id === studentQuestion.id;
        });
        this.studentQuestions[indexToUpdate] = studentQuestion;
        this.studentQuestions = StudentQuestionsComponent.sortStudentQuestionsByVote(this.studentQuestions);
    }
}
