import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { finalize, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-create-tutorial-group-free-day',
    templateUrl: './create-tutorial-group-free-period.component.html',
})
export class CreateTutorialGroupFreePeriodComponent implements OnInit {
    tutorialGroupFreePeriodToCreate: TutorialGroupFreePeriodDTO = new TutorialGroupFreePeriodDTO();
    isLoading: boolean;
    tutorialGroup: TutorialGroup;
    tutorialGroupConfigurationId: number;
    course: Course;

    constructor(
        private tutorialGroupFreePeriodService: TutorialGroupFreePeriodService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(take(1))
            .subscribe({
                next: ([params, { course }]) => {
                    this.tutorialGroupConfigurationId = Number(params.get('tutorialGroupsConfigurationId'));
                    this.course = course;
                },
            });
    }
    createTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { date, reason } = formData;

        this.tutorialGroupFreePeriodToCreate.date = date;
        this.tutorialGroupFreePeriodToCreate.reason = reason;

        this.isLoading = true;
        this.tutorialGroupFreePeriodService
            .create(this.course.id!, this.tutorialGroupConfigurationId, this.tutorialGroupFreePeriodToCreate)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups', 'configuration', this.tutorialGroupConfigurationId, 'tutorial-free-days']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
