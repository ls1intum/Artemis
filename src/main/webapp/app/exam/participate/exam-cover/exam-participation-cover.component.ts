import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import * as moment from 'moment';
import { SafeHtml } from '@angular/platform-browser';

import { ArtemisMarkdownService } from 'app/shared/markdown.service';

import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageService } from 'ngx-webstorage';
import { ExamSessionService } from 'app/exam/manage/exam-session/exam-session.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exam-participation-cover',
    templateUrl: './exam-participation-cover.component.html',
    styles: [],
})
export class ExamParticipationCoverComponent implements OnInit, OnDestroy {
    /**
     * if startView is set to true: startText and confirmationStartText will be displayed
     * if startView is set to false: endText and confirmationEndText will be displayed
     */
    @Input() startView: boolean;
    @Input() exam: Exam;
    course: Course | null;
    courseId: number;
    title: string;
    startEnabled: boolean;
    confirmed: boolean;
    examId: number;

    formattedGeneralInformation: SafeHtml | null;
    formattedConfirmationText: SafeHtml | null;

    interval: any;

    fullname?: string;
    falseName = false;

    constructor(
        private artemisMarkdown: ArtemisMarkdownService,
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
     * Starts the exam and creates an exam session and stores the token in the browser
     */
    startExam() {
        this.examSessionService.createExamSession(this.courseId, this.examId).subscribe((response) => {
            const sessionToken = response.body?.sessionToken ?? '';
            this.sessionStorage.store('ExamSessionToken', sessionToken);
            const localSessionToken = this.sessionStorage.retrieve('ExamSessionToken');
            console.log('Session Token', localSessionToken);

            // TODO start exam
        });
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
