import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { CompetencyGraphComponent } from 'app/atlas/manage/competency-graph/competency-graph.component';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CompetencyGraphDTO } from 'app/atlas/shared/entities/learning-path.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { ScienceService } from 'app/foundation/science/science.service';

export interface CompetencyGraphModalData {
    learningPathId: number;
    name?: string;
}

@Component({
    selector: 'jhi-competency-graph-modal',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FontAwesomeModule, CompetencyGraphComponent, TranslateDirective],
    templateUrl: './competency-graph-modal.component.html',
    styleUrl: './competency-graph-modal.component.scss',
})
export class CompetencyGraphModalComponent {
    protected readonly closeIcon = faXmark;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);
    private readonly scienceService = inject(ScienceService);
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    readonly name = signal<string | undefined>((this.dialogConfig.data as CompetencyGraphModalData).name);
    readonly learningPathId = signal<number>((this.dialogConfig.data as CompetencyGraphModalData).learningPathId);

    readonly isLoading = signal<boolean>(false);
    readonly competencyGraph = signal<CompetencyGraphDTO | undefined>(undefined);

    constructor() {
        effect(() => {
            const learningPathId = this.learningPathId();

            this.scienceService.logEvent(ScienceEventType.LEARNING_PATH__OPEN_GRAPH, learningPathId);

            untracked(() => this.loadCompetencyGraph(learningPathId));
        });
    }

    private async loadCompetencyGraph(learningPathId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const competencyGraph = await this.learningPathApiService.getLearningPathCompetencyGraph(learningPathId);
            this.competencyGraph.set(competencyGraph);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    closeModal(): void {
        this.dialogRef.close();
    }

    static openCompetencyGraphModal(dialogService: DialogService, learningPathId: number, name: string | undefined): void {
        dialogService.open(CompetencyGraphModalComponent, {
            style: { width: '90vw', maxWidth: '90rem' },
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            showHeader: false,
            styleClass: 'competency-graph-modal',
            data: <CompetencyGraphModalData>{ learningPathId, name },
        });
    }
}
