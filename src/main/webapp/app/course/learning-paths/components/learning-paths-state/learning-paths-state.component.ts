import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { HealthStatus, LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ActivatedRoute, Router } from '@angular/router';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';

interface LearningPathHealthStatusItem {
    translationKey: string;
    statusClass: string;
    healthStatus: HealthStatus;
}

@Component({
    selector: 'jhi-learning-paths-state',
    standalone: true,
    imports: [ArtemisSharedCommonModule],
    templateUrl: './learning-paths-state.component.html',
    styleUrls: ['./learning-paths-state.component.scss', '../../pages/learning-path-instructor-page/learning-path-instructor-page.component.scss'],
})
export class LearningPathsStateComponent {
    protected readonly faSpinner = faSpinner;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);
    private readonly router = inject(Router);
    private readonly activatedRoute = inject(ActivatedRoute);

    readonly courseId = input.required<number>();

    readonly isLoading = signal<boolean>(false);
    private readonly learningPathHealthStatus = signal<LearningPathHealthDTO | undefined>(undefined);

    readonly learningPathHealthItems = computed(() => {
        return (this.learningPathHealthStatus()?.status ?? []).map((status) => {
            return <LearningPathHealthStatusItem>{
                translationKey: this.getHealthStatusTranslationKey(status),
                statusClass: this.getHealthStatusClass(status),
                healthStatus: status,
            };
        });
    });

    constructor() {
        effect(() => this.loadLearningPathHealthStatus(this.courseId()), { allowSignalWrites: true });
    }

    protected async loadLearningPathHealthStatus(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const learningPathHealthStatus = await this.learningPathApiService.getLearningPathHealthStatus(courseId);
            this.learningPathHealthStatus.set(learningPathHealthStatus);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    private getHealthStatusTranslationKey(healthStatus: HealthStatus): string {
        const baseKey = 'artemisApp.learningPathManagement.learningPathsState.type';
        switch (healthStatus) {
            case HealthStatus.MISSING:
                return `${baseKey}.missing`;
            case HealthStatus.NO_COMPETENCIES:
                return `${baseKey}.noCompetencies`;
            case HealthStatus.NO_RELATIONS:
                return `${baseKey}.noRelations`;
            default:
                throw Error('Unknown health status');
        }
    }

    private getHealthStatusClass(healthStatus: HealthStatus) {
        switch (healthStatus) {
            case HealthStatus.MISSING:
                return 'warning-state';
            case HealthStatus.NO_COMPETENCIES:
                return 'danger-state';
            case HealthStatus.NO_RELATIONS:
                return 'danger-state';
            default:
                return 'info-state';
        }
    }

    protected async handleHealthStatusAction(healthStatus: HealthStatus) {
        switch (healthStatus) {
            case HealthStatus.MISSING:
                await this.generateMissingLearningPaths();
                break;
            case HealthStatus.NO_COMPETENCIES:
            case HealthStatus.NO_RELATIONS:
                this.navigateToManageCompetencyPage();
                break;
        }
    }

    private navigateToManageCompetencyPage(): void {
        this.router.navigate(['../competency-management'], { relativeTo: this.activatedRoute });
    }

    private async generateMissingLearningPaths(): Promise<void> {
        try {
            await this.learningPathApiService.generateMissingLearningPaths(this.courseId());
            await this.loadLearningPathHealthStatus(this.courseId());
            this.alertService.success('artemisApp.learningPathManagement.learningPathsState.type.missing.successAlert');
        } catch (error) {
            onError(this.alertService, error);
        }
    }
}
