import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EffectRef,
    EnvironmentInjector,
    OnDestroy,
    OnInit,
    effect,
    inject,
    runInInjectionContext,
} from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { Subject, combineLatest, take } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResourceRef } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { takeUntil } from 'rxjs/operators';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TutorialGroupRowButtonsComponent } from '../tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { ManagementTutorialGroupDetailComponent } from 'app/tutorialgroup/shared/tutorial-group-detail/management-tutorial-group-detail.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';

@Component({
    selector: 'jhi-management-tutorial-group-detail-container',
    templateUrl: './management-tutorial-group-detail-container.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, ManagementTutorialGroupDetailComponent, TutorialGroupRowButtonsComponent],
})
export class ManagementTutorialGroupDetailContainerComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private cdr = inject(ChangeDetectorRef);
    private environmentInjector = inject(EnvironmentInjector);

    ngUnsubscribe = new Subject<void>();

    isLoading = false;
    tutorialGroup: TutorialGroup;
    course: Course;
    tutorialGroupId: number;
    isAtLeastInstructor = false;
    private tutorialGroupsResource?: HttpResourceRef<Array<TutorialGroup> | undefined>;
    private loadEffect?: EffectRef;
    private lastTutorialGroups?: TutorialGroup[];

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: ([params, { course }]) => {
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    if (course) {
                        this.course = course;
                        this.isAtLeastInstructor = course.isAtLeastInstructor;
                    }
                    this.tutorialGroupsResource = this.tutorialGroupService.getAllForCourseResource(this.course.id!);
                    this.setupTutorialGroupEffect();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            })
            .add(() => this.cdr.detectChanges());
    }

    onTutorialGroupDeleted = () => {
        this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups']);
    };

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.loadEffect?.destroy();
    }

    private setupTutorialGroupEffect() {
        if (!this.tutorialGroupsResource) {
            return;
        }
        this.loadEffect?.destroy();
        runInInjectionContext(this.environmentInjector, () => {
            this.loadEffect = effect(() => {
                const resource = this.tutorialGroupsResource;
                if (!resource) {
                    return;
                }
                this.isLoading = resource.isLoading();
                const error = resource.error();
                if (error) {
                    onError(this.alertService, error as HttpErrorResponse);
                }
                const tutorialGroups = resource.value();
                if (!tutorialGroups || tutorialGroups === this.lastTutorialGroups) {
                    return;
                }
                this.lastTutorialGroups = tutorialGroups;
                this.tutorialGroupService.convertTutorialGroupArrayDatesFromServer(tutorialGroups);
                const tutorialGroup = tutorialGroups.find((group) => group.id === this.tutorialGroupId);
                if (tutorialGroup) {
                    this.tutorialGroup = tutorialGroup;
                }
                this.cdr.detectChanges();
            });
        });
    }
}
