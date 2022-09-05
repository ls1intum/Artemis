import { Component, OnInit } from '@angular/core';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { ConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/tutorial-groups-configuration.service';
import { onError } from 'app/shared/util/global.utils';
import { combineLatest } from 'rxjs';
import { finalize, switchMap, take } from 'rxjs/operators';
import timezones from 'timezones-list';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-edit-tutorial-groups-configuration',
    templateUrl: './edit-tutorial-groups-configuration.component.html',
    styleUrls: ['./edit-tutorial-groups-configuration.component.scss'],
})
export class EditTutorialGroupsConfigurationComponent implements OnInit {
    isLoading = false;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    formData: ConfigurationFormData;
    courseId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const tutorialGroupConfigurationId = Number(params.get('tutorialGroupsConfigurationId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupsConfigurationService.getOne(tutorialGroupConfigurationId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupsConfigurationResult) => {
                    if (tutorialGroupsConfigurationResult.body) {
                        this.tutorialGroupsConfiguration = tutorialGroupsConfigurationResult.body;
                        this.formData = {
                            timeZone: timezones.find((tz) => tz.tzCode === this.tutorialGroupsConfiguration.timeZone)!,
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

    updateTutorialGroupsConfiguration(formData: ConfigurationFormData) {
        const { timeZone, period } = formData;
        this.tutorialGroupsConfiguration.timeZone = timeZone?.tzCode!;
        if (period && period.length === 2) {
            this.tutorialGroupsConfiguration.tutorialPeriodStartInclusive = dayjs(period[0]);
            this.tutorialGroupsConfiguration.tutorialPeriodEndInclusive = dayjs(period[1]);
        }

        this.isLoading = true;
        this.tutorialGroupsConfigurationService
            .update(this.tutorialGroupsConfiguration)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    this.router.navigate(['../../..'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
