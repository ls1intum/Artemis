import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faPlus, faUmbrellaBeach } from '@fortawesome/free-solid-svg-icons';
import { Subject, combineLatest, finalize } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { Course } from 'app/entities/course.model';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { onError } from 'app/shared/util/global.utils';

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

    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];

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

    onTutorialGroupSelected = (tutorialGroup: TutorialGroup) => {
        this.router.navigate(['/course-management', this.courseId, 'tutorial-groups', tutorialGroup.id]);
    };

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
