import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { LearningPathApiService } from '../../shared/services/learning-path-api.service';
import { LearningPathsConfigurationDTO } from 'app/atlas/shared/entities/learning-path.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-learning-paths-configuration',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FontAwesomeModule, TranslateDirective, HelpIconComponent],
    templateUrl: './learning-paths-configuration.component.html',
    styleUrls: ['../learning-path-instructor-page/learning-path-instructor-page.component.scss'],
})
export class LearningPathsConfigurationComponent {
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
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadLearningPathsConfiguration(courseId));
        });
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

    protected toggleIncludeAllGradedExercises(): void {
        this.configHasBeenChanged.set(true);
        this.learningPathsConfiguration.set(Object.assign({}, this.learningPathsConfiguration(), { includeAllGradedExercises: !this.includeAllGradedExercisesEnabled() }));
    }

    protected async saveLearningPathsConfiguration(): Promise<void> {
        if (this.configHasBeenChanged()) {
            try {
                this.isSaving.set(true);
                await this.learningPathApiService.updateLearningPathsConfiguration(this.courseId(), this.learningPathsConfiguration()!);
                this.alertService.success('artemisApp.learningPathManagement.learningPathsConfiguration.saveSuccess');
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

    protected enableEditMode(): void {
        this.isEditMode.set(true);
    }
}
