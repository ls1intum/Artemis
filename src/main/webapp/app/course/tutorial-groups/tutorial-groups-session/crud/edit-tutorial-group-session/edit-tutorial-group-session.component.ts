import { Component, OnInit } from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-session/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { combineLatest } from 'rxjs';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/tutorial-group-session.service';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-edit-tutorial-group-session',
    templateUrl: './edit-tutorial-group-session.component.html',
    styleUrls: ['./edit-tutorial-group-session.component.scss'],
})
export class EditTutorialGroupSessionComponent implements OnInit {
    isLoading = false;
    session: TutorialGroupSession;
    formData: TutorialGroupSessionFormData;
    courseId: number;
    tutorialGroupId: number;
    sessionId: number;

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
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    this.sessionId = Number(params.get('sessionId'));
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupSessionService.getOneOfTutorialGroup(this.courseId, this.tutorialGroupId, this.sessionId);
                }),
                map((res: HttpResponse<TutorialGroupSession>) => res.body),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (session) => {
                    if (session) {
                        this.session = session;
                        this.formData = {
                            date: session.start?.toDate(),
                            startTime: session.start?.format('HH:mm:ss'),
                            endTime: session.end?.format('HH:mm:ss'),
                        };
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateSession(formData: TutorialGroupSessionFormData) {
        const { date, startTime, endTime } = formData;

        // we send it already in utc
        this.session.start = this.createUTC(date!, startTime!);
        this.session.end = this.createUTC(date!, endTime!);
        this.isLoading = true;
        this.tutorialGroupSessionService
            .update(this.courseId, this.tutorialGroupId, this.sessionId, this.session)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['../../session-management'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    private createUTC(date: Date, time: string): dayjs.Dayjs {
        const hours = time.split(':')[0];
        const minutes = time.split(':')[1];
        return dayjs(date).tz(this.session!.tutorialGroup!.course!.tutorialGroupsConfiguration?.timeZone).set({ hour: hours, minute: minutes }).utc();
    }
}
