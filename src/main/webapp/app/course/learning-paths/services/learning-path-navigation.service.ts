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

    async loadInitialLearningPathNavigation(learningPathId: number): Promise<void> {
        this.isLoading.set(true);
        await this.loadLearningPathNavigation(learningPathId, undefined);
        this.isLoading.set(false);
    }

    async loadRelativeLearningPathNavigation(learningPathId: number, selectedLearningObject: LearningPathNavigationObjectDTO): Promise<void> {
        await this.loadLearningPathNavigation(learningPathId, selectedLearningObject);
    }

    private async loadLearningPathNavigation(learningPathId: number, selectedLearningObject: LearningPathNavigationObjectDTO | undefined): Promise<void> {
        try {
            const learningPathNavigation = await this.learningPathApiService.getLearningPathNavigation(learningPathId, selectedLearningObject?.id, selectedLearningObject?.type);
            this.learningPathNavigation.set(learningPathNavigation);
            this.setCurrentLearningObjectCompletion(selectedLearningObject?.completed ?? false);
        } catch (error) {
            this.alertService.error(error);
        }
    }

    setCurrentLearningObjectCompletion(completed: boolean): void {
        this.isCurrentLearningObjectCompleted.set(completed);
    }
}
