import { Component, OnInit } from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { combineLatest } from 'rxjs';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-edit-tutorial-group-session',
    templateUrl: './edit-tutorial-group-session.component.html',
})
export class EditTutorialGroupSessionComponent implements OnInit {
    isLoading = false;
    session: TutorialGroupSession;
    formData: TutorialGroupSessionFormData;
    course: Course;
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
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([params, { course }]) => {
                    this.sessionId = Number(params.get('sessionId'));
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.course = course;
                    return this.tutorialGroupSessionService.getOneOfTutorialGroup(this.course.id!, this.tutorialGroupId, this.sessionId);
                }),
                map((res: HttpResponse<TutorialGroupSession>) => res.body),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (session) => {
                    if (session) {
                        this.session = session;
                        this.formData = {
                            date: session.start?.tz(this.course.timeZone).toDate(),
                            startTime: session.start?.tz(this.course.timeZone).format('HH:mm:ss'),
                            endTime: session.end?.tz(this.course.timeZone).format('HH:mm:ss'),
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
            .update(this.course.id!, this.tutorialGroupId, this.sessionId, tutorialGroupSessionDTO)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups', this.tutorialGroupId, 'sessions']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
