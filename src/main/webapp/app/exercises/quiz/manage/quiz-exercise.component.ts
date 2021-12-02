import { Component, Input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizExercise, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faEye, faFileExport, faPlayCircle, faPlus, faSignal, faSort, faTable, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-quiz-exercise',
    templateUrl: './quiz-exercise.component.html',
})
export class QuizExerciseComponent extends ExerciseComponent {
    readonly ActionType = ActionType;
    readonly QuizStatus = QuizStatus;

    @Input() quizExercises: QuizExercise[] = [];
    filteredQuizExercises: QuizExercise[] = [];

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faSignal = faSignal;
    faFileExport = faFileExport;
    faPlayCircle = faPlayCircle;

    constructor(
        private quizExerciseService: QuizExerciseService,
        private accountService: AccountService,
        private alertService: AlertService,
        private sortService: SortService,
        public exerciseService: ExerciseService,
        courseService: CourseManagementService,
        translateService: TranslateService,
        eventManager: EventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
    }

    protected loadExercises(): void {
        this.quizExerciseService.findForCourse(this.courseId).subscribe(
            (res: HttpResponse<QuizExercise[]>) => {
                this.quizExercises = res.body!;
                // reconnect exercise with course
                this.quizExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
                    exercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(exercise.course);
                    exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
                });
                this.setQuizExercisesStatus();
                this.emitExerciseCount(this.quizExercises.length);
                this.applyFilter();
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    protected applyFilter(): void {
        this.filteredQuizExercises = this.quizExercises.filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredQuizExercises.length);
    }

    /**
     * Get the id of the quiz exercise
     * @param item the quiz exercise of which the id should be returned
     */
    trackId(index: number, item: QuizExercise) {
        return item.id!;
    }

    protected getChangeEventName(): string {
        return 'quizExerciseListModification';
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.headers.get('X-artemisApp-error')!);
    }

    /**
     * Checks if the quiz exercise is over
     * @param quizExercise The quiz exercise we want to know if it's over
     * @returns {boolean} true if the quiz exercise is over, false if not.
     */
    quizIsOver(quizExercise: QuizExercise) {
        if (quizExercise.isPlannedToStart) {
            const plannedEndMoment = dayjs(quizExercise.releaseDate!).add(quizExercise.duration!, 'seconds');
            return plannedEndMoment.isBefore(dayjs());
            // the quiz is over
        }
        // the quiz hasn't started yet
        return false;
    }

    /**
     * Convert seconds to full minutes
     * @param seconds {number} the number of seconds
     * @returns {number} the number of full minutes
     */
    fullMinutesForSeconds(seconds: number) {
        return Math.floor(seconds / 60);
    }

    /**
     * Set the quiz open for practice
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    openForPractice(quizExerciseId: number) {
        this.quizExerciseService.openForPractice(quizExerciseId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise(res.body!);
            },
            (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne(quizExerciseId);
            },
        );
    }

    /**
     * Set the quiz exercise status for all quiz exercises.
     */
    setQuizExercisesStatus() {
        this.quizExercises.forEach((quizExercise) => (quizExercise.status = this.quizExerciseService.getStatus(quizExercise)));
    }

    /**
     * Start the given quiz-exercise immediately
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    startQuiz(quizExerciseId: number) {
        this.quizExerciseService.start(quizExerciseId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise(res.body!);
            },
            (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne(quizExerciseId);
            },
        );
    }

    /**
     * Do not load all quizExercise if only one has changed
     *
     * @param quizExerciseId
     */
    private loadOne(quizExerciseId: number) {
        this.quizExerciseService.find(quizExerciseId).subscribe((res: HttpResponse<QuizExercise>) => {
            this.handleNewQuizExercise(res.body!);
        });
    }

    private handleNewQuizExercise(newQuizExercise: QuizExercise) {
        const index = this.quizExercises.findIndex((quizExercise) => quizExercise.id === newQuizExercise.id);
        newQuizExercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(newQuizExercise.course!);
        newQuizExercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(newQuizExercise.course!);
        newQuizExercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(newQuizExercise.course!);
        newQuizExercise.status = this.quizExerciseService.getStatus(newQuizExercise);
        if (index === -1) {
            this.quizExercises.push(newQuizExercise);
        } else {
            this.quizExercises[index] = newQuizExercise;
        }
    }

    /**
     * Exports questions for the given quiz exercise in json file
     * @param quizExerciseId The quiz exercise id we want to export
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    exportQuizById(quizExerciseId: number, exportAll: boolean) {
        this.quizExerciseService.find(quizExerciseId).subscribe((res: HttpResponse<QuizExercise>) => {
            const exercise = res.body!;
            this.quizExerciseService.exportQuiz(exercise.quizQuestions, exportAll, exercise.title);
        });
    }

    /**
     * Make the given quiz-exercise visible to students
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    showQuiz(quizExerciseId: number) {
        this.quizExerciseService.setVisible(quizExerciseId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                this.handleNewQuizExercise(res.body!);
            },
            (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne(quizExerciseId);
            },
        );
    }

    /**
     * Deletes quiz exercise
     * @param quizExerciseId id of the quiz exercise that will be deleted
     */
    deleteQuizExercise(quizExerciseId: number) {
        return this.quizExerciseService.delete(quizExerciseId).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'quizExerciseListModification',
                    content: 'Deleted an quizExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.headers.get('X-artemisApp-error')!),
        );
    }

    /**
     * Resets quiz exercise
     * @param quizExerciseId id of the quiz exercise that will be deleted
     */
    resetQuizExercise(quizExerciseId: number) {
        this.quizExerciseService.reset(quizExerciseId).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'quizExerciseListModification',
                    content: 'Reset an quizExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.headers.get('X-artemisApp-error')!),
        );
    }

    public sortRows() {
        this.sortService.sortByProperty(this.quizExercises, this.predicate, this.reverse);
        this.applyFilter();
    }
}
