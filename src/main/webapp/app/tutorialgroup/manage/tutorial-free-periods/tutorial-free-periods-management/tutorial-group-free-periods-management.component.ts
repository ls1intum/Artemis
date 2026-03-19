import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject, viewChild } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { Subject, combineLatest, finalize, switchMap, take } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { SortService } from 'app/shared/service/sort.service';
import { faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/core/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { CreateTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { takeUntil } from 'rxjs/operators';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TutorialGroupFreePeriodsTableComponent } from './tutorial-group-free-periods-table/tutorial-group-free-periods-table.component';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

@Component({
    selector: 'jhi-tutorial-free-periods',
    templateUrl: './tutorial-group-free-periods-management.component.html',
    styleUrls: ['./tutorial-group-free-periods-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, FaIconComponent, TutorialGroupFreePeriodsTableComponent, CreateTutorialGroupFreePeriodComponent],
})
export class TutorialGroupFreePeriodsManagementComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private cdr = inject(ChangeDetectorRef);

    readonly createFreePeriodDialog = viewChild<CreateTutorialGroupFreePeriodComponent>('createFreePeriodDialog');

    isLoading = false;
    tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    tutorialGroupFreePeriods: TutorialGroupFreePeriod[] = [];
    course: Course;
    faTimes = faTimes;
    faPlus = faPlus;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngUnsubscribe = new Subject<void>();

    ngOnInit(): void {
        this.loadAll();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.dialogErrorSource.unsubscribe();
    }

    get freeDays(): TutorialGroupFreePeriod[] {
        return this.tutorialGroupFreePeriods.filter((tutorialGroupFreePeriod) => TutorialGroupFreePeriodsManagementComponent.isFreeDay(tutorialGroupFreePeriod));
    }
    public static isFreeDay(tutorialGroupFreePeriod: TutorialGroupFreePeriod): boolean {
        const startIsMidnight: boolean = tutorialGroupFreePeriod.start!.hour() === 0 && tutorialGroupFreePeriod.start!.minute() === 0;
        const endIsMidnight: boolean = tutorialGroupFreePeriod.end!.hour() === 23 && tutorialGroupFreePeriod.end!.minute() === 59;

        return tutorialGroupFreePeriod.start!.isSame(tutorialGroupFreePeriod.end!, 'day') && startIsMidnight && endIsMidnight;
    }

    get freePeriods(): TutorialGroupFreePeriod[] {
        return this.tutorialGroupFreePeriods.filter((tutorialGroupFreePeriod) => TutorialGroupFreePeriodsManagementComponent.isFreePeriod(tutorialGroupFreePeriod));
    }

    public static isFreePeriod(tutorialGroupFreePeriod: TutorialGroupFreePeriod): boolean {
        return !tutorialGroupFreePeriod.start!.isSame(tutorialGroupFreePeriod.end!, 'day');
    }

    get freePeriodsWithinDay(): TutorialGroupFreePeriod[] {
        return this.tutorialGroupFreePeriods.filter((tutorialGroupFreePeriod) => TutorialGroupFreePeriodsManagementComponent.isFreePeriodWithinDay(tutorialGroupFreePeriod));
    }

    public static isFreePeriodWithinDay(tutorialGroupFreePeriod: TutorialGroupFreePeriod) {
        return tutorialGroupFreePeriod.start!.date() === tutorialGroupFreePeriod.end!.date() && !TutorialGroupFreePeriodsManagementComponent.isFreeDay(tutorialGroupFreePeriod);
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
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (tutorialGroupsConfigurationResult) => {
                    if (tutorialGroupsConfigurationResult.body) {
                        this.tutorialGroupsConfiguration = tutorialGroupsConfigurationEntityFromDto(tutorialGroupsConfigurationResult.body);
                        if (this.tutorialGroupsConfiguration.tutorialGroupFreePeriods) {
                            this.tutorialGroupFreePeriods = this.sortService.sortByProperty(this.tutorialGroupsConfiguration.tutorialGroupFreePeriods, 'start', false);
                        } else {
                            this.tutorialGroupFreePeriods = [];
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    }

    openCreateFreePeriodDialog(event: MouseEvent) {
        event.stopPropagation();
        this.createFreePeriodDialog()?.open();
    }

    onFreePeriodCreated(): void {
        this.loadAll();
    }
}
