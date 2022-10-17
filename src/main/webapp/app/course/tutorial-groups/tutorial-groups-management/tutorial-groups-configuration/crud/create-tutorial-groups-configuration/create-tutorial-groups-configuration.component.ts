import { Component, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupsConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-create-tutorial-groups-configuration',
    templateUrl: './create-tutorial-groups-configuration.component.html',
})
export class CreateTutorialGroupsConfigurationComponent implements OnInit {
    newTutorialGroupsConfiguration = new TutorialGroupsConfiguration();
    isLoading: boolean;
    course: Course;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private courseManagementService: CourseManagementService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
            }
        });
        this.newTutorialGroupsConfiguration = new TutorialGroupsConfiguration();
    }

    createTutorialsGroupConfiguration(formData: TutorialGroupsConfigurationFormData) {
        const { timeZone, period } = formData;
        this.newTutorialGroupsConfiguration.timeZone = timeZone ? timeZone : 'Europe/Berlin';
        this.isLoading = true;
        this.tutorialGroupsConfigurationService
            .create(this.newTutorialGroupsConfiguration, this.course.id!, period ?? [])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (resp) => {
                    const createdConfiguration = resp.body!;
                    this.course.tutorialGroupsConfiguration = createdConfiguration;
                    this.courseManagementService.courseWasUpdated(this.course);
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups-management']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
