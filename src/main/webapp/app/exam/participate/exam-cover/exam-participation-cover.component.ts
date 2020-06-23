import { Component, Input, OnInit, OnDestroy, EventEmitter, Output } from '@angular/core';
import * as moment from 'moment';
import { SafeHtml } from '@angular/platform-browser';

import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';

import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageService } from 'ngx-webstorage';
import { ExamSessionService } from 'app/exam/manage/exam-session/exam-session.service';
import { ActivatedRoute } from '@angular/router';

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
    @Output() onExamStarted: EventEmitter<void> = new EventEmitter<void>();
    course: Course | null;
    courseId: number;
    title: string; // needed? unused at the moment
    startEnabled: boolean;
    confirmed: boolean;
    examId: number;

    formattedGeneralInformation: SafeHtml | null;
    formattedConfirmationText: SafeHtml | null;

    interval: any;
    waitingForExamStart = false;
    timeUntilStart = '0';
    formattedStartDate = '';

    fullname?: string;
    falseName = false;

    constructor(
        private courseService: CourseManagementService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private accountService: AccountService,
        private sessionStorage: SessionStorageService,
        private examSessionService: ExamSessionService,
        private route: ActivatedRoute,
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
        this.formattedStartDate = this.exam.startDate ? moment(this.exam.startDate).format('LT') : '';
        this.interval = setInterval(() => {
            this.updateDisplayedTimes();
        }, 100);

        this.route.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            this.examId = +params['examId'];
        });
    }

    ngOnDestroy() {
        clearInterval(this.interval);
    }

    /**
     * checks whether confirmation checkbox has been checked
     * if startView true:
     * if confirmed, we further check whether exam has started yet regularly
     */
    updateConfirmation() {
        this.confirmed = !this.confirmed;
        if (this.startView) {
            if (this.confirmed) {
                this.interval = setInterval(() => {
                    this.enableStartButton().then((enable) => (this.startEnabled = enable));
                }, 100);
            } else {
                this.startEnabled = false;
            }
        }
    }

    /**
     * check, whether exam has started yet and we therefore can enable the Start Exam Button
     */
    async enableStartButton() {
        let fullname = '';
        await this.accountService.identity().then((user) => {
            fullname = user?.name ?? '';
        });

        if (this.fullname && this.fullname !== fullname) {
            this.falseName = true;
        } else {
            this.falseName = false;
        }

        if (this.fullname && this.fullname === fullname && this.confirmed && this.exam && this.exam.startDate && moment(this.exam.startDate).isBefore(moment())) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * check if exam already started
     */
    notStarted(): boolean {
        if (!this.exam) {
            return false;
        }
        return this.exam.startDate ? moment(this.exam.startDate).isAfter(moment()) : false;
    }

    /**
     * TODO: add session management, this function is bound to the start exam button
     */
    startExam() {
        if (this.notStarted()) {
            this.waitingForExamStart = true;
        } else {
            this.examSessionService.createExamSession(this.courseId, this.examId).subscribe((response) => {
                const sessionToken = response.body?.sessionToken ?? '';
                this.sessionStorage.store('ExamSessionToken', sessionToken);
                const localSessionToken = this.sessionStorage.retrieve('ExamSessionToken');
                console.log('Session Token', localSessionToken);

                this.onExamStarted.emit();
            });
        }
    }

    /**
     * updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes() {
        const translationBasePath = 'showStatistic.';
        // update time until start
        if (this.exam && this.exam.startDate) {
            if (this.notStarted()) {
                this.timeUntilStart = this.relativeTimeText(moment(this.exam.startDate).diff(moment(), 'seconds'));
            } else {
                this.timeUntilStart = this.translateService.instant(translationBasePath + 'now');
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
        this.examSessionService.getCurrentExamSession(this.courseId, this.examId).subscribe((response) => {
            const localSessionToken = this.sessionStorage.retrieve('ExamSessionToken');
            const validSessionToken = response.body?.sessionToken ?? '';

            if (validSessionToken && localSessionToken === validSessionToken) {
                console.log(validSessionToken + ' is the same as ' + localSessionToken);
                // TODO: submit exam
            } else {
                console.log('Something went wrong');
                // error message
            }
        });
    }
}
