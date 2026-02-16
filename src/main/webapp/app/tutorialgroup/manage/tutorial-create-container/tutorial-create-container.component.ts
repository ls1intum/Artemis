import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CreateTutorialGroupEvent, TutorialCreateOrEditComponent } from 'app/tutorialgroup/manage/tutorial-create-or-edit/tutorial-create-or-edit.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupTutorService } from 'app/tutorialgroup/shared/service/tutorial-group-tutor.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';

@Component({
    selector: 'jhi-tutorial-create-container',
    imports: [TutorialCreateOrEditComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-create-container.component.html',
    styleUrl: './tutorial-create-container.component.scss',
})
export class TutorialCreateContainerComponent {
    private destroyRef = inject(DestroyRef);
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupsService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);
    private tutorialGroupTutorService = inject(TutorialGroupTutorService);
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
        effect(() => {});
    }

    createTutorialGroup(createTutorialGroupEvent: CreateTutorialGroupEvent) {
        this.isTutorialGroupLoading.set(true);
        const courseId = createTutorialGroupEvent.courseId;
        const createTutorialGroupDTO = createTutorialGroupEvent.createTutorialGroupDTO;
        this.tutorialGroupsService
            .createV2(courseId, createTutorialGroupDTO)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.isTutorialGroupLoading.set(false);
                    this.router.navigate(['..'], { relativeTo: this.activatedRoute });
                },
                error: () => {
                    this.alertService.addErrorAlert('Something went wrong while creating the tutorial group. Please try again.'); // TODO: create string key
                    this.isTutorialGroupLoading.set(false);
                },
            });
    }
}
