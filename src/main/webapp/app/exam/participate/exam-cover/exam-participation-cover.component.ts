import { Component, Input, OnInit, OnDestroy, EventEmitter, Output } from '@angular/core';
import * as moment from 'moment';
import { SafeHtml } from '@angular/platform-browser';

import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';

import { Exam } from 'app/entities/exam.model';
import { Course } from 'app/entities/course.model';

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
    courseId = 0;
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

    constructor(private courseService: CourseManagementService, private artemisMarkdown: ArtemisMarkdownService, private translateService: TranslateService) {}

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
                    this.startEnabled = this.enableStartButton();
                }, 100);
            } else {
                this.startEnabled = false;
            }
        }
    }

    /**
     * check, whether exam has started yet and we therefore can enable the Start Exam Button
     */
    enableStartButton() {
        if (this.confirmed && this.exam && this.exam.startDate && moment(this.exam.visibleDate).isBefore(moment())) {
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
            this.onExamStarted.emit();
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
}
