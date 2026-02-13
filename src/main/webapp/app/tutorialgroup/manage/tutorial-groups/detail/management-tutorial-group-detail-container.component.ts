import { Component, Signal, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute } from '@angular/router';
import { Router } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import {
    CourseTutorialGroupDetailComponent,
    DeleteTutorialGroupDetailSessionEvent,
    DeleteTutorialGroupEvent,
} from 'app/tutorialgroup/overview/course-tutorial-group-detail/course-tutorial-group-detail.component';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { TutorialGroupService } from 'app/tutorialgroup/shared/service/tutorial-group.service';

@Component({
    selector: 'jhi-management-tutorial-group-detail-container',
    templateUrl: './management-tutorial-group-detail-container.component.html',
    imports: [CourseTutorialGroupDetailComponent, LoadingIndicatorOverlayComponent],
})
export class ManagementTutorialGroupDetailContainerComponent {
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private tutorialGroupService = inject(TutorialGroupService);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private alertService = inject(AlertService);
    private tutorialGroupId = getNumericPathVariableSignal(this.route, 'tutorialGroupId');

    isLoading = signal(false);
    tutorialGroup = this.tutorialGroupService.tutorialGroup;
    course = this.getCourseSignal();

    constructor() {
        effect(() => {
            const courseId = this.course()?.id;
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.tutorialGroupService.fetchTutorialGroupDTO(courseId, tutorialGroupId);
            }
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

    private getCourseSignal(): Signal<Course | undefined> {
        return toSignal(this.route.data.pipe(map((data) => data['course'] as Course | undefined)), { initialValue: undefined });
    }
}
