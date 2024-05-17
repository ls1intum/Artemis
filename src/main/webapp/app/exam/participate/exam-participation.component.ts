import { Component, HostListener, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
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
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { Submission } from 'app/entities/submission.model';
import { Exam } from 'app/entities/exam.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { BehaviorSubject, Observable, Subject, Subscription, of, throwError } from 'rxjs';
import { catchError, distinctUntilChanged, filter, map, tap, throttleTime, timeout } from 'rxjs/operators';
import { InitializationState } from 'app/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { cloneDeep } from 'lodash-es';
import { captureException } from '@sentry/angular-ivy';
import { HttpErrorResponse } from '@angular/common/http';
import { ExamPage } from 'app/entities/exam-page.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { faArrowLeft, faCheckCircle, faGraduationCap, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ExamLiveEventType, ExamParticipationLiveEventsService, WorkingTimeUpdateEvent } from 'app/exam/participate/exam-participation-live-events.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

type GenerateParticipationStatus = 'generating' | 'failed' | 'success';

@Component({
    selector: 'jhi-exam-participation',
    templateUrl: './exam-participation.component.html',
    styleUrls: ['./exam-participation.scss', '../../overview/course-overview.scss'],
})
export class ExamParticipationComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChildren(ExamSubmissionComponent)
    currentPageComponents: QueryList<ExamSubmissionComponent>;

    readonly TEXT = ExerciseType.TEXT;
    readonly QUIZ = ExerciseType.QUIZ;
    readonly MODELING = ExerciseType.MODELING;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly FILEUPLOAD = ExerciseType.FILE_UPLOAD;

    courseId: number;
    examId: number;
    testRunId: number;
    testExam = false;
    testRun?: boolean;
    studentExamId: number;
    testStartTime?: dayjs.Dayjs;

    // determines if component was once drawn visited
    pageComponentVisited: boolean[];

    // needed, because studentExam is downloaded only when exam is started
    exam: Exam;
    studentExam: StudentExam;

    individualStudentEndDate: dayjs.Dayjs;
    individualStudentEndDateWithGracePeriod: dayjs.Dayjs;

    activeExamPage = new ExamPage();
    unsavedChanges = false;
    connected = true;
    loggedOut = false;

    handInEarly = false;
    handInPossible = true;
    submitInProgress = false;
    attendanceChecked = false;

    examSummaryButtonSecondsLeft = 10;
    examSummaryButtonTimer: ReturnType<typeof setInterval>;
    showExamSummary = false;

    exerciseIndex = 0;

    errorSubscription: Subscription;
    websocketSubscription?: Subscription;
    liveEventsSubscription?: Subscription;

    // Icons
    faCheckCircle = faCheckCircle;
    faArrowLeft = faArrowLeft;
    faSpinner = faSpinner;

    endEnabled: boolean;
    confirmed: boolean;
    accountName = '';
    enteredName = '';
    formattedGeneralInformation?: SafeHtml;
    formattedConfirmationText?: SafeHtml;
    graceEndDate: dayjs.Dayjs;
    criticalTime = dayjs.duration(30, 'seconds');

    isProgrammingExercise() {
        return !this.activeExamPage.isOverviewPage && this.activeExamPage.exercise?.type === ExerciseType.PROGRAMMING;
    }

    isProgrammingExerciseWithCodeEditor(): boolean {
        return this.isProgrammingExercise() && (this.activeExamPage.exercise as ProgrammingExercise).allowOnlineEditor === true;
    }

    isProgrammingExerciseWithOfflineIDE(): boolean {
        return this.isProgrammingExercise() && (this.activeExamPage.exercise as ProgrammingExercise).allowOfflineIde === true;
    }

    examStartConfirmed = false;

    // autoTimerInterval in seconds
    autoSaveTimer = 0;
    autoSaveInterval: number;

    private synchronizationAlert = new Subject<void>();

    private programmingSubmissionSubscriptions: Subscription[] = [];

    loadingExam: boolean;
    isAtLeastTutor?: boolean;
    isAtLeastInstructor?: boolean;

    generateParticipationStatus: BehaviorSubject<GenerateParticipationStatus> = new BehaviorSubject('success');

    // Icons
    faGraduationCap = faGraduationCap;

    constructor(
        private websocketService: JhiWebsocketService,
        private route: ActivatedRoute,
        private router: Router,
        private artemisMarkdown: ArtemisMarkdownService,
        private examParticipationService: ExamParticipationService,
        private modelingSubmissionService: ModelingSubmissionService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private textSubmissionService: TextSubmissionService,
        private serverDateService: ArtemisServerDateService,
        private translateService: TranslateService,
        private alertService: AlertService,
        private courseExerciseService: CourseExerciseService,
        private liveEventsService: ExamParticipationLiveEventsService,
        private courseService: CourseManagementService,
        private courseStorageService: CourseStorageService,
        private examManagementService: ExamManagementService,
    ) {
        // show only one synchronization error every 5s
        this.errorSubscription = this.synchronizationAlert.pipe(throttleTime(5000)).subscribe(() => {
            this.alertService.error('artemisApp.examParticipation.saveSubmissionError');
        });
    }

    /**
     * load exam information from the exam-participation-cover component
     */
    ngOnInit(): void {
        this.examParticipationService.examState$.subscribe((state) => {
            //this.handInEarly = state.handInEarly;
            //this.handInPossible = state.handInPossible;
            this.submitInProgress = state.submitInProgress;
            //this.attendanceChecked = state.attendanceChecked;
            this.courseId = state.courseId!;
            this.examId = state.examId!;
            this.testRunId = state.testRunId!;
            this.studentExamId = state.studentExamId!;
            this.exam = state.exam!;
            this.studentExam = state.studentExam!;
            this.studentExam.exercises = state.exercises!;
            this.accountName = state.accountName;
            this.testRun = state.testRun;
        });

        // listen to connect / disconnect events
        this.websocketSubscription = this.websocketService.connectionState.subscribe((status) => {
            this.connected = status.connected;
        });

        this.examParticipationService.examStarted$.subscribe((studentExam: StudentExam) => {
            this.studentExam = studentExam;
        });

        console.log('ngOnInit in participation, exam: ', this.exam);
        console.log('ngOnInit in participation, examId: ', this.examId);
        console.log('ngOnInit in participation, studentExam: ', this.studentExam);
        console.log('ngOnInit in participation, studentExamId: ', this.studentExamId);

        this.examStarted(this.studentExam);
        this.generateInformationForHtml();
        this.loadGradeEndDate();

        console.log('ngOnInit in participation, handInEarly', this.handInEarly);
        console.log('ngOnInit in participation, studentExam.submitted', this.studentExam.submitted);
        console.log('ngOnInit in participation, isOver()', this.isOver());
        console.log('ngOnInit in participation, examStartConfirmed', this.examStartConfirmed);
        console.log('ngOnInit in participation, isGracePeriodOver()', this.isGracePeriodOver());
    }

    generateInformationForHtml() {
        this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
        this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
    }

    loadGradeEndDate() {
        if (this.testRun) {
            this.graceEndDate = dayjs(this.testStartTime!).add(this.studentExam.workingTime!, 'seconds').add(this.exam.gracePeriod!, 'seconds');
        } else if (this.testExam) {
            this.graceEndDate = dayjs(this.studentExam.startedDate!).add(this.studentExam.workingTime!, 'seconds').add(this.exam.gracePeriod!, 'seconds');
        } else {
            this.graceEndDate = dayjs(this.exam.startDate).add(this.studentExam.workingTime!, 'seconds').add(this.exam.gracePeriod!, 'seconds');
        }
    }

    loadAndDisplaySummary() {
        this.examParticipationService.loadStudentExamWithExercisesForSummary(this.courseId, this.examId, this.studentExam.id!).subscribe({
            next: (studentExamWithExercises: StudentExam) => {
                this.studentExam = studentExamWithExercises;
                this.showExamSummary = true;
                this.loadingExam = false;
            },
            error: () => (this.loadingExam = false),
        });
    }

    canDeactivate() {
        return this.loggedOut || this.isOver() || !this.studentExam || this.handInEarly || !this.examStartConfirmed;
    }

    get canDeactivateWarning() {
        return this.translateService.instant('artemisApp.examParticipation.pendingChanges');
    }

    // displays the alert for confirming leaving the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any): void {
        if (!this.canDeactivate()) {
            event.returnValue = this.canDeactivateWarning;
        }
    }

    get activePageIndex(): number {
        if (!this.activeExamPage || this.activeExamPage.isOverviewPage || !this.studentExam.exercises) {
            return -1;
        }
        return this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === this.activeExamPage.exercise?.id);
    }

    get activePageComponent(): ExamPageComponent | undefined {
        // we have to find the current component based on the activeExercise because the queryList might not be full yet (e.g. only 2 of 5 components initialized)
        return this.currentPageComponents.find(
            (submissionComponent) => !this.activeExamPage.isOverviewPage && (submissionComponent as ExamSubmissionComponent).getExerciseId() === this.activeExamPage.exercise?.id,
        );
    }

    /**
     * exam start text confirmed and name entered, start button clicked and exam active
     */
    examStarted(studentExam: StudentExam) {
        if (studentExam) {
            // Keep working time
            studentExam.workingTime = this.studentExam?.workingTime ?? studentExam.workingTime;
            this.studentExam = studentExam;

            console.log('examStarted in participation, exam: ', this.exam);
            console.log('examStarted in participation, examId: ', this.examId);
            console.log('examStarted in participation, studentExam: ', this.studentExam);
            console.log('examStarted in participation, studentExamId: ', this.studentExamId);

            // provide exam-participation.service with exerciseId information (e.g. needed for exam notifications)
            const exercises: Exercise[] = this.studentExam.exercises!;
            const exerciseIds = exercises.map((exercise) => exercise.id).filter(Number) as number[];
            this.examParticipationService.setExamExerciseIds(exerciseIds);
            // set endDate with workingTime
            if (!!this.testRunId || this.testExam) {
                this.testStartTime = studentExam.startedDate ? dayjs(studentExam.startedDate) : dayjs();
                this.initIndividualEndDates(this.testStartTime!);
            } else {
                this.individualStudentEndDate = dayjs(this.exam.startDate).add(this.studentExam.workingTime!, 'seconds');
            }
            // initializes array which manages submission component and exam overview initialization
            this.pageComponentVisited = new Array(studentExam.exercises!.length).fill(false);
            // TODO: move to exam-participation.service after studentExam was retrieved
            // initialize all submissions as synced
            this.studentExam.exercises!.forEach((exercise) => {
                if (exercise.studentParticipations) {
                    exercise.studentParticipations!.forEach((participation) => {
                        if (participation.submissions && participation.submissions.length > 0) {
                            participation.submissions.forEach((submission) => {
                                submission.isSynced = true;
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
            this.initializeOverviewPage();
        }
        this.examStartConfirmed = true;
        this.startAutoSaveTimer();
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
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL && !this.isOver()) {
                this.triggerSave(false);
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    /**
     * triggered after student accepted exam end terms, will make final call to update submission on server
     */
    onExamEndConfirmed() {
        // temporary lock the submit button in order to protect against spam
        this.handInPossible = false;
        //this.examParticipationService.setExamState({ handInPossible: false });
        //his.submitInProgress = true;
        this.examParticipationService.setExamState({ submitInProgress: true });
        if (this.autoSaveInterval) {
            window.clearInterval(this.autoSaveInterval);
        }

        // Submit the exam with a timeout of 20s = 20000ms
        // If we don't receive a response within that time throw an error the subscription can then handle
        this.examParticipationService
            .submitStudentExam(this.courseId, this.examId, this.studentExam)
            .pipe(
                timeout({
                    each: 20000,
                    with: () => throwError(() => new Error('Submission request timed out. Please check your connection and try again.')),
                }),
            )
            .subscribe({
                next: () => {
                    if (this.testExam) {
                        // If we have a test exam, we reload the summary from the server right away
                        this.loadAndDisplaySummary();
                    }
                    //this.submitInProgress = false;
                    this.examParticipationService.setExamState({ submitInProgress: false });

                    // As we don't get the student exam from the server, we need to set the submitted flag and the submission date manually
                    this.studentExam.submitted = true;
                    this.studentExam.submissionDate = dayjs();

                    // Publish it so other components are aware of the change
                    this.examParticipationService.currentlyLoadedStudentExam.next(this.studentExam);

                    if (this.testRunId) {
                        this.router.navigate(['course-management', this.courseId, 'exams', this.examId, 'test-runs', this.testRunId, 'summary']);
                    }

                    this.examSummaryButtonTimer = setInterval(() => {
                        this.examSummaryButtonSecondsLeft -= 1;
                        if (this.examSummaryButtonSecondsLeft === 0) {
                            clearInterval(this.examSummaryButtonTimer);
                        }
                    }, 1000);
                },
                error: (error: Error) => {
                    // Explicitly check whether the error was caused by the submission not being in-time or already present, in this case, set hand in not possible
                    const alreadySubmitted = error.message === 'artemisApp.studentExam.alreadySubmitted';

                    // When we have already submitted load the existing submission
                    if (alreadySubmitted) {
                        if (this.testRunId) {
                            this.examParticipationService.loadTestRunWithExercisesForConduction(this.courseId, this.examId, this.testRunId).subscribe({
                                next: (studentExam: StudentExam) => {
                                    this.studentExam = studentExam;
                                },
                                error: (loadError: Error) => {
                                    this.alertService.error(loadError.message);

                                    // Allow the user to try to reload the exam from the server
                                    //this.submitInProgress = false;
                                    this.examParticipationService.setExamState({ submitInProgress: false });
                                    this.handInPossible = true;
                                    //this.examParticipationService.setExamState({ handInPossible: true });
                                },
                            });
                        } else {
                            this.examParticipationService.getOwnStudentExam(this.courseId, this.examId).subscribe({
                                next: (existingExam: StudentExam) => {
                                    this.studentExam = existingExam;
                                },
                                error: (loadError: Error) => {
                                    this.alertService.error(loadError.message);

                                    // Allow the user to try to reload the exam from the server
                                    //this.submitInProgress = false;
                                    this.examParticipationService.setExamState({ submitInProgress: false });
                                    this.handInPossible = true;
                                    //this.examParticipationService.setExamState({ handInPossible: true });
                                },
                            });
                        }
                    } else {
                        this.alertService.error(error.message);
                        //this.submitInProgress = false;
                        this.examParticipationService.setExamState({ submitInProgress: false });
                        this.handInPossible = error.message !== 'artemisApp.studentExam.submissionNotInTime';
                        //this.examParticipationService.setExamState({ handInPossible: error.message !== 'artemisApp.studentExam.submissionNotInTime' });
                    }
                },
            });
    }

    /**
     * called when exam ended because the working time is over
     */
    examEnded() {
        if (this.autoSaveInterval) {
            window.clearInterval(this.autoSaveInterval);
        }
        // update local studentExam for later sync with server
        this.updateLocalStudentExam();
    }

    /**
     * Called when a user wants to hand in early or decides to continue.
     */
    toggleHandInEarly() {
        this.attendanceChecked = this.exam?.testExam || !this.exam?.examWithAttendanceCheck;
        console.log('this.attendanceChecked 1: ' + this.attendanceChecked);
        // no need to fetch attendance check status from the server if it is a test exam or an exam without attendance check or when clicking continue
        if (this.exam.testExam || !this.exam.examWithAttendanceCheck || this.handInEarly) {
            this.handleHandInEarly();
        } else {
            this.examManagementService.isAttendanceChecked(this.courseId, this.examId).subscribe((res) => {
                if (res.body) {
                    this.attendanceChecked = this.exam?.testExam || !this.exam?.examWithAttendanceCheck || res.body;
                    console.log('this.attendanceChecked 2: ' + this.attendanceChecked);
                    //this.examParticipationService.setExamState({ attendanceChecked: res.body });
                }
                this.handleHandInEarly();
            });
        }
    }

    handleHandInEarly() {
        this.handInEarly = !this.handInEarly;
        console.log('handleHandInEarly, handInEarly', this.handInEarly);
        //this.examParticipationService.setExamState({ handInEarly: this.handInEarly });
        //!studentExam.submitted && ((isOver() && examStartConfirmed) || isGracePeriodOver())
        console.log('handleHandInEarly, studentExam.submitted', this.studentExam.submitted);
        console.log('handleHandInEarly, isOver()', this.isOver());
        console.log('handleHandInEarly, examStartConfirmed', this.examStartConfirmed);
        console.log('handleHandInEarly, isGracePeriodOver()', this.isGracePeriodOver());
        //this.handInEarly = !this.handInEarly;
        if (this.handInEarly) {
            // update local studentExam for later sync with server if the student wants to hand in early
            this.updateLocalStudentExam();
            try {
                this.triggerSave(false);
            } catch (error) {
                captureException(error);
            }
        } else if (this.studentExam?.exercises && this.activeExamPage) {
            const index = this.studentExam.exercises.findIndex((exercise) => !this.activeExamPage.isOverviewPage && exercise.id === this.activeExamPage.exercise?.id);
            this.exerciseIndex = index ? index : 0;

            // Reset the visited pages array so ngOnInit will be called for only the active page
            this.resetPageComponentVisited(this.exerciseIndex);
        }
    }

    /**
     * check if exam is over
     */
    isOver(): boolean {
        if (this.studentExam && this.studentExam.ended) {
            // if this was calculated to true by the server, we can be sure the student exam has finished
            return true;
        }
        if (this.handInEarly || this.studentExam?.submitted) {
            // implicitly the exam is over when the student wants to abort the exam or when the user has already submitted
            return true;
        }
        return this.individualStudentEndDate && this.individualStudentEndDate.isBefore(this.serverDateService.now());
    }

    /**
     * check if the grace period has already passed
     */
    isGracePeriodOver() {
        return this.individualStudentEndDateWithGracePeriod && this.individualStudentEndDateWithGracePeriod.isBefore(this.serverDateService.now());
    }

    /**
     * check if exam is visible
     */
    isVisible(): boolean {
        if (this.testRunId) {
            return true;
        }
        if (!this.exam) {
            return false;
        }
        return this.exam.visibleDate ? this.exam.visibleDate.isBefore(this.serverDateService.now()) : false;
    }

    /**
     * check if exam has started
     */
    isActive(): boolean {
        if (this.testRunId) {
            return true;
        }
        if (!this.exam) {
            return false;
        }
        return this.exam.startDate ? this.exam.startDate.isBefore(this.serverDateService.now()) : false;
    }

    ngOnDestroy(): void {
        this.programmingSubmissionSubscriptions.forEach((subscription) => {
            subscription.unsubscribe();
        });
        this.errorSubscription.unsubscribe();
        this.websocketSubscription?.unsubscribe();
        this.liveEventsSubscription?.unsubscribe();
        window.clearInterval(this.autoSaveInterval);
    }

    /**
     * Initializes the individual end dates and sets up a subscription for potential changes during the conduction
     * @param startDate the start date of the exam
     */
    initIndividualEndDates(startDate: dayjs.Dayjs) {
        this.individualStudentEndDate = dayjs(startDate).add(this.studentExam.workingTime!, 'seconds');
        this.individualStudentEndDateWithGracePeriod = this.individualStudentEndDate.clone().add(this.exam.gracePeriod!, 'seconds');

        this.subscribeToWorkingTimeUpdates(startDate);
    }

    private subscribeToWorkingTimeUpdates(startDate: dayjs.Dayjs) {
        if (this.liveEventsSubscription) {
            this.liveEventsSubscription.unsubscribe();
        }
        this.liveEventsSubscription = this.liveEventsService.observeNewEventsAsSystem([ExamLiveEventType.WORKING_TIME_UPDATE]).subscribe((event: WorkingTimeUpdateEvent) => {
            // Create new object to make change detection work, otherwise the date will not update
            this.studentExam = { ...this.studentExam, workingTime: event.newWorkingTime! };
            this.examParticipationService.currentlyLoadedStudentExam.next(this.studentExam);
            this.individualStudentEndDate = dayjs(startDate).add(this.studentExam.workingTime!, 'seconds');
            this.individualStudentEndDateWithGracePeriod = this.individualStudentEndDate.clone().add(this.exam.gracePeriod!, 'seconds');
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
        this.activeExamPage.isOverviewPage = false;
        this.activeExamPage.exercise = exercise;
        // set current exercise Index
        this.exerciseIndex = this.studentExam.exercises!.findIndex((exercise1) => exercise1.id === exercise.id);

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
        this.activeExamPage.isOverviewPage = true;
        this.activeExamPage.exercise = undefined;
        this.exerciseIndex = -1;
    }

    /**
     * this will make sure that the component is displayed in the user interface
     */
    private activateActiveComponent() {
        this.pageComponentVisited[this.activePageIndex] = true;
        const activeComponent = this.activePageComponent;
        if (activeComponent) {
            activeComponent.onActivate();
        }
    }

    /**
     * Resets the pageComponentVisited array by setting all elements to false, and then sets the element
     * at the specified activePageIndex to true, if provided and within the array bounds.
     *
     * @param {number} activePageIndex - The index of the currently active exercise page in the pageComponentVisited array.
     */
    private resetPageComponentVisited(activePageIndex: number) {
        this.pageComponentVisited.fill(false);
        if (activePageIndex >= 0) {
            this.pageComponentVisited[activePageIndex] = true;
        }
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
        this.autoSaveTimer = 0;

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
                }
            }
            (activeComponent as ExamSubmissionComponent).updateSubmissionFromView();
        }

        // go through ALL student exam exercises and check if there are unsynced submissions
        // we do this, because due to connectivity problems, other submissions than the currently active one might have not been saved to the server yet
        const submissionsToSync: { exercise: Exercise; submission: Submission }[] = [];
        this.studentExam.exercises!.forEach((exercise: Exercise) => {
            if (exercise.studentParticipations) {
                exercise.studentParticipations!.forEach((participation) => {
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
        this.examParticipationService.saveStudentExamToLocalStorage(this.courseId, this.examId, this.studentExam);

        // if no connection available -> don't try to sync, except it is forced
        // based on the submissions that need to be saved and the exercise, we perform different actions
        if (forceSave || this.connected) {
            submissionsToSync.forEach((submissionToSync: { exercise: Exercise; submission: Submission }) => {
                switch (submissionToSync.exercise.type) {
                    case ExerciseType.TEXT:
                        this.textSubmissionService.update(submissionToSync.submission as TextSubmission, submissionToSync.exercise.id!).subscribe({
                            next: () => this.onSaveSubmissionSuccess(submissionToSync.submission),
                            error: (error: HttpErrorResponse) => this.onSaveSubmissionError(error),
                        });
                        break;
                    case ExerciseType.MODELING:
                        this.modelingSubmissionService.update(submissionToSync.submission as ModelingSubmission, submissionToSync.exercise.id!).subscribe({
                            next: () => this.onSaveSubmissionSuccess(submissionToSync.submission),
                            error: (error: HttpErrorResponse) => this.onSaveSubmissionError(error),
                        });
                        break;
                    case ExerciseType.PROGRAMMING:
                        // nothing to do here, because programming exercises are submitted differently
                        break;
                    case ExerciseType.QUIZ:
                        this.examParticipationService.updateQuizSubmission(submissionToSync.exercise.id!, submissionToSync.submission as QuizSubmission).subscribe({
                            next: () => this.onSaveSubmissionSuccess(submissionToSync.submission),
                            error: (error: HttpErrorResponse) => this.onSaveSubmissionError(error),
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
        this.currentPageComponents.filter((component) => component.hasUnsavedChanges()).forEach((component) => component.updateSubmissionFromView());
    }

    private onSaveSubmissionSuccess(submission: Submission) {
        this.examParticipationService.setLastSaveFailed(false, this.courseId, this.examId);
        submission.isSynced = true;
        submission.submitted = true;
    }

    private onSaveSubmissionError(error: HttpErrorResponse) {
        this.examParticipationService.setLastSaveFailed(true, this.courseId, this.examId);

        if (error.status === 401) {
            // Unauthorized means the user needs to log in to resume
            // Therefore don't show errors because we are redirected to the login page
            this.loggedOut = true;
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
                    const exerciseForSubmission = this.studentExam.exercises?.find((programmingExercise) =>
                        programmingExercise.studentParticipations?.some((exerciseParticipation) => exerciseParticipation.id === submissionStateObj.participationId),
                    );
                    if (exerciseForSubmission?.studentParticipations && submissionStateObj.submission?.participation) {
                        // Update the original object as the server only sends a DTO over the websocket
                        // TODO: This is a dark hack to just make it work; the client assumes that ProgrammingSubmissionStateObj contains a submission
                        // TODO: but this is not always the case (only on the initial REST fetch call). WS submission updates are stripped down DTOs only.
                        const base = exerciseForSubmission.studentParticipations?.[0] || {};
                        exerciseForSubmission.studentParticipations[0] = { ...base, ...submissionStateObj.submission.participation };
                    }
                }),
            )
            .subscribe((programmingSubmissionObj) => {
                const exerciseForSubmission = this.studentExam.exercises?.find((programmingExercise) =>
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
                        const submissionCopy = cloneDeep(programmingSubmissionObj.submission);

                        /**
                         * Syncs the navigation bar correctly when the student only uses an IDE or the code editor.
                         * In case a student uses both, un-submitted changes in the code editor take precedence.
                         */
                        submissionCopy.isSynced = exerciseForSubmission.studentParticipations[0].submissions[0].isSynced;
                        submissionCopy.submitted = true;
                        delete submissionCopy.participation;
                        exerciseForSubmission.studentParticipations[0].submissions[0] = submissionCopy;
                    }
                }
            });
    }

    /**
     * Returns whether the student failed to submit on time. In this case the end page is adapted.
     */
    get studentFailedToSubmit(): boolean {
        if (this.testRun) {
            return false;
        }
        let individualStudentEndDate;
        if (this.exam?.testExam) {
            if (!this.studentExam.submitted && this.studentExam.started && this.studentExam.startedDate) {
                individualStudentEndDate = dayjs(this.studentExam.startedDate).add(this.studentExam.workingTime!, 'seconds');
            } else {
                return false;
            }
        } else {
            individualStudentEndDate = dayjs(this.exam?.startDate).add(this.studentExam.workingTime!, 'seconds');
        }
        return individualStudentEndDate.add(this.exam?.gracePeriod!, 'seconds').isBefore(this.serverDateService.now()) && !this.studentExam.submitted;
    }

    updateConfirmation() {
        this.endEnabled = this.confirmed;
    }

    get nameIsCorrect(): boolean {
        return this.enteredName.trim() === this.accountName.trim();
    }

    get inserted(): boolean {
        return this.enteredName.trim() !== '';
    }

    endButtonEnabled(): boolean {
        console.log('endButtonEnabled, nameIsCorrect: ' + this.nameIsCorrect);
        console.log('endButtonEnabled, confirmed: ' + this.confirmed);
        console.log('endButtonEnabled, exam: ' + this.exam);
        console.log('endButtonEnabled, handInPossible: ' + this.handInPossible);
        return this.nameIsCorrect && this.confirmed && this.exam && this.handInPossible;
    }
}
