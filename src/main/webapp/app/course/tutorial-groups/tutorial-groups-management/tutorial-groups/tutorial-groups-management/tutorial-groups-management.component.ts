import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
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
    private tutorialGroupService = inject(TutorialGroupsService);
    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private cdr = inject(ChangeDetectorRef);

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
}
