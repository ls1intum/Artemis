import { Component, OnInit } from '@angular/core';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupsConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest } from 'rxjs';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-edit-tutorial-groups-configuration',
    templateUrl: './edit-tutorial-groups-configuration.component.html',
})
export class EditTutorialGroupsConfigurationComponent implements OnInit {
    isLoading = false;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    formData: TutorialGroupsConfigurationFormData;
    course: Course;
    tutorialGroupConfigurationId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private courseManagementService: CourseManagementService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([params, { course }]) => {
                    this.tutorialGroupConfigurationId = Number(params.get('tutorialGroupsConfigurationId'));
                    this.course = course;
                    return this.tutorialGroupsConfigurationService.getOneOfCourse(this.course.id!);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupsConfigurationResult) => {
                    if (tutorialGroupsConfigurationResult.body) {
                        this.tutorialGroupsConfiguration = tutorialGroupsConfigurationResult.body;
                        this.formData = {
                            period: [
                                this.tutorialGroupsConfiguration.tutorialPeriodStartInclusive!.toDate(),
                                this.tutorialGroupsConfiguration.tutorialPeriodEndInclusive!.toDate(),
                            ],
                        };
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateTutorialGroupsConfiguration(formData: TutorialGroupsConfigurationFormData) {
        const { period } = formData;

        this.isLoading = true;
        this.tutorialGroupsConfigurationService
            .update(this.course.id!, this.tutorialGroupConfigurationId, this.tutorialGroupsConfiguration, period ?? [])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups']);
                }),
            )
            .subscribe({
                next: (resp) => {
                    const updatedConfiguration = resp.body!;
                    this.course.tutorialGroupsConfiguration = updatedConfiguration;
                    this.courseManagementService.courseWasUpdated(this.course);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
