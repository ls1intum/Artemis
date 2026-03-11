import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute } from '@angular/router';
import { Router } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import {
    CreateTutorialGroupSessionEvent,
    DeleteTutorialGroupEvent,
    ModifyTutorialGroupSessionEvent,
    TutorialGroupDetailComponent,
    UpdateTutorialGroupSessionEvent,
} from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupSessionService } from 'app/tutorialgroup/manage/service/tutorial-group-session.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { getRouteData } from 'app/shared/route/getRouteData';
import { TutorialGroupSharedStateService } from 'app/tutorialgroup/shared/service/tutorial-group-shared-state.service';
import { AccountService } from 'app/core/auth/account.service';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { isMessagingEnabled } from 'app/core/course/shared/entities/course.model';

@Component({
    selector: 'jhi-management-tutorial-group-detail-container',
    templateUrl: './management-tutorial-group-detail-container.component.html',
    imports: [TutorialGroupDetailComponent, LoadingIndicatorOverlayComponent],
})
export class ManagementTutorialGroupDetailContainerComponent {
    private destroyRef = inject(DestroyRef);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private tutorialGroupSharedStateService = inject(TutorialGroupSharedStateService);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private tutorialGroupId = getNumericPathVariableSignal(this.route, 'tutorialGroupId');

    isLoading = signal(false);
    tutorialGroup = this.tutorialGroupSharedStateService.tutorialGroup;
    courseId = getNumericPathVariableSignal(this.route, 'courseId', 2);
    isMessagingEnabled = computed(() => this.computeIfMessagingEnabled());
    loggedInUserIsAtLeastTutorOfGroup = computed(() => this.computeIfLoggedInUserIsAtLeastTutorOfGroup());
    loggedInUserIsAtLeastEditorInCourse = computed(() => this.computeIfLoggedInUserIsAtLeastEditorInCourse());
    loggedInUserIsAtLeastInstructorInCourse = computed(() => this.computeIfLoggedInUserIsAtLeastInstructorInCourse());

    constructor() {
        const course = getRouteData<Course>(this.route, 'course');
        this.tutorialGroupSharedStateService.course.set(course);

        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.tutorialGroupSharedStateService.fetchTutorialGroup(courseId, tutorialGroupId);
            }
        });
    }

    deleteSession(deletionEvent: ModifyTutorialGroupSessionEvent) {
        const { courseId, tutorialGroupId, tutorialGroupSessionId } = deletionEvent;
        this.isLoading.set(true);
        this.tutorialGroupSessionApiService
            .deleteSession(courseId, tutorialGroupId, tutorialGroupSessionId, 'response')
            .pipe(
                catchError((_) => {
                    this.isLoading.set(false);
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.deleteSession');
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
                    sessions: tutorialGroup.sessions.filter((session) => session.id !== tutorialGroupSessionId),
                });
            });
    }

    cancelSession(cancellationEvent: ModifyTutorialGroupSessionEvent) {
        this.isLoading.set(true);
        const courseId = cancellationEvent.courseId;
        const tutorialGroupId = cancellationEvent.tutorialGroupId;
        const tutorialGroupSessionId = cancellationEvent.tutorialGroupSessionId;
        this.tutorialGroupSessionService
            .cancel(courseId, tutorialGroupId, tutorialGroupSessionId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.tutorialGroupSharedStateService.toggleCancellationStatusOfSession(tutorialGroupSessionId);
                    this.isLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.cancelSession');
                    this.isLoading.set(false);
                },
            });
    }

    activateSession(cancellationEvent: ModifyTutorialGroupSessionEvent) {
        this.isLoading.set(true);
        const courseId = cancellationEvent.courseId;
        const tutorialGroupId = cancellationEvent.tutorialGroupId;
        const tutorialGroupSessionId = cancellationEvent.tutorialGroupSessionId;
        this.tutorialGroupSessionService
            .activate(courseId, tutorialGroupId, tutorialGroupSessionId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.tutorialGroupSharedStateService.toggleCancellationStatusOfSession(tutorialGroupSessionId);
                    this.isLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.activateSession');
                    this.isLoading.set(false);
                },
            });
    }

    updateSession(updateEvent: UpdateTutorialGroupSessionEvent) {
        this.isLoading.set(true);
        const courseId = updateEvent.courseId;
        const tutorialGroupId = updateEvent.tutorialGroupId;
        const tutorialGroupSessionId = updateEvent.tutorialGroupSessionId;
        const updateTutorialGroupSessionDTO = updateEvent.updateTutorialGroupSessionDTO;
        this.tutorialGroupSessionService
            .update(courseId, tutorialGroupId, tutorialGroupSessionId, updateTutorialGroupSessionDTO)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.tutorialGroupSharedStateService.fetchTutorialGroup(courseId, tutorialGroupId);
                    this.isLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.updateSession');
                    this.isLoading.set(false);
                },
            });
    }

    createSession(createEvent: CreateTutorialGroupSessionEvent) {
        this.isLoading.set(true);
        const courseId = createEvent.courseId;
        const tutorialGroupId = createEvent.tutorialGroupId;
        const createTutorialGroupSessionDTO = createEvent.createTutorialGroupSessionDTO;
        this.tutorialGroupSessionService
            .create(courseId, tutorialGroupId, createTutorialGroupSessionDTO)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.tutorialGroupSharedStateService.fetchTutorialGroup(courseId, tutorialGroupId);
                    this.isLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.createSession');
                    this.isLoading.set(false);
                },
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
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.deleteGroup');
                    return of(undefined);
                }),
            )
            .subscribe(() => {
                this.router.navigate(['../'], { relativeTo: this.route });
                this.isLoading.set(false);
            });
    }

    private computeIfMessagingEnabled(): boolean | undefined {
        const course = this.tutorialGroupSharedStateService.course();
        return isMessagingEnabled(course);
    }

    private computeIfLoggedInUserIsAtLeastTutorOfGroup(): boolean | undefined {
        const tutorialGroup = this.tutorialGroupSharedStateService.tutorialGroup();
        const course = this.tutorialGroupSharedStateService.course();
        if (!tutorialGroup || !course) return undefined;
        return tutorialGroup.tutorLogin === this.accountService.userIdentity()?.login || this.accountService.isAtLeastEditorInCourse(course);
    }

    private computeIfLoggedInUserIsAtLeastEditorInCourse(): boolean | undefined {
        const course = this.tutorialGroupSharedStateService.course();
        if (!course) return undefined;
        return this.accountService.isAtLeastEditorInCourse(course);
    }

    private computeIfLoggedInUserIsAtLeastInstructorInCourse(): boolean | undefined {
        const course = this.tutorialGroupSharedStateService.course();
        if (!course) return undefined;
        return this.accountService.isAtLeastInstructorInCourse(course);
    }
}
