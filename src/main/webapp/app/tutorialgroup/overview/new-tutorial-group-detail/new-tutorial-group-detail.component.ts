import { Component, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import { TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBuildingColumns, faCalendar, faCircleExclamation, faClock, faFlag, faMapPin, faTag, faUsers } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupDetailSessionDTO, TutorialGroupDetailSessionDTOStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SelectButton } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { Color, PieChartComponent, PieChartModule, ScaleType } from '@swimlane/ngx-charts';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupDetailSessionStatusIndicatorComponent } from 'app/tutorialgroup/overview/tutorial-group-detail-session-status-indicator/tutorial-group-detail-session-status-indicator.component';

interface SessionData {
    date: string;
    time: string;
    location: string;
    attendance?: string;
    status: TutorialGroupDetailSessionDTOStatus;
}

type ListOption = 'all-sessions' | 'future-sessions';

@Component({
    selector: 'jhi-new-tutorial-group-detail',
    imports: [
        ProfilePictureComponent,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        SelectButton,
        FormsModule,
        PieChartModule,
        TableModule,
        SelectModule,
        TutorialGroupDetailSessionStatusIndicatorComponent,
    ],
    templateUrl: './new-tutorial-group-detail.component.html',
    styleUrl: './new-tutorial-group-detail.component.scss',
})
export class NewTutorialGroupDetailComponent {
    private translateService = inject(TranslateService);

    course = input.required<Course>();
    tutorialGroup = input.required<TutorialGroupDetailGroupDTO>();
    tutorialGroupSessions = computed<SessionData[]>(() => {
        const now = dayjs();
        const selectedListOption = this.selectedListOption();
        return this.tutorialGroup()
            .sessions.filter((session) => {
                if (selectedListOption === 'all-sessions') return true;
                return session.start.isSameOrAfter(now);
            })
            .sort((first, second) => first.start.diff(second.start))
            .map((session) => this.computeSessionDataFrom(session));
    });
    nextSessionData = computed<SessionData | undefined>(() => this.computeNextSessionDataUsing(this.tutorialGroup().sessions));
    teachingAssistantImageUrl = computed(() => addPublicFilePrefix(this.tutorialGroup().teachingAssistantImageUrl));
    tutorialGroupLanguage = computed<string>(() => this.tutorialGroup().language);
    tutorialGroupCapacity = computed<string>(() => String(this.tutorialGroup().capacity ?? '-'));
    tutorialGroupMode = computed<string>(() => (this.tutorialGroup().isOnline ? 'artemisApp.generic.online' : 'artemisApp.generic.offline'));
    tutorialGroupCampus = computed<string>(() => this.tutorialGroup().campus ?? '-');
    private averageAttendanceRatio = computed<number | undefined>(() => {
        const sessionsWithAttendance = this.tutorialGroup().sessions.filter((session) => session.attendanceCount);
        if (sessionsWithAttendance.length === 0) return undefined;
        const averageAttendance = sessionsWithAttendance.reduce((sum, session) => sum + session.attendanceCount!, 0) / sessionsWithAttendance.length;
        const capacity = this.tutorialGroup().capacity;
        if (capacity === undefined) return undefined;
        return averageAttendance / capacity;
    });
    averageAttendancePercentage = computed<string | undefined>(() => {
        const attendanceRatio = this.averageAttendanceRatio();
        if (attendanceRatio === undefined) return undefined;
        const percentage = attendanceRatio * 100;
        return `Ø ${percentage.toFixed(0)}%`;
    });
    pieChart = viewChild(PieChartComponent);
    pieChartData = computed<NgxChartsSingleSeriesDataEntry[]>(() => {
        const averageAttendancePercentage = this.averageAttendanceRatio();
        if (!averageAttendancePercentage) {
            return [{ name: 'Not Attended', value: 100 }];
        }
        const attendedValue = averageAttendancePercentage * 100;
        const notAttendedValue = 100 - attendedValue;
        return [
            { name: 'Attended', value: attendedValue },
            { name: 'Not Attended', value: notAttendedValue },
        ];
    });
    pieChartColors = computed<Color>(() => {
        const averageAttendancePercentage = this.averageAttendanceRatio();
        if (!averageAttendancePercentage) {
            return {
                name: 'vivid',
                selectable: false,
                group: ScaleType.Ordinal,
                domain: [GraphColors.LIGHT_GREY],
            } as Color;
        } else {
            let color: string | undefined = undefined;
            if (averageAttendancePercentage >= 0.9) {
                color = 'var(--red)';
            } else if (averageAttendancePercentage >= 0.8) {
                color = 'var(--orange)';
            } else if (averageAttendancePercentage >= 0.7) {
                color = 'var(--yellow)';
            } else {
                color = 'var(--green)';
            }
            return {
                name: 'vivid',
                selectable: false,
                group: ScaleType.Ordinal,
                domain: [color, GraphColors.LIGHT_GREY],
            } as Color;
        }
    });
    listOptions: any[] = [
        { label: 'All Sessions', value: 'all-sessions' as const },
        { label: 'Future Sessions', value: 'future-sessions' as const },
    ];
    selectedListOption = signal<ListOption>('all-sessions');

    readonly faFlag = faFlag;
    readonly faUsers = faUsers;
    readonly faTag = faTag;
    readonly faCircleExclamation = faCircleExclamation;
    readonly faCalendar = faCalendar;
    readonly faClock = faClock;
    readonly faMapPin = faMapPin;
    readonly faBuildingColumns = faBuildingColumns;

    constructor() {
        effect(() => {
            const pieChart = this.pieChart();
            if (!pieChart) return;
            pieChart.margins = [0, 0, 0, 0];
            pieChart.update();
        });
    }

    private computeNextSessionDataUsing(sessions: TutorialGroupDetailSessionDTO[]): SessionData | undefined {
        if (sessions && sessions.length > 0) {
            const now = dayjs();
            const upcoming = sessions.filter((session) => dayjs(session.start).isAfter(now)).sort((first, second) => dayjs(first.start).diff(dayjs(second.start)));
            if (upcoming.length > 0) {
                const nextSession = upcoming[0];
                return this.computeSessionDataFrom(nextSession);
            }
            return undefined;
        }
        return undefined;
    }

    private computeSessionDataFrom(session: TutorialGroupDetailSessionDTO): SessionData {
        const weekdayStringKey = this.computeWeekdayStringKeyUsing(session.start);
        const weekday = this.translateService.instant(weekdayStringKey);
        const date = weekday + ', ' + session.start.format('DD.MM.YYYY');
        const time = session.start.format('HH:mm') + '-' + session.end.format('HH:mm');
        const location = session.location;
        const attendance = session.attendanceCount ? session.attendanceCount + ' / ' + this.tutorialGroup().capacity : undefined;
        const status = session.status;
        return { date, time, location, attendance, status };
    }

    private computeWeekdayStringKeyUsing(sessionStart: Dayjs): string {
        const weekDayIndex = sessionStart.day();
        const keys = [
            'global.weekdays.monday',
            'global.weekdays.tuesday',
            'global.weekdays.wednesday',
            'global.weekdays.thursday',
            'global.weekdays.friday',
            'global.weekdays.saturday',
            'global.weekdays.sunday',
        ];
        return keys[weekDayIndex - 1];
    }
}
