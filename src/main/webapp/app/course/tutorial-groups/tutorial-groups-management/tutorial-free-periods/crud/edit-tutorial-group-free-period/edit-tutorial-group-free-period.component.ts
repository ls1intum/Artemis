import { Component, OnInit } from '@angular/core';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest, finalize, map, switchMap, take } from 'rxjs';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-edit-tutorial-group-free-period',
    templateUrl: './edit-tutorial-group-free-period.component.html',
})
export class EditTutorialGroupFreePeriodComponent implements OnInit {
    isLoading = false;
    freePeriod: TutorialGroupFreePeriod;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    formData: TutorialGroupFreePeriodFormData;

    courseId: number;
    tutorialGroupConfigurationId: number;
    tutorialGroupFreePeriodId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private tutorialGroupFreePeriodService: TutorialGroupFreePeriodService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([params, data]) => {
                    this.courseId = data['course'].id;
                    this.tutorialGroupsConfiguration = data['course'].tutorialGroupsConfiguration;
                    this.tutorialGroupConfigurationId = this.tutorialGroupsConfiguration.id!;
                    this.tutorialGroupFreePeriodId = Number(params.get('tutorialGroupFreePeriodId'));
                    return this.tutorialGroupFreePeriodService.getOneOfConfiguration(this.courseId, this.tutorialGroupConfigurationId, this.tutorialGroupFreePeriodId);
                }),
                map((res: HttpResponse<TutorialGroupFreePeriod>) => res.body),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupFreePeriod) => {
                    if (tutorialGroupFreePeriod) {
                        this.freePeriod = tutorialGroupFreePeriod;
                        this.formData = {
                            date: tutorialGroupFreePeriod.start?.tz(this.tutorialGroupsConfiguration.timeZone).toDate(),
                            reason: tutorialGroupFreePeriod.reason,
                        };
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { date, reason } = formData;

        const tutorialGroupFreePeriodDto = new TutorialGroupFreePeriodDTO();
        tutorialGroupFreePeriodDto.date = date;
        tutorialGroupFreePeriodDto.reason = reason;

        this.isLoading = true;

        this.tutorialGroupFreePeriodService
            .update(this.courseId, this.tutorialGroupConfigurationId, this.tutorialGroupFreePeriodId, tutorialGroupFreePeriodDto)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate([
                        '/course-management',
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
