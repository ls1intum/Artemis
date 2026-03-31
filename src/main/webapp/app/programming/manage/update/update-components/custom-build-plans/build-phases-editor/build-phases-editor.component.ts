import { ChangeDetectionStrategy, Component, computed, model } from '@angular/core';

import { ButtonModule } from 'primeng/button';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { BuildPhase } from 'app/programming/shared/entities/build-plan-phases.model';
import { BuildPhaseEditorComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phase/build-phase-editor.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-build-phases-editor',
    imports: [ButtonModule, FaIconComponent, BuildPhaseEditorComponent, HelpIconComponent, TranslateDirective],
    templateUrl: './build-phases-editor.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
/**
 * Manages the ordered list of editable build phases.
 */
export class BuildPhasesEditorComponent {
    protected readonly faPlus = faPlus;

    readonly phases = model.required<BuildPhase[]>();

    readonly phaseCount = computed(() => this.phases().length);
    readonly phaseNames = computed(() => this.phases().map((phase) => phase.name));

    /**
     * Appends a new phase with default values.
     */
    addPhase() {
        this.phases.update((phases) => [
            ...phases,
            {
                name: '',
                script: '# enter the script of this phase',
                condition: 'ALWAYS',
                forceRun: false,
                resultPaths: [],
            },
        ]);
    }

    /**
     * Removes the phase at the provided index.
     *
     * @param index the index of the phase to remove
     */
    deletePhase(index: number) {
        this.phases.update((phases) => phases.filter((_, i) => i !== index));
    }

    /**
     * Replaces the phase at the provided index.
     *
     * @param index the index of the phase to update
     * @param updated the updated phase content
     */
    updatePhase(index: number, updated: BuildPhase) {
        this.phases.update((phases) => phases.map((p, i) => (i === index ? updated : p)));
    }

    /**
     * Moves a phase one position upward.
     *
     * @param index the index of the phase to move
     */
    moveUp(index: number) {
        this.phases.update((phases) => {
            if (index <= 0 || index >= phases.length) {
                return phases;
            }
            const updated = [...phases];
            [updated[index - 1], updated[index]] = [updated[index], updated[index - 1]];
            return updated;
        });
    }

    /**
     * Moves a phase one position downward.
     *
     * @param index the index of the phase to move
     */
    moveDown(index: number) {
        this.phases.update((phases) => {
            if (index < 0 || index >= phases.length - 1) {
                return phases;
            }
            const updated = [...phases];
            [updated[index], updated[index + 1]] = [updated[index + 1], updated[index]];
            return updated;
        });
    }
}
