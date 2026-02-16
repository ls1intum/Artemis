import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { TutorialGroupScheduleDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ActivatedRoute, Router } from '@angular/router';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TutorialCreateOrEditComponent, UpdateTutorialGroupEvent } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupService } from 'app/tutorialgroup/shared/service/tutorial-group.service';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupTutorService } from 'app/tutorialgroup/shared/service/tutorial-group-tutor.service';

@Component({
    selector: 'jhi-tutorial-edit-container',
    imports: [TutorialCreateOrEditComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-edit-container.component.html',
    styleUrl: './tutorial-edit-container.component.scss',
})
export class TutorialEditContainerComponent {
    private destroyRef = inject(DestroyRef);
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupService = inject(TutorialGroupService);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private tutorialGroupTutorService = inject(TutorialGroupTutorService);
    private router = inject(Router);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    isTutorsLoading = this.tutorialGroupTutorService.isLoading;
    tutors = this.tutorialGroupTutorService.tutors;
    isTutorialGroupLoading = this.tutorialGroupService.isLoading;
    tutorialGroup = this.tutorialGroupService.tutorialGroup;
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
        this.tutorialGroupsService
            .updateV2(courseId, tutorialGroupId, updateTutorialGroupDTO)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.isTutorialGroupLoading.set(false);
                    this.tutorialGroupService.fetchTutorialGroupDTO(courseId, tutorialGroupId);
                    this.router.navigate(['..'], { relativeTo: this.activatedRoute });
                },
                error: () => {
                    this.alertService.addErrorAlert('Something went wrong while updating the tutorial group. Please try again.'); // TODO: create string key
                    this.isTutorialGroupLoading.set(false);
                },
            });
    }

    private loadSchedule(courseId: number, tutorialGroupId: number) {
        this.isScheduleLoading.set(true);
        this.tutorialGroupsService.getTutorialGroupScheduleDTO(courseId, tutorialGroupId).subscribe({
            next: (schedule) => {
                this.schedule.set(schedule);
                this.isScheduleLoading.set(false);
            },
            error: () => {
                // TODO: create string key
                this.alertService.addErrorAlert('Something went wrong while loading the schedule for the tutorial group. Please try again by refreshing the page.');
                this.isScheduleLoading.set(false);
            },
        });
    }

    private loadGroupIfNecessary(courseId: number, tutorialGroupId: number) {
        const tutorialGroup = this.tutorialGroup();
        if (!tutorialGroup) {
            this.tutorialGroupService.fetchTutorialGroupDTO(courseId, tutorialGroupId);
        }
    }
}
