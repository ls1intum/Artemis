import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { combineLatest, take, switchMap, finalize, Subject } from 'rxjs';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/tutorial-groups-configuration.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupFreeDay } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { SortService } from 'app/shared/service/sort.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupFreeDayService } from 'app/course/tutorial-groups/tutorial-group-free-day.service';

@Component({
    selector: 'jhi-tutorial-free-days',
    templateUrl: './tutorial-free-days.component.html',
    styleUrls: ['./tutorial-free-days.component.scss'],
})
export class TutorialFreeDaysComponent implements OnInit {
    isLoading = false;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    tutorialGroupFreeDays: TutorialGroupFreeDay[] = [];
    courseId: number;
    faTimes = faTimes;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupFreeDaysService: TutorialGroupFreeDayService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private alertService: AlertService,
        private sortService: SortService,
    ) {}

    ngOnInit(): void {
        this.loadAll();
    }

    private loadAll() {
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
                        if (this.tutorialGroupsConfiguration.tutorialGroupFreeDays) {
                            this.tutorialGroupFreeDays = this.sortService.sortByProperty(this.tutorialGroupsConfiguration.tutorialGroupFreeDays, 'date', true);
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    deleteTutorialFreeDay(tutorialGroupFreeDay: TutorialGroupFreeDay) {
        this.isLoading = true;
        this.tutorialGroupFreeDaysService
            .delete(tutorialGroupFreeDay.id!)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe({
                next: () => {
                    this.loadAll();
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
    }
}
