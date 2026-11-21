import { Component, computed, effect, input, model, output, signal, untracked } from '@angular/core';
import {
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiDialogComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiSearchFieldComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SecurityTestStructure } from 'app/programming/shared/entities/security-policy.model';

/** A class the dialog offers. */
interface ClassOption {
    qualifiedName: string;
    simpleName: string;
    packageName: string;
    /** Whether it is declared because test cases of it are assigned; such a class cannot be removed here. */
    fromTests: boolean;
    /** Whether it has no test cases, e.g. a helper. */
    helper: boolean;
}

/**
 * Declares the classes of the tests repository that Ares treats as test code for a policy (screen "Declare Test Classes"), e.g. helpers without test
 * annotations. Classes of assigned test cases are always declared and locked here.
 */
@Component({
    selector: 'jhi-security-declare-classes-dialog',
    templateUrl: './security-declare-classes-dialog.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        TumAetUiDialogComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiSearchFieldComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTooltipDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class SecurityDeclareClassesDialogComponent {
    readonly visible = model(false);
    /** The classes of the tests repository; undefined while they load. */
    readonly structure = input<SecurityTestStructure | undefined>(undefined);
    readonly policyFile = input('');
    /** The classes declared because test cases of them are assigned. */
    readonly classesOfTests = input<string[]>([]);
    /** The classes declared on their own. */
    readonly extraClasses = input<string[]>([]);

    /** Emits the classes declared on their own afterwards. */
    readonly applied = output<string[]>();

    readonly search = signal('');
    readonly selection = signal<ReadonlySet<string>>(new Set());

    readonly options = computed<ClassOption[]>(() => {
        const fromTests = new Set(this.classesOfTests());
        const names = new Set([...(this.structure()?.testClasses ?? []).map((testClass) => testClass.qualifiedName), ...this.extraClasses()]);
        const helpers = new Set((this.structure()?.testClasses ?? []).filter((testClass) => testClass.testClass === false).map((testClass) => testClass.qualifiedName));
        return [...names]
            .sort((first, second) => first.localeCompare(second))
            .map((qualifiedName) => {
                const dot = qualifiedName.lastIndexOf('.');
                return {
                    qualifiedName,
                    simpleName: qualifiedName.substring(dot + 1),
                    packageName: dot >= 0 ? qualifiedName.substring(0, dot) : '',
                    fromTests: fromTests.has(qualifiedName),
                    helper: helpers.has(qualifiedName),
                };
            });
    });

    readonly shown = computed(() => {
        const query = this.search().trim().toLowerCase();
        return query ? this.options().filter((option) => option.qualifiedName.toLowerCase().includes(query)) : this.options();
    });

    readonly addedCount = computed(() => [...this.selection()].filter((name) => !this.extraClasses().includes(name)).length);
    readonly removedCount = computed(() => this.extraClasses().filter((name) => !this.selection().has(name)).length);

    constructor() {
        effect(() => {
            if (this.visible()) {
                const extra = this.extraClasses();
                untracked(() => {
                    this.selection.set(new Set(extra));
                    this.search.set('');
                });
            }
        });
    }

    protected toggle(option: ClassOption): void {
        const selection = new Set(this.selection());
        if (selection.has(option.qualifiedName)) {
            selection.delete(option.qualifiedName);
        } else {
            selection.add(option.qualifiedName);
        }
        this.selection.set(selection);
    }

    protected apply(): void {
        const selected = this.selection();
        this.applied.emit([...this.extraClasses().filter((name) => selected.has(name)), ...[...selected].filter((name) => !this.extraClasses().includes(name))]);
        this.visible.set(false);
    }
}
