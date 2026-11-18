import { DestroyRef, Service, inject, signal } from '@angular/core';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { map } from 'rxjs/operators';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { AccountService } from 'app/core/auth/account.service';

@Service()
export class TutorialGroupCourseAndGroupService {
    private tutorialGroupApiService = inject(TutorialGroupApi);
    private courseManagementService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private entityTitleService = inject(EntityTitleService);
    private destroyRef = inject(DestroyRef);
    private accountService = inject(AccountService);

    private groupRequest?: Subscription;
    private courseRequest?: Subscription;

    isTutorialGroupLoading = signal(false);
    tutorialGroup = signal<TutorialGroupDetailData | undefined>(undefined);
    isCourseLoading = signal(false);
    course = signal<Course | undefined>(undefined);

    constructor() {
        let currentUserId = this.accountService.userIdentity()?.id;
        this.accountService
            .getAuthenticationState()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((user) => {
                if (currentUserId === user?.id) return;
                currentUserId = user?.id;
                this.groupRequest?.unsubscribe();
                this.courseRequest?.unsubscribe();
                this.tutorialGroup.set(undefined);
                this.course.set(undefined);
                this.isTutorialGroupLoading.set(false);
                this.isCourseLoading.set(false);
            });
    }

    toggleCancellationStatusOfSession(sessionId: number) {
        this.tutorialGroup.update((tutorialGroup) => {
            if (!tutorialGroup) return tutorialGroup;
            return cloneWith(tutorialGroup, {
                sessions: tutorialGroup.sessions.map((session) => (session.id !== sessionId ? session : cloneWith(session, { isCancelled: !session.isCancelled }))),
            });
        });
    }

    insertSession(sessionToInsert: TutorialGroupSession) {
        this.tutorialGroup.update((tutorialGroup) => {
            if (!tutorialGroup) return tutorialGroup;

            const sessions = [...tutorialGroup.sessions];
            const existingSessionIndex = sessions.findIndex((session) => session.id === sessionToInsert.id);

            if (existingSessionIndex !== -1) {
                sessions.splice(existingSessionIndex, 1);
            }

            const insertIndex = sessions.findIndex((session) => sessionToInsert.start.isBefore(session.start));
            if (insertIndex === -1) {
                sessions.push(sessionToInsert);
            } else {
                sessions.splice(insertIndex, 0, sessionToInsert);
            }

            return cloneWith(tutorialGroup, { sessions });
        });
    }

    fetchTutorialGroup(courseId: number, tutorialGroupId: number) {
        this.groupRequest?.unsubscribe();
        this.tutorialGroup.set(undefined);
        this.isTutorialGroupLoading.set(true);
        this.groupRequest = this.tutorialGroupApiService
            .getTutorialGroup(courseId, tutorialGroupId)
            .pipe(
                map((tutorialGroupDetail) => new TutorialGroupDetailData(tutorialGroupDetail)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (dto) => {
                    this.tutorialGroup.set(dto);
                    this.entityTitleService.setTitle(EntityType.TUTORIAL_GROUP, [dto.id], dto.title);
                    this.isTutorialGroupLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchGroup');
                    this.isTutorialGroupLoading.set(false);
                },
            });
    }

    fetchCourse(courseId: number) {
        this.courseRequest?.unsubscribe();
        this.isCourseLoading.set(true);
        this.courseRequest = this.courseManagementService
            .find(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    const course = response.body;
                    if (course) {
                        this.course.set(course);
                    } else {
                        this.alertService.addErrorAlert('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchCourse');
                    }
                    this.isCourseLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchCourse');
                    this.isCourseLoading.set(false);
                },
            });
    }
}
