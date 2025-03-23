import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { Subject, combineLatest, finalize, switchMap, take } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/shared/entities/course.model';
import { takeUntil } from 'rxjs/operators';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TutorialGroupRowButtonsComponent } from '../tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { TutorialGroupDetailComponent } from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/services/tutorial-groups.service';

@Component({
    selector: 'jhi-tutorial-group-management-detail',
    templateUrl: './tutorial-group-management-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TutorialGroupDetailComponent, TutorialGroupRowButtonsComponent],
})
export class TutorialGroupManagementDetailComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private cdr = inject(ChangeDetectorRef);

    ngUnsubscribe = new Subject<void>();

    isLoading = false;
    tutorialGroup: TutorialGroup;
    course: Course;
    tutorialGroupId: number;
    isAtLeastInstructor = false;

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.data])
            .pipe(
                take(1),
                switchMap(([params, { course }]) => {
                    this.tutorialGroupId = Number(params.get('tutorialGroupId'));
                    if (course) {
                        this.course = course;
                        this.isAtLeastInstructor = course.isAtLeastInstructor;
                    }
                    return this.tutorialGroupService.getOneOfCourse(this.course.id!, this.tutorialGroupId);
                }),
                finalize(() => (this.isLoading = false)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    this.tutorialGroup = tutorialGroupResult.body!;
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
    }
}
