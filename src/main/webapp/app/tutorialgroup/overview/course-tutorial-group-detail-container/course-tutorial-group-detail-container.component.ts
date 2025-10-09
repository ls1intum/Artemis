import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { BehaviorSubject, Subscription, catchError, combineLatest, forkJoin, switchMap, tap } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { AsyncPipe } from '@angular/common';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { CourseTutorialGroupDetailComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail/course-tutorial-group-detail.component';

@Component({
    selector: 'jhi-course-tutorial-group-detail-container',
    templateUrl: './course-tutorial-group-detail-container.component.html',
    imports: [LoadingIndicatorContainerComponent, CourseTutorialGroupDetailComponent, AsyncPipe],
})
export class CourseTutorialGroupDetailContainerComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private courseManagementService = inject(CourseManagementService);

    isLoading = new BehaviorSubject<boolean>(false);
    tutorialGroup?: TutorialGroupDetailGroupDTO;
    course?: Course;
    private paramsSubscription: Subscription;

    ngOnInit(): void {
        const courseIdParams$ = this.route.parent?.parent?.params;
        const tutorialGroupIdParams$ = this.route.params;
        if (courseIdParams$) {
            this.paramsSubscription = combineLatest([courseIdParams$, tutorialGroupIdParams$])
                .pipe(
                    switchMap(([courseIdParams, tutorialGroupIdParams]) => {
                        this.isLoading.next(true);
                        const tutorialGroupId = parseInt(tutorialGroupIdParams.tutorialGroupId, 10);
                        const courseId = parseInt(courseIdParams.courseId, 10);
                        return forkJoin({
                            courseResult: this.courseManagementService.find(courseId),
                            tutorialGroupResult: this.tutorialGroupService.getTutorialGroupDetailGroupDTO(courseId, tutorialGroupId),
                        });
                    }),
                    tap({
                        next: () => {
                            this.isLoading.next(false);
                        },
                    }),
                    catchError((error: HttpErrorResponse) => {
                        this.isLoading.next(false);
                        onError(this.alertService, error);
                        throw error;
                    }),
                )
                .subscribe({
                    next: ({ courseResult, tutorialGroupResult }) => {
                        this.tutorialGroup = tutorialGroupResult;
                        this.course = courseResult.body ?? undefined;
                    },
                });
        }
    }

    ngOnDestroy(): void {
        this.paramsSubscription?.unsubscribe();
    }
}
