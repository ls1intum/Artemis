import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { TumAetUiDialogConfig, TumAetUiDialogService } from '@tumaet/ui-angular/dialog';
import { TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular/progress-spinner';
import { CompetencyGraphComponent } from 'app/atlas/manage/competency-graph/competency-graph.component';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { getErrorMessage } from 'app/foundation/util/global.utils';
import { CompetencyGraphDTO } from 'app/atlas/shared/entities/learning-path.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DialogTranslateHeaderComponent } from 'app/shared-ui/dynamic-dialog/dialog-translate-header.component';

export interface CompetencyGraphModalData {
    learningPathId: number;
    name?: string;
}

@Component({
    selector: 'jhi-competency-graph-modal',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [CompetencyGraphComponent, TumAetUiProgressSpinnerComponent, ArtemisTranslatePipe],
    templateUrl: './competency-graph-modal.component.html',
})
export class CompetencyGraphModalComponent {
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);
    private readonly dialogConfig = inject<TumAetUiDialogConfig<CompetencyGraphModalData>>(TumAetUiDialogConfig);

    readonly name = signal<string | undefined>(this.dialogConfig.data?.name);
    readonly learningPathId = signal<number>(this.dialogConfig.data!.learningPathId);

    readonly isLoading = signal<boolean>(false);
    readonly competencyGraph = signal<CompetencyGraphDTO | undefined>(undefined);

    constructor() {
        effect(() => {
            const learningPathId = this.learningPathId();
            untracked(() => void this.loadCompetencyGraph(learningPathId));
        });
    }

    private async loadCompetencyGraph(learningPathId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const competencyGraph = await this.learningPathApiService.getLearningPathCompetencyGraph(learningPathId);
            this.competencyGraph.set(competencyGraph);
        } catch (error) {
            this.alertService.error(getErrorMessage(error));
        } finally {
            this.isLoading.set(false);
        }
    }

    static openCompetencyGraphModal(dialogService: TumAetUiDialogService, learningPathId: number, name: string | undefined): void {
        const data: CompetencyGraphModalData & { headerKey: string; headerParams?: { name: string } } =
            name === undefined
                ? { learningPathId, name, headerKey: 'artemisApp.learningPath.competencyGraphModal.title' }
                : { learningPathId, name, headerKey: 'artemisApp.learningPath.competencyGraphModal.titleWithName', headerParams: { name } };
        dialogService.open(CompetencyGraphModalComponent, {
            size: 'large',
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data,
            templates: { header: DialogTranslateHeaderComponent },
        });
    }
}
