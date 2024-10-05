import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    ContentChild,
    EventEmitter,
    Input,
    OnChanges,
    Output,
    SimpleChanges,
    TemplateRef,
    ViewEncapsulation,
    inject,
} from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-tutorial-group-sessions-table',
    templateUrl: './tutorial-group-sessions-table.component.html',
    styleUrls: ['./tutorial-group-sessions-table.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class TutorialGroupSessionsTableComponent implements OnChanges {
    private sortService = inject(SortService);
    private changeDetectorRef = inject(ChangeDetectorRef);

    @ContentChild(TemplateRef, { static: true }) extraColumn: TemplateRef<any>;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    sessions: TutorialGroupSession[] = [];

    @Input()
    timeZone?: string = undefined;

    timeZoneUsedForDisplay = dayjs.tz.guess();

    @Input()
    showIdColumn = false;

    @Input()
    isReadOnly = false;

    @Output() attendanceUpdated = new EventEmitter<void>();

    upcomingSessions: TutorialGroupSession[] = [];
    pastSessions: TutorialGroupSession[] = [];

    nextSession: TutorialGroupSession | undefined = undefined;

    isCollapsed = true;
    get numberOfColumns(): number {
        let numberOfColumns = this.tutorialGroup.tutorialGroupSchedule ? 4 : 3;
        if (this.extraColumn) {
            numberOfColumns++;
        }
        if (this.showIdColumn) {
            numberOfColumns++;
        }
        return numberOfColumns;
    }

    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                const change = changes[propName];
                switch (propName) {
                    case 'sessions': {
                        if (change.currentValue) {
                            this.splitIntoUpcomingAndPastSessions(this.sortService.sortByProperty(change.currentValue, 'start', false));
                        }
                        break;
                    }
                    case 'timeZone': {
                        if (change.currentValue) {
                            this.timeZoneUsedForDisplay = change.currentValue;
                            this.changeDetectorRef.detectChanges();
                        }
                        break;
                    }
                    case 'tutorialGroup': {
                        if (change.currentValue) {
                            this.nextSession = change.currentValue.nextSession;
                            this.changeDetectorRef.detectChanges();
                        }
                        break;
                    }
                }
            }
        }
    }

    public getCurrentDate(): dayjs.Dayjs {
        return dayjs();
    }

    public trackSession(index: number, item: TutorialGroupSession): string {
        return `${item.id}`;
    }

    private splitIntoUpcomingAndPastSessions(sessions: TutorialGroupSession[]) {
        const upcoming: TutorialGroupSession[] = [];
        const past: TutorialGroupSession[] = [];
        const now = this.getCurrentDate();

        for (const session of sessions) {
            if (session.end!.isBefore(now)) {
                past.push(session);
            } else {
                upcoming.push(session);
            }
        }
        this.upcomingSessions = upcoming;
        this.pastSessions = past;
        this.changeDetectorRef.detectChanges();
    }

    onAttendanceChanged(session: TutorialGroupSession) {
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
