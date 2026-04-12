import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TutorialCreateOrEditComponent, UpdateTutorialGroupEvent } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupTutorsService } from 'app/tutorialgroup/manage/service/tutorial-group-tutors.service';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupSchedule } from 'app/openapi/model/tutorialGroupSchedule';

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
    private tutorialGroupCourseAndGroupService = inject(TutorialGroupCourseAndGroupService);
    private alertService = inject(AlertService);
    private tutorialGroupTutorService = inject(TutorialGroupTutorsService);
    private router = inject(Router);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    tutors = this.tutorialGroupTutorService.tutors;
    tutorialGroup = this.tutorialGroupCourseAndGroupService.tutorialGroup;
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
                    this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
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
            this.tutorialGroupCourseAndGroupService.fetchTutorialGroup(courseId, tutorialGroupId);
        }
    }
}
