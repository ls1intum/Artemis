import { Injectable, computed, inject, signal } from '@angular/core';
import { LearningPathNavigationDto, LearningPathNavigationObjectDto } from 'app/entities/competency/learning-path.model';
import { AlertService } from 'app/core/util/alert.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';

@Injectable({ providedIn: 'root' })
export class LearningPathNavigationService {
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    readonly isLoading = signal(false);

    readonly learningPathNavigation = signal<LearningPathNavigationDto | undefined>(undefined);
    readonly currentLearningObject = computed(() => this.learningPathNavigation()?.currentLearningObject);

    readonly isCurrentLearningObjectCompleted = signal(false);

    async loadInitialLearningPathNavigation(learningPathId: number) {
        this.isLoading.set(true);
        await this.loadLearningPathNavigation(learningPathId, undefined);
        this.isLoading.set(false);
    }

    async loadRelativeLearningPathNavigation(learningPathId: number, selectedLearningObject: LearningPathNavigationObjectDto) {
        await this.loadLearningPathNavigation(learningPathId, selectedLearningObject);
    }

    private async loadLearningPathNavigation(learningPathId: number, selectedLearningObject: LearningPathNavigationObjectDto | undefined) {
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
