import { ChangeDetectorRef, Component, ContentChild, Input, OnChanges, SimpleChanges, TemplateRef } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course, isMessagingEnabled } from 'app/entities/course.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { getDayTranslationKey } from '../weekdays';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TranslateService } from '@ngx-translate/core';
import { faCircle, faCircleInfo, faCircleXmark, faPercent, faQuestionCircle, faUserCheck } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { SortService } from 'app/shared/service/sort.service';
import { getInitialsFromString } from 'app/utils/text.utils';
import { getBackgroundColorHue } from 'app/utils/color.utils';

@Component({
    selector: 'jhi-tutorial-group-detail',
    templateUrl: './tutorial-group-detail.component.html',
    styleUrls: ['./tutorial-group-detail.component.scss'],
})
export class TutorialGroupDetailComponent implements OnChanges {
    @ContentChild(TemplateRef, { static: true }) header: TemplateRef<any>;

    @Input()
    timeZone?: string = undefined;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    course: Course;
    formattedAdditionalInformation?: SafeHtml;

    readonly Math = Math;

    sessions: TutorialGroupSession[] = [];

    tutorInitials: string;
    tutorialTimeslotString: string | undefined;
    tutorDefaultProfilePictureHue: string;
    isMessagingEnabled: boolean;
    utilization: number | undefined;

    // Icons
    readonly faUserCheck = faUserCheck;
    readonly faPercent = faPercent;
    readonly faCircleInfo = faCircleInfo;
    readonly faQuestionCircle = faQuestionCircle;
    readonly faCircle = faCircle;
    readonly faCircleXmark = faCircleXmark;

    constructor(
        private artemisMarkdownService: ArtemisMarkdownService,
        private changeDetectorRef: ChangeDetectorRef,
        private translateService: TranslateService,
        private sortService: SortService,
    ) {}

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName) && propName === 'tutorialGroup') {
                const change = changes[propName];

                if (change.currentValue && change.currentValue.additionalInformation) {
                    this.formattedAdditionalInformation = this.artemisMarkdownService.safeHtmlForMarkdown(this.tutorialGroup.additionalInformation);
                }
                if (change.currentValue && change.currentValue.tutorialGroupSessions) {
                    this.sessions = change.currentValue.tutorialGroupSessions;
                }
                this.changeDetectorRef.detectChanges();
            }
        }
        this.getTutorialDetail();
    }

    getTutorialTimeSlotString(): string | undefined {
        if (!this.tutorialGroup.tutorialGroupSchedule) {
            return undefined;
        }
        const day = this.translateService.instant(getDayTranslationKey(this.tutorialGroup.tutorialGroupSchedule?.dayOfWeek));
        const start = this.tutorialGroup.tutorialGroupSchedule?.startTime?.split(':').slice(0, 2).join(':');
        const end = this.tutorialGroup.tutorialGroupSchedule?.endTime?.split(':').slice(0, 2).join(':');
        const repetition = this.translateService.instant(
            this.tutorialGroup.tutorialGroupSchedule!.repetitionFrequency! === 1
                ? 'artemisApp.entities.tutorialGroupSchedule.repetitionOneWeek'
                : 'artemisApp.entities.tutorialGroupSchedule.repetitionNWeeks',
            { n: this.tutorialGroup.tutorialGroupSchedule!.repetitionFrequency! },
        );
        return `${day} ${start}-${end}, ${repetition}`;
    }

    getTutorialDetail() {
        const tutorialGroup = this.tutorialGroup;

        this.tutorDefaultProfilePictureHue = getBackgroundColorHue(tutorialGroup.teachingAssistantId ? tutorialGroup.teachingAssistantId.toString() : 'default');
        this.tutorInitials = getInitialsFromString(tutorialGroup.teachingAssistantName ?? 'NA');
        this.isMessagingEnabled = isMessagingEnabled(this.course);
        if (tutorialGroup.averageAttendance && tutorialGroup.capacity) {
            this.utilization = Math.round((tutorialGroup.averageAttendance / tutorialGroup.capacity) * 100);
        } else {
            this.utilization = undefined;
        }
        this.tutorialTimeslotString = this.getTutorialTimeSlotString();
    }

    recalculateAttendanceDetails() {
        let activeAndFinishedSessions =
            this.tutorialGroup.tutorialGroupSessions?.filter((session) => session.status === TutorialGroupSessionStatus.ACTIVE && dayjs().isAfter(session.end)) ?? [];
        activeAndFinishedSessions = this.sortService.sortByProperty(activeAndFinishedSessions, 'start', false);
        const relevantSessions = activeAndFinishedSessions.slice(0, 3).filter((session) => session.attendanceCount !== undefined);

        if (relevantSessions.length) {
            const averageAttendance = relevantSessions.map((session) => session.attendanceCount ?? 0).reduce((acc, attendance) => acc + attendance) / relevantSessions.length;
            this.tutorialGroup.averageAttendance = Math.round(averageAttendance);
        }

        this.getTutorialDetail();
    }
}
