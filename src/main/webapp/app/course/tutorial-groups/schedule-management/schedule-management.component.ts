import { ChangeDetectionStrategy, Component, OnInit, ViewEncapsulation } from '@angular/core';
import { CalendarEvent, CalendarView, DAYS_OF_WEEK } from 'angular-calendar';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';
import { combineLatest } from 'rxjs';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSession } from 'app/entities/TutorialGroupSession.model';

@Component({
    selector: 'jhi-schedule-management',
    templateUrl: './schedule-management.component.html',
    styleUrls: ['./schedule-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class ScheduleManagementComponent implements OnInit {
    isLoading = false;
    view: CalendarView = CalendarView.Month;

    viewDate: Date = new Date();

    get events(): CalendarEvent[] {
        return this.tutorialGroupSession.map((session) => {
            return {
                start: session.start?.toDate()!,
                end: session.end?.toDate()!,
                title: session.tutorialGroup?.title!,
                color: {
                    primary: '#ad2121',
                    secondary: '#FAE3E3',
                },
            };
        });
    }

    // exclude weekends
    excludeDays: number[] = [0, 6];

    weekStartsOn = DAYS_OF_WEEK.SUNDAY;

    CalendarView = CalendarView;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private tutorialGroupSessionService: TutorialGroupSessionService,
        private alertService: AlertService,
    ) {}

    courseId: number;
    tutorialGroup: TutorialGroup;
    tutorialGroupSession: TutorialGroupSession[] = [];

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupService.getOneOfCourse(tutorialGroupId, this.courseId);
                }),
                switchMap((tutorialGroupResult) => {
                    if (tutorialGroupResult.body) {
                        this.tutorialGroup = tutorialGroupResult.body;
                    }
                    return this.tutorialGroupSessionService.getSessions(this.courseId, this.tutorialGroup.id!);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    if (tutorialGroupResult.body) {
                        this.tutorialGroupSession = tutorialGroupResult.body;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
