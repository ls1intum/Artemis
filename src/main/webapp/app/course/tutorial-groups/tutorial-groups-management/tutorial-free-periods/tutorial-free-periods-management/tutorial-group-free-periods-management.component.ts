import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { EMPTY, Subject, combineLatest, finalize, from, switchMap, take } from 'rxjs';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { SortService } from 'app/shared/service/sort.service';
import { faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CreateTutorialGroupFreePeriodComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { catchError, takeUntil } from 'rxjs/operators';

@Component({
    selector: 'jhi-tutorial-free-periods',
    templateUrl: './tutorial-group-free-periods-management.component.html',
    styleUrls: ['./tutorial-group-free-periods-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupFreePeriodsManagementComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private alertService = inject(AlertService);
    private sortService = inject(SortService);
    private modalService = inject(NgbModal);
    private cdr = inject(ChangeDetectorRef);

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
                        this.tutorialGroupsConfiguration = tutorialGroupsConfigurationResult.body;
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
        const modalRef: NgbModalRef = this.modalService.open(CreateTutorialGroupFreePeriodComponent, { size: 'lg', scrollable: false, backdrop: 'static', animation: false });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.tutorialGroupConfigurationId = this.tutorialGroupsConfiguration.id!;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.loadAll();
            });
    }
}
