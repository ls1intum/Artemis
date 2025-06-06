import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { TranslateService } from '@ngx-translate/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import dayjs from 'dayjs/esm';
import { EXAM_START_WAIT_TIME_MINUTES } from 'app/app.constants';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { faArrowLeft, faCircleExclamation, faDoorClosed, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { NgClass } from '@angular/common';
import { ExamLiveEventsButtonComponent } from '../events/button/exam-live-events-button.component';
import { ExamStartInformationComponent } from '../exam-start-information/exam-start-information.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-participation-cover',
    templateUrl: './exam-participation-cover.component.html',
    styleUrls: ['./exam-participation-cover.scss'],
    imports: [NgClass, ExamLiveEventsButtonComponent, ExamStartInformationComponent, FormsModule, TranslateDirective, FaIconComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class ExamParticipationCoverComponent implements OnChanges, OnDestroy, OnInit {
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private translateService = inject(TranslateService);
    private accountService = inject(AccountService);
    private examParticipationService = inject(ExamParticipationService);
    private serverDateService = inject(ArtemisServerDateService);

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
    @Input() attendanceChecked = false;
    @Input() testRunStartTime: dayjs.Dayjs | undefined;
    @Input() isProduction = true;
    @Input() isTestServer = false;
    @Output() onExamStarted: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    @Output() onExamEnded: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    @Output() onExamContinueAfterHandInEarly = new EventEmitter<void>();
    course?: Course;
    startEnabled: boolean;
    endEnabled: boolean;
    confirmed: boolean;
    isAttendanceChecked: boolean;

    testRun?: boolean;
    testExam?: boolean;

    formattedGeneralInformation?: SafeHtml;
    formattedConfirmationText?: SafeHtml;

    interval: number;
    waitingForExamStart = false;
    isFetching = false;
    loadExamSubscription?: Subscription;
    timeUntilStart = '0';

    accountName = '';
    enteredName = '';

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;
    faCircleExclamation = faCircleExclamation;
    faDoorClosed = faDoorClosed;

    ngOnInit(): void {
        this.isAttendanceChecked = this.exam.testExam || !this.exam.examWithAttendanceCheck || this.attendanceChecked;
    }

    /**
     * on changes uses the correct information to display in either start or final view
     * changes in the exam and subscription is handled in the exam-participation.component
     * if the student exam changes, we need to update the displayed times
     */
    ngOnChanges() {
        this.confirmed = false;
        this.startEnabled = false;
        this.testRun = this.studentExam.testRun;
        this.testExam = this.exam.testExam;

        if (this.startView) {
            this.examParticipationService.setEndView(false);
            this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
        } else {
            this.examParticipationService.setEndView(true);
            this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
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
        this.loadExamSubscription?.unsubscribe();
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
            this.isFetching = true;
            this.loadExamSubscription = this.examParticipationService
                .loadStudentExamWithExercisesForConduction(this.exam.course!.id!, this.exam.id!, this.studentExam.id!)
                .subscribe((studentExam: StudentExam) => {
                    this.isFetching = false;
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
     * @param remainingTimeSeconds the amount of seconds to display
     * @return humanized text for the given amount of seconds
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
        this.examParticipationService.setEndView(false);
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
}
