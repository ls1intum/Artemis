import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { BuildPhase, BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { BuildPhaseEditor } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phase/build-phase-editor';

@Component({
    selector: 'jhi-build-phases-editor',
    imports: [CommonModule, FormsModule, ButtonModule, InputTextModule, SelectModule, TextareaModule, FaIconComponent, BuildPhaseEditor],
    templateUrl: './build-phases-editor.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildPhasesEditor {
    protected readonly faPlus = faPlus;

    readonly phases = signal<BuildPhase[]>([
        {
            name: '',
            script: '# enter the script of this phase',
            condition: 'ALWAYS',
            resultPaths: [],
        },
    ]);

    readonly phaseCount = computed(() => this.phases().length);

    addPhase(): void {
        this.phases.update((phases) => [
            ...phases,
            {
                name: '',
                script: '# enter the script of this phase',
                condition: 'ALWAYS',
                resultPaths: [],
            },
        ]);
    }

    deletePhase(index: number): void {
        this.phases.update((phases) => phases.filter((_, i) => i !== index));
    }

    updatePhase(index: number, updated: BuildPhase): void {
        this.phases.update((phases) => phases.map((p, i) => (i === index ? updated : p)));
    }

    moveUp(index: number): void {
        if (index === 0) return;
        this.phases.update((phases) => {
            const updated = [...phases];
            [updated[index - 1], updated[index]] = [updated[index], updated[index - 1]];
            return updated;
        });
    }

    moveDown(index: number): void {
        this.phases.update((phases) => {
            if (index === phases.length - 1) return phases;
            const updated = [...phases];
            [updated[index], updated[index + 1]] = [updated[index + 1], updated[index]];
            return updated;
        });
    }

    getBuildPlanPhases(): BuildPlanPhases {
        return { phases: this.phases() };
    }
}
