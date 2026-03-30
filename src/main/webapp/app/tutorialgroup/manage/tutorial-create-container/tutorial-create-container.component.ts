import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariable';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CreateTutorialGroupEvent, TutorialCreateOrEditComponent } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupTutorsService } from 'app/tutorialgroup/manage/service/tutorial-group-tutors.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';

@Component({
    selector: 'jhi-tutorial-create-container',
    imports: [TutorialCreateOrEditComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-create-container.component.html',
    styleUrl: './tutorial-create-container.component.scss',
})
export class TutorialCreateContainerComponent {
    private destroyRef = inject(DestroyRef);
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupApiService = inject(TutorialGroupApiService);
    private alertService = inject(AlertService);
    private tutorialGroupTutorService = inject(TutorialGroupTutorsService);
    private router = inject(Router);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    isTutorialGroupLoading = signal(false);
    isTutorsLoading = this.tutorialGroupTutorService.isLoading;
    tutors = this.tutorialGroupTutorService.tutors;
    isLoading = computed<boolean>(() => this.isTutorialGroupLoading() || this.isTutorsLoading());

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            if (courseId) {
                this.tutorialGroupTutorService.loadTutors(courseId);
            }
        });
    }

    createTutorialGroup(createTutorialGroupEvent: CreateTutorialGroupEvent) {
        this.isTutorialGroupLoading.set(true);
        const courseId = createTutorialGroupEvent.courseId;
        const createTutorialGroupDTO = createTutorialGroupEvent.createTutorialGroupDTO;
        this.tutorialGroupApiService
            .createTutorialGroup(courseId, createTutorialGroupDTO)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (newTutorialGroupId) => {
                    this.isTutorialGroupLoading.set(false);
                    this.router.navigate(['..', newTutorialGroupId], { relativeTo: this.activatedRoute });
                },
                error: () => {
                    this.alertService.addErrorAlert('artemisApp.pages.createOrEditTutorialGroup.networkError.createGroup');
                    this.isTutorialGroupLoading.set(false);
                },
            });
    }
}
