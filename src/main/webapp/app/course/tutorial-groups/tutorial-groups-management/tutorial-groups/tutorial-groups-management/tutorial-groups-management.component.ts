import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, combineLatest, finalize } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { faPlus, faUmbrellaBeach } from '@fortawesome/free-solid-svg-icons';
import { Course, isMessagingEnabled } from 'app/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { takeUntil } from 'rxjs/operators';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';

@Component({
    selector: 'jhi-tutorial-groups-management',
    templateUrl: './tutorial-groups-management.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupsManagementComponent implements OnInit, OnDestroy {
    ngUnsubscribe = new Subject<void>();

    courseId: number;
    course: Course;
    isAtLeastInstructor = false;

    configuration: TutorialGroupsConfiguration;

    isLoading = false;
    tutorialGroups: TutorialGroup[] = [];
    faPlus = faPlus;
    faUmbrellaBeach = faUmbrellaBeach;

    readonly isMessagingEnabled = isMessagingEnabled;

    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];

    selectedFields: string[] = ['ID'];
    availableFields = [
        { name: 'ID', value: 'ID' },
        { name: 'Title', value: 'Title' },
        { name: 'Campus', value: 'Campus' },
        { name: 'Language', value: 'Language' },
        { name: 'Additional Information', value: 'Additional Information' },
        { name: 'Capacity', value: 'Capacity' },
        { name: 'Is Online', value: 'Is Online' },
        { name: 'Day of Week', value: 'Day of Week' },
    ];

    constructor(
        private tutorialGroupService: TutorialGroupsService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.data.pipe(takeUntil(this.ngUnsubscribe)).subscribe(({ course }) => {
            if (course) {
                this.course = course;
                this.courseId = course.id!;
                this.isAtLeastInstructor = course.isAtLeastInstructor;
                this.loadTutorialGroups();
            }
        });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    loadTutorialGroups() {
        this.isLoading = true;

        combineLatest([this.tutorialGroupService.getAllForCourse(this.courseId), this.tutorialGroupsConfigurationService.getOneOfCourse(this.course.id!)])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: ([tutorialGroupsRes, configurationRes]) => {
                    const tutorialGroups = tutorialGroupsRes.body!;
                    tutorialGroups.sort((a, b) => {
                        if (a.isUserTutor && !b.isUserTutor) {
                            return -1;
                        } else if (!a.isUserTutor && b.isUserTutor) {
                            return 1;
                        } else {
                            return a.title!.localeCompare(b.title!);
                        }
                    });
                    this.tutorialGroups = tutorialGroups;

                    this.configuration = configurationRes.body!;
                    if (this.configuration.tutorialGroupFreePeriods) {
                        this.tutorialGroupFreeDays = this.configuration.tutorialGroupFreePeriods;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    }

    exportTutorialGroupsToCSV(): void {
        this.tutorialGroupService.exportTutorialGroupsToCSV(this.courseId, this.selectedFields).subscribe({
            next: (blob: Blob) => {
                const a = document.createElement('a');
                const objectUrl = URL.createObjectURL(blob);
                a.href = objectUrl;
                a.download = 'tutorial-groups.csv';
                a.click();
                URL.revokeObjectURL(objectUrl);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }
}
