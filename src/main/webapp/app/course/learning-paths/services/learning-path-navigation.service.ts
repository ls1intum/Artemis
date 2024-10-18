import { computed, inject, Injectable, signal } from '@angular/core';
import { LearningPathNavigationDTO, LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';

@Injectable({ providedIn: 'root' })
export class LearningPathNavigationService {
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    readonly isLoading = signal<boolean>(false);

    readonly learningPathNavigation = signal<LearningPathNavigationDTO | undefined>(undefined);
    readonly currentLearningObject = computed(() => this.learningPathNavigation()?.currentLearningObject);

    readonly isCurrentLearningObjectCompleted = signal<boolean>(false);

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
