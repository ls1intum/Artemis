import { Component, OnInit } from '@angular/core';
import { TutorialGroupRegistration } from 'app/entities/tutorial-group/tutorial-group-registration.model';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupRegistrationService } from 'app/course/tutorial-groups/services/tutorial-group-registration.service';
import { finalize, map } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-course-tutorial-groups-registered',
    templateUrl: './course-tutorial-groups-registered.component.html',
    styleUrls: ['./course-tutorial-groups-registered.component.scss'],
})
export class CourseTutorialGroupsRegisteredComponent implements OnInit {
    isLoading = false;
    registrationsOfLoggedInUser: TutorialGroupRegistration[] = [];
    courseId: number;

    constructor(private activatedRoute: ActivatedRoute, private alertService: AlertService, private tutorialGroupRegistrationService: TutorialGroupRegistrationService) {}

    ngOnInit(): void {
        this.activatedRoute.parent?.parent?.paramMap.subscribe((params) => {
            this.courseId = Number(params.get('courseId'));
            if (this.courseId) {
                this.loadRegistrationsOfLoggedInUser();
            }
        });
    }

    public loadRegistrationsOfLoggedInUser() {
        this.isLoading = true;
        this.tutorialGroupRegistrationService
            .getRegistrationsOfUser(this.courseId)
            .pipe(
                map((res: HttpResponse<TutorialGroupRegistration[]>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (registrations: TutorialGroupRegistration[]) => {
                    this.registrationsOfLoggedInUser = registrations ?? [];
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }
}
