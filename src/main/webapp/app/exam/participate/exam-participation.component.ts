import { Component, OnInit, OnDestroy, ViewChildren, QueryList, HostListener } from '@angular/core';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ActivatedRoute } from '@angular/router';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { Submission } from 'app/entities/submission.model';
import { Exam } from 'app/entities/exam.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { BehaviorSubject, Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { InitializationState } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/alert/alert.service';
import { Subject } from 'rxjs';
import { throttleTime } from 'rxjs/operators';
import * as moment from 'moment';
import { Moment } from 'moment';

type GenerateParticipationStatus = 'generating' | 'failed' | 'success';

@Component({
    selector: 'jhi-exam-participation',
    templateUrl: './exam-participation.component.html',
    styleUrls: ['./exam-participation.scss'],
})
export class ExamParticipationComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChildren(ExamSubmissionComponent)
    currentSubmissionComponents: QueryList<ExamSubmissionComponent>;

    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;

    courseId: number;
    examId: number;

    // determines if component was once drawn visited
    submissionComponentVisited: boolean[];

    // needed, because studentExam is downloaded only when exam is started
    exam: Exam;
    examTitle = '';
    studentExam: StudentExam;

    individualStudentEndDate: Moment;

    activeExercise: Exercise;
    unsavedChanges = false;
    disconnected = false;

    isProgrammingExercise() {
        return this.activeExercise.type === ExerciseType.PROGRAMMING;
    }

    isProgrammingExerciseWithCodeEditor(): boolean {
        return this.isProgrammingExercise() && (this.activeExercise as ProgrammingExercise).allowOnlineEditor;
    }

    isProgrammingExerciseWithOfflineIDE(): boolean {
        return this.isProgrammingExercise() && (this.activeExercise as ProgrammingExercise).allowOfflineIde;
    }

    examConfirmed = false;

    /**
     * Websocket channels
     */
    onConnected: () => void;
    onDisconnected: () => void;

    // autoTimerInterval in seconds
    autoSaveTimer = 0;
    autoSaveInterval: number;

    private synchronizationAlert$ = new Subject();

    loadingExam: boolean;

    generateParticipationStatus: BehaviorSubject<GenerateParticipationStatus> = new BehaviorSubject('success');

    constructor(
        private courseCalculationService: CourseScoreCalculationService,
        private jhiWebsocketService: JhiWebsocketService,
        private route: ActivatedRoute,
        private examParticipationService: ExamParticipationService,
        private modelingSubmissionService: ModelingSubmissionService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private textSubmissionService: TextSubmissionService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private serverDateService: ArtemisServerDateService,
        private translateService: TranslateService,
        private alertService: AlertService,
        private courseExerciseService: CourseExerciseService,
    ) {
        // show only one synchronization error every 5s
        this.synchronizationAlert$.pipe(throttleTime(5000)).subscribe(() => this.alertService.error('artemisApp.examParticipation.saveSubmissionError'));
    }

    /**
     * initializes courseId and course
     */
    ngOnInit(): void {
        this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
            this.examId = parseInt(params['examId'], 10);
            if (!!window.history.state.exam) {
                this.examTitle = window.history.state.exam?.title;
            }
            this.loadingExam = true;
            this.examParticipationService.loadExam(this.courseId, this.examId).subscribe(
                (exam) => {
                    this.exam = exam;
                    this.individualStudentEndDate = exam.endDate ? exam.endDate : this.serverDateService.now();
                    if (this.isOver()) {
                        this.examParticipationService.loadStudentExam(this.exam.course.id, this.exam.id).subscribe((studentExam: StudentExam) => {
                            this.studentExam = studentExam;
                        });
                    }
                    this.loadingExam = false;
                },
                // if error occurs
                () => (this.loadingExam = false),
            );
        });
        this.initLiveMode();
    }

    canDeactivate() {
        // TODO: also handle the case when the student wants to finish the exam early
        return this.isOver();
    }

    get canDeactivateWarning() {
        return this.translateService.instant('artemisApp.examParticipation.pendingChanges');
    }

    // displays the alert for confirming leaving the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any): void {
        if (!this.isOver()) {
            $event.returnValue = this.canDeactivateWarning;
        }
    }

    get activeExerciseIndex(): number {
        if (!this.activeExercise) {
            return 0;
        }
        return this.studentExam.exercises.findIndex((examExercise) => examExercise.id === this.activeExercise.id);
    }

    get activeSubmissionComponent(): ExamSubmissionComponent | undefined {
        return this.currentSubmissionComponents.find((submissionComponent, index) => index === this.activeExerciseIndex);
    }

    /**
     * exam start text confirmed and name entered, start button clicked and exam avtive
     */
    examStarted(studentExam: StudentExam) {
        if (studentExam) {
            // init studentExam
            this.studentExam = studentExam;
            // set endDate with workingTime
            this.individualStudentEndDate = this.exam.startDate ? moment(this.exam.startDate).add(studentExam.workingTime, 'seconds') : this.individualStudentEndDate;
            // initializes array which manages submission component initialization
            this.submissionComponentVisited = new Array(studentExam.exercises.length).fill(false);
            // TODO: move to exam-participation.service after studentExam was retrieved
            // initialize all submissions as synced
            this.studentExam.exercises.forEach((exercise) => {
                // We do not support hints at the moment. Setting an empty array here disables the hint requests
                exercise.exerciseHints = [];
                exercise.studentParticipations.forEach((participation) => {
                    if (participation.submissions && participation.submissions.length > 0) {
                        participation.submissions.forEach((submission) => {
                            submission.isSynced = true;
                        });
                    }
                });
            });
            const initialExercise = this.studentExam.exercises[0];
            this.initializeExercise(initialExercise);
        }
        this.examConfirmed = true;
        this.startAutoSaveTimer();
    }

    /**
     * checks if there is a participation for the given exercise and if it was initialized properly
     * @param exercise to check
     * @returns true if valid, false otherwise
     */
    private isExerciseParticipationValid(exercise: Exercise): boolean {
        // check if there is at least one participation with state === Initialized
        return (
            exercise.studentParticipations &&
            exercise.studentParticipations.length !== 0 &&
            exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED
        );
    }

    /**
     * start AutoSaveTimer
     */
    public startAutoSaveTimer(): void {
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= 30 && !this.isOver()) {
                this.triggerSave(false);
            }
        }, 1000);
    }

    examEnded() {
        this.triggerSave(true);
        window.clearInterval(this.autoSaveInterval);
    }

    /**
     * check if exam is over
     */
    isOver(): boolean {
        return this.individualStudentEndDate && this.individualStudentEndDate.isBefore(this.serverDateService.now());
    }

    /**
     * check if exam is visible
     */
    isVisible(): boolean {
        if (!this.exam) {
            return false;
        }
        return this.exam.visibleDate ? this.exam.visibleDate.isBefore(this.serverDateService.now()) : false;
    }

    /**
     * check if exam has started
     */
    isActive(): boolean {
        if (!this.exam) {
            return false;
        }
        return this.exam.startDate ? this.exam.startDate.isBefore(this.serverDateService.now()) : false;
    }

    ngOnDestroy(): void {
        window.clearInterval(this.autoSaveInterval);
    }

    initLiveMode() {
        // listen to connect / disconnect events
        this.onConnected = () => {
            this.disconnected = false;
        };
        this.jhiWebsocketService.bind('connect', () => {
            this.onConnected();
        });
        this.onDisconnected = () => {
            this.disconnected = true;
        };
        this.jhiWebsocketService.bind('disconnect', () => {
            this.onDisconnected();
        });
    }

    /**
     * update the current exercise from the navigation
     * @param exerciseChange
     */
    onExerciseChange(exerciseChange: { exercise: Exercise; force: boolean }): void {
        this.triggerSave(exerciseChange.force);
        this.initializeExercise(exerciseChange.exercise);
    }

    /**
     * sets active exercise and checks if participation is valid for exercise
     * if not -> initialize participation and in case of programming exercises subscribe to latestSubmissions
     * @param exercise to initialize
     */
    private initializeExercise(exercise: Exercise) {
        this.activeExercise = exercise;
        // if we do not have a valid participation for the exercise -> initialize it
        if (!this.isExerciseParticipationValid(exercise)) {
            // TODO: after getting online subscribe is not executed, might be a problem of the Observable in crateParticipationForExercise
            this.createParticipationForExercise(exercise).subscribe((participation) => {
                if (participation !== null) {
                    // for programming exercises -> wait for latest submission before showing exercise
                    if (exercise.type === ExerciseType.PROGRAMMING) {
                        this.programmingSubmissionService.getLatestPendingSubmissionByParticipationId(participation.id, exercise.id, true).subscribe((programmingSubmissionObj) => {
                            if (programmingSubmissionObj.submission) {
                                participation.submissions = [programmingSubmissionObj.submission];
                            }
                            this.submissionComponentVisited[this.activeExerciseIndex] = true;
                            if (this.activeSubmissionComponent) {
                                this.activeSubmissionComponent.onActivate();
                            }
                        });
                    } else {
                        this.submissionComponentVisited[this.activeExerciseIndex] = true;
                        if (this.activeSubmissionComponent) {
                            this.activeSubmissionComponent.onActivate();
                        }
                    }
                }
            });
        } else {
            this.submissionComponentVisited[this.activeExerciseIndex] = true;
            if (this.activeSubmissionComponent) {
                this.activeSubmissionComponent.onActivate();
            }
        }
    }

    /**
     * creates a participation for the exercise, if it did not exist already
     * @param exercise
     */
    createParticipationForExercise(exercise: Exercise): Observable<StudentParticipation | null> {
        this.generateParticipationStatus.next('generating');
        return this.courseExerciseService.startExercise(this.exam.course.id, exercise.id).pipe(
            map((createdParticipation: StudentParticipation) => {
                // if the same participations is not yet present in the exercise -> add it
                if (exercise.studentParticipations.findIndex((existingParticipation) => existingParticipation.id === createdParticipation.id) < 0) {
                    // remove because of circular dependency when converting to JSON
                    delete createdParticipation.exercise;
                    exercise.studentParticipations.push(createdParticipation);
                    if (createdParticipation.submissions && createdParticipation.submissions.length > 0) {
                        createdParticipation.submissions[0].isSynced = true;
                    }
                }
                this.generateParticipationStatus.next('success');
                return createdParticipation;
            }),
            catchError(() => {
                this.generateParticipationStatus.next('failed');
                return Observable.of(null);
            }),
        );
    }

    /**
     * We support 4 different cases here:
     * 1) Navigate between two exercises
     * 2) Click on Save & Continue
     * 3) The 30s timer was triggered
     * 4) exam is about to end (<1s left)
     *      --> in this case, we can even save all submissions with isSynced = true
     *
     * @param force is set to true, when the current exercise should be saved (even if there are no changes)
     */
    triggerSave(force: boolean) {
        // before the request, we would mark the submission as isSynced = true
        // right after the response - in case it was successful - we mark the submission as isSynced = false
        this.autoSaveTimer = 0;

        if ((this.activeSubmissionComponent && force) || this.activeSubmissionComponent?.hasUnsavedChanges()) {
            // this will lead to a save below, because isSynced will be set to false
            this.activeSubmissionComponent.updateSubmissionFromView();
        }

        // goes through all exercises and checks if there are unsynched submissions
        const submissionsToSync: { exercise: Exercise; submission: Submission }[] = [];
        this.studentExam.exercises.forEach((exercise: Exercise) => {
            exercise.studentParticipations.forEach((participation) => {
                if (participation.submissions) {
                    participation.submissions
                        .filter((submission) => !submission.isSynced)
                        .forEach((unsynchedSubmission) => {
                            submissionsToSync.push({ exercise, submission: unsynchedSubmission });
                        });
                }
            });
        });

        // if no connection available -> don't try to sync
        if (force || !this.disconnected) {
            submissionsToSync.forEach((submissionToSync: { exercise: Exercise; submission: Submission }) => {
                switch (submissionToSync.exercise.type) {
                    case ExerciseType.TEXT:
                        this.textSubmissionService.update(submissionToSync.submission as TextSubmission, submissionToSync.exercise.id).subscribe(
                            () => (submissionToSync.submission.isSynced = true),
                            (error) => this.onSaveSubmissionError(error),
                        );
                        break;
                    case ExerciseType.FILE_UPLOAD:
                        // nothing to do
                        break;
                    case ExerciseType.MODELING:
                        this.modelingSubmissionService.update(submissionToSync.submission as ModelingSubmission, submissionToSync.exercise.id).subscribe(
                            () => (submissionToSync.submission.isSynced = true),
                            (error) => this.onSaveSubmissionError(error),
                        );
                        break;
                    case ExerciseType.PROGRAMMING:
                        // nothing to do
                        break;
                    case ExerciseType.QUIZ:
                        this.examParticipationService.updateQuizSubmission(submissionToSync.exercise.id, submissionToSync.submission as QuizSubmission).subscribe(
                            () => (submissionToSync.submission.isSynced = true),
                            (error) => this.onSaveSubmissionError(error),
                        );
                        break;
                }
            });
        }

        // overwrite studentExam in localStorage
        this.examParticipationService.saveStudentExamToLocalStorage(this.courseId, this.examId, this.studentExam);
    }

    private onSaveSubmissionError(error: string) {
        // show an only one error for 5s - see constructor
        this.synchronizationAlert$.next();
        console.error(error);
    }
}
