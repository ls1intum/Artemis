import { Component, OnDestroy, OnInit } from '@angular/core';
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
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ExamLiveEventType, ExamParticipationLiveEventsService, WorkingTimeUpdateEvent } from 'app/exam/participate/exam-participation-live-events.service';

@Component({
    selector: 'jhi-exam-participation-cover',
    templateUrl: './exam-participation-cover.component.html',
    styleUrls: ['./exam-participation-cover.scss'],
})
export class ExamParticipationCoverComponent implements OnDestroy, OnInit {
    handInEarly: boolean;
    handInPossible: boolean;
    submitInProgress: boolean;
    attendanceChecked: boolean;
    examStartConfirmed = false;

    course?: Course;
    startEnabled: boolean;
    endEnabled: boolean;
    confirmed: boolean;
    isAttendanceChecked: boolean;

    exam: Exam;
    studentExam: StudentExam;

    testRun?: boolean;
    testExam?: boolean;

    formattedGeneralInformation: SafeHtml;
    formattedConfirmationText?: SafeHtml;

    interval: number;
    waitingForExamStart = false;
    timeUntilStart = '0';

    accountName = '';
    enteredName = '';

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;

    courseId: number;
    examId: number;
    testRunId: number;
    studentExamId: number;
    loadingExam: boolean;

    liveEventsSubscription?: Subscription;

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
        private liveEventsService: ExamParticipationLiveEventsService,
    ) {}

    ngOnInit(): void {
        this.route.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
            this.examParticipationService.setExamState({ courseId: this.courseId });
        });

        this.examParticipationService.examState$.subscribe((state) => {
            this.examStartConfirmed = state.examStartConfirmed;
            this.handInEarly = state.handInEarly;
            this.handInPossible = state.handInPossible;
            this.submitInProgress = state.submitInProgress;
            this.attendanceChecked = state.attendanceChecked;
        });

        this.route.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
            this.testRunId = parseInt(params['testRunId'], 10);
            this.examParticipationService.setExamState({ examId: this.examId });
            this.examParticipationService.setExamState({ testRunId: this.testRunId });
            // As a student can have multiple test exams, the studentExamId is passed as a parameter.
            if (params['studentExamId']) {
                // If a new StudentExam should be created, the keyword start is used (and no StudentExam exists)
                this.testExam = true;
                if (params['studentExamId'] !== 'start') {
                    this.studentExamId = parseInt(params['studentExamId'], 10);
                    this.examParticipationService.setExamState({ studentExamId: this.studentExamId });
                }
            }
            this.loadingExam = true;
            if (this.testRunId) {
                this.route.parent?.parent?.params.subscribe((params) => {
                    this.courseId = parseInt(params['courseId'], 10);
                    this.examParticipationService.setExamState({ courseId: this.courseId });
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

        this.isAttendanceChecked = this.exam?.testExam || !this.exam?.examWithAttendanceCheck || this.attendanceChecked;

        this.accountService.identity().then((user) => {
            if (user && user.name) {
                this.accountName = user.name;
                this.examParticipationService.setExamState({ accountName: this.accountName });
            }
        });

        this.generateInformationForHtml();
    }

    generateInformationForHtml() {
        this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam?.startText);
        this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam?.confirmationStartText);
    }

    /**
     * on changes uses the correct information to display in either start or final view
     * changes in the exam and subscription is handled in the exam-participation.component
     * if the student exam changes, we need to update the displayed times
     */

    /*
    ngOnChanges(): void {
        this.confirmed = false;
        this.startEnabled = false;
        this.testRun = this.studentExam.testRun;
        this.testExam = this.exam.testExam;

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
                this.accountName = user.name;
            }
        });
    }
    */

    ngOnDestroy() {
        if (this.interval) {
            clearInterval(this.interval);
        }
        this.liveEventsSubscription?.unsubscribe();
    }

    /**
     * checks whether confirmation checkbox has been checked
     * if confirmed, we further check whether exam has started yet regularly
     */
    updateConfirmation() {
        this.startEnabled = this.confirmed;
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
     * loads the exam from the server and initializes the view
     */
    startExam() {
        if (this.testRun) {
            this.examParticipationService.saveStudentExamToLocalStorage(this.exam.course!.id!, this.exam.id!, this.studentExam);
            // this.onExamStarted.emit(this.studentExam);
            this.examParticipationService.emitExamStarted(this.studentExam);
            this.router.navigate(['courses', this.courseId, 'exams', this.examId, 'participation']);
        } else {
            this.examParticipationService.loadStudentExamWithExercisesForConduction(this.courseId, this.examId, this.studentExam.id!).subscribe((studentExam: StudentExam) => {
                this.studentExam = studentExam;
                this.examParticipationService.saveStudentExamToLocalStorage(this.courseId, this.examId, studentExam);
                if (this.hasStarted()) {
                    //this.onExamStarted.emit(studentExam);
                    this.examParticipationService.emitExamStarted(studentExam);
                    this.examParticipationService.setExamState({ exercises: studentExam.exercises! });
                    this.router.navigate(['courses', this.courseId, 'exams', this.examId, 'participation']);
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
        //this.router.navigate(['courses', this.courseId, 'exams', this.examId, 'participation']);
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
                //this.onExamStarted.emit(studentExam);
                this.examParticipationService.emitExamStarted(studentExam);
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

    get nameIsCorrect(): boolean {
        return this.enteredName.trim() === this.accountName.trim();
    }

    get inserted(): boolean {
        return this.enteredName.trim() !== '';
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
        this.examParticipationService.setExamState({ exam: this.exam });
        this.examParticipationService.setExamState({ studentExam: this.studentExam });
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
                    this.examParticipationService.emitExamStarted(this.studentExam);
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
}
