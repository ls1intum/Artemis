import { Component, Signal, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { onError } from 'app/shared/util/global.utils';
import { catchError, map, of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute } from '@angular/router';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import {
    CourseTutorialGroupDetailComponent,
    DeleteTutorialGroupDetailSessionEvent,
    DeleteTutorialGroupEvent,
} from 'app/tutorialgroup/overview/course-tutorial-group-detail/course-tutorial-group-detail.component';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';

@Component({
    selector: 'jhi-management-tutorial-group-detail-container',
    templateUrl: './management-tutorial-group-detail-container.component.html',
    imports: [CourseTutorialGroupDetailComponent, LoadingIndicatorOverlayComponent],
})
export class ManagementTutorialGroupDetailContainerComponent {
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private tutorialGroupService = inject(TutorialGroupsService);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private alertService = inject(AlertService);
    private tutorialGroupId = this.getTutorialGroupIdSignal();

    isLoading = signal(false);
    tutorialGroup = signal<TutorialGroupDetailGroupDTO | undefined>(undefined);
    course = this.getCourseSignal();

    constructor() {
        effect(() => {
            const courseId = this.course()?.id;
            const tutorialGroupId = this.tutorialGroupId();
            if (!courseId || !tutorialGroupId) {
                return;
            }
            this.isLoading.set(true);
            this.tutorialGroupService
                .getTutorialGroupDetailGroupDTO(courseId, tutorialGroupId)
                .pipe(
                    catchError((error: HttpErrorResponse) => {
                        this.isLoading.set(false);
                        onError(this.alertService, error);
                        return of(undefined);
                    }),
                )
                .subscribe((tutorialGroupResult) => {
                    this.isLoading.set(false);
                    this.tutorialGroup.set(tutorialGroupResult ?? undefined);
                });
        });
    }

    deleteSession(deletionEvent: DeleteTutorialGroupDetailSessionEvent) {
        const { courseId, tutorialGroupId, sessionId } = deletionEvent;
        this.isLoading.set(true);
        this.tutorialGroupSessionApiService
            .deleteSession(courseId, tutorialGroupId, sessionId, 'response')
            .pipe(
                catchError((_) => {
                    this.isLoading.set(false);
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.managementActionErrorAlert.sessionDeletion');
                    return of(undefined);
                }),
            )
            .subscribe((response) => {
                this.isLoading.set(false);
                const tutorialGroup = this.tutorialGroup();
                if (!response || !tutorialGroup) {
                    return;
                }
                this.tutorialGroup.set({
                    ...tutorialGroup,
                    sessions: tutorialGroup.sessions.filter((session) => session.id !== sessionId),
                });
            });
    }

    deleteGroup(deletionEvent: DeleteTutorialGroupEvent) {
        const { courseId, tutorialGroupId } = deletionEvent;
        this.isLoading.set(true);
        this.tutorialGroupApiService
            .delete(courseId, tutorialGroupId, 'response')
            .pipe(
                catchError((_) => {
                    this.isLoading.set(false);
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.managementActionErrorAlert.groupDeletion');
                    return of(undefined);
                }),
            )
            .subscribe((response) => {
                this.router.navigate(['../'], { relativeTo: this.route });
                this.isLoading.set(false);
            });
    }

    private getTutorialGroupIdSignal(): Signal<number | undefined> {
        return toSignal(
            this.route.params.pipe(
                map((params) => {
                    const tutorialGroupId = params?.['tutorialGroupId'];
                    return tutorialGroupId !== undefined ? Number(tutorialGroupId) : undefined;
                }),
            ),
            { initialValue: undefined },
        );
    }

    private getCourseSignal(): Signal<Course | undefined> {
        return toSignal(this.route.data.pipe(map((data) => data['course'] as Course | undefined)), { initialValue: undefined });
    }
}
