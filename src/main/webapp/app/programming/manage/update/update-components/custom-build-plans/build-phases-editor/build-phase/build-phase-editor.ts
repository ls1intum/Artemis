import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';

import { faArrowDown, faArrowUp, faPlus, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { BUILD_PHASE_CONDITION, BuildPhase, BuildPhaseCondition } from 'app/programming/shared/entities/build-plan-phases.model';
import { FormsModule } from '@angular/forms';
import { Select } from 'primeng/select';
import { InputText } from 'primeng/inputtext';
import { ButtonDirective, ButtonIcon, ButtonLabel } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MonacoEditorFitTextComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/monaco-editor-auto-size/monaco-editor-fit-text.component';

@Component({
    selector: 'jhi-build-phase',
    imports: [FormsModule, Select, InputText, ButtonDirective, ButtonIcon, FaIconComponent, MonacoEditorFitTextComponent, ButtonLabel],
    templateUrl: './build-phase-editor.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildPhaseEditor {
    protected readonly faPlus = faPlus;
    protected readonly faXmark = faXmark;
    protected readonly faTrash = faTrash;
    protected readonly faArrowDown = faArrowDown;
    protected readonly faArrowUp = faArrowUp;

    readonly phase = model.required<BuildPhase>();
    readonly isFirst = input<boolean>(false);
    readonly isLast = input<boolean>(false);
    readonly isOnly = input<boolean>(false);

    readonly phaseChange = output<BuildPhase>();
    readonly delete = output<void>();
    readonly moveUp = output<void>();
    readonly moveDown = output<void>();

    readonly conditionOptions = Object.entries(BUILD_PHASE_CONDITION).map(([key, label]) => ({
        value: key as BuildPhaseCondition,
        label,
    }));

    updateName(name: string): void {
        this.phaseChange.emit({ ...this.phase(), name });
    }

    updateScript(script: string): void {
        this.phaseChange.emit({ ...this.phase(), script });
    }

    updateCondition(condition: BuildPhaseCondition): void {
        this.phaseChange.emit({ ...this.phase(), condition });
    }

    updateResultPath(index: number, value: string): void {
        const resultPaths = [...(this.phase().resultPaths ?? [])];
        resultPaths[index] = value;
        this.phaseChange.emit({ ...this.phase(), resultPaths });
    }

    addResultPath(): void {
        const resultPaths = [...(this.phase().resultPaths ?? []), ''];
        this.phaseChange.emit({ ...this.phase(), resultPaths });
    }

    deleteResultPath(deleteIndex: number): void {
        const resultPaths = (this.phase().resultPaths ?? []).filter((_, i) => i !== deleteIndex);
        this.phaseChange.emit({ ...this.phase(), resultPaths });
    }
}
