import { Component, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { onError } from 'app/shared/util/global.utils';
import { ConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-create-tutorial-groups-configuration',
    templateUrl: './create-tutorial-groups-configuration.component.html',
})
export class CreateTutorialGroupsConfigurationComponent implements OnInit {
    newTutorialGroupsConfiguration = new TutorialGroupsConfiguration();
    isLoading: boolean;
    courseId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.parent!.paramMap.subscribe((params) => {
            this.courseId = Number(params.get('courseId'));
        });
        this.newTutorialGroupsConfiguration = new TutorialGroupsConfiguration();
    }

    createTutorialsGroupConfiguration(formData: ConfigurationFormData) {
        const { timeZone, period } = formData;
        this.newTutorialGroupsConfiguration.timeZone = timeZone ? timeZone : 'Europe/Berlin';
        this.isLoading = true;
        this.tutorialGroupsConfigurationService
            .create(this.newTutorialGroupsConfiguration, this.courseId, period ?? [])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    // at /course-management/courseID/tutorial-groups-management/configuration/create
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
