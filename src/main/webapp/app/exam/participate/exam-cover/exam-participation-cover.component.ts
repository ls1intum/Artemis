import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
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

@Component({
    selector: 'jhi-exam-participation-cover',
    templateUrl: './exam-participation-cover.component.html',
    styleUrls: ['./exam-participation-cover.scss'],
})
export class ExamParticipationCoverComponent implements OnInit, OnDestroy {
    /**
     * if startView is set to true: startText and confirmationStartText will be displayed
     * if startView is set to false: endText and confirmationEndText will be displayed
     */
    @Input() startView: boolean;
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;
    @Input() handInEarly = false;
    @Input() handInPossible = true;
    @Input() submitInProgress = false;
    @Input() testRunStartTime: dayjs.Dayjs | undefined;
    @Output() onExamStarted: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    @Output() onExamEnded: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    @Output() onExamContinueAfterHandInEarly = new EventEmitter<void>();
    course?: Course;
    startEnabled: boolean;
    endEnabled: boolean;
    confirmed: boolean;

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

    constructor(
        private courseService: CourseManagementService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private accountService: AccountService,
        private examParticipationService: ExamParticipationService,
        private serverDateService: ArtemisServerDateService,
    ) {}

    /**
     * on init uses the correct information to display in either start or final view
     * changes in the exam and subscription is handled in the exam-participation.component
     */
    ngOnInit(): void {
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
                this.graceEndDate = dayjs(this.testRunStartTime!).add(this.studentExam.workingTime!, 'seconds').add(this.exam.gracePeriod!, 'seconds');
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
        const translationBasePath = 'showStatistic.';
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
        return this.enteredName.trim() === this.accountName.trim();
    }

    get inserted(): boolean {
        return this.enteredName.trim() !== '';
    }

    /**
     * Returns whether the student failed to submit on time. In this case the end page is adapted.
     */
    get studentFailedToSubmit(): boolean {
        if (this.testRun) {
            return false;
        }
        let individualStudentEndDate;
        if (this.exam.testExam) {
            if (!this.studentExam.submitted && this.studentExam.started && this.studentExam.startedDate) {
                individualStudentEndDate = dayjs(this.studentExam.startedDate).add(this.studentExam.workingTime!, 'seconds');
            } else {
                return false;
            }
        } else {
            individualStudentEndDate = dayjs(this.exam.startDate).add(this.studentExam.workingTime!, 'seconds');
        }
        return individualStudentEndDate.add(this.exam.gracePeriod!, 'seconds').isBefore(this.serverDateService.now()) && !this.studentExam.submitted;
    }
}
