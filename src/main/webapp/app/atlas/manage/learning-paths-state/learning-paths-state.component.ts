import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { LearningPathApiService } from 'app/atlas/shared/learning-path-api.service';
import { HealthStatus, LearningPathHealthDTO } from 'app/atlas/shared/entities/learning-path-health.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-learning-paths-state',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FontAwesomeModule, NgClass],
    templateUrl: './learning-paths-state.component.html',
    styleUrls: ['./learning-paths-state.component.scss', '../learning-path-instructor-page/learning-path-instructor-page.component.scss'],
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
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadLearningPathHealthState(courseId));
        });
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
