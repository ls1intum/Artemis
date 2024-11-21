import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { CompetencyGraphDTO, CompetencyGraphNodeValueType } from 'app/entities/competency/learning-path.model';
import { AlertService } from 'app/core/util/alert.service';
import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { onError } from 'app/shared/util/global.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-learning-paths-analytics',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [CompetencyGraphComponent, TranslateDirective, CommonModule],
    templateUrl: './learning-paths-analytics.component.html',
    styleUrl: './learning-paths-analytics.component.scss',
})
export class LearningPathsAnalyticsComponent {
    protected readonly CompetencyGraphNodeValueType = CompetencyGraphNodeValueType;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    readonly courseId = input.required<number>();

    readonly isLoading = signal<boolean>(false);
    readonly instructorCompetencyGraph = signal<CompetencyGraphDTO | undefined>(undefined);

    readonly valueSelection = signal<CompetencyGraphNodeValueType>(CompetencyGraphNodeValueType.AVERAGE_MASTERY_PROGRESS);

    constructor() {
        effect(
            () => {
                const courseId = this.courseId();
                untracked(() => this.loadInstructionCompetencyGraph(courseId));
            },
            { allowSignalWrites: true },
        );
    }

    private async loadInstructionCompetencyGraph(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const instructorCompetencyGraph = await this.learningPathApiService.getLearningPathInstructorCompetencyGraph(courseId);
            this.instructorCompetencyGraph.set(instructorCompetencyGraph);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }
}
