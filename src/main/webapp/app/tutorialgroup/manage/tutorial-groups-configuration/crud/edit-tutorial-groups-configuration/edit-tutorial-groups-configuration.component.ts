import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { TutorialGroupsConfigurationFormData } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { onError } from 'app/foundation/util/global.utils';
import { Subject, combineLatest } from 'rxjs';
import { finalize, switchMap, take, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { LoadingIndicatorContainerComponent } from 'app/shared-ui/loading-indicator-container/loading-indicator-container.component';
import { TutorialGroupsConfigurationFormComponent } from '../tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupConfigurationDTO, tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-edit-tutorial-groups-configuration',
    templateUrl: './edit-tutorial-groups-configuration.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TutorialGroupsConfigurationFormComponent],
})
export class EditTutorialGroupsConfigurationComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private courseStorageService = inject(CourseStorageService);
    private alertService = inject(AlertService);

    ngUnsubscribe = new Subject<void>();

    readonly isLoading = signal(false);
    tutorialGroupsConfiguration: TutorialGroupConfigurationDTO;
    readonly formData = signal<TutorialGroupsConfigurationFormData>(undefined!);
    readonly course = signal<Course>(undefined!);
    tutorialGroupConfigurationId: number;

    ngOnInit(): void {
        this.isLoading.set(true);
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([params, { course }]) => {
                    this.tutorialGroupConfigurationId = Number(params.get('tutorialGroupsConfigurationId'));
                    this.course.set(course);
                    return this.tutorialGroupsConfigurationService.getOneOfCourse(this.course().id!);
                }),
                finalize(() => this.isLoading.set(false)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (tutorialGroupsConfigurationResult) => {
                    if (tutorialGroupsConfigurationResult.body) {
                        this.tutorialGroupsConfiguration = tutorialGroupsConfigurationResult.body;
                        this.formData.set({
                            period: [
                                dayjs(this.tutorialGroupsConfiguration.tutorialPeriodStartInclusive).toDate(),
                                dayjs(this.tutorialGroupsConfiguration.tutorialPeriodEndInclusive).toDate(),
                            ],
                            useTutorialGroupChannels: this.tutorialGroupsConfiguration.useTutorialGroupChannels,
                            usePublicTutorialGroupChannels: this.tutorialGroupsConfiguration.usePublicTutorialGroupChannels,
                        });
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    updateTutorialGroupsConfiguration(formData: TutorialGroupsConfigurationFormData) {
        const { period, useTutorialGroupChannels, usePublicTutorialGroupChannels } = formData;

        this.isLoading.set(true);
        this.tutorialGroupsConfiguration.useTutorialGroupChannels = useTutorialGroupChannels;
        this.tutorialGroupsConfiguration.usePublicTutorialGroupChannels = usePublicTutorialGroupChannels;
        this.tutorialGroupsConfigurationService
            .update(this.course().id!, this.tutorialGroupConfigurationId, this.tutorialGroupsConfiguration, period ?? [])
            .pipe(
                finalize(() => {
                    this.isLoading.set(false);
                    this.router.navigate(['/course-management', this.course().id!, 'tutorial-groups']);
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (resp) => {
                    this.course().tutorialGroupsConfiguration = tutorialGroupsConfigurationEntityFromDto(resp.body!);
                    this.courseStorageService.updateCourse(this.course());
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
