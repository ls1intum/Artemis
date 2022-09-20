import { Component, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { finalize, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-create-tutorial-group-free-day',
    templateUrl: './create-tutorial-group-free-period.component.html',
})
export class CreateTutorialGroupFreePeriodComponent implements OnInit {
    tutorialGroupFreePeriodToCreate: TutorialGroupFreePeriodDTO = new TutorialGroupFreePeriodDTO();
    isLoading: boolean;
    tutorialGroup: TutorialGroup;
    tutorialGroupConfigurationId: number;
    courseId: number;

    constructor(
        private tutorialGroupFreePeriodService: TutorialGroupFreePeriodService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.paramMap])
            .pipe(take(1))
            .subscribe({
                next: ([params, parentParams]) => {
                    this.tutorialGroupConfigurationId = Number(params.get('tutorialGroupsConfigurationId'));
                    this.courseId = Number(parentParams.get('courseId'));
                },
            });
    }
    createTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { date, reason } = formData;

        this.tutorialGroupFreePeriodToCreate.date = date;
        this.tutorialGroupFreePeriodToCreate.reason = reason;

        this.isLoading = true;
        this.tutorialGroupFreePeriodService
            .create(this.courseId, this.tutorialGroupConfigurationId, this.tutorialGroupFreePeriodToCreate)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate([
                        'course-management',
                        this.courseId,
                        'tutorial-groups-management',
                        'configuration',
                        this.tutorialGroupConfigurationId,
                        'tutorial-free-days',
                    ]);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
