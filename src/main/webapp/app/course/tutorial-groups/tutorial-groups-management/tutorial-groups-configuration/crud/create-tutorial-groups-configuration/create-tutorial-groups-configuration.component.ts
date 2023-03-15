import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, switchMap, take, takeUntil } from 'rxjs/operators';

import { AlertService } from 'app/core/util/alert.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { Course } from 'app/entities/course.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-create-tutorial-groups-configuration',
    templateUrl: './create-tutorial-groups-configuration.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateTutorialGroupsConfigurationComponent implements OnInit, OnDestroy {
    ngUnsubscribe = new Subject<void>();

    newTutorialGroupsConfiguration = new TutorialGroupsConfiguration();
    isLoading: boolean;
    course: Course;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupsConfigurationService: TutorialGroupsConfigurationService,
        private courseManagementService: CourseManagementService,
        private alertService: AlertService,
        private courseCalculationService: CourseScoreCalculationService,

        private cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.paramMap
            .pipe(
                take(1),
                switchMap((params) => {
                    const courseId = Number(params.get('courseId'));
                    return this.courseManagementService.find(courseId);
                }),
                finalize(() => (this.isLoading = false)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (courseResult) => {
                    if (courseResult.body) {
                        this.course = courseResult.body;
                        this.newTutorialGroupsConfiguration = new TutorialGroupsConfiguration();
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    }

    createTutorialsGroupConfiguration(formData: TutorialGroupsConfigurationFormData) {
        const { period, usePublicTutorialGroupChannels, useTutorialGroupChannels } = formData;
        this.isLoading = true;
        this.newTutorialGroupsConfiguration.useTutorialGroupChannels = useTutorialGroupChannels;
        this.newTutorialGroupsConfiguration.usePublicTutorialGroupChannels = usePublicTutorialGroupChannels;
        this.tutorialGroupsConfigurationService
            .create(this.newTutorialGroupsConfiguration, this.course.id!, period ?? [])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (resp) => {
                    this.course.tutorialGroupsConfiguration = resp.body!;
                    this.courseManagementService.courseWasUpdated(this.course);
                    this.courseCalculationService.updateCourse(this.course);
                    this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups-checklist']);
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
