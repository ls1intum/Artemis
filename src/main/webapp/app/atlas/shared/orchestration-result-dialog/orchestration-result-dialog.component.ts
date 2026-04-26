import { DecimalPipe } from '@angular/common';
import { Component, computed, input, model } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AppliedActionDTO, AppliedActionType } from 'app/atlas/shared/dto/competency-orchestration-dto';

const TYPES_KEY_PREFIX = 'artemisApp.atlasOrchestrator.resultDialog.types.';
const WEIGHT_BANDS_KEY_PREFIX = 'artemisApp.atlasOrchestrator.resultDialog.weightBands.';

@Component({
    selector: 'jhi-orchestration-result-dialog',
    templateUrl: './orchestration-result-dialog.component.html',
    styleUrl: './orchestration-result-dialog.component.scss',
    imports: [DialogModule, TableModule, TagModule, ButtonModule, DecimalPipe, ArtemisTranslatePipe, TranslateDirective],
})
export class OrchestrationResultDialogComponent {
    readonly visible = model<boolean>(false);
    readonly summaryMessage = input<string | undefined>(undefined);
    readonly appliedActions = input.required<AppliedActionDTO[]>();

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

    protected actionTypeKey(type: AppliedActionType): string {
        switch (type) {
            case AppliedActionType.Create:
                return TYPES_KEY_PREFIX + 'CREATE';
            case AppliedActionType.Edit:
                return TYPES_KEY_PREFIX + 'EDIT';
            case AppliedActionType.Assign:
                return TYPES_KEY_PREFIX + 'ASSIGN';
            case AppliedActionType.Unassign:
                return TYPES_KEY_PREFIX + 'UNASSIGN';
            case AppliedActionType.Delete:
                return TYPES_KEY_PREFIX + 'DELETE';
        }
    }

    /**
     * Compute the weight-band tag in one place so the template can render the band label and the
     * weight without re-checking optionality. Returning {@code undefined} is the signal to render
     * no tag at all (used for non-ASSIGN actions where {@code weight} is absent). Locale-aware
     * formatting is left to the {@code DecimalPipe} in the template.
     */
    protected weightBandTag(weight: number | undefined): { translationKey: string; weight: number } | undefined {
        if (weight === undefined || weight === null || Number.isNaN(weight)) {
            return undefined;
        }
        const translationKey = weight >= 0.8 ? WEIGHT_BANDS_KEY_PREFIX + 'primary' : weight >= 0.4 ? WEIGHT_BANDS_KEY_PREFIX + 'partial' : WEIGHT_BANDS_KEY_PREFIX + 'incidental';
        return { translationKey, weight };
    }

    protected close() {
        this.visible.set(false);
    }
}
