import { ChangeDetectionStrategy, Component, computed, inject, input, model, output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { faArrowDown, faArrowUp, faPlus, faTrash, faTriangleExclamation, faXmark } from '@fortawesome/free-solid-svg-icons';
import {
    BUILD_PHASE_CONDITION,
    BUILD_PHASE_NAME_PATTERN,
    BUILD_PHASE_RESERVED_NAMES,
    BuildPhase,
    BuildPhaseCondition,
    hasExpectedTestsBeforeDueDate,
} from 'app/programming/shared/entities/build-plan-phases.model';
import { FormsModule } from '@angular/forms';
import {
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiSelectComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MonacoEditorFitTextComponent } from 'app/programming/manage/build-plan-editor/build-phases-editor/monaco-editor-auto-size/monaco-editor-fit-text.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-build-phase',
    imports: [
        FormsModule,
        FaIconComponent,
        MonacoEditorFitTextComponent,
        HelpIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiSelectComponent,
        TumAetUiTooltipDirective,
    ],
    templateUrl: './build-phase-editor.component.html',
    styles: `
        :host ::ng-deep .markdown-preview pre {
            border: none;
            padding: 0;
            margin-bottom: 0;
        }

        .phase-divider {
            border-block-end: 1px solid var(--border-color);
        }

        .phase-number {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-width: 1.5rem;
            height: 1.5rem;
            padding: 0 0.5rem;
            border-radius: 9999px;
            background: var(--primary);
            color: var(--white);
            font-size: 0.75rem;
            font-weight: 700;
        }
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
/**
 * Edits a single build phase (name, condition, script, result paths).
 */
export class BuildPhaseEditorComponent {
    private readonly translateService = inject(TranslateService);
    private readonly artemisMarkdownService = inject(ArtemisMarkdownService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    protected readonly faPlus = faPlus;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faXmark = faXmark;
    protected readonly faTrash = faTrash;
    protected readonly faArrowDown = faArrowDown;
    protected readonly faArrowUp = faArrowUp;

    private cachedResultPaths: string[] = [];

    readonly phase = model.required<BuildPhase>();

    readonly index = input.required<number>();
    readonly phaseNames = input.required<string[]>();
    readonly isLast = input.required<boolean>();
    readonly readonly = input(false);
    readonly isExamMode = input(false);

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

    readonly nameValidationMessageKey = computed(() => {
        if (!this.phase().name || !this.isNamePatternValid()) {
            return 'artemisApp.programmingExercise.buildPhasesEditor.phaseNameInvalidCharacters';
        }
        // a reserved name breaks none of the character rules, so pointing at those would send the instructor hunting for a
        // fault that is not there
        if (this.isNameReserved()) {
            return 'artemisApp.programmingExercise.buildPhasesEditor.phaseNameReserved';
        }
        return 'artemisApp.programmingExercise.buildPhasesEditor.phaseNameDuplicate';
    });

    readonly shouldShowNameValidationError = computed(() => !this.isNameValid());
    readonly shouldShowExamModeWarning = computed(() => this.isExamMode() && hasExpectedTestsBeforeDueDate(this.phase()));
    readonly readonlyScriptHtml = computed(() => this.artemisMarkdownService.safeHtmlForMarkdown(`\`\`\`bash\n${this.phase().script}\n\`\`\``));

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
        this.phase.update((oldPhase) => cloneWith(oldPhase, { name }));
    }

    /**
     * Updates the phase script.
     *
     * @param script the new script content
     */
    updateScript(script: string): void {
        this.phase.update((oldPhase) => cloneWith(oldPhase, { script }));
    }

    /**
     * Updates the execution condition for the phase.
     *
     * @param condition the selected execution condition
     */
    updateCondition(condition: BuildPhaseCondition): void {
        this.phase.update((oldPhase) => cloneWith(oldPhase, { condition }));
    }

    /**
     * Updates whether this phase should always run in the post-action block.
     *
     * @param forceRun true if this phase should run regardless of previous failures
     */
    updateForceRun(forceRun: boolean): void {
        this.phase.update((oldPhase) => cloneWith(oldPhase, { forceRun }));
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
                return cloneWith(oldPhase, { resultPaths: [] });
            }

            const restoredResultPaths = this.cachedResultPaths.length ? this.cachedResultPaths : [''];
            this.cachedResultPaths = [];
            return cloneWith(oldPhase, { resultPaths: [...restoredResultPaths] });
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
            return cloneWith(oldPhase, { resultPaths });
        });
    }

    /**
     * Appends an empty result path entry.
     */
    addResultPath(): void {
        this.phase.update((oldPhase) => cloneWith(oldPhase, { resultPaths: [...(oldPhase.resultPaths ?? []), ''] }));
    }

    /**
     * Removes the result path at the provided index.
     *
     * @param deleteIndex the index of the result path to remove
     */
    deleteResultPath(deleteIndex: number): void {
        this.phase.update((oldPhase) => cloneWith(oldPhase, { resultPaths: (oldPhase.resultPaths ?? []).filter((_, i) => i !== deleteIndex) }));
    }
}
