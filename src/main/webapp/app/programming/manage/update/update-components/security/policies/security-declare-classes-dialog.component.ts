import { Component, computed, effect, input, model, output, signal, untracked } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronRight } from '@fortawesome/free-solid-svg-icons';
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

/** A package of the tree, with the classes the dialog offers in it. */
interface PackageGroup {
    packageName: string;
    classes: ClassOption[];
    /** The classes that can be declared or removed here, i.e. not those declared because of their test cases. */
    selectable: string[];
    allSelected: boolean;
    partlySelected: boolean;
    open: boolean;
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
        FaIconComponent,
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
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronRight = faChevronRight;

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

    /** The packages the user collapsed; the dialog opens with every package expanded. */
    private readonly collapsedPackages = signal<ReadonlySet<string>>(new Set());

    /** The shown classes as a tree of packages, like the dialog that assigns test cases. */
    readonly groups = computed<PackageGroup[]>(() => {
        const byPackage = new Map<string, ClassOption[]>();
        for (const option of this.shown()) {
            byPackage.set(option.packageName, [...(byPackage.get(option.packageName) ?? []), option]);
        }
        const selection = this.selection();
        return [...byPackage.entries()]
            .sort(([first], [second]) => first.localeCompare(second))
            .map(([packageName, classes]) => {
                const selectable = classes.filter((option) => !option.fromTests).map((option) => option.qualifiedName);
                const selected = selectable.filter((name) => selection.has(name)).length;
                return {
                    packageName,
                    classes: [...classes].sort((first, second) => first.simpleName.localeCompare(second.simpleName)),
                    selectable,
                    allSelected: selectable.length > 0 && selected === selectable.length,
                    partlySelected: selected > 0 && selected < selectable.length,
                    // A search shows every match, also in a collapsed package.
                    open: !!this.search().trim() || !this.collapsedPackages().has(packageName),
                };
            });
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
                    this.collapsedPackages.set(new Set());
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

    /** Declares every class of the package that can be declared here, or removes them all if all of them are declared. */
    protected togglePackage(group: PackageGroup): void {
        const selection = new Set(this.selection());
        for (const name of group.selectable) {
            if (group.allSelected) {
                selection.delete(name);
            } else {
                selection.add(name);
            }
        }
        this.selection.set(selection);
    }

    protected toggleOpen(packageName: string): void {
        const collapsed = new Set(this.collapsedPackages());
        if (!collapsed.delete(packageName)) {
            collapsed.add(packageName);
        }
        this.collapsedPackages.set(collapsed);
    }

    protected apply(): void {
        const selected = this.selection();
        this.applied.emit([...this.extraClasses().filter((name) => selected.has(name)), ...[...selected].filter((name) => !this.extraClasses().includes(name))]);
        this.visible.set(false);
    }
}
