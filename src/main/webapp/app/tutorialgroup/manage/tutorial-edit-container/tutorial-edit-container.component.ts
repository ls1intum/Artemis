import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { TutorialGroupScheduleDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ActivatedRoute, Router } from '@angular/router';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TutorialCreateOrEditComponent, UpdateTutorialGroupEvent } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupTutorsService } from 'app/tutorialgroup/manage/service/tutorial-group-tutors.service';
import { TutorialGroupSharedStateService } from 'app/tutorialgroup/shared/service/tutorial-group-shared-state.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';

@Component({
    selector: 'jhi-tutorial-edit-container',
    imports: [TutorialCreateOrEditComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-edit-container.component.html',
    styleUrl: './tutorial-edit-container.component.scss',
})
export class TutorialEditContainerComponent {
    private destroyRef = inject(DestroyRef);
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private tutorialGroupSharedStateService = inject(TutorialGroupSharedStateService);
    private alertService = inject(AlertService);
    private tutorialGroupTutorService = inject(TutorialGroupTutorsService);
    private router = inject(Router);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    isTutorsLoading = this.tutorialGroupTutorService.isLoading;
    tutors = this.tutorialGroupTutorService.tutors;
    isTutorialGroupLoading = this.tutorialGroupSharedStateService.isTutorialGroupLoading;
    tutorialGroup = this.tutorialGroupSharedStateService.tutorialGroup;
    isScheduleLoading = signal(false);
    schedule = signal<TutorialGroupScheduleDTO | undefined>(undefined);
    isLoading = computed<boolean>(() => this.isTutorsLoading() || this.isTutorialGroupLoading() || this.isScheduleLoading());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.tutorialGroupTutorService.loadTutors(courseId);
            }
        });
        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupId = this.tutorialGroupId();
            if (courseId && tutorialGroupId) {
                this.loadGroupIfNecessary(courseId, tutorialGroupId);
                this.loadSchedule(courseId, tutorialGroupId);
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
                    this.isTutorialGroupLoading.set(false);
                    this.tutorialGroupSharedStateService.fetchTutorialGroup(courseId, tutorialGroupId);
                    this.router.navigate(['..'], { relativeTo: this.activatedRoute });
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.createOrEditTutorialGroup.networkError.updateGroup');
                    this.isTutorialGroupLoading.set(false);
                },
            });
    }

    private loadSchedule(courseId: number, tutorialGroupId: number) {
        this.isScheduleLoading.set(true);
        this.tutorialGroupApiService.getTutorialGroupSchedule(courseId, tutorialGroupId).subscribe({
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
        const tutorialGroup = this.tutorialGroup();
        if (!tutorialGroup) {
            this.tutorialGroupSharedStateService.fetchTutorialGroup(courseId, tutorialGroupId);
        }
    }
}
