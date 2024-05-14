import { Component, EventEmitter, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { EXAM_START_WAIT_TIME_MINUTES } from 'app/app.constants';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { faArrowLeft, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ExamLiveEventType, ExamParticipationLiveEventsService, WorkingTimeUpdateEvent } from 'app/exam/participate/exam-participation-live-events.service';

@Component({
    selector: 'jhi-exam-participation-cover',
    templateUrl: './exam-participation-cover.component.html',
    styleUrls: ['./exam-participation-cover.scss'],
})
export class ExamParticipationCoverComponent implements OnChanges, OnDestroy, OnInit {
    /**
     * if startView is set to true: startText and confirmationStartText will be displayed
     * if startView is set to false: endText and confirmationEndText will be displayed
     */
    // @Input() startView: boolean;
    // @Input() exam: Exam;
    // @Input() studentExam: StudentExam;
    //@Input() testRunStartTime: dayjs.Dayjs | undefined;

    handInEarly: boolean;
    handInPossible: boolean;
    submitInProgress: boolean;
    attendanceChecked: boolean;
    testStartTime?: dayjs.Dayjs;
    examStartConfirmed = false;
    startView = true;

    @Output() onExamStarted: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    @Output() onExamEnded: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    @Output() onExamContinueAfterHandInEarly = new EventEmitter<void>();
    course?: Course;
    startEnabled: boolean;
    endEnabled: boolean;
    confirmed: boolean;
    isAttendanceChecked: boolean;

    exam: Exam;
    studentExam: StudentExam;

    testRun?: boolean;
    testExam?: boolean;

    formattedGeneralInformation?: SafeHtml;
    formattedConfirmationText?: SafeHtml;

    interval: number;
    waitingForExamStart = false;
    timeUntilStart = '0';

    accountName = '';
    enteredName = '';

    graceEndDate: dayjs.Dayjs;
    criticalTime = dayjs.duration(30, 'seconds');

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;

    courseId: number;
    examId: number;
    testRunId: number;
    studentExamId: number;
    loadingExam: boolean;

    errorSubscription: Subscription;
    websocketSubscription?: Subscription;
    liveEventsSubscription?: Subscription;

    connected = true;
    isAtLeastTutor?: boolean;
    isAtLeastInstructor?: boolean;

    individualStudentEndDate: dayjs.Dayjs;
    individualStudentEndDateWithGracePeriod: dayjs.Dayjs;

    showExamSummary = false;

    constructor(
        private courseService: CourseManagementService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private accountService: AccountService,
        private examParticipationService: ExamParticipationService,
        private serverDateService: ArtemisServerDateService,
        private route: ActivatedRoute,
        private router: Router,
        private courseStorageService: CourseStorageService,
        private websocketService: JhiWebsocketService,
        private liveEventsService: ExamParticipationLiveEventsService,
    ) {}

    ngOnInit(): void {
        this.route.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.examParticipationService.examState$.subscribe((state) => {
            this.examStartConfirmed = state.examStartConfirmed;
            this.testStartTime = state.testStartTime;
            this.handInEarly = state.handInEarly;
            this.handInPossible = state.handInPossible;
            this.submitInProgress = state.submitInProgress;
            this.attendanceChecked = state.attendanceChecked;
        });

        this.route.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
            this.testRunId = parseInt(params['testRunId'], 10);
            // As a student can have multiple test exams, the studentExamId is passed as a parameter.
            if (params['studentExamId']) {
                // If a new StudentExam should be created, the keyword start is used (and no StudentExam exists)
                this.testExam = true;
                if (params['studentExamId'] !== 'start') {
                    this.studentExamId = parseInt(params['studentExamId'], 10);
                }
            }
            this.loadingExam = true;
            if (this.testRunId) {
                this.route.parent?.parent?.params.subscribe((params) => {
                    this.courseId = parseInt(params['courseId'], 10);
                });
                this.examParticipationService.loadTestRunWithExercisesForConduction(this.courseId, this.examId, this.testRunId).subscribe({
                    next: (studentExam) => {
                        this.studentExam = studentExam;
                        this.studentExam.exam!.course = new Course();
                        this.studentExam.exam!.course.id = this.courseId;
                        this.exam = studentExam.exam!;
                        this.testExam = this.exam.testExam!;
                        this.loadingExam = false;
                    },
                    error: () => (this.loadingExam = false),
                });
            } else {
                this.examParticipationService.getOwnStudentExam(this.courseId, this.examId).subscribe({
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
            this.connected = status.connected;
        });
        this.isAttendanceChecked = this.exam.testExam || !this.exam.examWithAttendanceCheck || this.attendanceChecked;
    }

    /**
     * on changes uses the correct information to display in either start or final view
     * changes in the exam and subscription is handled in the exam-participation.component
     * if the student exam changes, we need to update the displayed times
     */
    ngOnChanges(): void {
        this.confirmed = false;
        this.startEnabled = false;
        this.testRun = this.studentExam.testRun;
        this.testExam = this.exam.testExam;
        this.isStartViewEnded();

        if (this.startView) {
            this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
        } else {
            this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
            // this should be the individual working end + the grace period
            if (this.testRun) {
                this.graceEndDate = dayjs(this.testStartTime!).add(this.studentExam.workingTime!, 'seconds').add(this.exam.gracePeriod!, 'seconds');
            } else if (this.testExam) {
                this.graceEndDate = dayjs(this.studentExam.startedDate!).add(this.studentExam.workingTime!, 'seconds').add(this.exam.gracePeriod!, 'seconds');
            } else {
                this.graceEndDate = dayjs(this.exam.startDate).add(this.studentExam.workingTime!, 'seconds').add(this.exam.gracePeriod!, 'seconds');
            }
        }

        this.accountService.identity().then((user) => {
            if (user && user.name) {
                console.log('Girdi');
                this.accountName = user.name;
            }
        });
    }

    ngOnDestroy() {
        if (this.interval) {
            clearInterval(this.interval);
        }
    }

    /**
     * checks whether confirmation checkbox has been checked
     * if startView true:
     * if confirmed, we further check whether exam has started yet regularly
     */
    updateConfirmation() {
        if (this.startView) {
            this.startEnabled = this.confirmed;
        } else {
            this.endEnabled = this.confirmed;
        }
    }

    /**
     * check if exam already started
     */
    hasStarted(): boolean {
        if (this.testRun) {
            return true;
        }
        return this.exam?.startDate ? this.exam.startDate.isBefore(this.serverDateService.now()) : false;
    }

    /**
     * displays popup or start exam participation immediately
     */
    startExam() {
        if (this.testRun) {
            this.examParticipationService.saveStudentExamToLocalStorage(this.exam.course!.id!, this.exam.id!, this.studentExam);
            this.onExamStarted.emit(this.studentExam);
        } else {
            this.examParticipationService
                .loadStudentExamWithExercisesForConduction(this.exam.course!.id!, this.exam.id!, this.studentExam.id!)
                .subscribe((studentExam: StudentExam) => {
                    this.studentExam = studentExam;
                    this.examParticipationService.saveStudentExamToLocalStorage(this.exam.course!.id!, this.exam.id!, studentExam);
                    if (this.hasStarted()) {
                        this.onExamStarted.emit(studentExam);
                    } else {
                        this.waitingForExamStart = true;
                        if (this.interval) {
                            clearInterval(this.interval);
                        }
                        this.interval = window.setInterval(() => {
                            this.updateDisplayedTimes(studentExam);
                        }, UI_RELOAD_TIME);
                    }
                });
        }
    }

    /**
     * updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes(studentExam: StudentExam) {
        const translationBasePath = 'artemisApp.showStatistic.';
        // update time until start
        if (this.exam && this.exam.startDate) {
            if (this.hasStarted()) {
                this.timeUntilStart = this.translateService.instant(translationBasePath + 'now');
                this.onExamStarted.emit(studentExam);
            } else {
                this.timeUntilStart = this.relativeTimeText(this.exam.startDate.diff(this.serverDateService.now(), 'seconds'));
            }
        } else {
            this.timeUntilStart = '';
        }
    }

    /**
     * Express the given timespan as humanized text
     *
     * @param remainingTimeSeconds {number} the amount of seconds to display
     * @return {string} humanized text for the given amount of seconds
     */
    relativeTimeText(remainingTimeSeconds: number): string {
        if (remainingTimeSeconds > 210) {
            return Math.ceil(remainingTimeSeconds / 60) + ' min';
        } else if (remainingTimeSeconds > 59) {
            return Math.floor(remainingTimeSeconds / 60) + ' min ' + (remainingTimeSeconds % 60) + ' s';
        } else {
            return remainingTimeSeconds + ' s';
        }
    }

    /**
     * Submits the exam
     */
    submitExam() {
        this.onExamEnded.emit();
    }

    /**
     * Notify the parent component that the user wants to continue after hand in early
     */
    continueAfterHandInEarly() {
        this.onExamContinueAfterHandInEarly.emit();
    }

    get startButtonEnabled(): boolean {
        if (this.testRun) {
            return this.nameIsCorrect && this.confirmed && !!this.exam;
        }
        const now = this.serverDateService.now();
        return !!(
            this.nameIsCorrect &&
            this.confirmed &&
            this.exam &&
            this.exam.visibleDate &&
            this.exam.visibleDate.isBefore(now) &&
            now.add(EXAM_START_WAIT_TIME_MINUTES, 'minute').isAfter(this.exam.startDate!)
        );
    }

    get endButtonEnabled(): boolean {
        return this.nameIsCorrect && this.confirmed && this.exam && this.handInPossible;
    }

    get nameIsCorrect(): boolean {
        console.log('entered name: ' + this.enteredName.trim());
        console.log('account name: ' + this.accountName.trim());
        return this.enteredName.trim() === this.accountName.trim();
    }

    get inserted(): boolean {
        return this.enteredName.trim() !== '';
    }

    /**
     * Returns whether the student failed to submit on time. In this case the end page is adapted.
     */
    get studentFailedToSubmit(): boolean {
        return false;
        // if (this.testRun) {
        //     return false;
        // }
        // let individualStudentEndDate;
        // if (this.exam?.testExam) {
        //     if (!this.studentExam.submitted && this.studentExam.started && this.studentExam.startedDate) {
        //         individualStudentEndDate = dayjs(this.studentExam.startedDate).add(this.studentExam.workingTime!, 'seconds');
        //     } else {
        //         return false;
        //     }
        // } else {
        //     individualStudentEndDate = dayjs(this.exam?.startDate).add(this.studentExam.workingTime!, 'seconds');
        // }
        // return individualStudentEndDate.add(this.exam?.gracePeriod!, 'seconds').isBefore(this.serverDateService.now()) && !this.studentExam.submitted;
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

    isVisible(): boolean {
        if (this.testRunId) {
            return true;
        }
        if (!this.exam) {
            return false;
        }
        return this.exam.visibleDate ? this.exam.visibleDate.isBefore(this.serverDateService.now()) : false;
    }

    isActive(): boolean {
        if (this.testRunId) {
            return true;
        }
        if (!this.exam) {
            return false;
        }
        return this.exam.startDate ? this.exam.startDate.isBefore(this.serverDateService.now()) : false;
    }

    handleStudentExam(studentExam: StudentExam) {
        this.studentExam = studentExam;
        this.exam = studentExam.exam!;
        this.testExam = this.exam.testExam!;
        if (!this.exam.testExam) {
            this.initIndividualEndDates(this.exam.startDate!);
        }

        // only show the summary if the student was able to submit on time.
        if (this.isOver() && this.studentExam.submitted) {
            this.loadAndDisplaySummary();
        } else {
            // Directly start the exam when we continue from a failed save
            if (this.examParticipationService.lastSaveFailed(this.courseId, this.examId)) {
                this.examParticipationService.loadStudentExamWithExercisesForConductionFromLocalStorage(this.courseId, this.examId).subscribe((localExam: StudentExam) => {
                    // Keep the working time from the server
                    localExam.workingTime = this.studentExam.workingTime ?? localExam.workingTime;

                    this.studentExam = localExam;
                    this.loadingExam = false;
                    //this.examStarted(this.studentExam);
                });
            } else {
                this.loadingExam = false;
            }
        }
    }

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
     * Handles the case when there is no student exam. Here we have to check if the user is at least tutor to show the redirect to the exam management page.
     * This check is not done in the normal case due to performance reasons of 2000 students sending additional requests
     */
    handleNoStudentExam() {
        const course = this.courseStorageService.getCourse(this.courseId);
        if (!course) {
            this.courseService.find(this.courseId).subscribe((courseResponse) => {
                this.isAtLeastTutor = courseResponse.body?.isAtLeastTutor;
                this.isAtLeastInstructor = courseResponse.body?.isAtLeastInstructor;
            });
        } else {
            this.isAtLeastTutor = course.isAtLeastTutor;
            this.isAtLeastInstructor = course.isAtLeastInstructor;
        }
        this.loadingExam = false;
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

    isStartViewEnded() {
        if (!this.studentExam.submitted && ((this.isOver() && this.examStartConfirmed) || this.isGracePeriodOver())) {
            this.startView = false;
        }
        this.startView = true;
    }
}
