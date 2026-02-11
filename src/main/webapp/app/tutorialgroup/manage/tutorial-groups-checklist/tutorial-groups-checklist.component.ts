import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, combineLatest, finalize, switchMap, take } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { faCog, faPlus } from '@fortawesome/free-solid-svg-icons';
import { takeUntil } from 'rxjs/operators';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ChecklistCheckComponent } from 'app/shared/components/checklist-check/checklist-check.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

@Component({
    selector: 'jhi-tutorial-groups-checklist',
    templateUrl: './tutorial-groups-checklist.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, ChecklistCheckComponent, RouterLink, FaIconComponent],
})
export class TutorialGroupsChecklistComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private cdr = inject(ChangeDetectorRef);

    isLoading = false;
    course: Course;
    isTimeZoneConfigured = false;
    isTutorialGroupConfigurationCreated = false;

    protected readonly faCog = faCog;
    protected readonly faPlus = faPlus;

    ngUnsubscribe = new Subject<void>();

    get isFullyConfigured(): boolean {
        return this.isTimeZoneConfigured && this.isTutorialGroupConfigurationCreated;
    }

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.paramMap
            .pipe(
                take(1),
                switchMap((params) => {
                    const courseId = Number(params.get('courseId'));
                    return combineLatest([this.courseManagementService.find(courseId), this.tutorialGroupsConfigurationService.getOneOfCourse(courseId)]);
                }),
                finalize(() => (this.isLoading = false)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: ([courseResult, configurationResult]) => {
                    if (courseResult.body) {
                        this.course = courseResult.body;
                        this.isTimeZoneConfigured = !!this.course.timeZone;
                    }
                    if (configurationResult.body) {
                        this.course.tutorialGroupsConfiguration = tutorialGroupsConfigurationEntityFromDto(configurationResult.body);
                        this.isTutorialGroupConfigurationCreated = !!this.course.tutorialGroupsConfiguration;
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
