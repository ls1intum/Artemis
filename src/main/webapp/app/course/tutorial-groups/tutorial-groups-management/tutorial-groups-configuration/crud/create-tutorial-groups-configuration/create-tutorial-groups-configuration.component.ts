import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupsConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { finalize, switchMap, take, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Subject } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Component({
    selector: 'jhi-create-tutorial-groups-configuration',
    templateUrl: './create-tutorial-groups-configuration.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateTutorialGroupsConfigurationComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private courseStorageService = inject(CourseStorageService);
    private cdr = inject(ChangeDetectorRef);

    ngUnsubscribe = new Subject<void>();

    newTutorialGroupsConfiguration = new TutorialGroupsConfiguration();
    isLoading: boolean;
    course: Course;

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
                    this.courseStorageService.updateCourse(this.course);
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
