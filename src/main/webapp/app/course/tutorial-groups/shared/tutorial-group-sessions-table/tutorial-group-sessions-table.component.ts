import { ChangeDetectorRef, Component, ContentChild, Input, OnChanges, SimpleChanges, TemplateRef } from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-tutorial-group-sessions-table',
    templateUrl: './tutorial-group-sessions-table.component.html',
    styleUrls: ['./tutorial-group-sessions-table.component.scss'],
})
export class TutorialGroupSessionsTableComponent implements OnChanges {
    @ContentChild(TemplateRef) extraColumn: TemplateRef<any>;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    sessions: TutorialGroupSession[] = [];

    @Input()
    timeZone?: string = undefined;

    get timeZoneUsedForDisplay(): string {
        return this.timeZone || dayjs.tz.guess();
    }

    @Input()
    showIdColumn = false;

    upcomingSessions: TutorialGroupSession[] = [];
    pastSessions: TutorialGroupSession[] = [];

    get numberOfColumns(): number {
        let numberOfColumns = this.tutorialGroup.tutorialGroupSchedule ? 3 : 2;
        if (this.extraColumn) {
            numberOfColumns++;
        }
        if (this.showIdColumn) {
            numberOfColumns++;
        }
        return numberOfColumns;
    }

    constructor(private sortService: SortService, private changeDetectorRef: ChangeDetectorRef) {}
    ngOnChanges(changes: SimpleChanges) {
        for (const propName in changes) {
            if (changes.hasOwnProperty(propName)) {
                const change = changes[propName];
                switch (propName) {
                    case 'sessions': {
                        if (change.currentValue) {
                            this.splitIntoUpcomingAndPastSessions(this.sortService.sortByProperty(change.currentValue, 'start', false));
                        }
                    }
                }
            }
        }
    }

    public getCurrentDate(): dayjs.Dayjs {
        return dayjs();
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
}
