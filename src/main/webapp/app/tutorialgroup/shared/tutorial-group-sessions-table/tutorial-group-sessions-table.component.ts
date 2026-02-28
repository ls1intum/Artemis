import { ChangeDetectionStrategy, ChangeDetectorRef, Component, TemplateRef, ViewEncapsulation, contentChild, effect, inject, input, output } from '@angular/core';
import { TutorialGroupSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupSessionRowComponent } from './tutorial-group-session-row/tutorial-group-session-row.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutorial-group-sessions-table',
    templateUrl: './tutorial-group-sessions-table.component.html',
    styleUrls: ['./tutorial-group-sessions-table.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, TutorialGroupSessionRowComponent, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class TutorialGroupSessionsTableComponent {
    private sortService = inject(SortService);
    private changeDetectorRef = inject(ChangeDetectorRef);

    readonly extraColumn = contentChild(TemplateRef);

    readonly tutorialGroup = input.required<TutorialGroup>();

    readonly sessions = input<TutorialGroupSessionDTO[]>([]);

    readonly timeZone = input<string>();

    timeZoneUsedForDisplay = dayjs.tz.guess();

    readonly showIdColumn = input(false);

    readonly isReadOnly = input(false);

    readonly attendanceUpdated = output<void>();

    upcomingSessions: TutorialGroupSessionDTO[] = [];
    pastSessions: TutorialGroupSessionDTO[] = [];

    nextSession: TutorialGroupSessionDTO | undefined = undefined;

    isCollapsed = true;

    constructor() {
        // Effect to handle sessions changes
        effect(() => {
            const sessionsList = this.sessions();
            if (sessionsList) {
                this.splitIntoUpcomingAndPastSessions(this.sortService.sortByProperty([...sessionsList], 'start', false));
            }
        });

        // Effect to handle timeZone changes
        effect(() => {
            const tz = this.timeZone();
            if (tz) {
                this.timeZoneUsedForDisplay = tz;
            }
        });

        // Effect to handle tutorialGroup changes
        effect(() => {
            const group = this.tutorialGroup();
            if (group) {
                this.nextSession = group.nextSession;
            }
        });
    }

    get numberOfColumns(): number {
        let numberOfColumns = this.tutorialGroup().tutorialGroupSchedule ? 4 : 3;
        if (this.extraColumn()) {
            numberOfColumns++;
        }
        if (this.showIdColumn()) {
            numberOfColumns++;
        }
        return numberOfColumns;
    }

    public getCurrentDate(): dayjs.Dayjs {
        return dayjs();
    }

    public trackSession(index: number, item: TutorialGroupSessionDTO): string {
        return `${item.id}`;
    }

    private splitIntoUpcomingAndPastSessions(sessions: TutorialGroupSessionDTO[]) {
        const upcoming: TutorialGroupSessionDTO[] = [];
        const past: TutorialGroupSessionDTO[] = [];
        const now = this.getCurrentDate();

        for (const session of sessions) {
            if (session.endDate!.isBefore(now)) {
                past.push(session);
            } else {
                upcoming.push(session);
            }
        }
        this.upcomingSessions = upcoming;
        this.pastSessions = past;
    }

    onAttendanceChanged(session: TutorialGroupSessionDTO) {
        // Note: We synchronize the attendance of upcoming or past sessions with the next session and vice versa
        if (session.id === this.nextSession?.id) {
            this.nextSession = session;
            const upcomingIndex = this.upcomingSessions.findIndex((s) => s.id === session.id);
            if (upcomingIndex !== -1) {
                this.upcomingSessions[upcomingIndex] = session;
            }
            const pastIndex = this.pastSessions.findIndex((s) => s.id === session.id);
            if (pastIndex !== -1) {
                this.pastSessions[pastIndex] = session;
            }
            this.changeDetectorRef.detectChanges();
        }
        this.attendanceUpdated.emit();
    }
}
