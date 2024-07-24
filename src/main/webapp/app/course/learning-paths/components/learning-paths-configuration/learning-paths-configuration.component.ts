import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCircleQuestion, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { LearningPathApiService } from '../../services/learning-path-api.service';
import { LearningPathsConfigurationDTO } from 'app/entities/competency/learning-path.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-learning-paths-configuration',
    standalone: true,
    imports: [FontAwesomeModule, ArtemisSharedCommonModule],
    templateUrl: './learning-paths-configuration.component.html',
    styleUrl: './learning-paths-configuration.component.scss',
})
export class LearningPathsConfigurationComponent {
    protected readonly faCircleQuestion = faCircleQuestion;
    protected readonly faSpinner = faSpinner;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    readonly courseId = input.required<number>();

    readonly isEditMode = signal<boolean>(false);
    readonly configHasBeenChanged = signal<boolean>(false);

    readonly isConfigLoading = signal<boolean>(false);
    readonly isSaving = signal<boolean>(false);
    private readonly learningPathsConfiguration = signal<LearningPathsConfigurationDTO | undefined>(undefined);
    readonly includeAllGradedExercisesEnabled = computed(() => this.learningPathsConfiguration()?.includeAllGradedExercises ?? false);

    constructor() {
        effect(() => this.loadLearningPathsConfiguration(this.courseId()), { allowSignalWrites: true });
    }

    private async loadLearningPathsConfiguration(courseId: number): Promise<void> {
        try {
            this.isConfigLoading.set(true);
            const learningPathsConfiguration = await this.learningPathApiService.getLearningPathsConfiguration(courseId);
            this.learningPathsConfiguration.set(learningPathsConfiguration);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isConfigLoading.set(false);
        }
    }

    toggleIncludeAllGradedExercises(): void {
        this.configHasBeenChanged.set(true);
        this.learningPathsConfiguration.set({
            ...this.learningPathsConfiguration(),
            includeAllGradedExercises: !this.includeAllGradedExercisesEnabled(),
        });
    }

    async saveLearningPathsConfiguration(): Promise<void> {
        if (this.configHasBeenChanged()) {
            try {
                this.isSaving.set(true);
                await this.learningPathApiService.updateLearningPathsConfiguration(this.courseId(), this.learningPathsConfiguration()!);
                // TODO: Maybe add alert service success
                this.isEditMode.set(false);
            } catch (error) {
                onError(this.alertService, error);
            } finally {
                this.isSaving.set(false);
            }
        } else {
            this.isEditMode.set(false);
        }
    }

    enableEditMode(): void {
        this.isEditMode.set(true);
    }
}
