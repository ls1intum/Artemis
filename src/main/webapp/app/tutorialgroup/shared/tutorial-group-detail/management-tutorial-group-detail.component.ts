import { ChangeDetectorRef, Component, ContentChild, OnChanges, SimpleChanges, TemplateRef, inject, input } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { getDayTranslationKey } from '../util/weekdays';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TranslateService } from '@ngx-translate/core';
import { faCircle, faCircleInfo, faCircleXmark, faPercent, faQuestionCircle, faUserCheck } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { SortService } from 'app/shared/service/sort.service';
import { NgClass, NgStyle, NgTemplateOutlet } from '@angular/common';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupSessionsTableComponent } from '../tutorial-group-sessions-table/tutorial-group-sessions-table.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IconCardComponent } from 'app/tutorialgroup/shared/icon-card/icon-card.component';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-management-tutorial-group-detail',
    templateUrl: './management-tutorial-group-detail.component.html',
    styleUrls: ['./management-tutorial-group-detail.component.scss'],
    imports: [
        NgTemplateOutlet,
        IconCardComponent,
        ProfilePictureComponent,
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        NgbTooltip,
        NgClass,
        NgStyle,
        TutorialGroupSessionsTableComponent,
        ArtemisTranslatePipe,
    ],
})
export class ManagementTutorialGroupDetailComponent implements OnChanges {
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private artemisMarkdownService = inject(ArtemisMarkdownService);
    private changeDetectorRef = inject(ChangeDetectorRef);
    private translateService = inject(TranslateService);
    private sortService = inject(SortService);

    @ContentChild(TemplateRef, { static: true }) header: TemplateRef<any>;

    readonly timeZone = input<string>();
    readonly tutorialGroup = input.required<TutorialGroup>();
    readonly course = input<Course>();

    formattedAdditionalInformation?: SafeHtml;

    readonly Math = Math;

    sessions: TutorialGroupSession[] = [];

    tutorialTimeslotString: string | undefined;
    isMessagingEnabled: boolean;
    utilization: number | undefined;

    // Icons
    readonly faUserCheck = faUserCheck;
    readonly faPercent = faPercent;
    readonly faCircleInfo = faCircleInfo;
    readonly faQuestionCircle = faQuestionCircle;
    readonly faCircle = faCircle;
    readonly faCircleXmark = faCircleXmark;

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName) && propName === 'tutorialGroup') {
                const change = changes[propName];

                if (change.currentValue && change.currentValue.additionalInformation) {
                    this.formattedAdditionalInformation = this.artemisMarkdownService.safeHtmlForMarkdown(this.tutorialGroup().additionalInformation);
                }
                if (change.currentValue && change.currentValue.tutorialGroupSessions) {
                    this.sessions = change.currentValue.tutorialGroupSessions;
                }
                this.changeDetectorRef.detectChanges();
            }
        }
        this.getTutorialDetail();
    }

    private getTutorialDetail() {
        const tutorialGroup = this.tutorialGroup();

        this.isMessagingEnabled = isMessagingEnabled(this.course());
        if (tutorialGroup.averageAttendance && tutorialGroup.capacity) {
            this.utilization = Math.round((tutorialGroup.averageAttendance / tutorialGroup.capacity) * 100);
        } else {
            this.utilization = undefined;
        }
        this.tutorialTimeslotString = this.getTutorialTimeSlotString();
    }

    private getTutorialTimeSlotString(): string | undefined {
        const tutorialGroup = this.tutorialGroup();
        if (!tutorialGroup.tutorialGroupSchedule) {
            return undefined;
        }
        const day = this.translateService.instant(getDayTranslationKey(tutorialGroup.tutorialGroupSchedule?.dayOfWeek));
        const start = tutorialGroup.tutorialGroupSchedule?.startTime?.split(':').slice(0, 2).join(':');
        const end = tutorialGroup.tutorialGroupSchedule?.endTime?.split(':').slice(0, 2).join(':');
        const repetition = this.translateService.instant(
            tutorialGroup.tutorialGroupSchedule!.repetitionFrequency! === 1
                ? 'artemisApp.entities.tutorialGroupSchedule.repetitionOneWeek'
                : 'artemisApp.entities.tutorialGroupSchedule.repetitionNWeeks',
            { n: tutorialGroup.tutorialGroupSchedule!.repetitionFrequency! },
        );
        return `${day} ${start}-${end}, ${repetition}`;
    }

    recalculateAttendanceDetails() {
        let activeAndFinishedSessions =
            this.tutorialGroup().tutorialGroupSessions?.filter((session) => session.status === TutorialGroupSessionStatus.ACTIVE && dayjs().isAfter(session.end)) ?? [];
        activeAndFinishedSessions = this.sortService.sortByProperty(activeAndFinishedSessions, 'start', false);
        const relevantSessions = activeAndFinishedSessions.slice(0, 3).filter((session) => session.attendanceCount !== undefined);

        if (relevantSessions.length) {
            const averageAttendance = relevantSessions.map((session) => session.attendanceCount ?? 0).reduce((acc, attendance) => acc + attendance) / relevantSessions.length;
            this.tutorialGroup().averageAttendance = Math.round(averageAttendance);
        }

        this.getTutorialDetail();
    }
}
