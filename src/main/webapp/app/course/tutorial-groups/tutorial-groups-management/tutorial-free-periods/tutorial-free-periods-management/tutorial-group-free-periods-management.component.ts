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
import { faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-tutorial-free-periods',
    templateUrl: './tutorial-group-free-periods-management.component.html',
    styleUrls: ['./tutorial-group-free-periods-management.component.scss'],
})
export class TutorialGroupFreePeriodsManagementComponent implements OnInit {
    isLoading = false;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    tutorialGroupFreePeriods: TutorialGroupFreePeriod[] = [];
    course: Course;
    faTimes = faTimes;
    faPlus = faPlus;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private alertService: AlertService,
        private sortService: SortService,
    ) {}

    ngOnInit(): void {
        this.loadAll();
    }

    public isInThePast(tutorialGroupFreeDay: TutorialGroupFreePeriod): boolean {
        return tutorialGroupFreeDay.start!.isBefore(this.getCurrentDate());
    }

    public getCurrentDate(): dayjs.Dayjs {
        return dayjs();
    }

    loadAll() {
        this.isLoading = true;
        combineLatest([this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([{ course }]) => {
                    this.course = course;
                    return this.tutorialGroupsConfigurationService.getOneOfCourse(this.course.id!);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (tutorialGroupsConfigurationResult) => {
                    if (tutorialGroupsConfigurationResult.body) {
                        this.tutorialGroupsConfiguration = tutorialGroupsConfigurationResult.body;
                        if (this.tutorialGroupsConfiguration.tutorialGroupFreePeriods) {
                            this.tutorialGroupFreePeriods = this.sortService.sortByProperty(this.tutorialGroupsConfiguration.tutorialGroupFreePeriods, 'start', false);
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
