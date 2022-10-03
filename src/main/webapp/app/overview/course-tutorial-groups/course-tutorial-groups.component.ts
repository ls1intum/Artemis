import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize, map } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupRegistrationService } from 'app/course/tutorial-groups/services/tutorial-group-registration.service';
import { TutorialGroupRegistration } from 'app/entities/tutorial-group/tutorial-group-registration.model';

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
    styleUrls: ['./course-tutorial-groups.component.scss'],
})
export class CourseTutorialGroupsComponent implements OnInit {
    isLoading = false;
    courseId: number;
    registrationsOfLoggedInUser: TutorialGroupRegistration[] = [];

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
