import { Component, OnInit } from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { onError } from 'app/shared/util/global.utils';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-session/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';
import dayjs from 'dayjs/esm';
import { combineLatest } from 'rxjs';
import { TutorialGroupFreeDay } from 'app/entities/tutorial-group/tutorial-group-free-day.model';

@Component({
    selector: 'jhi-create-tutorial-group-session',
    templateUrl: './create-tutorial-group-session.component.html',
    styleUrls: ['./create-tutorial-group-session.component.scss'],
})
export class CreateTutorialGroupSessionComponent implements OnInit {
    tutorialGroupSessionToCreate: TutorialGroupSession = new TutorialGroupSession();
    isLoading: boolean;
    tutorialGroup: TutorialGroup;

    constructor(
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private tutorialGroupSessionService: TutorialGroupSessionService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    return this.tutorialGroupService.getOne(tutorialGroupId);
                }),
                map((res: HttpResponse<TutorialGroup>) => res.body),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroup: TutorialGroup) => {
                    this.tutorialGroup = tutorialGroup;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });

        this.tutorialGroupSessionToCreate = new TutorialGroupSession();
    }

    createTutorialGroupSession(formData: TutorialGroupSessionFormData) {
        const { date, startTime, endTime } = formData;

        // we send it already in utc
        this.tutorialGroupSessionToCreate.start = this.createUTC(date!, startTime!);
        this.tutorialGroupSessionToCreate.end = this.createUTC(date!, endTime!);
        this.isLoading = true;
        this.tutorialGroupSessionService
            .create(this.tutorialGroup.id!, this.tutorialGroupSessionToCreate)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['../../schedule-management'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    private createUTC(date: Date, time: string): dayjs.Dayjs {
        const hours = time.split(':')[0];
        const minutes = time.split(':')[1];
        return dayjs(date).tz(this.tutorialGroup.course!.tutorialGroupsConfiguration?.timeZone).set({ hour: hours, minute: minutes }).utc();
    }
}
