import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription, catchError, combineLatest, of, switchMap, tap } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import {
    CourseTutorialGroupDetailComponent,
    DeleteTutorialGroupDetailSessionEvent,
} from 'app/tutorialgroup/overview/course-tutorial-group-detail/course-tutorial-group-detail.component';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';

@Component({
    selector: 'jhi-management-tutorial-group-detail-container',
    templateUrl: './management-tutorial-group-detail-container.component.html',
    imports: [CourseTutorialGroupDetailComponent],
})
export class ManagementTutorialGroupDetailContainerComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private tutorialGroupService = inject(TutorialGroupsService);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);
    private alertService = inject(AlertService);
    private paramsSubscription: Subscription;

    ngUnsubscribe = new Subject<void>();

    isLoading = signal(false);
    tutorialGroup?: TutorialGroupDetailGroupDTO;
    course?: Course;

    ngOnInit(): void {
        const tutorialGroupIdParams$ = this.route.params;
        this.paramsSubscription = combineLatest([this.route.data, tutorialGroupIdParams$])
            .pipe(
                switchMap(([{ course }, tutorialGroupIdParams]) => {
                    this.isLoading.set(true);
                    const tutorialGroupId = parseInt(tutorialGroupIdParams.tutorialGroupId, 10);
                    if (course) {
                        this.course = course;
                        return this.tutorialGroupService.getTutorialGroupDetailGroupDTO(course.id, tutorialGroupId);
                    }
                    return of(undefined);
                }),
                tap({
                    next: () => {
                        this.isLoading.set(false);
                    },
                }),
                catchError((error: HttpErrorResponse) => {
                    this.isLoading.set(false);
                    onError(this.alertService, error);
                    return of(undefined);
                }),
            )
            .subscribe({
                next: (tutorialGroupResult) => {
                    this.tutorialGroup = tutorialGroupResult ?? undefined;
                },
            });
    }

    ngOnDestroy(): void {
        this.paramsSubscription?.unsubscribe();
    }

    deleteSession(deletionEvent: DeleteTutorialGroupDetailSessionEvent) {
        const { courseId, tutorialGroupId, sessionId } = deletionEvent;
        this.isLoading.set(true);
        this.tutorialGroupSessionApiService
            .deleteSession(courseId, tutorialGroupId, sessionId, 'response')
            .pipe(
                catchError((error: HttpErrorResponse) => {
                    this.isLoading.set(false);
                    onError(this.alertService, error);
                    return of(undefined);
                }),
            )
            .subscribe((response) => {
                this.isLoading.set(false);
                if (!response || !this.tutorialGroup) {
                    return;
                }
                this.tutorialGroup = {
                    ...this.tutorialGroup,
                    sessions: this.tutorialGroup.sessions.filter((session) => session.id !== sessionId),
                };
            });
    }
}
