import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { HealthStatus, LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ActivatedRoute, Router } from '@angular/router';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-learning-paths-state',
    standalone: true,
    imports: [ArtemisSharedCommonModule],
    templateUrl: './learning-paths-state.component.html',
    styleUrls: ['./learning-paths-state.component.scss', '../../pages/learning-path-instructor-page/learning-path-instructor-page.component.scss'],
})
export class LearningPathsStateComponent {
    protected readonly faSpinner = faSpinner;

    private readonly baseTranslationKey = 'artemisApp.learningPathManagement.learningPathsState.type';
    readonly translationKeys: Record<HealthStatus, string> = {
        [HealthStatus.MISSING]: `${this.baseTranslationKey}.missing`,
        [HealthStatus.NO_COMPETENCIES]: `${this.baseTranslationKey}.noCompetencies`,
        [HealthStatus.NO_RELATIONS]: `${this.baseTranslationKey}.noRelations`,
    };

    readonly stateCssClasses: Record<HealthStatus, string> = {
        [HealthStatus.MISSING]: 'warning-state',
        [HealthStatus.NO_COMPETENCIES]: 'danger-state',
        [HealthStatus.NO_RELATIONS]: 'warning-state',
    };

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);
    private readonly router = inject(Router);
    private readonly activatedRoute = inject(ActivatedRoute);

    readonly courseId = input.required<number>();

    readonly isLoading = signal<boolean>(false);
    private readonly learningPathHealth = signal<LearningPathHealthDTO | undefined>(undefined);
    readonly learningPathHealthState = computed(() => this.learningPathHealth()?.status ?? []);

    constructor() {
        effect(() => this.loadLearningPathHealthState(this.courseId()), { allowSignalWrites: true });
    }

    protected async loadLearningPathHealthState(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const learningPathHealthState = await this.learningPathApiService.getLearningPathHealthStatus(courseId);
            this.learningPathHealth.set(learningPathHealthState);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    protected async handleHealthStateAction(healthState: HealthStatus): Promise<void> {
        switch (healthState) {
            case HealthStatus.MISSING:
                await this.generateMissingLearningPaths();
                break;
            case HealthStatus.NO_COMPETENCIES:
            case HealthStatus.NO_RELATIONS:
                await this.navigateToManageCompetencyPage();
                break;
        }
    }

    private async navigateToManageCompetencyPage(): Promise<void> {
        await this.router.navigate(['../competency-management'], { relativeTo: this.activatedRoute });
    }

    private async generateMissingLearningPaths(): Promise<void> {
        try {
            await this.learningPathApiService.generateMissingLearningPaths(this.courseId());
            this.alertService.success(`${this.baseTranslationKey}.missing.successAlert`);
            await this.loadLearningPathHealthState(this.courseId());
        } catch (error) {
            onError(this.alertService, error);
        }
    }
}
