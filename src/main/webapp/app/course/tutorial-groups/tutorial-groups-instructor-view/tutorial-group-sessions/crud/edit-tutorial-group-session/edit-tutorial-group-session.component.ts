import { Component, OnInit } from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { combineLatest } from 'rxjs';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';

@Component({
    selector: 'jhi-edit-tutorial-group-session',
    templateUrl: './edit-tutorial-group-session.component.html',
})
export class EditTutorialGroupSessionComponent implements OnInit {
    isLoading = false;
    session: TutorialGroupSession;
    formData: TutorialGroupSessionFormData;
    courseId: number;
    tutorialGroupId: number;
    sessionId: number;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;

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
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([params, data]) => {
                    this.sessionId = Number(params.get('sessionId'));
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.courseId = data['course'].id;
                    this.tutorialGroupsConfiguration = data['course'].tutorialGroupsConfiguration;
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
                            date: session.start?.tz(this.tutorialGroupsConfiguration.timeZone).toDate(),
                            startTime: session.start?.tz(this.tutorialGroupsConfiguration.timeZone).format('HH:mm:ss'),
                            endTime: session.end?.tz(this.tutorialGroupsConfiguration.timeZone).format('HH:mm:ss'),
                            location: session.location,
                        };
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateSession(formData: TutorialGroupSessionFormData) {
        const { date, startTime, endTime, location } = formData;

        const tutorialGroupSessionDTO = new TutorialGroupSessionDTO();

        tutorialGroupSessionDTO.date = date;
        tutorialGroupSessionDTO.startTime = startTime;
        tutorialGroupSessionDTO.endTime = endTime;
        tutorialGroupSessionDTO.location = location;

        this.isLoading = true;

        this.tutorialGroupSessionService
            .update(this.courseId, this.tutorialGroupId, this.sessionId, tutorialGroupSessionDTO)
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
}
