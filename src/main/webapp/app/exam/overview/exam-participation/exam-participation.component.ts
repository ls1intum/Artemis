import { Component, HostListener, OnDestroy, OnInit, inject, signal, viewChildren } from '@angular/core';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { BehaviorSubject, Observable, Subject, Subscription, combineLatest, of, throwError } from 'rxjs';
import { catchError, distinctUntilChanged, filter, map, tap, throttleTime, timeout } from 'rxjs/operators';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Course } from 'app/course/shared/entities/course.model';
import { captureException } from '@sentry/angular';
import { HttpErrorResponse } from '@angular/common/http';
import { ExamPage } from 'app/exam/shared/entities/exam-page.model';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/foundation/constants/exercise-exam-constants';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { TestRunRibbonComponent } from '../../manage/test-runs/test-run-ribbon.component';
import { ExamParticipationCoverComponent } from '../exam-cover/exam-participation-cover.component';
import { AsyncPipe, NgClass } from '@angular/common';
import { ExamBarComponent } from '../exam-bar/exam-bar.component';
import { ExamNavigationSidebarComponent } from '../exam-navigation-sidebar/exam-navigation-sidebar.component';
import { QuizExamSubmissionComponent } from '../exercises/quiz/quiz-exam-submission.component';
import { FileUploadExamSubmissionComponent } from '../exercises/file-upload/file-upload-exam-submission.component';
import { TextExamSubmissionComponent } from '../exercises/text/text-exam-submission.component';
import { ModelingExamSubmissionComponent } from '../exercises/modeling/modeling-exam-submission.component';
import { ProgrammingExamSubmissionComponent } from '../exercises/programming/programming-exam-submission.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { JhiConnectionStatusComponent } from 'app/shared-ui/connection-status/connection-status.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { ExamResultSummaryComponent } from '../summary/exam-result-summary.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { isExamSummaryPublished } from 'app/exam/overview/exam.utils';
import { ExamExerciseOverviewPageComponent } from '../exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import {
    ExamLiveEventType,
    ExamParticipationLiveEventsService,
    ProblemStatementUpdateEvent,
    WorkingTimeUpdateEvent,
} from 'app/exam/overview/services/exam-participation-live-events.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { faCheckCircle, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { ExamPageComponent } from 'app/exam/overview/exercises/exam-page.component';
import { SidebarCardElement, SidebarData } from 'app/foundation/types/sidebar';
import { Message } from 'primeng/message';
import { ButtonDirective } from 'primeng/button';
import { deepClone, hydrate } from 'app/foundation/util/deep-clone.util';

type GenerateParticipationStatus = 'generating' | 'failed' | 'success';

@Component({
    selector: 'jhi-exam-participation',
    templateUrl: './exam-participation.component.html',
    styleUrls: ['./exam-participation.scss'],
    imports: [
        CdkScrollable,
        TestRunRibbonComponent,
        ExamParticipationCoverComponent,
        NgClass,
        ExamBarComponent,
        ExamNavigationSidebarComponent,
        QuizExamSubmissionComponent,
        FileUploadExamSubmissionComponent,
        TextExamSubmissionComponent,
        ModelingExamSubmissionComponent,
        ProgrammingExamSubmissionComponent,
        TranslateDirective,
        JhiConnectionStatusComponent,
        FaIconComponent,
        ExamResultSummaryComponent,
        RouterLink,
        AsyncPipe,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ExamExerciseOverviewPageComponent,
        CourseSidebarToggleButtonComponent,
        Message,
        ButtonDirective,
    ],
})
export class ExamParticipationComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    private websocketService = inject(WebsocketService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private examParticipationService = inject(ExamParticipationService);
    private modelingSubmissionService = inject(ModelingSubmissionService);
    private programmingSubmissionService = inject(ProgrammingSubmissionService);
    private textSubmissionService = inject(TextSubmissionService);
    private serverDateService = inject(ArtemisServerDateService);
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private courseExerciseService = inject(CourseExerciseService);
    private liveEventsService = inject(ExamParticipationLiveEventsService);
    private courseService = inject(CourseManagementService);
    private courseStorageService = inject(CourseStorageService);
    private examExerciseUpdateService = inject(ExamExerciseUpdateService);
    private examManagementService = inject(ExamManagementService);

    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faGraduationCap = faGraduationCap;

    readonly currentPageComponents = viewChildren(ExamSubmissionComponent);

    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILEUPLOAD = ExerciseType.FILE_UPLOAD;

    // needed for recalculation of exam content height
    readonly EXAM_HEIGHT_OFFSET = 88;

    readonly courseId = signal<number>(undefined!);
    readonly examId = signal<number>(undefined!);
    readonly testRunId = signal<number>(undefined!);
    readonly testExam = signal(false);
    readonly studentExamId = signal<number>(undefined!);
    readonly testStartTime = signal<dayjs.Dayjs | undefined>(undefined);

    readonly isSidebarCollapsed = signal(false);
    private readonly sidebarToggle = signal<(() => void) | undefined>(undefined);
    readonly toggleSidebar = (): void => this.sidebarToggle()?.();

    // determines if component was once drawn visited
    readonly pageComponentVisited = signal<boolean[]>(undefined!);

    // needed, because studentExam is downloaded only when exam is started
    readonly exam = signal<Exam>(undefined!);
    readonly studentExam = signal<StudentExam>(undefined!);

    readonly individualStudentEndDate = signal<dayjs.Dayjs>(undefined!);
    readonly individualStudentEndDateWithGracePeriod = signal<dayjs.Dayjs>(undefined!);

    readonly activeExamPage = signal<ExamPage>(new ExamPage());
    unsavedChanges = false;
    readonly connected = signal(true);
    readonly loggedOut = signal(false);

    readonly handInEarly = signal(false);
    readonly handInPossible = signal(true);
    readonly submitInProgress = signal(false);
    readonly attendanceChecked = signal(false);

    readonly examSummaryButtonSecondsLeft = signal(10);
    examSummaryButtonTimer?: ReturnType<typeof setInterval>;
    readonly showExamSummary = signal(false);
    // True while the last summary request failed. The summary is then withheld entirely instead of falling back to the
    // conduction-era exam cached on this device, which carries no results and would read as a complete summary (#13317).
    readonly summaryLoadFailed = signal(false);
    // Repeats whichever summary request failed; set alongside summaryLoadFailed
    private retrySummaryLoad?: () => void;

    readonly exerciseIndex = signal(0);

    errorSubscription: Subscription;
    websocketSubscription?: Subscription;
    workingTimeUpdateEventsSubscription?: Subscription;
    problemStatementUpdateEventsSubscription?: Subscription;
    /**
     * The exam load belonging to the current route (test run, test exam summary, or own student exam). It is cancelled
     * whenever the route changes, see {@link cancelPendingExamLoad}.
     */
    examLoadSubscription?: Subscription;

    readonly sidebarData = signal<SidebarData>(undefined!);
    readonly sidebarExercises = signal<SidebarCardElement[]>([]);

    isProgrammingExercise() {
        return !this.activeExamPage().isOverviewPage && this.activeExamPage().exercise!.type === ExerciseType.PROGRAMMING;
    }

    isProgrammingExerciseWithCodeEditor(): boolean {
        return this.isProgrammingExercise() && (this.activeExamPage().exercise as ProgrammingExercise).allowOnlineEditor === true;
    }

    isProgrammingExerciseWithOfflineIDE(): boolean {
        return this.isProgrammingExercise() && (this.activeExamPage().exercise as ProgrammingExercise).allowOfflineIde === true;
    }

    readonly examStartConfirmed = signal(false);

    // autoTimerInterval in seconds
    readonly autoSaveTimer = signal(0);
    autoSaveInterval?: number;

    private synchronizationAlert = new Subject<void>();

    private programmingSubmissionSubscriptions: Subscription[] = [];

    readonly loadingExam = signal<boolean>(undefined!);
    // Render-version signal read by the template-bound isOver()/isGracePeriodOver() getters. Bumped whenever
    // state outside Angular's reactivity changes (wall-clock transitions, in-place submission sync mutations)
    // so the exam view and its default-CD children (e.g. the navigation sidebar icons) re-render under zoneless.
    private readonly wallClockVersion = signal(0);
    readonly isAtLeastTutor = signal<boolean | undefined>(undefined);
    readonly isAtLeastInstructor = signal<boolean | undefined>(undefined);

    generateParticipationStatus: BehaviorSubject<GenerateParticipationStatus> = new BehaviorSubject<GenerateParticipationStatus>('success');

    constructor() {
        // show only one synchronization error every 5s
        this.errorSubscription = this.synchronizationAlert.pipe(throttleTime(5000)).subscribe(() => {
            this.alertService.error('artemisApp.examParticipation.saveSubmissionError');
        });
    }

    /**
     * loads the exam from the server and initializes the view
     */
    ngOnInit(): void {
        combineLatest({
            parentParams: this.route.parent?.parent?.params ?? of({ courseId: undefined }),
            currentParams: this.route.params,
        }).subscribe(({ parentParams, currentParams }) => {
            const courseId = currentParams['courseId'] || parentParams['courseId'];
            this.courseId.set(parseInt(courseId, 10));
        });
        this.route.params.subscribe((params) => {
            // This component is reused when only the :examId parameter changes, so nothing derived from the previous exam
            // may survive into the next one: neither an in-flight request, nor a summary failure, nor a summary that is
            // still on screen (not every branch below goes through a summary request that would reset those), nor the
            // route parameters themselves.
            this.resetForNewRoute();
            this.examId.set(parseInt(params['examId'], 10));
            this.testRunId.set(parseInt(params['testRunId'], 10));
            // As a student can have multiple test exams, the studentExamId is passed as a parameter.
            const studentExamId = this.route.firstChild?.snapshot.params['studentExamId'];
            this.testExam.set(!!studentExamId);
            this.studentExamId.set(studentExamId ? parseInt(studentExamId, 10) : undefined!);
            this.loadingExam.set(true);
            if (this.testRunId()) {
                this.examLoadSubscription = this.examParticipationService.loadTestRunWithExercisesForConduction(this.courseId(), this.examId(), this.testRunId()).subscribe({
                    next: (studentExam) => {
                        this.studentExam.set(studentExam);
                        studentExam.exam!.course = new Course();
                        studentExam.exam!.course.id = this.courseId();
                        this.exam.set(studentExam.exam!);
                        this.testExam.set(this.exam().testExam!);
                        this.loadingExam.set(false);
                    },
                    error: () => {
                        this.loadingExam.set(false);
                    },
                });
            } else if (this.testExam() && this.studentExamId()) {
                this.loadTestExamStudentExamForSummary();
            } else {
                this.examLoadSubscription = this.examParticipationService.getOwnStudentExam(this.courseId(), this.examId()).subscribe({
                    next: (studentExam) => {
                        this.handleStudentExam(studentExam);
                    },
                    error: () => {
                        this.handleNoStudentExam();
                    },
                });
            }
        });

        // listen to connect / disconnect events
        this.websocketSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.connected.set(status.connected);
        });
    }

    /**
     * Make sure to warn the user before leaving (or reloading) the page in exam mode
     * NOTE: while the beforeunload event might be deprecated in the future, it is currently the only way to display a confirmation dialog when the user tries to leave the page
     * @param event the beforeunload event
     */
    @HostListener('window:beforeunload', ['$event'])
    beforeUnloadHandler(event: BeforeUnloadEvent) {
        if (this.examStartConfirmed() && !this.isOver()) {
            event.preventDefault();
            return this.translateService.instant('artemisApp.examParticipation.reloadWarning');
        }
        return true;
    }

    loadAndDisplaySummary() {
        this.resetForNewLoad();
        this.examLoadSubscription = this.examParticipationService.loadStudentExamWithExercisesForSummary(this.courseId(), this.examId(), this.studentExam().id!).subscribe({
            next: (studentExamWithExercises: StudentExam) => {
                this.studentExam.set(studentExamWithExercises);
                this.showExamSummary.set(true);
                this.loadingExam.set(false);
            },
            error: () => {
                this.handleFailedSummaryLoad(() => this.loadAndDisplaySummary());
            },
        });
        if (!this.testExam()) {
            this.examParticipationService.resetExamLayout();
        }
    }

    /**
     * Loads the student exam of a test exam via the summary endpoint. Unlike {@link loadAndDisplaySummary} this is the
     * initial load, so it also has to set up the exam itself; the summary is then displayed by {@link handleStudentExam}.
     */
    private loadTestExamStudentExamForSummary(): void {
        this.resetForNewLoad();
        this.examLoadSubscription = this.examParticipationService.loadStudentExamWithExercisesForSummary(this.courseId(), this.examId(), this.studentExamId()).subscribe({
            next: (studentExam) => {
                this.handleStudentExam(studentExam, true);
            },
            error: () => {
                this.handleFailedSummaryLoad(() => this.loadTestExamStudentExamForSummary());
            },
        });
    }

    /**
     * Drops everything the previously displayed exam left behind before a new load starts: the request still in flight,
     * the retryable summary-failure state, and the summary view itself. Every entry point that begins a load goes
     * through here, so a new call site cannot silently keep one of them alive — the component is reused when only the
     * :examId parameter changes, and each of these surviving into the next exam is a bug of its own (#13317).
     */
    private resetForNewLoad(): void {
        this.cancelPendingExamLoad();
        this.clearFailedSummaryLoad();
        this.showExamSummary.set(false);
    }

    /**
     * Drops everything the previous exam left on screen, on top of {@link resetForNewLoad}. Only the route subscription
     * may call this: a summary load stays within one exam and has to keep the exam it is loading the summary for.
     * <p>
     * The conduction view is gated on {@link exam} and {@link studentExam} alone, so leaving them set would keep the
     * previous exam rendered while the next one loads, and keep it rendered underneath the message if that load fails.
     * The remaining signals decide how that view is rendered, and each of them describes the previous exam: an exam the
     * student confirmed the start of would make the next one skip its start cover, and a handed-in or ended one would
     * make the next one look like it was already over (#13317).
     */
    private resetForNewRoute(): void {
        this.resetForNewLoad();
        this.stopConductionOfPreviousExam();
        this.exam.set(undefined!);
        this.studentExam.set(undefined!);
        this.examStartConfirmed.set(false);
        this.handInEarly.set(false);
        this.activeExamPage.set(new ExamPage());
        this.individualStudentEndDate.set(undefined!);
        this.individualStudentEndDateWithGracePeriod.set(undefined!);
    }

    /**
     * Stops the work {@link examStarted} set up for the exam that was being conducted, so none of it keeps running
     * against the next exam. Resetting the signals alone is not enough: the autosave timer would keep firing
     * {@link triggerSave} on a cleared student exam, and a live event of the previous exam would be applied to the next
     * one, in the case of a working-time update even reconstructing a student exam that was just dropped (#13317).
     */
    private stopConductionOfPreviousExam(): void {
        this.stopAutoSaveTimer();
        this.workingTimeUpdateEventsSubscription?.unsubscribe();
        this.workingTimeUpdateEventsSubscription = undefined;
        this.problemStatementUpdateEventsSubscription?.unsubscribe();
        this.problemStatementUpdateEventsSubscription = undefined;
        this.programmingSubmissionSubscriptions.forEach((subscription) => subscription.unsubscribe());
        // Replacing the array rather than clearing it: ngOnDestroy iterates this list, and it must not keep the
        // subscriptions of an exam that is no longer displayed.
        this.programmingSubmissionSubscriptions = [];
    }

    /**
     * Drops the exam load that is still in flight, if any. Without this, a response arriving after the route changed
     * would apply to the exam that is no longer displayed: a late failure would restore the error state that was just
     * cleared, together with a retry callback reading the route parameters of the exam now being loaded (#13317).
     */
    private cancelPendingExamLoad(): void {
        this.examLoadSubscription?.unsubscribe();
        this.examLoadSubscription = undefined;
    }

    /**
     * Keeps the summary withheld and surfaces a retryable error state. Showing the previously loaded student exam instead
     * would present an exam without results as a complete summary long after the error toast expired (#13317).
     *
     * @param retry repeats the request that just failed, see {@link retryLoadSummary}
     */
    private handleFailedSummaryLoad(retry: () => void): void {
        this.retrySummaryLoad = retry;
        this.showExamSummary.set(false);
        this.summaryLoadFailed.set(true);
        this.loadingExam.set(false);
    }

    /**
     * Drops the retryable error state so that a message cannot outlive the request that produced it.
     */
    private clearFailedSummaryLoad(): void {
        this.summaryLoadFailed.set(false);
        this.retrySummaryLoad = undefined;
    }

    /**
     * Retries loading the summary after a failed request.
     */
    retryLoadSummary(): void {
        if (!this.retrySummaryLoad) {
            return;
        }
        this.loadingExam.set(true);
        this.retrySummaryLoad();
    }

    canDeactivate() {
        return this.loggedOut() || this.isOver() || !this.studentExam() || this.handInEarly() || !this.examStartConfirmed();
    }

    get canDeactivateWarning() {
        return this.translateService.instant('artemisApp.examParticipation.pendingChanges');
    }

    get activePageIndex(): number {
        if (!this.activeExamPage() || this.activeExamPage().isOverviewPage) {
            return -1;
        }
        return this.studentExam().exercises!.findIndex((examExercise) => examExercise.id === this.activeExamPage().exercise!.id);
    }

    get activePageComponent(): ExamPageComponent | undefined {
        // we have to find the current component based on the activeExercise because the queryList might not be full yet (e.g. only 2 of 5 components initialized)
        return this.currentPageComponents().find(
            (submissionComponent) => !this.activeExamPage().isOverviewPage && submissionComponent.getExerciseId() === this.activeExamPage().exercise!.id,
        );
    }

    setSidebarToggle(isCollapsed: boolean, toggleSidebar: () => void): void {
        this.isSidebarCollapsed.set(isCollapsed);
        this.sidebarToggle.set(toggleSidebar);
    }

    /**
     * exam start text confirmed and name entered, start button clicked and exam active
     *
     * @param studentExam            the student exam to start
     * @param resumedFromFailedSave  true when resuming from the locally cached exam after a failed save. In that case the
     *                               locally cached sync state of each submission is kept (so not-yet-saved answers stay
     *                               isSynced=false and are re-sent) instead of marking everything as synced.
     */
    examStarted(studentExam: StudentExam, resumedFromFailedSave = false) {
        if (studentExam) {
            // Keep working time
            studentExam.workingTime = this.studentExam()?.workingTime ?? studentExam.workingTime;
            this.studentExam.set(studentExam);
            // no need to change the whole page layout for test runs
            if (this.testRunId()) {
                this.examParticipationService.setExamLayout(false, true);
            } else {
                this.examParticipationService.setExamLayout();
            }
            // set endDate with workingTime
            if (!!this.testRunId() || this.testExam()) {
                const testStartTime = studentExam.startedDate ? dayjs(studentExam.startedDate) : dayjs();
                this.testStartTime.set(testStartTime);
                this.initIndividualEndDates(testStartTime);
            } else {
                this.individualStudentEndDate.set(dayjs(this.exam().startDate).add(this.studentExam().workingTime!, 'seconds'));
            }
            // initializes array which manages submission component and exam overview initialization
            this.pageComponentVisited.set(new Array(studentExam.exercises!.length).fill(false));
            this.prepareSidebarData();
            // TODO: move to exam-participation.service after studentExam was retrieved
            // initialize all submissions as synced
            this.studentExam().exercises!.forEach((exercise) => {
                if (exercise.studentParticipations) {
                    exercise.studentParticipations.forEach((participation) => {
                        if (participation.submissions && participation.submissions.length > 0) {
                            participation.submissions.forEach((submission) => {
                                // When resuming from local storage after a failed save, keep the locally cached sync state:
                                // a submission that was not yet saved to the server must stay isSynced=false so the autosave
                                // re-sends it. Unconditionally marking everything synced here would silently drop those answers.
                                if (!resumedFromFailedSave) {
                                    submission.isSynced = true;
                                }
                                if (submission.submitted == undefined) {
                                    // only set submitted to false if the value was not specified before
                                    submission.submitted = false;
                                }
                            });
                        } else if (exercise.type === ExerciseType.PROGRAMMING) {
                            // We need to provide a submission to update the navigation bar status indicator
                            // This is important otherwise the save mechanisms would not work properly
                            if (!participation.submissions || participation.submissions.length === 0) {
                                participation.submissions = [];
                                participation.submissions.push(ProgrammingSubmission.createInitialCleanSubmissionForExam());
                            }
                        }
                        // reconnect the participation with the exercise, in case this relationship was deleted before (e.g. due to breaking circular dependencies)
                        participation.exercise = exercise;

                        // setup subscription for programming exercises
                        if (exercise.type === ExerciseType.PROGRAMMING) {
                            const programmingSubmissionSubscription = this.createProgrammingExerciseSubmission(exercise.id!, participation.id!, false);
                            this.programmingSubmissionSubscriptions.push(programmingSubmissionSubscription);
                        }
                    });
                }
            });
            this.subscribeToProblemStatementUpdates();
            this.initializeOverviewPage();
        }
        this.examStartConfirmed.set(true);
        this.startAutoSaveTimer();
        if (resumedFromFailedSave && studentExam) {
            // Immediately re-send any answers that were restored from local storage but not yet saved to the server,
            // instead of waiting for the next autosave cycle. Submissions that fail again stay isSynced=false and are
            // retried by the autosave timer. Guarded by studentExam because triggerSave dereferences the current exam.
            // Force the save (forceSave=true): the recovery re-send is a plain HTTP request and must NOT be gated on the
            // WebSocket `connected()` state, which right after a reload has often not re-established yet (especially in a
            // multi-node cluster). Gating it there would silently defer the recovery to the next autosave cycle, which is
            // exactly the answer-loss window this recovery path exists to close.
            this.triggerSave(true);
        }
    }

    /**
     * checks if there is a participation for the given exercise and if it was initialized properly
     * @param exercise to check
     * @returns true if valid, false otherwise
     */
    private static isExerciseParticipationValid(exercise: Exercise): boolean {
        // check if there is at least one participation with state === Initialized or state === FINISHED
        return (
            exercise.studentParticipations !== undefined &&
            exercise.studentParticipations.length !== 0 &&
            (exercise.studentParticipations[0].initializationState === InitializationState.INITIALIZED ||
                exercise.studentParticipations[0].initializationState === InitializationState.FINISHED)
        );
    }

    /**
     * start AutoSaveTimer
     */
    public startAutoSaveTimer(): void {
        // Stop first: assigning over a running interval would drop the only handle to it, leaving it ticking for the
        // rest of the page's life, past even ngOnDestroy. Also restarts the tick counter, so a new exam does not
        // inherit the elapsed ticks of the previous one and save immediately.
        this.stopAutoSaveTimer();
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer.update((v) => v + 1);
            if (this.autoSaveTimer() >= AUTOSAVE_EXERCISE_INTERVAL && !this.isOver()) {
                this.triggerSave(false);
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    /**
     * Stops the autosave timer and drops its handle, so a later start cannot leave this interval running unreachably.
     */
    private stopAutoSaveTimer(): void {
        window.clearInterval(this.autoSaveInterval);
        this.autoSaveInterval = undefined;
        this.autoSaveTimer.set(0);
    }

    /**
     * triggered after student accepted exam end terms, will make final call to update submission on server
     */
    onExamEndConfirmed() {
        // temporary lock the submit button in order to protect against spam
        this.handInPossible.set(false);
        this.submitInProgress.set(true);
        this.stopAutoSaveTimer();

        // Submit the exam with a timeout of 20s = 20000ms
        // If we don't receive a response within that time throw an error the subscription can then handle
        this.examParticipationService
            .submitStudentExam(this.courseId(), this.examId(), this.studentExam())
            .pipe(
                timeout({
                    each: 20000,
                    with: () => throwError(() => new Error('Submission request timed out. Please check your connection and try again.')),
                }),
            )
            .subscribe({
                next: () => {
                    this.submitInProgress.set(false);

                    // As we don't get the student exam from the server, we need to set the submitted flag and the submission date manually
                    this.studentExam().submitted = true;
                    this.studentExam().submissionDate = dayjs();

                    // The exam is now submitted, so any earlier failed-save flag is obsolete. Clear it, otherwise a reload
                    // before the exam ends would re-enter the restore path and re-send answers for an already-submitted exam.
                    this.examParticipationService.setLastSaveFailed(false, this.courseId(), this.examId());

                    // Publish it so other components are aware of the change
                    this.examParticipationService.currentlyLoadedStudentExam.next(this.studentExam());

                    // Leave the hand-in-early cover: the exam is submitted, so its Finish button is disabled from here on and the
                    // student has to reach the submission confirmation instead. Without this they stay on the confirmation screen
                    // with a dead Finish button until the exam ends, which reads as if the submission had not gone through. This
                    // signal write also re-renders the panel, which reads the (mutated) submitted flag above.
                    this.handInEarly.set(false);

                    if (this.testRunId()) {
                        // If this is a test run, forward the user directly to the exam summary
                        void this.router.navigate(['course-management', this.courseId(), 'exams', this.examId(), 'test-runs', this.testRunId(), 'summary']);
                    }

                    if (this.testExam()) {
                        this.examParticipationService.resetExamLayout();
                        void this.router.navigate(['courses', this.courseId(), 'exams', this.examId(), 'test-exam', this.studentExam().id]);
                        this.examParticipationService.setShouldUpdateTestExams(true);
                    }

                    this.examSummaryButtonTimer = setInterval(() => {
                        this.examSummaryButtonSecondsLeft.update((v) => v - 1);
                        if (this.examSummaryButtonSecondsLeft() === 0) {
                            clearInterval(this.examSummaryButtonTimer);
                        }
                    }, 1000);
                },
                error: (error: Error) => {
                    // Explicitly check whether the error was caused by the submission not being in-time or already present, in this case, set hand in not possible
                    const alreadySubmitted = error.message === 'artemisApp.studentExam.alreadySubmitted';

                    // When we have already submitted load the existing submission
                    if (alreadySubmitted) {
                        if (this.testRunId()) {
                            this.examParticipationService.loadTestRunWithExercisesForConduction(this.courseId(), this.examId(), this.testRunId()).subscribe({
                                next: (studentExam: StudentExam) => {
                                    this.studentExam.set(studentExam);
                                },
                                error: (loadError: Error) => {
                                    this.alertService.error(loadError.message);

                                    // Allow the user to try to reload the exam from the server
                                    this.submitInProgress.set(false);
                                    this.handInPossible.set(true);
                                },
                            });
                        } else {
                            this.examParticipationService.getOwnStudentExam(this.courseId(), this.examId()).subscribe({
                                next: (existingExam: StudentExam) => {
                                    this.studentExam.set(existingExam);
                                },
                                error: (loadError: Error) => {
                                    this.alertService.error(loadError.message);

                                    // Allow the user to try to reload the exam from the server
                                    this.submitInProgress.set(false);
                                    this.handInPossible.set(true);
                                },
                            });
                        }
                    } else {
                        this.alertService.error(error.message);
                        this.submitInProgress.set(false);
                        this.handInPossible.set(error.message !== 'artemisApp.studentExam.submissionNotInTime');
                    }
                },
            });
    }

    /**
     * called when exam ended because the working time is over
     */
    examEnded() {
        this.stopAutoSaveTimer();
        // update local studentExam for later sync with server
        this.updateLocalStudentExam();
        // The end view is gated by the time-based isOver() getter. The exam timer fires this handler
        // ~1s before the end and then stops ticking, so nothing else would re-evaluate isOver() once
        // the time has actually elapsed. Bump the wall-clock version signal (read by isOver) now and
        // again right after the remaining second so the hand-in/end cover reliably appears.
        this.wallClockVersion.update((version) => version + 1);
        setTimeout(() => this.wallClockVersion.update((version) => version + 1), 1500);
    }

    /**
     * Called when a user wants to hand in early or decides to continue.
     */
    toggleHandInEarly() {
        // no need to fetch attendance check status from the server if it is a test exam or an exam without attendance check or when clicking continue
        if (this.exam().testExam || !this.exam().examWithAttendanceCheck || this.handInEarly()) {
            this.handleHandInEarly();
        } else {
            this.examManagementService.isAttendanceChecked(this.courseId(), this.examId()).subscribe((res) => {
                if (res.body) {
                    this.attendanceChecked.set(res.body);
                }
                this.handleHandInEarly();
            });
        }
    }

    handleHandInEarly() {
        this.handInEarly.set(!this.handInEarly());
        if (this.handInEarly()) {
            // update local studentExam for later sync with server if the student wants to hand in early
            this.updateLocalStudentExam();
            try {
                this.triggerSave(false);
            } catch (error) {
                captureException(error);
            }
        } else if (this.studentExam()?.exercises && this.activeExamPage()) {
            const index = this.studentExam().exercises!.findIndex((exercise) => !this.activeExamPage().isOverviewPage && exercise.id === this.activeExamPage().exercise!.id);
            this.exerciseIndex.set(index ? index : 0);

            // Reset the visited pages array so ngOnInit will be called for only the active page
            this.resetPageComponentVisited(this.exerciseIndex());
        }
    }

    /**
     * Returns whether the student failed to submit on time. In this case the end page is adapted.
     */
    get studentFailedToSubmit(): boolean {
        if (this.testRunId()) {
            return false;
        }
        let individualStudentEndDate;
        if (this.exam().testExam) {
            if (!this.studentExam().submitted && this.studentExam().started && this.studentExam().startedDate) {
                individualStudentEndDate = dayjs(this.studentExam().startedDate).add(this.studentExam().workingTime!, 'seconds');
            } else {
                return false;
            }
        } else {
            individualStudentEndDate = dayjs(this.exam().startDate).add(this.studentExam().workingTime!, 'seconds');
        }
        return individualStudentEndDate.add(this.exam().gracePeriod!, 'seconds').isBefore(this.serverDateService.now()) && !this.studentExam().submitted;
    }

    /**
     * check if exam is over
     */
    isOver(): boolean {
        this.wallClockVersion();
        if (this.studentExam() && this.studentExam().ended) {
            // if this was calculated to true by the server, we can be sure the student exam has finished
            return true;
        }
        if (this.handInEarly() || this.studentExam()?.submitted) {
            // implicitly the exam is over when the student wants to abort the exam or when the user has already submitted
            return true;
        }
        return this.individualStudentEndDate() && this.individualStudentEndDate().isBefore(this.serverDateService.now());
    }

    /**
     * Whether the student may currently see the summary (submission overview incl. exam questions, own answers and PDF export) of their submitted exam.
     * Controlled by the optional exam.examSummaryPublicationDate; unset means the summary is available immediately after submission (default behavior).
     */
    isExamSummaryVisible(): boolean {
        this.wallClockVersion();
        return isExamSummaryPublished(!!this.testRunId(), this.exam(), this.serverDateService);
    }

    /**
     * check if the grace period has already passed
     */
    isGracePeriodOver() {
        this.wallClockVersion();
        return this.individualStudentEndDateWithGracePeriod() && this.individualStudentEndDateWithGracePeriod().isBefore(this.serverDateService.now());
    }

    /**
     * check if exam is visible
     */
    isVisible(): boolean {
        if (this.testRunId()) {
            return true;
        }
        if (!this.exam()) {
            return false;
        }
        const visibleDate = this.exam().visibleDate;
        return visibleDate ? visibleDate.isBefore(this.serverDateService.now()) : false;
    }

    /**
     * check if exam has started
     */
    isActive(): boolean {
        if (this.testRunId()) {
            return true;
        }
        if (!this.exam()) {
            return false;
        }
        const startDate = this.exam().startDate;
        return startDate ? startDate.isBefore(this.serverDateService.now()) : false;
    }

    checkVerticalOverflow(): boolean {
        // Get the sidebar-content element
        const sidebarContent = document.querySelector('.content-exam-height');
        if (sidebarContent) {
            return sidebarContent.scrollHeight > sidebarContent.clientHeight;
        }
        return false;
    }

    ngOnDestroy(): void {
        this.programmingSubmissionSubscriptions.forEach((subscription) => {
            subscription.unsubscribe();
        });
        this.errorSubscription.unsubscribe();
        this.websocketSubscription?.unsubscribe();
        this.workingTimeUpdateEventsSubscription?.unsubscribe();
        this.problemStatementUpdateEventsSubscription?.unsubscribe();
        this.examLoadSubscription?.unsubscribe();
        this.examParticipationService.resetExamLayout();
        this.stopAutoSaveTimer();
    }

    /**
     * Takes over a loaded student exam and decides what to show for it: the conduction view, the submission
     * confirmation, or the summary.
     *
     * @param studentExam      the loaded student exam
     * @param loadedForSummary whether it was already loaded through the summary endpoint and therefore carries the
     *                         exercises the summary needs. The summary is then displayed directly instead of being
     *                         requested again: the repeat is the very same GET, and a transient failure of it would
     *                         replace a summary that had already loaded successfully with the error state (#13317).
     */
    handleStudentExam(studentExam: StudentExam | undefined, loadedForSummary = false) {
        if (!studentExam) {
            // Leave the loading state; otherwise the view would keep showing the spinner with nothing to render
            this.loadingExam.set(false);
            return;
        }
        this.studentExam.set(studentExam);
        this.exam.set(studentExam.exam!);
        this.testExam.set(this.exam().testExam!);
        if (!this.exam().testExam) {
            this.initIndividualEndDates(this.exam().startDate!);
        }

        // only show the summary if the student was able to submit on time and the summary is already visible (see examSummaryPublicationDate).
        if (this.isOver() && this.studentExam().submitted) {
            if (!this.isExamSummaryVisible()) {
                // the instructor delayed the submission overview; withhold it and only show the submission confirmation with the release date
                this.loadingExam.set(false);
            } else if (loadedForSummary) {
                // already loaded through the summary endpoint, so it can be shown right away
                this.showExamSummary.set(true);
                this.loadingExam.set(false);
            } else {
                this.loadAndDisplaySummary();
            }
        } else {
            // Directly start the exam when we continue from a failed save
            if (this.examParticipationService.lastSaveFailed(this.courseId(), this.examId())) {
                this.examParticipationService
                    .loadStudentExamWithExercisesForConductionFromLocalStorage(this.courseId(), this.examId())
                    .subscribe((localExam: StudentExam | undefined) => {
                        if (localExam) {
                            // Keep the working time from the server
                            localExam.workingTime = this.studentExam().workingTime ?? localExam.workingTime;
                            this.studentExam.set(localExam);
                            this.loadingExam.set(false);
                            // Resume from the locally cached exam: keep not-yet-saved answers (isSynced=false) and re-send them.
                            this.examStarted(this.studentExam(), true);
                            // Inform the student that their previously entered answers were restored and are being saved,
                            // so it is clear that nothing was lost when the page was reloaded.
                            this.alertService.info('artemisApp.examParticipation.answersRestoredFromLocalStorage');
                        }
                    });
            } else {
                this.loadingExam.set(false);
            }
        }
    }

    /**
     * Handles the case when there is no student exam. Here we have to check if the user is at least tutor to show the redirect to the exam management page.
     * This check is not done in the normal case due to performance reasons of 2000 students sending additional requests
     */
    handleNoStudentExam() {
        const course = this.courseStorageService.getCourse(this.courseId());
        if (!course) {
            this.courseService.find(this.courseId()).subscribe((courseResponse) => {
                this.isAtLeastTutor.set(courseResponse.body?.isAtLeastTutor);
                this.isAtLeastInstructor.set(courseResponse.body?.isAtLeastInstructor);
            });
        } else {
            this.isAtLeastTutor.set(course.isAtLeastTutor);
            this.isAtLeastInstructor.set(course.isAtLeastInstructor);
        }
        this.loadingExam.set(false);
    }

    /**
     * Initializes the individual end dates and sets up a subscription for potential changes during the conduction
     * @param startDate the start date of the exam
     */
    initIndividualEndDates(startDate: dayjs.Dayjs) {
        this.individualStudentEndDate.set(dayjs(startDate).add(this.studentExam().workingTime!, 'seconds'));
        this.individualStudentEndDateWithGracePeriod.set(this.individualStudentEndDate().clone().add(this.exam().gracePeriod!, 'seconds'));

        this.subscribeToWorkingTimeUpdates(startDate);
    }

    private subscribeToWorkingTimeUpdates(startDate: dayjs.Dayjs) {
        if (this.workingTimeUpdateEventsSubscription) {
            this.workingTimeUpdateEventsSubscription.unsubscribe();
        }
        // observeNewEventsAsSystem already filters by the requested event type, so the emitted events are
        // WorkingTimeUpdateEvent at runtime; the cast keeps the callback param typed without an extra `filter`
        // (which would incorrectly drop events lacking an explicit `eventType`, e.g. in tests).
        this.workingTimeUpdateEventsSubscription = (
            this.liveEventsService.observeNewEventsAsSystem([ExamLiveEventType.WORKING_TIME_UPDATE]) as Observable<WorkingTimeUpdateEvent>
        ).subscribe((event: WorkingTimeUpdateEvent) => {
            // A new top-level reference is required, not a copy: children bind `[studentExam]="studentExam()"`, and
            // Angular compares a property binding with `Object.is` before it reaches their `input()`, so re-setting the
            // same reference would leave the cover page showing the old working time. `withSameValues` carries every
            // nested value over untouched, so the submissions the exercise components are editing stay the same objects.
            const studentExam = StudentExam.withSameValues(this.studentExam());
            studentExam.workingTime = event.newWorkingTime;
            this.studentExam.set(studentExam);
            this.examParticipationService.currentlyLoadedStudentExam.next(this.studentExam());
            // A real-exam event carries the exam's (possibly changed) start/end date; apply it so the pre-start
            // countdown and the start-based content visibility (isActive/isVisible) recompute. A test-exam event omits
            // the schedule (the exam dates are only its availability window, not the student's conduction window), so
            // the exam dates are left untouched and the end date is derived from the student's own start below.
            if (event.newStartDate) {
                // Same reasoning as above: the cover page's start/end dates are derived in a child effect.
                const exam = Exam.withSameValues(this.exam());
                exam.startDate = event.newStartDate;
                exam.endDate = event.newEndDate ?? exam.endDate;
                this.exam.set(exam);
            }
            // Derive the end date from the new start when present, otherwise from the start captured when the
            // subscription was created (the exam start for real exams, the student's startedDate for test exams),
            // instead of a stale value.
            const effectiveStartDate = event.newStartDate ?? startDate;
            this.individualStudentEndDate.set(dayjs(effectiveStartDate).add(this.studentExam().workingTime!, 'seconds'));
            this.individualStudentEndDateWithGracePeriod.set(this.individualStudentEndDate().clone().add(this.exam().gracePeriod!, 'seconds'));
            this.liveEventsService.acknowledgeEvent(event, false);
        });
    }

    private subscribeToProblemStatementUpdates() {
        if (this.problemStatementUpdateEventsSubscription) {
            this.problemStatementUpdateEventsSubscription.unsubscribe();
        }
        // See subscribeToWorkingTimeUpdates: the events are already type-filtered by observeNewEventsAsSystem,
        // so we cast rather than add a `filter` that would drop events without an explicit `eventType`.
        this.problemStatementUpdateEventsSubscription = (
            this.liveEventsService.observeNewEventsAsSystem([ExamLiveEventType.PROBLEM_STATEMENT_UPDATE]) as Observable<ProblemStatementUpdateEvent>
        ).subscribe((event: ProblemStatementUpdateEvent) => {
            this.updateProblemStatement(event);
            this.liveEventsService.acknowledgeEvent(event, false);
        });
    }

    /**
     * update the current exercise from the navigation
     * @param exerciseChange
     */
    onPageChange(exerciseChange: { overViewChange: boolean; exercise?: Exercise; forceSave: boolean }): void {
        const activeComponent = this.activePageComponent;
        if (activeComponent) {
            activeComponent.onDeactivate();
        }
        try {
            this.triggerSave(exerciseChange.forceSave);
        } catch (error) {
            // an error here should never lead to the wrong exercise being shown
            captureException(error);
        }
        if (!exerciseChange.overViewChange) {
            this.initializeExercise(exerciseChange.exercise!);
        } else {
            this.initializeOverviewPage();
        }
    }

    /**
     * sets active exercise and checks if participation is valid for exercise
     * if not -> initialize participation and in case of programming exercises subscribe to latestSubmissions
     * @param exercise to initialize
     */
    private initializeExercise(exercise: Exercise) {
        this.activeExamPage().isOverviewPage = false;
        this.activeExamPage().exercise = exercise;
        // set current exercise Index
        this.exerciseIndex.set(this.studentExam().exercises!.findIndex((exercise1) => exercise1.id === exercise.id));

        // if we do not have a valid participation for the exercise -> initialize it
        if (!ExamParticipationComponent.isExerciseParticipationValid(exercise)) {
            // TODO: after client is online again, subscribe is not executed, might be a problem of the Observable in createParticipationForExercise
            this.createParticipationForExercise(exercise).subscribe((participation) => {
                if (participation) {
                    // for programming exercises -> wait for latest submission before showing exercise
                    if (exercise.type === ExerciseType.PROGRAMMING) {
                        const subscription = this.createProgrammingExerciseSubmission(exercise.id!, participation.id!, true);
                        // we have to create a fake submission here, otherwise the navigation bar status will not work and the save mechanism might have problems
                        participation.submissions = [ProgrammingSubmission.createInitialCleanSubmissionForExam()];
                        this.programmingSubmissionSubscriptions.push(subscription);
                    }
                    this.activateActiveComponent();
                }
            });
        } else {
            this.activateActiveComponent();
        }
    }

    private initializeOverviewPage() {
        this.activeExamPage().isOverviewPage = true;
        this.activeExamPage().exercise = undefined;
        this.exerciseIndex.set(-1);
    }

    /**
     * this will make sure that the component is displayed in the user interface
     */
    private activateActiveComponent() {
        this.pageComponentVisited.update((visited) => {
            const next = [...visited];
            next[this.activePageIndex] = true;
            return next;
        });
        const activeComponent = this.activePageComponent;
        if (activeComponent) {
            activeComponent.onActivate();
        }
    }

    updateSidebarData() {
        this.sidebarData.set({
            groupByCategory: false,
            sidebarType: 'inExam',
            ungroupedData: this.sidebarExercises(),
        });
    }

    prepareSidebarData() {
        if (!this.studentExam().exercises) {
            return;
        }

        this.sidebarExercises.set(this.examParticipationService.mapExercisesToSidebarCardElements(this.studentExam().exercises!));
        this.updateSidebarData();
    }

    /**
     * Resets the pageComponentVisited array by setting all elements to false, and then sets the element
     * at the specified activePageIndex to true, if provided and within the array bounds.
     *
     * @param {number} activePageIndex - The index of the currently active exercise page in the pageComponentVisited array.
     */
    private resetPageComponentVisited(activePageIndex: number) {
        this.pageComponentVisited.update((visited) => {
            const next = visited.map(() => false);
            if (activePageIndex >= 0) {
                next[activePageIndex] = true;
            }
            return next;
        });
    }

    /**
     * This is a fallback mechanism in case the instructor did not prepare the exercise start before the student started it on the client.
     * In this case, no participation and not submission exist and first need to be created on the server before the student can work on this exercise locally
     * @param exercise
     */
    createParticipationForExercise(exercise: Exercise): Observable<StudentParticipation | undefined> {
        this.generateParticipationStatus.next('generating');
        return this.courseExerciseService.startExercise(exercise.id!).pipe(
            map((createdParticipation: StudentParticipation) => {
                // note: it is important that we exchange the existing student participation and that we do not push it
                exercise.studentParticipations = [createdParticipation];
                if (createdParticipation.submissions && createdParticipation.submissions.length > 0) {
                    createdParticipation.submissions[0].isSynced = true;
                }
                this.generateParticipationStatus.next('success');
                return createdParticipation;
            }),
            catchError(() => {
                this.generateParticipationStatus.next('failed');
                return of(undefined);
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
     * @param forceSave is set to true, when the current exercise should be saved (even if there are no changes)
     */
    triggerSave(forceSave: boolean) {
        // before the request, we would mark the submission as isSynced = true
        // right after the response - in case it was successful - we mark the submission as isSynced = false
        this.autoSaveTimer.set(0);

        const activeComponent = this.activePageComponent;

        // in the case saving is forced, we mark the current exercise as not synced, so it will definitely be saved
        if ((activeComponent && forceSave) || (activeComponent as ExamSubmissionComponent)?.hasUnsavedChanges()) {
            const activeSubmission = (activeComponent as ExamSubmissionComponent)?.getSubmission();
            const activeExerciseType = (activeComponent as ExamSubmissionComponent)?.exerciseType;
            if (activeSubmission) {
                // this will lead to a save below, because isSynced will be set to false
                // it only makes sense to set "isSynced" to false for quiz, text and modeling
                if (activeExerciseType !== ExerciseType.PROGRAMMING && activeExerciseType !== ExerciseType.FILE_UPLOAD) {
                    activeSubmission.isSynced = false;
                    // isSynced was mutated in place; notify sync-state-dependent UI (e.g. the save button) to re-evaluate.
                    this.examParticipationService.notifySubmissionSyncStateChanged();
                }
            }
            (activeComponent as ExamSubmissionComponent).updateSubmissionFromView();
        }

        // go through ALL student exam exercises and check if there are unsynced submissions
        // we do this, because due to connectivity problems, other submissions than the currently active one might have not been saved to the server yet
        const submissionsToSync: { exercise: Exercise; submission: Submission }[] = [];
        this.studentExam().exercises!.forEach((exercise: Exercise) => {
            if (exercise.studentParticipations) {
                exercise.studentParticipations.forEach((participation) => {
                    if (participation.submissions) {
                        participation.submissions
                            .filter((submission) => !submission.isSynced)
                            .forEach((unsynchedSubmission) => {
                                submissionsToSync.push({ exercise, submission: unsynchedSubmission });
                            });
                    }
                });
            }
        });

        // save the studentExam in localStorage, so that we would be able to retrieve it later on, in case the student needs to reload the page while being offline
        this.examParticipationService.saveStudentExamToLocalStorage(this.courseId(), this.examId(), this.studentExam());

        // if no connection available -> don't try to sync, except it is forced
        // based on the submissions that need to be saved and the exercise, we perform different actions
        if (forceSave || this.connected()) {
            submissionsToSync.forEach((submissionToSync: { exercise: Exercise; submission: Submission }) => {
                switch (submissionToSync.exercise.type) {
                    case ExerciseType.TEXT:
                        this.examParticipationService.setSubmissionSaving(submissionToSync.submission, true);
                        this.textSubmissionService.update(submissionToSync.submission, submissionToSync.exercise.id!).subscribe({
                            next: () => this.onSaveSubmissionSuccess(submissionToSync.submission),
                            error: (error: HttpErrorResponse) => this.onSaveSubmissionError(error, submissionToSync.submission),
                        });
                        break;
                    case ExerciseType.MODELING:
                        this.examParticipationService.setSubmissionSaving(submissionToSync.submission, true);
                        this.modelingSubmissionService.update(submissionToSync.submission, submissionToSync.exercise.id!).subscribe({
                            next: () => this.onSaveSubmissionSuccess(submissionToSync.submission),
                            error: (error: HttpErrorResponse) => this.onSaveSubmissionError(error, submissionToSync.submission),
                        });
                        break;
                    case ExerciseType.PROGRAMMING:
                        // nothing to do here, because programming exercises are submitted differently
                        break;
                    case ExerciseType.QUIZ:
                        this.examParticipationService.setSubmissionSaving(submissionToSync.submission, true);
                        this.examParticipationService.updateQuizSubmission(submissionToSync.exercise.id!, submissionToSync.submission).subscribe({
                            next: () => this.onSaveSubmissionSuccess(submissionToSync.submission),
                            error: (error: HttpErrorResponse) => this.onSaveSubmissionError(error, submissionToSync.submission),
                        });
                        break;
                    case ExerciseType.FILE_UPLOAD:
                        // nothing to do here, because file upload exercises are only submitted manually, not when you switch between exercises
                        break;
                }
            });
        }
    }

    private updateLocalStudentExam() {
        this.currentPageComponents()
            .filter((component) => component.hasUnsavedChanges())
            .forEach((component) => component.updateSubmissionFromView());
    }

    private onSaveSubmissionSuccess(submission: Submission) {
        this.examParticipationService.setSubmissionSaving(submission, false);
        submission.isSynced = true;
        submission.submitted = true;
        // isSynced is mutated in place; notify sync-state-dependent UI (e.g. the save button) to re-evaluate.
        this.examParticipationService.notifySubmissionSyncStateChanged();
        // Only clear the failed-save flag once every syncable answer (quiz/text/modeling) is actually synced. Clearing it
        // after a single successful save while another exercise's answer is still unsynced would wrongly suppress the
        // restore-on-reload path for that not-yet-saved answer (a partial re-send must keep the exam marked save-failed).
        if (!this.hasUnsyncedSubmissions()) {
            this.examParticipationService.setLastSaveFailed(false, this.courseId(), this.examId());
        }
        // In-place mutations above are invisible to signals; nudge the render-version so the
        // navigation sidebar's save-state icons refresh under zoneless.
        this.wallClockVersion.update((version) => version + 1);
    }

    /**
     * Returns whether any syncable answer (quiz, text or modeling) of the current student exam is still unsynced.
     * Programming and file-upload submissions are excluded because they are never auto-synced via {@link triggerSave},
     * so they must not keep the failed-save flag set. The flag must stay set while a syncable answer is still pending,
     * so a reload restores and re-sends it.
     */
    private hasUnsyncedSubmissions(): boolean {
        const syncableExerciseTypes = [ExerciseType.QUIZ, ExerciseType.TEXT, ExerciseType.MODELING];
        return (this.studentExam()?.exercises ?? []).some(
            (exercise) =>
                syncableExerciseTypes.includes(exercise.type!) &&
                (exercise.studentParticipations ?? []).some((participation) => (participation.submissions ?? []).some((submission) => !submission.isSynced)),
        );
    }

    private onSaveSubmissionError(error: HttpErrorResponse, submission: Submission) {
        this.examParticipationService.setSubmissionSaving(submission, false);
        this.examParticipationService.setLastSaveFailed(true, this.courseId(), this.examId());
        // The submission stays isSynced=false after a failed save; notify sync-state-dependent UI to re-evaluate
        // (e.g. keep the save button enabled) since the flag was mutated in place.
        this.examParticipationService.notifySubmissionSyncStateChanged();

        if (error.status === 401) {
            // Unauthorized means the user needs to log in to resume
            // Therefore don't show errors because we are redirected to the login page
            this.loggedOut.set(true);
        } else {
            // show only one error for 5s - see constructor
            this.synchronizationAlert.next();
        }
    }

    /**
     * Creates a subscription for the latest programming exercise submission for a given exerciseId and participationId
     * This is done here, because this component exists throughout the whole lifecycle of an exam
     * (e.g. programming-exam-submission exists only while the exam is not over)
     * @param exerciseId id of the exercise we want to subscribe to
     * @param participationId id of the participation we want to subscribe to
     * @param fetchPending whether the latest pending submission should be fetched (true) or _only_ the websocket subscription is created (false)
     */
    private createProgrammingExerciseSubmission(exerciseId: number, participationId: number, fetchPending: boolean): Subscription {
        return this.programmingSubmissionService
            .getLatestPendingSubmissionByParticipationId(participationId, exerciseId, true, false, fetchPending)
            .pipe(
                filter((submissionStateObj) => submissionStateObj != undefined),
                distinctUntilChanged(),
                tap((submissionStateObj) => {
                    const exerciseForSubmission = this.studentExam().exercises?.find((programmingExercise) =>
                        programmingExercise.studentParticipations?.some((exerciseParticipation) => exerciseParticipation.id === submissionStateObj.participationId),
                    );
                    if (exerciseForSubmission?.studentParticipations && submissionStateObj.submission?.participation) {
                        // Update the original object as the server only sends a DTO over the websocket
                        // TODO: This is a dark hack to just make it work; the client assumes that ProgrammingSubmissionStateObj contains a submission
                        // TODO: but this is not always the case (only on the initial REST fetch call). WS submission updates are stripped down DTOs only.
                        const studentParticipation = exerciseForSubmission.studentParticipations?.[0] || {};
                        // hydrate rather than a copy: the comment above asks to update the ORIGINAL object, and mutating in place keeps
                        // every nested reference (notably the exercise back-reference) intact while detaching the DTO's own values.
                        exerciseForSubmission.studentParticipations[0] = hydrate(studentParticipation, submissionStateObj.submission.participation) satisfies StudentParticipation;
                    }
                }),
            )
            .subscribe((programmingSubmissionObj) => {
                const exerciseForSubmission = this.studentExam().exercises?.find((programmingExercise) =>
                    programmingExercise.studentParticipations?.some((exerciseParticipation) => exerciseParticipation.id === programmingSubmissionObj.participationId),
                );
                if (
                    exerciseForSubmission?.studentParticipations &&
                    exerciseForSubmission.studentParticipations.length > 0 &&
                    exerciseForSubmission.studentParticipations[0].submissions &&
                    exerciseForSubmission.studentParticipations[0].submissions.length > 0
                ) {
                    if (programmingSubmissionObj.submission) {
                        // delete backwards reference so that it is still serializable
                        const submissionCopy = deepClone(programmingSubmissionObj.submission);

                        /**
                         * Syncs the navigation bar correctly when the student only uses an IDE or the code editor.
                         * In case a student uses both, un-submitted changes in the code editor take precedence.
                         */
                        submissionCopy.isSynced = exerciseForSubmission.studentParticipations[0].submissions[0].isSynced;
                        submissionCopy.submitted = true;
                        delete submissionCopy.participation;
                        exerciseForSubmission.studentParticipations[0].submissions[0] = submissionCopy;
                        // `submitted` was flipped on a plain object from a websocket callback, which schedules no
                        // change detection. Without this the navigation sidebar and exercise overview keep the old
                        // icon and saved counter — defeating this block's stated purpose of syncing the navigation
                        // bar when the student submits from an IDE rather than the code editor.
                        this.examParticipationService.notifySubmissionSyncStateChanged();
                    }
                }
            });
    }

    /**
     * Updates the problem statement of an exercise.
     * If the exercise was already opened, the problem statement is updated using ExamExerciseUpdateService,
     * and differences between the old and new problem statements are highlighted.
     *
     * If the exercise wasn't previously opened, the problem statement will be updated without highlighting differences.
     * This is because ExamExerciseUpdateHighlighterComponents are initialized only when a student opens an exercise.
     *
     * We avoid initializing all exercise components when a student opens an exam to prevent system overload.
     * For large exams, initializing all components at once could result in even 16,000 REST calls, potentially overloading the system.
     */
    private updateProblemStatement(event: ProblemStatementUpdateEvent): void {
        const index = this.studentExam().exercises!.findIndex((exercise) => exercise.id === event.exerciseId);
        const wasExerciseOpened = this.pageComponentVisited()[index];
        if (wasExerciseOpened) {
            this.examExerciseUpdateService.updateLiveExamExercise(event.exerciseId, event.problemStatement);
        } else {
            const exercise = this.studentExam().exercises![index];
            exercise.problemStatement = event.problemStatement;
        }
    }

    /**
     * Updates the current exam height offset property to recalculate the height of exam sidebar and sidebar content
     * @param newHeight New exam bar height calculated based on the window resizements
     */
    updateHeight(newHeight: number) {
        document.documentElement.style.setProperty('--exam-height-offset', `${newHeight + this.EXAM_HEIGHT_OFFSET}px`);
    }
}
