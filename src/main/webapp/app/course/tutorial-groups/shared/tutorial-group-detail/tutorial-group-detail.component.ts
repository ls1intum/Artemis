import { ChangeDetectorRef, Component, ContentChild, Input, OnChanges, SimpleChanges, TemplateRef } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course, isMessagingEnabled } from 'app/entities/course.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { getDayTranslationKey } from '../weekdays';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Detail, DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { TranslateService } from '@ngx-translate/core';

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
    getDayTranslationKey = getDayTranslationKey;

    faQuestionCircle = faQuestionCircle;
    readonly Math = Math;

    sessions: TutorialGroupSession[] = [];

    tutorialDetailSections: DetailOverviewSection[];

    constructor(
        private artemisMarkdownService: ArtemisMarkdownService,
        private changeDetectorRef: ChangeDetectorRef,
        private translateService: TranslateService,
    ) {}

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            // eslint-disable-next-line no-prototype-builtins
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
        this.getTutorialDetailSections();
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

    getTutorialDetailSections() {
        const tutorialGroup = this.tutorialGroup;
        const tutorialDetails: Detail[] = [
            { type: DetailType.Link, title: 'artemisApp.entities.tutorialGroup.course', data: { text: tutorialGroup.courseTitle, routerLink: ['../..'] } },
            { type: DetailType.Text, title: 'artemisApp.entities.tutorialGroup.title', data: { text: tutorialGroup.title } },
        ];

        if (tutorialGroup.channel && isMessagingEnabled(this.course)) {
            tutorialDetails.push({
                type: DetailType.Link,
                title: 'artemisApp.entities.tutorialGroup.channel',
                data: {
                    text: tutorialGroup.channel.name,
                    routerLink: tutorialGroup.channel.isMember && ['/courses', this.course.id!, 'messages'],
                    queryParams: { conversationId: tutorialGroup.channel.id },
                },
            });
        }
        tutorialDetails.push(
            { type: DetailType.Text, title: 'artemisApp.entities.tutorialGroup.teachingAssistant', data: { text: tutorialGroup.teachingAssistantName } },
            {
                type: DetailType.Text,
                title: 'artemisApp.entities.tutorialGroup.utilization',
                titleHelpText: 'artemisApp.entities.tutorialGroup.utilizationHelpDetail',
                data: { text: tutorialGroup.averageAttendance && tutorialGroup.capacity && Math.round((tutorialGroup.averageAttendance / tutorialGroup.capacity) * 100) },
            },
            {
                type: DetailType.Text,
                title: 'artemisApp.entities.tutorialGroup.averageAttendanceDetail',
                titleHelpText: 'artemisApp.entities.tutorialGroup.averageAttendanceHelpDetail',
                data: { text: tutorialGroup.averageAttendance && Math.round(tutorialGroup.averageAttendance) },
            },
            { type: DetailType.Text, title: 'artemisApp.entities.tutorialGroup.capacity', data: { text: tutorialGroup.capacity } },
            { type: DetailType.Text, title: 'artemisApp.entities.tutorialGroup.registrations', data: { text: tutorialGroup.numberOfRegisteredUsers } },
            { type: DetailType.Boolean, title: 'artemisApp.entities.tutorialGroup.isOnline', data: { boolean: tutorialGroup.isOnline } },
            { type: DetailType.Text, title: 'artemisApp.entities.tutorialGroup.language', data: { text: tutorialGroup.language } },
            { type: DetailType.Text, title: 'artemisApp.entities.tutorialGroup.campus', data: { text: tutorialGroup.campus } },
            { type: DetailType.Markdown, title: 'artemisApp.entities.tutorialGroup.additionalInformation', data: { innerHtml: this.formattedAdditionalInformation } },
            { type: DetailType.Text, title: 'artemisApp.entities.tutorialGroup.schedule', data: { text: this.getTutorialTimeSlotString() } },
        );
        if (tutorialGroup.isOnline) {
            tutorialDetails.push({
                type: DetailType.Text,
                title: 'artemisApp.forms.scheduleForm.locationInput.labelOnline',
                data: { text: tutorialGroup.tutorialGroupSchedule?.location },
            });
        } else {
            tutorialDetails.push({
                type: DetailType.Text,
                title: 'artemisApp.forms.scheduleForm.locationInput.labelOffline',
                data: { text: tutorialGroup.tutorialGroupSchedule?.location },
            });
        }
        this.tutorialDetailSections = [
            {
                headline: 'artemisApp.pages.courseTutorialGroupDetail.sections.general',
                details: tutorialDetails,
            },
        ];
    }
}
