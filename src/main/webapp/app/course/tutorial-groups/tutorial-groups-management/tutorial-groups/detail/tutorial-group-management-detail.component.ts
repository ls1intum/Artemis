import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { Subject, combineLatest, finalize, switchMap, take } from 'rxjs';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { takeUntil } from 'rxjs/operators';

@Component({
    selector: 'jhi-tutorial-group-management-detail',
    templateUrl: './tutorial-group-management-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorialGroupManagementDetailComponent implements OnInit, OnDestroy {
    ngUnsubscribe = new Subject<void>();

    isLoading = false;
    tutorialGroup: TutorialGroup;
    course: Course;
    tutorialGroupId: number;
    isAtLeastInstructor = false;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private tutorialGroupService: TutorialGroupsService,
        private alertService: AlertService,
        private cdr: ChangeDetectorRef,
    ) {}

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

    onCourseClicked = () => {
        this.router.navigate(['/course-management', this.course.id!]);
    };

    onRegistrationsClicked = () => {
        this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups', this.tutorialGroupId, 'registered-students']);
    };

    onTutorialGroupDeleted = () => {
        this.router.navigate(['/course-management', this.course.id!, 'tutorial-groups']);
    };

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
