import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { AlertService } from 'app/core/util/alert.service';
import { BehaviorSubject, Subscription, catchError, combineLatest, forkJoin, switchMap, tap } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-course-tutorial-group-detail',
    templateUrl: './course-tutorial-group-detail.component.html',
    styleUrls: ['./course-tutorial-group-detail.component.scss'],
})
export class CourseTutorialGroupDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private courseManagementService = inject(CourseManagementService);
    private profileService = inject(ProfileService);

    isLoading$ = new BehaviorSubject<boolean>(false);
    tutorialGroup?: TutorialGroup;
    course?: Course;
    private paramsSubscription: Subscription;
    profileSubscription?: Subscription;
    isProduction = true;
    isTestServer = false;

    ngOnInit(): void {
        const courseIdParams$ = this.route.parent?.parent?.params;
        const tutorialGroupIdParams$ = this.route.params;
        if (courseIdParams$) {
            this.paramsSubscription = combineLatest([courseIdParams$, tutorialGroupIdParams$])
                .pipe(
                    switchMap(([courseIdParams, tutorialGroupIdParams]) => {
                        this.isLoading$.next(true);
                        const tutorialGroupId = parseInt(tutorialGroupIdParams.tutorialGroupId, 10);
                        const courseId = parseInt(courseIdParams.courseId, 10);
                        return forkJoin({
                            courseResult: this.courseManagementService.find(courseId),
                            tutorialGroupResult: this.tutorialGroupService.getOneOfCourse(courseId, tutorialGroupId),
                        });
                    }),
                    tap({
                        next: () => {
                            this.isLoading$.next(false);
                        },
                    }),
                    catchError((error: HttpErrorResponse) => {
                        this.isLoading$.next(false);
                        onError(this.alertService, error);
                        throw error;
                    }),
                )
                .subscribe({
                    next: ({ courseResult, tutorialGroupResult }) => {
                        this.tutorialGroup = tutorialGroupResult.body ?? undefined;
                        this.course = courseResult.body ?? undefined;
                    },
                });
        }
        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo?.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
        });
    }

    ngOnDestroy(): void {
        this.paramsSubscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
    }
}
