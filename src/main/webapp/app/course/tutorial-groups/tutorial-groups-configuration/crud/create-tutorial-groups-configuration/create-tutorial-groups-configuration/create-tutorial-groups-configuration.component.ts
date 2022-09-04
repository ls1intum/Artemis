import { Component, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { onError } from 'app/shared/util/global.utils';
import { ConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import dayjs from 'dayjs/esm';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/tutorial-groups-configuration.service';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-create-tutorial-groups-configuration',
    templateUrl: './create-tutorial-groups-configuration.component.html',
    styleUrls: ['./create-tutorial-groups-configuration.component.scss'],
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

        this.newTutorialGroupsConfiguration.timeZone = timeZone?.tzCode!;
        if (period && period.length === 2) {
            this.newTutorialGroupsConfiguration.tutorialPeriodStartInclusive = dayjs(period[0]);
            this.newTutorialGroupsConfiguration.tutorialPeriodEndInclusive = dayjs(period[1]);
        }

        this.isLoading = true;

        this.tutorialGroupsConfigurationService
            .create(this.newTutorialGroupsConfiguration, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['/course-management', this.courseId, 'tutorial-groups-management'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
