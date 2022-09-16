import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { combineLatest, take, switchMap, finalize, Subject } from 'rxjs';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { SortService } from 'app/shared/service/sort.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupFreePeriodService } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';

@Component({
    selector: 'jhi-tutorial-free-periods',
    templateUrl: './tutorial-group-free-periods-management.component.html',
})
export class TutorialGroupFreePeriodsManagementComponent implements OnInit {
    isLoading = false;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    tutorialGroupFreePeriods: TutorialGroupFreePeriod[] = [];
    courseId: number;
    faTimes = faTimes;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupFreePeriodService: TutorialGroupFreePeriodService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private alertService: AlertService,
        private sortService: SortService,
    ) {}

    ngOnInit(): void {
        this.loadAll();
    }

    private loadAll() {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const tutorialGroupConfigurationId = Number(params.get('tutorialGroupsConfigurationId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.tutorialGroupsConfigurationService.getOneOfCourse(this.courseId, tutorialGroupConfigurationId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupsConfigurationResult) => {
                    if (tutorialGroupsConfigurationResult.body) {
                        this.tutorialGroupsConfiguration = tutorialGroupsConfigurationResult.body;
                        if (this.tutorialGroupsConfiguration.tutorialGroupFreePeriods) {
                            this.tutorialGroupFreePeriods = this.sortService.sortByProperty(this.tutorialGroupsConfiguration.tutorialGroupFreePeriods, 'date', true);
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    deleteTutorialGroupFreePeriod(tutorialGroupFreePeriod: TutorialGroupFreePeriod) {
        this.isLoading = true;
        this.tutorialGroupFreePeriodService
            .delete(this.courseId, this.tutorialGroupsConfiguration.id!, tutorialGroupFreePeriod.id!)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe({
                next: () => {
                    this.loadAll();
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
    }
}
