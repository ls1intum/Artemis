import { Component, signal } from '@angular/core';

import { faArrowDown, faArrowUp, faChevronDown, faChevronRight, faPlus, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { BUILD_PHASE_CONDITION, BuildPhase, BuildPhaseCondition } from 'app/programming/shared/entities/build-plan-phases.model';
import { FormsModule } from '@angular/forms';
import { Select } from 'primeng/select';
import { InputText } from 'primeng/inputtext';
import { ButtonDirective, ButtonIcon } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MonacoEditorFitTextComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/monaco-editor-auto-size/monaco-editor-fit-text.component';

@Component({
    selector: 'jhi-build-phase',
    imports: [FormsModule, Select, InputText, ButtonDirective, ButtonIcon, FaIconComponent, MonacoEditorFitTextComponent],
    templateUrl: './build-phase-editor.html',
})
export class BuildPhaseEditor {
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronRight = faChevronRight;
    protected readonly faPlus = faPlus;
    protected readonly faXmark = faXmark;
    protected readonly faTrash = faTrash;
    protected readonly faArrowDown = faArrowDown;
    protected readonly faArrowUp = faArrowUp;

    readonly conditionOptions = Object.entries(BUILD_PHASE_CONDITION).map(([key, label]) => ({
        value: key as BuildPhaseCondition,
        label,
    }));

    readonly phase = signal<BuildPhase>({
        name: 'script',
        script: '# enter the script of this phase',
        condition: 'ALWAYS',
        resultPaths: [],
    });

    readonly expanded = signal<boolean>(false);

    toggleExpanded(): void {
        this.expanded.update((expanded) => !expanded);
    }

    updateName(name: string): void {
        this.phase.update((phase) => ({ ...phase, name }));
    }

    updateScript(script: string): void {
        this.phase.update((phase) => ({ ...phase, script }));
    }

    updateCondition(condition: BuildPhaseCondition): void {
        this.phase.update((phase) => ({ ...phase, condition }));
    }

    addResultPath(): void {
        this.phase.update((phase) => ({ ...phase, resultPaths: [...phase.resultPaths, ''] }));
    }

    deleteResultPath(deleteIndex: number): void {
        this.phase.update((phase) => ({ ...phase, resultPaths: (phase.resultPaths ?? []).filter((_, i) => i !== deleteIndex) }));
    }
}
