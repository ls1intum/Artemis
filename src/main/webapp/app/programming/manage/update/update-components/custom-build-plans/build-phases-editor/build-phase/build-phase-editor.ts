import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';

import { faArrowDown, faArrowUp, faPlus, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { BUILD_PHASE_CONDITION, BuildPhase, BuildPhaseCondition } from 'app/programming/shared/entities/build-plan-phases.model';
import { FormsModule } from '@angular/forms';
import { Select } from 'primeng/select';
import { InputText } from 'primeng/inputtext';
import { ButtonDirective, ButtonIcon, ButtonLabel } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MonacoEditorFitTextComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/monaco-editor-auto-size/monaco-editor-fit-text.component';
import { Card } from 'primeng/card';
import { Badge } from 'primeng/badge';

@Component({
    selector: 'jhi-build-phase',
    imports: [FormsModule, Select, InputText, ButtonDirective, ButtonIcon, FaIconComponent, MonacoEditorFitTextComponent, ButtonLabel, Card, Badge],
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

    readonly index = input.required<number>();
    readonly isLast = input.required<boolean>();

    readonly isFirst = computed(() => this.index() === 0);
    readonly isOnly = computed(() => this.isFirst() && this.isLast());
    readonly testsExpectedForThisPhase = computed(() => !!this.phase().resultPaths?.length);

    readonly delete = output<void>();
    readonly moveUp = output<void>();
    readonly moveDown = output<void>();

    readonly conditionOptions = Object.entries(BUILD_PHASE_CONDITION).map(([key, label]) => ({
        value: key as BuildPhaseCondition,
        label,
    }));

    updateName(name: string): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, name }));
    }

    updateScript(script: string): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, script }));
    }

    updateCondition(condition: BuildPhaseCondition): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, condition }));
    }

    updateResultPath(index: number, value: string): void {
        this.phase.update((oldPhase) => {
            const resultPaths = [...(oldPhase.resultPaths ?? [])];
            resultPaths[index] = value;
            return { ...oldPhase, resultPaths };
        });
    }

    addResultPath(): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, resultPaths: [...(oldPhase.resultPaths ?? []), ''] }));
    }

    deleteResultPath(deleteIndex: number): void {
        this.phase.update((oldPhase) => ({
            ...oldPhase,
            resultPaths: (oldPhase.resultPaths ?? []).filter((_, i) => i !== deleteIndex),
        }));
    }
}
