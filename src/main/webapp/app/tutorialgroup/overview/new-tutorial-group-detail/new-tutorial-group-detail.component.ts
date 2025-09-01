import { Component, computed, effect, input, viewChild } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import { TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBuildingColumns, faCalendar, faCalendarDay, faClock, faFlag, faMapPin, faTag, faUsers } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupDetailSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SelectButton } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { Color, PieChartComponent, PieChartModule, ScaleType } from '@swimlane/ngx-charts';
import { TableModule } from 'primeng/table';

interface NextSessionData {
    weekday: string;
    date: string;
    time: string;
    location: string;
}

@Component({
    selector: 'jhi-new-tutorial-group-detail',
    imports: [ProfilePictureComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, SelectButton, FormsModule, PieChartModule, TableModule],
    templateUrl: './new-tutorial-group-detail.component.html',
    styleUrl: './new-tutorial-group-detail.component.scss',
})
export class NewTutorialGroupDetailComponent {
    nextSessionData = computed<NextSessionData | undefined>(() => {
        const sessions = this.tutorialGroup().sessions;
        if (sessions && sessions.length > 0) {
            const now = dayjs();
            const upcoming = sessions.filter((session) => dayjs(session.start).isAfter(now)).sort((first, second) => dayjs(first.start).diff(dayjs(second.start)));
            if (upcoming.length > 0) {
                const nextSession = upcoming[0];
                const weekday = this.computeNextSessionWeekdayStringKey(nextSession.start);
                const date = nextSession.start.format('DD.MM.YYYY');
                const time = nextSession.start.format('HH:mm') + '-' + nextSession.end.format('HH:mm');
                const location = nextSession.location;
                const nextSessionData: NextSessionData = { weekday, date, time, location };
                return nextSessionData;
            }
            return undefined;
        }
        return undefined;
    });

    course = input.required<Course>();
    tutorialGroup = input.required<TutorialGroupDetailGroupDTO>();
    tutorialGroupSessions = computed<TutorialGroupDetailSessionDTO[]>(() => this.tutorialGroup().sessions);
    teachingAssistantImageUrl = computed(() => addPublicFilePrefix(this.tutorialGroup().teachingAssistantImageUrl));
    pieChart = viewChild(PieChartComponent);
    ngxData: NgxChartsSingleSeriesDataEntry[] = [
        { name: 'Attended', value: 60 },
        { name: 'Not Attended', value: 40 },
    ];
    ngxColor = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.GREEN, GraphColors.RED, GraphColors.LIGHT_GREY],
    } as Color;

    readonly faFlag = faFlag;
    readonly faUsers = faUsers;
    readonly faTag = faTag;
    readonly faCalendarDay = faCalendarDay;
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

    tutorialGroupLanguage = computed<string>(() => this.tutorialGroup().language);

    tutorialGroupCapacity = computed<string>(() => String(this.tutorialGroup().capacity ?? '-'));

    tutorialGroupMode = computed<string>(() => (this.tutorialGroup().isOnline ? 'artemisApp.generic.online' : 'artemisApp.generic.offline'));

    tutorialGroupCampus = computed<string>(() => this.tutorialGroup().campus ?? '-');

    stateOptions: any[] = [
        { label: 'One-Way', value: 'one-way' },
        { label: 'Return', value: 'return' },
    ];

    value: string = 'off';

    private computeNextSessionWeekdayStringKey(nextSessionStart: Dayjs): string {
        const weekDayIndex = nextSessionStart.day();
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
