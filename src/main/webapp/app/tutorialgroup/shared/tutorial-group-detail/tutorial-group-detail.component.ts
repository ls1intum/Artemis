import { Component, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import { TutorialGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    faBan,
    faBuildingColumns,
    faCalendar,
    faCheck,
    faCircleExclamation,
    faCirclePlay,
    faClock,
    faFlag,
    faMapPin,
    faPenToSquare,
    faQuestion,
    faTag,
    faTrash,
    faUsers,
} from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CreateOrUpdateTutorialGroupSessionDTO, TutorialGroupSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { SelectButton } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { Color, PieChartComponent, PieChartModule, ScaleType } from '@swimlane/ngx-charts';
import { SelectModule } from 'primeng/select';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupDetailSessionStatusIndicatorComponent } from 'app/tutorialgroup/shared/tutorial-group-detail-session-status-indicator/tutorial-group-detail-session-status-indicator.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ButtonModule } from 'primeng/button';
import { AccountService } from 'app/core/auth/account.service';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import {
    TutorialSessionCreateOrEditModalComponent,
    UpdateTutorialGroupSessionData,
} from 'app/tutorialgroup/manage/tutorial-group-session-create-or-edit-modal/tutorial-session-create-or-edit-modal.component';
import { TooltipModule } from 'primeng/tooltip';

interface TutorialGroupDetailSession {
    id: number;
    date: string;
    time: string;
    location: string;
    isCancelled: boolean;
    locationChanged: boolean;
    timeChanged: boolean;
    dateChanged: boolean;
    attendance?: string;
}

export interface ModifyTutorialGroupSessionEvent {
    courseId: number;
    tutorialGroupId: number;
    tutorialGroupSessionId: number;
}

export interface UpdateTutorialGroupSessionEvent {
    courseId: number;
    tutorialGroupId: number;
    tutorialGroupSessionId: number;
    updateTutorialGroupSessionDTO: CreateOrUpdateTutorialGroupSessionDTO;
}

export interface CreateTutorialGroupSessionEvent {
    courseId: number;
    tutorialGroupId: number;
    createTutorialGroupSessionDTO: CreateOrUpdateTutorialGroupSessionDTO;
}

export interface DeleteTutorialGroupEvent {
    courseId: number;
    tutorialGroupId: number;
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
        ButtonModule,
        ConfirmDialogModule,
        TutorialSessionCreateOrEditModalComponent,
        TooltipModule,
    ],
    providers: [ConfirmationService],
    templateUrl: './tutorial-group-detail.component.html',
    styleUrl: './tutorial-group-detail.component.scss',
})
export class TutorialGroupDetailComponent {
    protected readonly faFlag = faFlag;
    protected readonly faUsers = faUsers;
    protected readonly faTag = faTag;
    protected readonly faCircleExclamation = faCircleExclamation;
    protected readonly faCalendar = faCalendar;
    protected readonly faClock = faClock;
    protected readonly faMapPin = faMapPin;
    protected readonly faBuildingColumns = faBuildingColumns;
    protected readonly faQuestion = faQuestion;
    protected readonly faCheck = faCheck;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faTrash = faTrash;
    protected readonly faBan = faBan;
    protected readonly faCirclePlay = faCirclePlay;
    protected readonly currentTutorialLectureId = inject(LectureService).currentTutorialLectureId;

    private translateService = inject(TranslateService);
    private oneToOneChatService = inject(OneToOneChatService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private confirmationService = inject(ConfirmationService);
    private router = inject(Router);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private averageAttendanceRatio = computed<number | undefined>(() => this.computeAverageAttendanceRatio(this.tutorialGroup().sessions, this.tutorialGroup().capacity));
    private sessionModal = viewChild.required<TutorialSessionCreateOrEditModalComponent>('sessionModal');

    activatedRoute = inject(ActivatedRoute);
    course = input.required<Course>();
    tutorialGroup = input.required<TutorialGroupDTO>();
    allowManagementActions = input<boolean>(false);
    tutorialGroupSessions = computed<TutorialGroupDetailSession[]>(() => this.computeSessionsToDisplay());
    nextSession = computed<TutorialGroupDetailSession | undefined>(() => this.computeNextSessionDataUsing());
    teachingAssistantImageUrl = computed(() => addPublicFilePrefix(this.tutorialGroup().tutorImageUrl));
    tutorialGroupLanguage = computed<string>(() => this.tutorialGroup().language);
    tutorialGroupCapacity = computed<string>(() => String(this.tutorialGroup().capacity ?? '-'));
    tutorialGroupMode = computed<string>(() => (this.tutorialGroup().isOnline ? 'artemisApp.generic.online' : 'artemisApp.generic.offline'));
    tutorialGroupCampus = computed<string>(() => this.tutorialGroup().campus ?? '-');
    averageAttendancePercentage = computed<string | undefined>(() => this.computeAverageAttendancePercentage());
    pieChart = viewChild(PieChartComponent);
    pieChartData = computed<NgxChartsSingleSeriesDataEntry[]>(() => this.computePieChartData());
    pieChartColors = computed<Color>(() => this.computePieChartColor());
    sessionListOptions = computed(() => this.computeSessionListOptions());
    selectedSessionListOption = signal<ListOption>('all-sessions');
    messagingEnabled = computed<boolean>(() => isMessagingEnabled(this.course()));
    tutorChatLink = computed(() => this.computeTutorChatLink());
    groupChannelLink = computed(() => this.computeGroupChannelLink());
    userIsNotTutor = computed(() => this.accountService.userIdentity()?.login !== this.tutorialGroup().tutorLogin);
    onDeleteSession = output<ModifyTutorialGroupSessionEvent>();
    onCancelSession = output<ModifyTutorialGroupSessionEvent>();
    onUpdateSession = output<UpdateTutorialGroupSessionEvent>();
    onCreateSession = output<CreateTutorialGroupSessionEvent>();
    onActivateSession = output<ModifyTutorialGroupSessionEvent>();
    onDeleteGroup = output<DeleteTutorialGroupEvent>();

    constructor() {
        effect(() => {
            const pieChart = this.pieChart();
            if (!pieChart) return;
            pieChart.margins = [0, 0, 0, 0];
            pieChart.update();
        });
    }

    createTutorChat() {
        const courseId = this.course().id;
        const tutorLogin = this.tutorialGroup().tutorLogin;
        if (courseId) {
            this.oneToOneChatService.create(courseId, tutorLogin).subscribe({
                next: (response) => {
                    const chatId = response.body?.id;
                    if (chatId) {
                        this.router.navigate(['/courses', courseId, 'communication'], { queryParams: { conversationId: chatId } });
                    } else {
                        this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.createOneToOneChatError');
                    }
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.createOneToOneChatError');
                },
            });
        }
    }

    confirmTutorialGroupDeletion(event: Event) {
        this.confirmationService.confirm({
            target: event.target as EventTarget,
            message: 'Do you really want to delete this tutorial group? This action can not be reversed!',
            header: 'Delete Tutorial Group?',
            rejectButtonProps: {
                label: this.translateService.instant('entity.action.cancel'),
                severity: 'secondary',
            },
            acceptButtonProps: {
                label: this.translateService.instant('entity.action.delete'),
                severity: 'danger',
            },
            accept: () => {
                this.deleteTutorialGroup();
            },
        });
    }

    confirmSessionDeletion(event: Event, sessionId: number) {
        this.confirmationService.confirm({
            target: event.target as EventTarget,
            message: 'Do you really want to delete this session?',
            header: 'Delete Session?',
            rejectButtonProps: {
                label: this.translateService.instant('entity.action.cancel'),
                severity: 'secondary',
            },
            acceptButtonProps: {
                label: this.translateService.instant('entity.action.delete'),
                severity: 'danger',
            },
            accept: () => {
                this.deleteTutorialGroupSession(sessionId);
            },
        });
    }

    openSessionModal(sessionId?: number) {
        if (sessionId) {
            const session = this.tutorialGroup().sessions.find((session) => session.id == sessionId);
            if (session) {
                this.sessionModal().open(session);
            }
        } else {
            this.sessionModal().open();
        }
    }

    cancelTutorialGroupSession(sessionId: number) {
        const courseId = this.course().id;
        if (!courseId) return;
        const tutorialGroupId = this.tutorialGroup().id;
        this.onCancelSession.emit({
            courseId: courseId,
            tutorialGroupId: tutorialGroupId,
            tutorialGroupSessionId: sessionId,
        });
    }

    activateTutorialGroupSession(sessionId: number) {
        const courseId = this.course().id;
        if (!courseId) return;
        const tutorialGroupId = this.tutorialGroup().id;
        this.onActivateSession.emit({
            courseId: courseId,
            tutorialGroupId: tutorialGroupId,
            tutorialGroupSessionId: sessionId,
        });
    }

    updateTutorialGroupSession(updateTutorialGroupSessionData: UpdateTutorialGroupSessionData) {
        const courseId = this.course().id;
        if (!courseId) return;
        const tutorialGroupId = this.tutorialGroup().id;
        const updateTutorialGroupSessionEvent: UpdateTutorialGroupSessionEvent = {
            courseId: courseId,
            tutorialGroupId: tutorialGroupId,
            tutorialGroupSessionId: updateTutorialGroupSessionData.tutorialGroupSessionId,
            updateTutorialGroupSessionDTO: updateTutorialGroupSessionData.updateTutorialGroupSessionDTO,
        };
        this.onUpdateSession.emit(updateTutorialGroupSessionEvent);
    }

    createTutorialGroupSession(createTutorialGroupSessionDTO: CreateOrUpdateTutorialGroupSessionDTO) {
        const courseId = this.course().id;
        if (!courseId) return;
        const tutorialGroupId = this.tutorialGroup().id;
        const createTutorialGroupSessionEvent: CreateTutorialGroupSessionEvent = {
            courseId: courseId,
            tutorialGroupId: tutorialGroupId,
            createTutorialGroupSessionDTO: createTutorialGroupSessionDTO,
        };
        this.onCreateSession.emit(createTutorialGroupSessionEvent);
    }

    private deleteTutorialGroupSession(sessionId: number) {
        const courseId = this.course().id;
        if (!courseId) return;
        const tutorialGroupId = this.tutorialGroup().id;
        this.onDeleteSession.emit({
            courseId: courseId,
            tutorialGroupId: tutorialGroupId,
            tutorialGroupSessionId: sessionId,
        });
    }

    private deleteTutorialGroup() {
        const courseId = this.course().id;
        const tutorialGroupId = this.tutorialGroup().id;
        if (courseId) {
            this.onDeleteGroup.emit({
                courseId,
                tutorialGroupId,
            });
        }
    }

    private computeSessionsToDisplay(): TutorialGroupDetailSession[] {
        this.currentLocale();
        const selectedListOption = this.selectedSessionListOption();
        const tutorialGroup = this.tutorialGroup();
        const sessions = tutorialGroup.sessions;
        const capacity = tutorialGroup.capacity;
        const now = dayjs();
        return sessions
            .filter((session) => {
                if (selectedListOption === 'all-sessions') return true;
                return session.start.isSameOrAfter(now);
            })
            .sort((first, second) => first.start.diff(second.start))
            .map((session) => this.computeSessionDataFrom(session, capacity));
    }

    private computeAverageAttendanceRatio(sessions: TutorialGroupSessionDTO[], capacity: number | undefined): number | undefined {
        if (capacity === undefined) return undefined;
        const sessionsWithAttendance = sessions.filter((session) => session.attendance !== undefined && session.attendance !== null);
        if (sessionsWithAttendance.length === 0) return undefined;
        const averageAttendance = sessionsWithAttendance.reduce((sum, session) => sum + session.attendance!, 0) / sessionsWithAttendance.length;
        return averageAttendance / capacity;
    }

    private computeAverageAttendancePercentage(): string | undefined {
        const attendanceRatio = this.averageAttendanceRatio();
        if (attendanceRatio === undefined) return undefined;
        const percentage = attendanceRatio * 100;
        return `Ø ${percentage.toFixed(0)}%`;
    }

    private computePieChartData(): NgxChartsSingleSeriesDataEntry[] {
        const averageAttendanceRatio = this.averageAttendanceRatio();
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

    private computePieChartColor(): Color {
        const averageAttendanceRatio = this.averageAttendanceRatio();
        if (averageAttendanceRatio === undefined) {
            return {
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
                group: ScaleType.Ordinal,
                domain: [color, GraphColors.LIGHT_GREY],
            } as Color;
        }
    }

    private computeTutorChatLink() {
        const courseId = this.course().id;
        const tutorChatId = this.tutorialGroup().tutorChatId;
        if (!courseId || !tutorChatId) return undefined;
        return {
            routerLink: ['/courses', courseId, 'communication'],
            queryParameters: { conversationId: tutorChatId },
        };
    }

    private computeGroupChannelLink() {
        const courseId = this.course().id;
        const groupChannelId = this.tutorialGroup().groupChannelId;
        if (!courseId || !groupChannelId) return undefined;
        return {
            routerLink: ['/courses', courseId, 'communication'],
            queryParameters: { conversationId: groupChannelId },
        };
    }

    private computeSessionListOptions() {
        this.currentLocale();
        const allSessionsLabelKey = 'artemisApp.pages.tutorialGroupDetail.sessionListOptionLabel.allSessions';
        const futureSessionsLabelKey = 'artemisApp.pages.tutorialGroupDetail.sessionListOptionLabel.futureSessions';
        return [
            { label: this.translateService.instant(allSessionsLabelKey), value: 'all-sessions' as const },
            { label: this.translateService.instant(futureSessionsLabelKey), value: 'future-sessions' as const },
        ];
    }

    private computeNextSessionDataUsing(): TutorialGroupDetailSession | undefined {
        const tutorialGroup = this.tutorialGroup();
        const sessions = tutorialGroup.sessions;
        const capacity = tutorialGroup.capacity;
        if (sessions && sessions.length > 0) {
            const now = dayjs();
            const upcoming = sessions.filter((session) => dayjs(session.start).isAfter(now)).sort((first, second) => dayjs(first.start).diff(dayjs(second.start)));
            if (upcoming.length > 0) {
                const nextSession = upcoming[0];
                return this.computeSessionDataFrom(nextSession, capacity);
            }
            return undefined;
        }
        return undefined;
    }

    private computeSessionDataFrom(session: TutorialGroupSessionDTO, capacity: number | undefined): TutorialGroupDetailSession {
        const id = session.id;
        const weekdayStringKey = this.computeWeekdayStringKeyUsing(session.start);
        const weekday = this.translateService.instant(weekdayStringKey);
        const date = weekday + ', ' + session.start.format('DD.MM.YYYY');
        const time = session.start.format('HH:mm') + '-' + session.end.format('HH:mm');
        const location = session.location;
        const isCancelled = session.isCancelled;
        const locationChanged = session.locationChanged;
        const timeChanged = session.timeChanged;
        const dateChanged = session.dateChanged;
        let attendance: string | undefined = undefined;
        if (session.attendance !== undefined) {
            if (capacity !== undefined) {
                attendance = session.attendance + ' / ' + capacity;
            } else {
                attendance = session.attendance.toString();
            }
        }
        return { id, date, time, location, isCancelled, locationChanged, timeChanged, dateChanged, attendance };
    }

    private computeWeekdayStringKeyUsing(sessionStart: Dayjs): string {
        const weekDayIndex = sessionStart.isoWeekday();
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
