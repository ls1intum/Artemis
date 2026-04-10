import { ChangeDetectionStrategy, Component, computed, inject, input, model, output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { faArrowDown, faArrowUp, faPlus, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import {
    BUILD_PHASE_CONDITION,
    BUILD_PHASE_NAME_PATTERN,
    BUILD_PHASE_RESERVED_NAMES,
    BuildPhase,
    BuildPhaseCondition,
} from 'app/programming/shared/entities/build-plan-phases.model';
import { FormsModule } from '@angular/forms';
import { Select } from 'primeng/select';
import { InputText } from 'primeng/inputtext';
import { ButtonDirective, ButtonIcon, ButtonLabel } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MonacoEditorFitTextComponent } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/monaco-editor-auto-size/monaco-editor-fit-text.component';
import { Card } from 'primeng/card';
import { Badge } from 'primeng/badge';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { Tooltip } from 'primeng/tooltip';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { Checkbox } from 'primeng/checkbox';
import { Message } from 'primeng/message';

@Component({
    selector: 'jhi-build-phase',
    imports: [
        FormsModule,
        Select,
        InputText,
        ButtonDirective,
        ButtonIcon,
        FaIconComponent,
        MonacoEditorFitTextComponent,
        ButtonLabel,
        Card,
        Badge,
        HelpIconComponent,
        Tooltip,
        TranslateDirective,
        ArtemisTranslatePipe,
        Checkbox,
        Message,
    ],
    templateUrl: './build-phase-editor.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
/**
 * Edits a single build phase (name, condition, script, result paths).
 */
export class BuildPhaseEditorComponent {
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    protected readonly faPlus = faPlus;
    protected readonly faXmark = faXmark;
    protected readonly faTrash = faTrash;
    protected readonly faArrowDown = faArrowDown;
    protected readonly faArrowUp = faArrowUp;

    private cachedResultPaths: string[] = [];

    readonly phase = model.required<BuildPhase>();

    readonly index = input.required<number>();
    readonly phaseNames = input.required<string[]>();
    readonly isLast = input.required<boolean>();

    readonly displayedNumber = computed(() => this.index() + 1);
    readonly isFirst = computed(() => this.index() === 0);
    readonly isOnly = computed(() => this.isFirst() && this.isLast());

    readonly isNamePatternValid = computed(() => BUILD_PHASE_NAME_PATTERN.test(this.phase().name));
    readonly isNameReserved = computed(() => BUILD_PHASE_RESERVED_NAMES.has(this.phase().name.toLowerCase()));

    readonly isNameUnique = computed(() => {
        const currentIndex = this.index();
        const normalizedCurrentName = this.phase().name.toLowerCase();
        if (!normalizedCurrentName) {
            return false;
        }
        return this.phaseNames().every((phaseName, index) => index === currentIndex || phaseName.toLowerCase() !== normalizedCurrentName);
    });

    readonly isNameValid = computed(() => this.isNamePatternValid() && !this.isNameReserved() && this.isNameUnique());

    readonly nameValidationMessageKey = computed(() =>
        this.phase().name && this.isNamePatternValid() && !this.isNameReserved()
            ? 'artemisApp.programmingExercise.buildPhasesEditor.phaseNameDuplicate'
            : 'artemisApp.programmingExercise.buildPhasesEditor.phaseNameInvalidCharacters',
    );

    readonly shouldShowNameValidationError = computed(() => !this.isNameValid());

    readonly conditionOptions = computed(() => {
        this.currentLocale();
        return Object.keys(BUILD_PHASE_CONDITION).map((key) => {
            const value = key as BuildPhaseCondition;
            return {
                value: value,
                label: this.translateService.instant(BUILD_PHASE_CONDITION[value]) as string,
            };
        });
    });

    readonly delete = output<void>();
    readonly moveUp = output<void>();
    readonly moveDown = output<void>();

    /**
     * Updates the phase name.
     *
     * @param name the new phase name
     */
    updateName(name: string): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, name }));
    }

    /**
     * Updates the phase script.
     *
     * @param script the new script content
     */
    updateScript(script: string): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, script }));
    }

    /**
     * Updates the execution condition for the phase.
     *
     * @param condition the selected execution condition
     */
    updateCondition(condition: BuildPhaseCondition): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, condition }));
    }

    /**
     * Updates whether this phase should always run in the post-action block.
     *
     * @param forceRun true if this phase should run regardless of previous failures
     */
    updateForceRun(forceRun: boolean): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, forceRun }));
    }

    /**
     * Updates whether this phase is expected to produce test results.
     *
     * @param testsExpected true if this phase should collect result paths
     */
    updateTestsExpected(testsExpected: boolean): void {
        this.phase.update((oldPhase) => {
            const currentResultPaths = oldPhase.resultPaths ?? [];
            if (!testsExpected) {
                this.cachedResultPaths = [...currentResultPaths];
                return { ...oldPhase, resultPaths: [] };
            }

            const restoredResultPaths = this.cachedResultPaths.length ? this.cachedResultPaths : [''];
            this.cachedResultPaths = [];
            return { ...oldPhase, resultPaths: [...restoredResultPaths] };
        });
    }

    /**
     * Replaces a single result path entry.
     *
     * @param index the index of the result path to update
     * @param value the new glob pattern
     */
    updateResultPath(index: number, value: string): void {
        this.phase.update((oldPhase) => {
            const resultPaths = [...(oldPhase.resultPaths ?? [])];
            resultPaths[index] = value;
            return { ...oldPhase, resultPaths };
        });
    }

    /**
     * Appends an empty result path entry.
     */
    addResultPath(): void {
        this.phase.update((oldPhase) => ({ ...oldPhase, resultPaths: [...(oldPhase.resultPaths ?? []), ''] }));
    }

    /**
     * Removes the result path at the provided index.
     *
     * @param deleteIndex the index of the result path to remove
     */
    deleteResultPath(deleteIndex: number): void {
        this.phase.update((oldPhase) => ({
            ...oldPhase,
            resultPaths: (oldPhase.resultPaths ?? []).filter((_, i) => i !== deleteIndex),
        }));
    }
}
