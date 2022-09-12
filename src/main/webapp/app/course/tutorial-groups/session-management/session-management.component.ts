import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { combineLatest } from 'rxjs';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { SortService } from 'app/shared/service/sort.service';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-session-management',
    templateUrl: './session-management.component.html',
    styleUrls: ['./session-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class SessionManagementComponent implements OnInit {
    isLoading = false;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private tutorialGroupSessionService: TutorialGroupSessionService,
        private alertService: AlertService,
        private sortService: SortService,
        private changeDetectorRef: ChangeDetectorRef,
    ) {}

    faPlus = faPlus;

    courseId: number;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    tutorialGroup: TutorialGroup;
    tutorialGroupSchedule: TutorialGroupSchedule;
    upcomingSessions: TutorialGroupSession[] = [];
    pastSessions: TutorialGroupSession[] = [];

    tutorialGroupSessionStatus = TutorialGroupSessionStatus;

    determineRowClass(session: TutorialGroupSession): string {
        if (session.status === TutorialGroupSessionStatus.CANCELLED) {
            return 'table-danger';
        }
        if (!session.tutorialGroupSchedule) {
            return 'table-warning';
        }
        return '';
    }

    ngOnInit(): void {
        this.loadAll();
    }

    loadAll() {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([params, data]) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = data['course'].id;
                    this.tutorialGroupsConfiguration = data['course'].tutorialGroupsConfiguration;
                    return this.tutorialGroupService.getOneOfCourse(this.courseId, tutorialGroupId).pipe(finalize(() => (this.isLoading = false)));
                }),
                map((res: HttpResponse<TutorialGroup>) => {
                    return res.body;
                }),
            )
            .subscribe({
                next: (tutorialGroup) => {
                    if (tutorialGroup) {
                        this.tutorialGroup = tutorialGroup;
                        if (tutorialGroup.tutorialGroupSessions) {
                            this.splitIntoUpcomingAndPastSessions(this.sortService.sortByProperty(tutorialGroup.tutorialGroupSessions, 'start', false));
                        }
                        if (tutorialGroup.tutorialGroupSchedule) {
                            this.tutorialGroupSchedule = tutorialGroup.tutorialGroupSchedule;
                        }
                        // convert sessions to the configured timezone to not be confusing for the user
                        this.upcomingSessions.forEach((session) => {
                            session.start = session.start?.tz(this.tutorialGroupsConfiguration.timeZone);
                            session.end = session.end?.tz(this.tutorialGroupsConfiguration.timeZone);
                        });
                    }
                    this.isLoading = false;
                    this.changeDetectorRef.detectChanges();
                },
                error: (res: HttpErrorResponse) => {
                    this.isLoading = false;
                    onError(this.alertService, res);
                },
            });
    }

    private splitIntoUpcomingAndPastSessions(sessions: TutorialGroupSession[]) {
        const upcoming: TutorialGroupSession[] = [];
        const past: TutorialGroupSession[] = [];
        for (const session of sessions) {
            if (session.end?.isBefore(dayjs())) {
                upcoming.push(session);
            } else {
                past.push(session);
            }
        }
        this.upcomingSessions = upcoming;
        this.pastSessions = past;
    }
}
