import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupsConfigurationFormData } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { finalize, switchMap, take, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Subject } from 'rxjs';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupsConfigurationFormComponent } from '../tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { TutorialGroupConfigurationDTO, tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

@Component({
    selector: 'jhi-create-tutorial-groups-configuration',
    templateUrl: './create-tutorial-groups-configuration.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupsConfigurationFormComponent],
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

    newTutorialGroupsConfiguration = {} as TutorialGroupConfigurationDTO;
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
                        this.newTutorialGroupsConfiguration = {} as TutorialGroupConfigurationDTO;
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
                    this.course.tutorialGroupsConfiguration = tutorialGroupsConfigurationEntityFromDto(resp.body!);
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
