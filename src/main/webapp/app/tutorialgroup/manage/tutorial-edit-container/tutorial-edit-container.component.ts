import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { getNumericPathVariableSignal } from 'app/foundation/route/getPathVariable';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TutorialCreateOrEditComponent, UpdateTutorialGroupEvent } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared-ui/loading-indicator-overlay/loading-indicator-overlay.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { TutorialGroupTutorsService } from 'app/tutorialgroup/manage/service/tutorial-group-tutors.service';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { TutorialGroupSchedule } from 'app/openapi/model/tutorial-group-schedule';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';

@Component({
    selector: 'jhi-tutorial-edit-container',
    imports: [TutorialCreateOrEditComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-edit-container.component.html',
    styleUrl: './tutorial-edit-container.component.scss',
})
export class TutorialEditContainerComponent {
    private destroyRef = inject(DestroyRef);
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupApiService = inject(TutorialGroupApi);
    private tutorialGroupCourseAndGroupService = inject(TutorialGroupCourseAndGroupService);
    private alertService = inject(AlertService);
    private tutorialGroupTutorService = inject(TutorialGroupTutorsService);
    private router = inject(Router);
    private entityTitleService = inject(EntityTitleService);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    tutors = this.tutorialGroupTutorService.tutors;
    tutorialGroup = computed(() => {
        const group = this.tutorialGroupCourseAndGroupService.tutorialGroup();
        return group?.id === this.tutorialGroupId() ? group : undefined;
    });
    schedule = signal<TutorialGroupSchedule | undefined>(undefined);
    isTutorsLoading = this.tutorialGroupTutorService.isLoading;
    isTutorialGroupLoading = this.tutorialGroupCourseAndGroupService.isTutorialGroupLoading;
    isScheduleLoading = signal(false);
    isLoading = computed<boolean>(() => this.isTutorsLoading() || this.isTutorialGroupLoading() || this.isScheduleLoading());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.tutorialGroupTutorService.loadTutors(courseId);
            }
        });
        effect((onCleanup) => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.loadGroupIfNecessary(courseId, tutorialGroupId);
                const scheduleRequest = this.loadSchedule(courseId, tutorialGroupId);
                onCleanup(() => scheduleRequest.unsubscribe());
            }
        });
    }

    updateTutorialGroup(updateTutorialGroupEvent: UpdateTutorialGroupEvent) {
        this.isTutorialGroupLoading.set(true);
        const courseId = updateTutorialGroupEvent.courseId;
        const tutorialGroupId = updateTutorialGroupEvent.tutorialGroupId;
        const updateTutorialGroupDTO = updateTutorialGroupEvent.updateTutorialGroupDTO;
        this.tutorialGroupApiService
            .updateTutorialGroup(courseId, tutorialGroupId, updateTutorialGroupDTO)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.entityTitleService.setTitle(EntityType.TUTORIAL_GROUP, [tutorialGroupId], updateTutorialGroupDTO.title);
                    this.isTutorialGroupLoading.set(false);
                    this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
                    void this.router.navigate(['/course-management', courseId, 'tutorial-groups']);
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.createOrEditTutorialGroup.networkError.updateGroup');
                    this.isTutorialGroupLoading.set(false);
                },
            });
    }

    private loadSchedule(courseId: number, tutorialGroupId: number) {
        this.schedule.set(undefined);
        this.isScheduleLoading.set(true);
        return this.tutorialGroupApiService.getTutorialGroupSchedule(courseId, tutorialGroupId).subscribe({
            next: (schedule) => {
                this.schedule.set(schedule);
                this.isScheduleLoading.set(false);
            },
            error: () => {
                this.alertService.addErrorAlert('artemisApp.pages.createOrEditTutorialGroup.networkError.fetchSchedule');
                this.isScheduleLoading.set(false);
            },
        });
    }

    private loadGroupIfNecessary(courseId: number, tutorialGroupId: number) {
        const tutorialGroup = untracked(this.tutorialGroup);
        if (tutorialGroup?.id !== tutorialGroupId) {
            this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
        }
    }
}
