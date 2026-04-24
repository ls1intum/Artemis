import { Component, computed, input, model } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AppliedActionDTO, AppliedActionType } from 'app/atlas/shared/dto/competency-orchestration-dto';

@Component({
    selector: 'jhi-orchestration-result-dialog',
    templateUrl: './orchestration-result-dialog.component.html',
    styleUrl: './orchestration-result-dialog.component.scss',
    imports: [DialogModule, TableModule, TagModule, ButtonModule, ArtemisTranslatePipe, TranslateDirective],
})
export class OrchestrationResultDialogComponent {
    readonly visible = model<boolean>(false);
    readonly summaryMessage = input<string>('');
    readonly appliedActions = input<AppliedActionDTO[]>([]);

    readonly hasActions = computed(() => this.appliedActions().length > 0);

    protected actionSeverity(type: AppliedActionType): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
        switch (type) {
            case AppliedActionType.Create:
                return 'success';
            case AppliedActionType.Edit:
                return 'info';
            case AppliedActionType.Assign:
                return 'info';
            case AppliedActionType.Unassign:
                return 'warn';
            case AppliedActionType.Delete:
                return 'danger';
            default:
                return 'secondary';
        }
    }

    protected weightBandKey(weight: number | undefined): string | undefined {
        if (weight === undefined) {
            return undefined;
        }
        if (weight >= 0.8) {
            return 'primary';
        }
        if (weight >= 0.4) {
            return 'partial';
        }
        return 'incidental';
    }

    protected close() {
        this.visible.set(false);
    }
}
