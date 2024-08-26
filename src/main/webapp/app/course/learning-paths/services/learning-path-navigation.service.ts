import { Injectable, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import { LearningPathNavigationDTO, LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';

@Injectable({ providedIn: 'root' })
export class LearningPathNavigationService {
    private readonly learningPathApiService: LearningPathApiService = inject(LearningPathApiService);
    private readonly alertService: AlertService = inject(AlertService);

    readonly isLoading: WritableSignal<boolean> = signal(false);

    readonly learningPathNavigation: WritableSignal<LearningPathNavigationDTO | undefined> = signal(undefined);
    readonly currentLearningObject: Signal<LearningPathNavigationObjectDTO | undefined> = computed(() => this.learningPathNavigation()?.currentLearningObject);

    readonly isCurrentLearningObjectCompleted: WritableSignal<boolean> = signal(false);

    async loadLearningPathNavigation(learningPathId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const learningPathNavigation = await this.learningPathApiService.getLearningPathNavigation(learningPathId);
            this.learningPathNavigation.set(learningPathNavigation);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    async loadRelativeLearningPathNavigation(learningPathId: number, selectedLearningObject: LearningPathNavigationObjectDTO): Promise<void> {
        try {
            const learningPathNavigation = await this.learningPathApiService.getRelativeLearningPathNavigation(
                learningPathId,
                selectedLearningObject.id,
                selectedLearningObject.type,
                selectedLearningObject.competencyId,
            );
            this.learningPathNavigation.set(learningPathNavigation);
        } catch (error) {
            this.alertService.error(error);
        }
    }

    completeLearningPath(): void {
        this.learningPathNavigation.set({ predecessorLearningObject: this.currentLearningObject(), progress: 100 });
    }

    setCurrentLearningObjectCompletion(completed: boolean): void {
        this.isCurrentLearningObjectCompleted.set(completed);
    }
}
