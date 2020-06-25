import { Component, Input, OnInit, OnDestroy, EventEmitter, Output } from '@angular/core';
import * as moment from 'moment';
import { SafeHtml } from '@angular/platform-browser';

import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';

import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';

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
    @Output() onExamStarted: EventEmitter<StudentExam> = new EventEmitter<StudentExam>();
    course: Course | null;
    startEnabled: boolean;
    confirmed: boolean;

    formattedGeneralInformation: SafeHtml | null;
    formattedConfirmationText: SafeHtml | null;

    interval: number;
    waitingForExamStart = false;
    timeUntilStart = '0';
    formattedStartDate = '';

    accountName = '';
    enteredName?: string;

    constructor(
        private courseService: CourseManagementService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private accountService: AccountService,
        private examParticipationService: ExamParticipationService,
    ) {}

    /**
     * on init use the correct information to display in either start or final view
     * changes in the exam and subscription is handled in the exam-participation.component
     */
    ngOnInit(): void {
        this.confirmed = false;
        this.startEnabled = false;
        if (this.startView) {
            this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
        } else {
            this.formattedGeneralInformation = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
        }
        this.formattedStartDate = this.exam.startDate ? this.exam.startDate.format('LT') : '';

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
        }
    }

    /**
     * check if exam already started
     */
    hasStarted(): boolean {
        return this.exam?.startDate ? this.exam.startDate.isBefore(moment()) : false;
    }

    /**
     * displays popup or start exam participation immediately
     */
    startExam() {
        this.examParticipationService.loadStudentExam(this.exam.course.id, this.exam.id).subscribe((studentExam: StudentExam) => {
            this.examParticipationService.saveStudentExamToLocalStorage(this.exam.course.id, this.exam.id, studentExam);
            if (this.hasStarted()) {
                this.onExamStarted.emit(studentExam);
            } else {
                this.waitingForExamStart = true;
                this.interval = window.setInterval(() => {
                    this.updateDisplayedTimes(studentExam);
                }, 100);
            }
        });
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
                this.timeUntilStart = this.relativeTimeText(this.exam.startDate.diff(moment(), 'seconds'));
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
     * Submits the exam if user has valid token
     */
    submit() {
        // TODO: refactor following code
        // this.examSessionService.getCurrentExamSession(this.courseId, this.examId).subscribe((response) => {
        //     const localSessionToken = this.sessionStorage.retrieve('ExamSessionToken');
        //     const validSessionToken = response.body?.sessionToken ?? '';
        //     if (validSessionToken && localSessionToken === validSessionToken) {
        //         console.log(validSessionToken + ' is the same as ' + localSessionToken);
        //         // TODO: submit exam
        //     } else {
        //         console.log('Something went wrong');
        //         // error message
        //     }
        // });
    }

    get startButtonEnabled(): boolean {
        return !!(!this.falseName && this.confirmed && this.exam && this.exam.visibleDate && this.exam.visibleDate.isBefore(moment()));
    }

    get falseName(): boolean {
        return this.enteredName !== this.accountName;
    }
}
