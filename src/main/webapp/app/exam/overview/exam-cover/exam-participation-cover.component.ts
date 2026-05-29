import { Component, OnDestroy, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { TranslateService } from '@ngx-translate/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Course } from 'app/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import dayjs from 'dayjs/esm';
import { EXAM_START_WAIT_TIME_MINUTES } from 'app/app.constants';
import { UI_RELOAD_TIME } from 'app/foundation/constants/exercise-exam-constants';
import { faArrowLeft, faCircleExclamation, faDoorClosed, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { NgClass } from '@angular/common';
import { ExamLiveEventsButtonComponent } from '../events/button/exam-live-events-button.component';
import { ExamStartInformationComponent } from '../exam-start-information/exam-start-information.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-participation-cover',
    templateUrl: './exam-participation-cover.component.html',
    styleUrls: ['./exam-participation-cover.scss'],
    imports: [NgClass, ExamLiveEventsButtonComponent, ExamStartInformationComponent, FormsModule, TranslateDirective, FaIconComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class ExamParticipationCoverComponent implements OnDestroy, OnInit {
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private translateService = inject(TranslateService);
    private accountService = inject(AccountService);
    private examParticipationService = inject(ExamParticipationService);
    private serverDateService = inject(ArtemisServerDateService);

    /**
     * if startView is set to true: startText and confirmationStartText will be displayed
     * if startView is set to false: endText and confirmationEndText will be displayed
     */
    readonly startView = input<boolean>(undefined!);
    readonly exam = input<Exam>(undefined!);
    readonly studentExam = input<StudentExam>(undefined!);
    readonly handInEarly = input(false);
    readonly handInPossible = input(true);
    readonly submitInProgress = input(false);
    readonly attendanceChecked = input(false);
    readonly testRunStartTime = input<dayjs.Dayjs>();
    readonly onExamStarted = output<StudentExam>();
    readonly onExamEnded = output<StudentExam>();
    readonly onExamContinueAfterHandInEarly = output<void>();
    course?: Course;
    readonly startEnabled = signal(false);
    readonly endEnabled = signal(false);
    confirmed = false;
    readonly isAttendanceChecked = signal(false);

    readonly testRun = computed(() => this.studentExam()?.testRun);
    readonly testExam = computed(() => this.exam()?.testExam);

    readonly formattedGeneralInformation = signal<SafeHtml | undefined>(undefined);
    readonly formattedConfirmationText = signal<SafeHtml | undefined>(undefined);

    interval: number;
    readonly waitingForExamStart = signal(false);
    readonly isFetching = signal(false);
    loadExamSubscription?: Subscription;
    readonly timeUntilStart = signal('0');

    readonly accountName = signal('');
    enteredName = '';

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;
    faCircleExclamation = faCircleExclamation;
    faDoorClosed = faDoorClosed;

    constructor() {
        // mirror previous ngOnChanges behavior reactively
        effect(() => {
            const exam = this.exam();
            const studentExam = this.studentExam();
            if (!exam || !studentExam) {
                return;
            }
            this.confirmed = false;
            this.startEnabled.set(false);

            if (this.startView()) {
                this.examParticipationService.setEndView(false);
                this.formattedGeneralInformation.set(this.artemisMarkdown.safeHtmlForMarkdown(exam.startText));
                this.formattedConfirmationText.set(this.artemisMarkdown.safeHtmlForMarkdown(exam.confirmationStartText));
            } else {
                this.examParticipationService.setEndView(true);
                this.formattedGeneralInformation.set(this.artemisMarkdown.safeHtmlForMarkdown(exam.endText));
                this.formattedConfirmationText.set(this.artemisMarkdown.safeHtmlForMarkdown(exam.confirmationEndText));
            }

            this.accountService.identity().then((user) => {
                if (user && user.name) {
                    this.accountName.set(user.name);
                }
            });
        });
    }

    ngOnInit(): void {
        const exam = this.exam();
        this.isAttendanceChecked.set(exam.testExam || !exam.examWithAttendanceCheck || this.attendanceChecked());
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
        if (this.startView()) {
            this.startEnabled.set(this.confirmed);
        } else {
            this.endEnabled.set(this.confirmed);
        }
    }

    /**
     * check if exam already started
     */
    hasStarted(): boolean {
        if (this.testRun()) {
            return true;
        }
        const exam = this.exam();
        return exam?.startDate ? exam.startDate.isBefore(this.serverDateService.now()) : false;
    }

    /**
     * displays popup or start exam participation immediately
     */
    startExam() {
        if (this.testRun()) {
            const studentExam = this.studentExam();
            this.examParticipationService.saveStudentExamToLocalStorage(this.exam().course!.id!, this.exam().id!, studentExam);
            this.onExamStarted.emit(studentExam);
        } else {
            this.isFetching.set(true);
            this.loadExamSubscription = this.examParticipationService
                .loadStudentExamWithExercisesForConduction(this.exam().course!.id!, this.exam().id!, this.studentExam().id!)
                .subscribe((studentExam: StudentExam) => {
                    this.isFetching.set(false);
                    this.examParticipationService.saveStudentExamToLocalStorage(this.exam().course!.id!, this.exam().id!, studentExam);
                    if (this.hasStarted()) {
                        this.onExamStarted.emit(studentExam);
                    } else {
                        this.waitingForExamStart.set(true);
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
        const exam = this.exam();
        if (exam && exam.startDate) {
            if (this.hasStarted()) {
                this.timeUntilStart.set(this.translateService.instant(translationBasePath + 'now'));
                this.onExamStarted.emit(studentExam);
            } else {
                this.timeUntilStart.set(this.relativeTimeText(exam.startDate.diff(this.serverDateService.now(), 'seconds')));
            }
        } else {
            this.timeUntilStart.set('');
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
        this.onExamEnded.emit(this.studentExam());
    }

    /**
     * Notify the parent component that the user wants to continue after hand in early
     */
    continueAfterHandInEarly() {
        this.examParticipationService.setEndView(false);
        this.onExamContinueAfterHandInEarly.emit();
    }

    get startButtonEnabled(): boolean {
        if (this.testRun()) {
            return this.nameIsCorrect && this.confirmed && !!this.exam();
        }
        const now = this.serverDateService.now();
        const exam = this.exam();
        return !!(
            this.nameIsCorrect &&
            this.confirmed &&
            exam &&
            exam.visibleDate &&
            exam.visibleDate.isBefore(now) &&
            now.add(EXAM_START_WAIT_TIME_MINUTES, 'minute').isAfter(exam.startDate!)
        );
    }

    get endButtonEnabled(): boolean {
        return this.nameIsCorrect && this.confirmed && !!this.exam() && this.handInPossible();
    }

    get nameIsCorrect(): boolean {
        return this.enteredName.trim() === this.accountName().trim();
    }

    get inserted(): boolean {
        return this.enteredName.trim() !== '';
    }
}
