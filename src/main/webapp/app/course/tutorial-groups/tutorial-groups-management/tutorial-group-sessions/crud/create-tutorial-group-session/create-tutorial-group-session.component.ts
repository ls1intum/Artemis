import { Component, OnInit } from '@angular/core';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { onError } from 'app/shared/util/global.utils';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TutorialGroupSessionFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { combineLatest } from 'rxjs';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-create-tutorial-group-session',
    templateUrl: './create-tutorial-group-session.component.html',
})
export class CreateTutorialGroupSessionComponent implements OnInit {
    tutorialGroupSessionToCreate: TutorialGroupSessionDTO = new TutorialGroupSessionDTO();
    isLoading: boolean;
    tutorialGroup: TutorialGroup;
    course: Course;

    constructor(
        private activatedRoute: ActivatedRoute,
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
                    const tutorialGroupId = Number(params.get('tutorialGroupId'));
                    this.course = course;
                    return this.tutorialGroupService.getOneOfCourse(course.id!, tutorialGroupId);
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
        const { date, startTime, endTime, location } = formData;

        this.tutorialGroupSessionToCreate.date = date;
        this.tutorialGroupSessionToCreate.startTime = startTime;
        this.tutorialGroupSessionToCreate.endTime = endTime;
        this.tutorialGroupSessionToCreate.location = location;

        this.isLoading = true;

        this.tutorialGroupSessionService
            .create(this.course.id!, this.tutorialGroup.id!, this.tutorialGroupSessionToCreate)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups', this.tutorialGroup.id, 'sessions']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
