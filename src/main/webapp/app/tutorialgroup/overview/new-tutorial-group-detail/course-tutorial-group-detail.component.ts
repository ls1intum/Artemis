import { Component, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import { TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBuildingColumns, faCalendar, faCircleExclamation, faClock, faFlag, faMapPin, faTag, faUsers } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupDetailSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { SelectButton } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { Color, PieChartComponent, PieChartModule, ScaleType } from '@swimlane/ngx-charts';
import { SelectModule } from 'primeng/select';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupDetailSessionStatusIndicatorComponent } from 'app/tutorialgroup/overview/tutorial-group-detail-session-status-indicator/tutorial-group-detail-session-status-indicator.component';
import { Router, RouterLink } from '@angular/router';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { AlertService } from 'app/shared/service/alert.service';

interface TutorialGroupDetailSession {
    date: string;
    time: string;
    location: string;
    isCancelled: boolean;
    locationChanged: boolean;
    timeChanged: boolean;
    dateChanged: boolean;
    attendance?: string;
}

type ListOption = 'all-sessions' | 'future-sessions';

@Component({
    selector: 'jhi-course-tutorial-group-detail',
    imports: [
        ProfilePictureComponent,
        FaIconComponent,
        TranslateDirective,
        SelectButton,
        FormsModule,
        PieChartModule,
        SelectModule,
        TutorialGroupDetailSessionStatusIndicatorComponent,
        NgClass,
        RouterLink,
    ],
    templateUrl: './course-tutorial-group-detail.component.html',
    styleUrl: './course-tutorial-group-detail.component.scss',
})
export class CourseTutorialGroupDetailComponent {
    private translateService = inject(TranslateService);
    private oneToOneChatService = inject(OneToOneChatService);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private locale = toSignal(this.translateService.onLangChange.pipe(map((event) => event.lang)), {
        initialValue: this.translateService.currentLang,
    });
    private averageAttendanceRatio = computed<number | undefined>(() => this.computeAverageAttendanceRation(this.tutorialGroup().sessions, this.tutorialGroup().capacity));

    course = input.required<Course>();
    tutorialGroup = input.required<TutorialGroupDetailGroupDTO>();
    tutorialGroupSessions = computed<TutorialGroupDetailSession[]>(() => {
        this.locale();
        return this.computeSessionsToDisplay(this.selectedSessionListOption(), this.tutorialGroup().sessions);
    });
    nextSessionData = computed<TutorialGroupDetailSession | undefined>(() => this.computeNextSessionDataUsing(this.tutorialGroup().sessions));
    teachingAssistantImageUrl = computed(() => addPublicFilePrefix(this.tutorialGroup().teachingAssistantImageUrl));
    tutorialGroupLanguage = computed<string>(() => this.tutorialGroup().language);
    tutorialGroupCapacity = computed<string>(() => String(this.tutorialGroup().capacity ?? '-'));
    tutorialGroupMode = computed<string>(() => (this.tutorialGroup().isOnline ? 'artemisApp.generic.online' : 'artemisApp.generic.offline'));
    tutorialGroupCampus = computed<string>(() => this.tutorialGroup().campus ?? '-');
    averageAttendancePercentage = computed<string | undefined>(() => this.computeAverageAttendancePercentage(this.averageAttendanceRatio()));
    pieChart = viewChild(PieChartComponent);
    pieChartData = computed<NgxChartsSingleSeriesDataEntry[]>(() => this.computePieChartData(this.averageAttendanceRatio()));
    pieChartColors = computed<Color>(() => this.computePieChartColor(this.averageAttendanceRatio()));
    sessionListOptions = computed(() => {
        this.locale();
        return this.computeSessionListOptions();
    });
    selectedSessionListOption = signal<ListOption>('all-sessions');
    messagingEnabled = computed<boolean>(() => isMessagingEnabled(this.course()));
    tutorChatLink = computed(() => this.computeTutorChatLink(this.course().id, this.tutorialGroup().tutorChatId));
    groupChannelLink = computed(() => this.computeGroupChannelLink(this.course().id, this.tutorialGroup().groupChannelId));

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

    private computeSessionsToDisplay(selectedListOption: ListOption, sessions: TutorialGroupDetailSessionDTO[]): TutorialGroupDetailSession[] {
        const now = dayjs();
        return sessions
            .filter((session) => {
                if (selectedListOption === 'all-sessions') return true;
                return session.start.isSameOrAfter(now);
            })
            .sort((first, second) => first.start.diff(second.start))
            .map((session) => this.computeSessionDataFrom(session));
    }

    private computeAverageAttendanceRation(sessions: TutorialGroupDetailSessionDTO[], capacity: number | undefined): number | undefined {
        if (capacity === undefined) return undefined;
        const sessionsWithAttendance = sessions.filter((session) => session.attendanceCount);
        if (sessionsWithAttendance.length === 0) return undefined;
        const averageAttendance = sessionsWithAttendance.reduce((sum, session) => sum + session.attendanceCount!, 0) / sessionsWithAttendance.length;
        return averageAttendance / capacity;
    }

    private computeAverageAttendancePercentage(attendanceRatio: number | undefined): string | undefined {
        if (attendanceRatio === undefined) return undefined;
        const percentage = attendanceRatio * 100;
        return `Ø ${percentage.toFixed(0)}%`;
    }

    private computePieChartData(averageAttendanceRatio: number | undefined): NgxChartsSingleSeriesDataEntry[] {
        if (!averageAttendanceRatio) {
            return [{ name: 'Not Attended', value: 100 }];
        }
        const attendedValue = averageAttendanceRatio * 100;
        const notAttendedValue = 100 - attendedValue;
        return [
            { name: 'Attended', value: attendedValue },
            { name: 'Not Attended', value: notAttendedValue },
        ];
    }

    private computePieChartColor(averageAttendanceRatio: number | undefined): Color {
        if (!averageAttendanceRatio) {
            return {
                name: 'vivid',
                selectable: false,
                group: ScaleType.Ordinal,
                domain: [GraphColors.LIGHT_GREY],
            } as Color;
        } else {
            let color: string | undefined = undefined;
            if (averageAttendanceRatio >= 0.9) {
                color = 'var(--red)';
            } else if (averageAttendanceRatio >= 0.8) {
                color = 'var(--orange)';
            } else if (averageAttendanceRatio >= 0.7) {
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
    }

    private computeTutorChatLink(courseId: number | undefined, tutorChatId: number | undefined) {
        if (!courseId || !tutorChatId) return undefined;
        return {
            routerLink: ['/courses', courseId, 'communication'],
            queryParams: { conversationId: tutorChatId },
        };
    }

    private computeGroupChannelLink(courseId: number | undefined, groupChannelId: number | undefined) {
        if (!courseId || !groupChannelId) return undefined;
        return {
            routerLink: ['/courses', courseId, 'communication'],
            queryParams: { conversationId: groupChannelId },
        };
    }

    private computeSessionListOptions() {
        const allSessionsLabelKey = 'artemisApp.pages.tutorialGroupDetail.sessionListOptionLabel.allSessions';
        const futureSessionsLabelKey = 'artemisApp.pages.tutorialGroupDetail.sessionListOptionLabel.futureSessions';
        return [
            { label: this.translateService.instant(allSessionsLabelKey), value: 'all-sessions' as const },
            { label: this.translateService.instant(futureSessionsLabelKey), value: 'future-sessions' as const },
        ];
    }

    private computeNextSessionDataUsing(sessions: TutorialGroupDetailSessionDTO[]): TutorialGroupDetailSession | undefined {
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

    private computeSessionDataFrom(session: TutorialGroupDetailSessionDTO): TutorialGroupDetailSession {
        const weekdayStringKey = this.computeWeekdayStringKeyUsing(session.start);
        const weekday = this.translateService.instant(weekdayStringKey);
        const date = weekday + ', ' + session.start.format('DD.MM.YYYY');
        const time = session.start.format('HH:mm') + '-' + session.end.format('HH:mm');
        const location = session.location;
        const isCancelled = session.isCancelled;
        const locationChanged = session.locationChanged;
        const timeChanged = session.timeChanged;
        const dateChanged = session.dateChanged;
        const attendance = session.attendanceCount ? session.attendanceCount + ' / ' + this.tutorialGroup().capacity : undefined;
        return { date, time, location, isCancelled, locationChanged, timeChanged, dateChanged, attendance };
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

    createTutorChat() {
        const courseId = this.course().id;
        const tutorLogin = this.tutorialGroup().teachingAssistantLogin;
        if (courseId) {
            this.oneToOneChatService.create(courseId, tutorLogin).subscribe({
                next: (res) => {
                    const chatId = res.body?.id;
                    if (chatId) {
                        this.router.navigate(['/courses', courseId, 'communication'], { queryParams: { conversationId: chatId } });
                    } else {
                        this.alertService.addErrorAlert('Error while creating tutor chat');
                    }
                },
                error: () => {
                    this.alertService.addErrorAlert('Error while creating tutor chat');
                },
            });
        }
    }
}
