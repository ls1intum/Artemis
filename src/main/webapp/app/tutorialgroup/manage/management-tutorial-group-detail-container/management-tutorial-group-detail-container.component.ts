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
    TutorialGroupDetailAccessLevel,
    TutorialGroupDetailComponent,
    UpdateTutorialGroupSessionEvent,
} from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupSessionService } from 'app/tutorialgroup/manage/service/tutorial-group-session.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { getRouteData } from 'app/shared/route/getRouteData';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
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
    private tutorialGroupCourseAndGroupService = inject(TutorialGroupCourseAndGroupService);
    private tutorialGroupSessionApiService = inject(TutorialGroupSessionApiService);
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private tutorialGroupId = getNumericPathVariableSignal(this.route, 'tutorialGroupId');
    private isActionLoading = signal(false);
    private course = this.tutorialGroupCourseAndGroupService.course;

    isLoading = computed(() => this.tutorialGroupCourseAndGroupService.isTutorialGroupLoading() || this.isActionLoading());
    tutorialGroup = this.tutorialGroupCourseAndGroupService.tutorialGroup;
    courseId = getNumericPathVariableSignal(this.route, 'courseId');
    isMessagingEnabled = computed(() => isMessagingEnabled(this.course()));
    loggedInUserTutorialGroupDetailAccessLevel = computed(() => this.computeLoggedInUserTutorialGroupDetailAccessLevel());

    constructor() {
        const course = getRouteData<Course>(this.route, 'course');
        this.tutorialGroupCourseAndGroupService.course.set(course);

        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
            }
        });
    }

    deleteSession(deletionEvent: ModifyTutorialGroupSessionEvent) {
        const { courseId, tutorialGroupId, tutorialGroupSessionId } = deletionEvent;
        this.isActionLoading.set(true);
        this.tutorialGroupSessionApiService
            .deleteSession(courseId, tutorialGroupId, tutorialGroupSessionId, 'response')
            .pipe(
                catchError((_) => {
                    this.isActionLoading.set(false);
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.deleteSession');
                    return of(undefined);
                }),
            )
            .subscribe((response) => {
                this.isActionLoading.set(false);
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
        this.isActionLoading.set(true);
        const courseId = cancellationEvent.courseId;
        const tutorialGroupId = cancellationEvent.tutorialGroupId;
        const tutorialGroupSessionId = cancellationEvent.tutorialGroupSessionId;
        this.tutorialGroupSessionService
            .cancel(courseId, tutorialGroupId, tutorialGroupSessionId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.tutorialGroupCourseAndGroupService.toggleCancellationStatusOfSession(tutorialGroupSessionId);
                    this.isActionLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.cancelSession');
                    this.isActionLoading.set(false);
                },
            });
    }

    activateSession(cancellationEvent: ModifyTutorialGroupSessionEvent) {
        this.isActionLoading.set(true);
        const courseId = cancellationEvent.courseId;
        const tutorialGroupId = cancellationEvent.tutorialGroupId;
        const tutorialGroupSessionId = cancellationEvent.tutorialGroupSessionId;
        this.tutorialGroupSessionService
            .activate(courseId, tutorialGroupId, tutorialGroupSessionId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.tutorialGroupCourseAndGroupService.toggleCancellationStatusOfSession(tutorialGroupSessionId);
                    this.isActionLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.activateSession');
                    this.isActionLoading.set(false);
                },
            });
    }

    updateSession(updateEvent: UpdateTutorialGroupSessionEvent) {
        this.isActionLoading.set(true);
        const courseId = updateEvent.courseId;
        const tutorialGroupId = updateEvent.tutorialGroupId;
        const tutorialGroupSessionId = updateEvent.tutorialGroupSessionId;
        const updateTutorialGroupSessionDTO = updateEvent.updateTutorialGroupSessionDTO;
        this.tutorialGroupSessionService
            .update(courseId, tutorialGroupId, tutorialGroupSessionId, updateTutorialGroupSessionDTO)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
                    this.isActionLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.updateSession');
                    this.isActionLoading.set(false);
                },
            });
    }

    createSession(createEvent: CreateTutorialGroupSessionEvent) {
        this.isActionLoading.set(true);
        const courseId = createEvent.courseId;
        const tutorialGroupId = createEvent.tutorialGroupId;
        const createTutorialGroupSessionDTO = createEvent.createTutorialGroupSessionDTO;
        this.tutorialGroupSessionService
            .create(courseId, tutorialGroupId, createTutorialGroupSessionDTO)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
                    this.isActionLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.createSession');
                    this.isActionLoading.set(false);
                },
            });
    }

    deleteGroup(deletionEvent: DeleteTutorialGroupEvent) {
        const { courseId, tutorialGroupId } = deletionEvent;
        this.isActionLoading.set(true);
        this.tutorialGroupApiService
            .deleteTutorialGroup(courseId, tutorialGroupId, 'response')
            .pipe(
                catchError((_) => {
                    this.isActionLoading.set(false);
                    this.alertService.addErrorAlert('artemisApp.pages.tutorialGroupDetail.networkError.deleteGroup');
                    return of(undefined);
                }),
            )
            .subscribe(() => {
                this.router.navigate(['../'], { relativeTo: this.route });
                this.isActionLoading.set(false);
            });
    }

    private computeLoggedInUserTutorialGroupDetailAccessLevel(): TutorialGroupDetailAccessLevel | undefined {
        const course = this.course();
        if (!course) return undefined;
        if (this.accountService.isAtLeastInstructorInCourse(course)) {
            return TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN;
        }
        if (this.accountService.isAtLeastEditorInCourse(course)) {
            return TutorialGroupDetailAccessLevel.EDITOR_OF_GROUP;
        }
        const tutorialGroup = this.tutorialGroup();
        if (tutorialGroup && tutorialGroup.tutorLogin === this.accountService.userIdentity()?.login) {
            return TutorialGroupDetailAccessLevel.TUTOR_OF_GROUP;
        }
        return TutorialGroupDetailAccessLevel.TUTOR_OF_OTHER_GROUP_OR_EDITOR_OR_INSTRUCTOR_OF_OTHER_COURSE;
    }
}
