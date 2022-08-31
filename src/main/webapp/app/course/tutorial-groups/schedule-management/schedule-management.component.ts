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
import { getDayTranslationKey } from '../shared/weekdays';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';

@Component({
    selector: 'jhi-schedule-management',
    templateUrl: './schedule-management.component.html',
    styleUrls: ['./schedule-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class ScheduleManagementComponent implements OnInit {
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

    courseId: number;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    tutorialGroup: TutorialGroup;
    tutorialGroupSchedule: TutorialGroupSchedule;
    tutorialGroupSessions: TutorialGroupSession[] = [];

    tutorialGroupSessionStatus = TutorialGroupSessionStatus;

    sessionTrackByFn = (index: number, session: TutorialGroupSession): number => session.id!;

    generateSessionLabel(tutorialGroupSession: TutorialGroupSession): string {
        if (!tutorialGroupSession?.start || !tutorialGroupSession?.end) {
            return '';
        } else {
            return tutorialGroupSession.start.format('LLLL') + ' - ' + tutorialGroupSession.end.format('LT');
        }
    }

    getDayTranslationKey = getDayTranslationKey;
    cancelOrActivate(session: TutorialGroupSession): void {
        if (session.status === TutorialGroupSessionStatus.ACTIVE) {
            this.cancelSession(session);
        } else {
            this.activateSession(session);
        }
    }

    cancelSession(session: TutorialGroupSession): void {
        this.tutorialGroupSessionService.cancel(this.courseId!, this.tutorialGroup.id!, session.id!).subscribe({
            next: () => {
                this.loadAll();
            },
            error: (res: HttpErrorResponse) => {
                this.isLoading = false;
                onError(this.alertService, res);
            },
        });
    }

    activateSession(session: TutorialGroupSession): void {
        this.tutorialGroupSessionService.activate(this.courseId!, this.tutorialGroup.id!, session.id!).subscribe({
            next: () => {
                this.loadAll();
            },
            error: (res: HttpErrorResponse) => {
                this.isLoading = false;
                onError(this.alertService, res);
            },
        });
    }

    ngOnInit(): void {
        this.loadAll();
    }

    private loadAll() {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupService.getOneOfCourse(tutorialGroupId, this.courseId).pipe(finalize(() => (this.isLoading = false)));
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
                            this.tutorialGroupSessions = this.sortService.sortByProperty(tutorialGroup.tutorialGroupSessions, 'start', true);
                        }
                        if (tutorialGroup.tutorialGroupSchedule) {
                            this.tutorialGroupSchedule = tutorialGroup.tutorialGroupSchedule;
                        }
                        if (tutorialGroup.course?.tutorialGroupsConfiguration) {
                            this.tutorialGroupsConfiguration = tutorialGroup.course.tutorialGroupsConfiguration;
                            // convert sessions to the configured timezone for easier management
                            this.tutorialGroupSessions.forEach((session) => {
                                session.start = session.start?.tz(this.tutorialGroupsConfiguration.timeZone);
                                session.end = session.end?.tz(this.tutorialGroupsConfiguration.timeZone);
                            });
                        }
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
}
