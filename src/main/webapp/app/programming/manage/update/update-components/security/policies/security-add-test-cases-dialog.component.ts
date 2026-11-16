import { Component, computed, effect, input, model, output, signal, untracked } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiDialogComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiSearchFieldComponent,
    TumAetUiTagComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { SecurityTestStructure } from 'app/programming/shared/entities/security-policy.model';
import { TEST_CASE_SEPARATOR, testClassOf } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

/** A test case of the tree; a test class without test cases is one case of its own, keyed by the class. */
interface CaseNode {
    key: string;
    name: string;
    /** The label of the policy that captures it; absent if none or this one. */
    coveredBy?: string;
    /** Whether it runs without the sandbox, because of its own annotation or the one of its class, so it cannot be assigned. */
    switchedOff: boolean;
    /** Whether more than one test method has its name, so it is assigned only with its whole class and follows the other cases of the class. */
    overloaded: boolean;
}

/** A test class of the tree. */
interface ClassNode {
    qualifiedName: string;
    simpleName: string;
    /** Whether none of its test cases can use a policy, because the sandbox is switched off for them. */
    switchedOff: boolean;
    cases: CaseNode[];
    /** The keys of the cases no other policy captures. */
    available: string[];
    /** The label of a policy that captures one of its cases, which a locked class names. */
    lockedBy?: string;
}

/** A package of the tree. */
interface PackageNode {
    packageName: string;
    classes: ClassNode[];
    available: string[];
}

/** The state of a selectable group of the tree, as the template shows it. */
interface SelectionView {
    /** The selectable keys of the group that are selected. */
    selected: number;
    allSelected: boolean;
    partlySelected: boolean;
}

/** A test case as the template shows it. */
interface CaseView extends CaseNode {
    checked: boolean;
    change: 'added' | 'removed' | undefined;
}

/** A test class as the template shows it. */
interface ClassView extends Omit<ClassNode, 'cases'>, SelectionView {
    cases: CaseView[];
    open: boolean;
    locked: boolean;
    /** Whether the class lists its test cases one by one; a class without test cases is a case of its own. */
    expandable: boolean;
}

/** A package as the template shows it. */
interface PackageView extends SelectionView {
    packageName: string;
    available: string[];
    classes: ClassView[];
    open: boolean;
}

/**
 * Assigns the test cases of the tests repository to a security policy (screen "Assign Test Cases"). Checked test cases are captured by the policy; unchecking
 * one releases it. Test cases another policy captures are locked and name that policy. A fully checked class is assigned as a whole, so test cases added to
 * it later use the policy too; otherwise only the checked test cases are.
 */
@Component({
    selector: 'jhi-security-add-test-cases-dialog',
    templateUrl: './security-add-test-cases-dialog.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        TumAetUiDialogComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiSearchFieldComponent,
        TumAetUiTagComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTooltipDirective,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class SecurityAddTestCasesDialogComponent {
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronRight = faChevronRight;

    readonly visible = model(false);
    /** The test structure; undefined while it loads. */
    readonly structure = input<SecurityTestStructure | undefined>(undefined);
    /** The file of the policy, which the description names. */
    readonly policyFile = input('');
    /** The tests the policy is assigned, see {@link SecurityPolicyFile.assignedTests}. */
    readonly ownTests = input<string[]>([]);
    /** The label of the policy that captures each entry of the other policies. */
    readonly otherTests = input<Map<string, string>>(new Map());

    /** Emits the tests the policy is assigned afterwards. */
    readonly applied = output<string[]>();

    readonly search = signal('');
    /** Packages are open and classes closed unless toggled. */
    readonly toggled = signal<ReadonlySet<string>>(new Set());
    readonly selection = signal<ReadonlySet<string>>(new Set());
    private readonly initial = signal<ReadonlySet<string>>(new Set());
    /** Whether the selection of this opening is set up; it waits for the test structure, which the first opening loads. */
    private initialized = false;
    /** What the instructor checked and unchecked before a newer structure started loading, to apply to it. */
    private keptChanges: { added: string[]; removed: string[]; wholeClasses: string[]; releasedClasses: string[] } | undefined;
    /** The classes of the last structure shown, which a newer one replaces. */
    private shownClasses: ClassNode[] = [];

    readonly packages = computed<PackageNode[]>(() => {
        const byPackage = new Map<string, ClassNode[]>();
        for (const testClass of (this.structure()?.testClasses ?? []).filter((candidate) => candidate.testClass !== false)) {
            const dot = testClass.qualifiedName.lastIndexOf('.');
            const packageName = dot >= 0 ? testClass.qualifiedName.substring(0, dot) : '';
            const testCases = testClass.testCases ?? [];
            const fixedCases = new Set(testClass.fixedCases ?? []);
            const overloadedCases = new Set(testClass.overloadedCases ?? []);
            const classSwitchedOff = !!testClass.switchedOff;
            const cases: CaseNode[] = testCases.length
                ? testCases.map((name) => ({
                      key: testClass.qualifiedName + TEST_CASE_SEPARATOR + name,
                      name,
                      coveredBy: this.coveringPolicy(testClass.qualifiedName, name),
                      switchedOff: classSwitchedOff || fixedCases.has(name),
                      overloaded: overloadedCases.has(name),
                  }))
                : [
                      {
                          key: testClass.qualifiedName,
                          name: testClass.qualifiedName.substring(dot + 1),
                          coveredBy: this.coveringPolicy(testClass.qualifiedName, undefined),
                          switchedOff: classSwitchedOff,
                          overloaded: false,
                      },
                  ];
            const node: ClassNode = {
                qualifiedName: testClass.qualifiedName,
                simpleName: testClass.qualifiedName.substring(dot + 1),
                switchedOff: cases.every((testCase) => testCase.switchedOff),
                cases,
                available: cases.filter((testCase) => !testCase.coveredBy && !testCase.switchedOff).map((testCase) => testCase.key),
                lockedBy: cases.find((testCase) => testCase.coveredBy)?.coveredBy,
            };
            byPackage.set(packageName, [...(byPackage.get(packageName) ?? []), node]);
        }
        return [...byPackage.entries()]
            .sort(([first], [second]) => first.localeCompare(second))
            .map(([packageName, classes]) => ({ packageName, classes, available: classes.flatMap((node) => node.available) }));
    });

    /** The packages and classes the search keeps, with the cases it keeps. */
    readonly filteredPackages = computed<PackageNode[]>(() => {
        const query = this.search().trim().toLowerCase();
        if (!query) {
            return this.packages();
        }
        return this.packages()
            .map((group) => {
                const packageMatches = group.packageName.toLowerCase().includes(query);
                const classes = group.classes
                    .map((node) => {
                        const classMatches = packageMatches || node.simpleName.toLowerCase().includes(query);
                        const cases = classMatches ? node.cases : node.cases.filter((testCase) => testCase.name.toLowerCase().includes(query));
                        // Selecting a filtered class or package selects only the test cases it shows.
                        return cloneWith(node, { cases, available: node.available.filter((key) => cases.some((testCase) => testCase.key === key)) });
                    })
                    .filter((node) => node.cases.length > 0);
                return { packageName: group.packageName, classes, available: classes.flatMap((node) => node.available) };
            })
            .filter((group) => group.classes.length > 0);
    });

    /** The tree as the template shows it, with the state of every row. */
    readonly view = computed<PackageView[]>(() =>
        this.filteredPackages().map((group) =>
            cloneWith(this.selectionOf(group.available), {
                packageName: group.packageName,
                available: group.available,
                open: this.isOpen(group.packageName, true),
                classes: group.classes.map((node) =>
                    cloneWith(this.selectionOf(node.available), {
                        qualifiedName: node.qualifiedName,
                        simpleName: node.simpleName,
                        switchedOff: node.switchedOff,
                        available: node.available,
                        lockedBy: node.lockedBy,
                        open: this.isOpen(node.qualifiedName, false),
                        locked: !node.available.length,
                        expandable: node.cases.length > 1 || node.cases[0].key !== node.qualifiedName,
                        cases: node.cases.map((testCase) =>
                            cloneWith(testCase, { checked: !!testCase.coveredBy || this.selection().has(testCase.key), change: this.changeOf(testCase.key) }),
                        ),
                    }),
                ),
            }),
        ),
    );

    private selectionOf(keys: readonly string[]): SelectionView {
        const selected = this.selectedCount(keys);
        const allSelected = this.isAllSelected(keys);
        return { selected, allSelected, partlySelected: selected > 0 && !allSelected };
    }

    /** The test cases no policy captures. */
    readonly unconnected = computed(() => {
        const own = this.expand(this.ownTests());
        return this.packages().flatMap((group) => group.available.filter((key) => !own.has(key)));
    });
    readonly unselectedUnconnected = computed(() => this.unconnected().filter((key) => !this.selection().has(key)));

    readonly addedCount = computed(() => [...this.selection()].filter((key) => !this.initial().has(key)).length);
    readonly removedCount = computed(() => [...this.initial()].filter((key) => !this.selection().has(key)).length);

    constructor() {
        effect(() => {
            const classes = this.packages().flatMap((group) => group.classes);
            if (classes.length) {
                this.shownClasses = classes;
            }
        });
        effect(() => {
            // A whole class only expands to its test cases once the structure is there, so the selection is set up when it arrives. While a newer structure
            // loads, e.g. after a reload, the selection is set up again from it, because one made from the old test cases could release assignments; what the
            // instructor checked or unchecked meanwhile is kept and applied to it.
            if (!this.visible()) {
                this.initialized = false;
                this.keptChanges = undefined;
                return;
            }
            if (!this.structure()) {
                if (this.initialized) {
                    untracked(() => {
                        const initial = this.initial();
                        const selection = this.selection();
                        const classes = this.shownClasses;
                        this.keptChanges = {
                            added: [...selection].filter((key) => !initial.has(key)),
                            removed: [...initial].filter((key) => !selection.has(key)),
                            // A class the instructor selected as a whole stays so, also with the test cases the new structure adds, and a released class stays
                            // released. Classes the instructor did not change follow the new structure and assignments, e.g. one removed meanwhile.
                            wholeClasses: classes
                                .filter(
                                    (node) =>
                                        node.cases.some((testCase) => !initial.has(testCase.key)) &&
                                        node.cases.every((testCase) => !testCase.switchedOff && selection.has(testCase.key)),
                                )
                                .map((node) => node.qualifiedName),
                            releasedClasses: classes
                                .filter((node) => node.cases.some((testCase) => initial.has(testCase.key)) && !node.cases.some((testCase) => selection.has(testCase.key)))
                                .map((node) => node.qualifiedName),
                        };
                    });
                }
                this.initialized = false;
                return;
            }
            if (this.initialized) {
                return;
            }
            const own = this.ownTests();
            untracked(() => {
                const initial = this.expand(own);
                const selection = new Set(initial);
                const kept = this.keptChanges;
                if (kept) {
                    const available = new Set(this.packages().flatMap((group) => group.available));
                    const classes = new Map(this.packages().flatMap((group) => group.classes.map((node) => [node.qualifiedName, node] as const)));
                    kept.added.filter((key) => available.has(key)).forEach((key) => selection.add(key));
                    kept.removed.forEach((key) => selection.delete(key));
                    const wholeKeys = kept.wholeClasses.flatMap((name) => classes.get(name)?.available ?? []);
                    const releasedKeys = kept.releasedClasses.flatMap((name) => classes.get(name)?.cases.map((testCase) => testCase.key) ?? []);
                    wholeKeys.forEach((key) => selection.add(key));
                    releasedKeys.forEach((key) => selection.delete(key));
                    // The new structure may add test cases to a class, so its overloaded ones follow its other test cases again.
                    this.withOverloadsFollowing(selection, [...kept.added, ...kept.removed, ...wholeKeys, ...releasedKeys]);
                } else {
                    this.toggled.set(new Set());
                    this.search.set('');
                }
                this.keptChanges = undefined;
                this.initial.set(initial);
                this.selection.set(selection);
                this.initialized = true;
            });
        });
    }

    /** @returns the keys of the cases the entries stand for: every case of a whole class */
    private expand(entries: readonly string[]): Set<string> {
        const keys = new Set<string>();
        for (const entry of entries) {
            const node = this.packages()
                .flatMap((group) => group.classes)
                .find((candidate) => candidate.qualifiedName === testClassOf(entry));
            if (!node) {
                keys.add(entry);
            } else if (entry === node.qualifiedName) {
                // A switched-off test case cannot use the policy, so a whole class does not select it.
                node.cases.filter((testCase) => !testCase.switchedOff).forEach((testCase) => keys.add(testCase.key));
            } else if (!node.cases.some((testCase) => testCase.key === entry && testCase.switchedOff)) {
                // An entry of a case the structure does not show stays selected, so applying keeps it.
                keys.add(entry);
            }
        }
        return keys;
    }

    private coveringPolicy(qualifiedName: string, testCase: string | undefined): string | undefined {
        const others = this.otherTests();
        return others.get(qualifiedName) ?? (testCase === undefined ? undefined : others.get(qualifiedName + TEST_CASE_SEPARATOR + testCase));
    }

    isOpen(key: string, packageLevel: boolean): boolean {
        if (this.search().trim()) {
            return true;
        }
        return this.toggled().has(key) !== packageLevel;
    }

    protected toggleOpen(key: string): void {
        const toggled = new Set(this.toggled());
        if (toggled.has(key)) {
            toggled.delete(key);
        } else {
            toggled.add(key);
        }
        this.toggled.set(toggled);
    }

    selectedCount(keys: readonly string[]): number {
        return keys.filter((key) => this.selection().has(key)).length;
    }

    isAllSelected(keys: readonly string[]): boolean {
        return keys.length > 0 && keys.every((key) => this.selection().has(key));
    }

    /** Checking a group selects all of its available cases; unchecking a fully selected group releases them. */
    protected toggleKeys(keys: readonly string[]): void {
        const selection = new Set(this.selection());
        if (this.isAllSelected(keys)) {
            keys.forEach((key) => selection.delete(key));
        } else {
            keys.forEach((key) => selection.add(key));
        }
        this.selection.set(this.withOverloadsFollowing(selection, keys));
    }

    protected selectAllUnconnected(): void {
        const unconnected = this.unconnected();
        this.selection.set(this.withOverloadsFollowing(new Set([...this.selection(), ...unconnected]), unconnected));
    }

    /**
     * Overloaded test cases are assigned only with their whole class, so in each class the change touches they are selected exactly when all of its other
     * test cases are. A class of overloaded test cases only is selected or released as a whole.
     */
    private withOverloadsFollowing(selection: Set<string>, changed: readonly string[]): Set<string> {
        const changedClasses = new Set(changed.map((key) => testClassOf(key)));
        for (const node of this.packages().flatMap((group) => group.classes)) {
            const overloads = node.cases.filter((testCase) => testCase.overloaded && !testCase.coveredBy && !testCase.switchedOff);
            const others = node.cases.filter((testCase) => !testCase.overloaded);
            if (!changedClasses.has(node.qualifiedName) || !overloads.length) {
                continue;
            }
            // In a class of overloaded test cases only, e.g. one the search shows in part, selecting any of them selects the class.
            const complete = others.length
                ? others.every((testCase) => selection.has(testCase.key))
                : changed.some((key) => testClassOf(key) === node.qualifiedName && selection.has(key));
            overloads.forEach((testCase) => (complete ? selection.add(testCase.key) : selection.delete(testCase.key)));
        }
        return selection;
    }

    /** The tag of a case: whether applying assigns or releases it. */
    changeOf(key: string): 'added' | 'removed' | undefined {
        const was = this.initial().has(key);
        const is = this.selection().has(key);
        return was && !is ? 'removed' : !was && is ? 'added' : undefined;
    }

    /** A fully selected class is assigned as a whole; otherwise its selected cases are. Entries the tree does not show stay. */
    protected apply(): void {
        // While a newer structure loads, the selection is not set up from it yet.
        if (!this.structure() || !this.initialized) {
            return;
        }
        const classes = this.packages().flatMap((group) => group.classes);
        const known = new Set(classes.map((node) => node.qualifiedName));
        const caseKeys = new Set(classes.flatMap((node) => node.cases.map((testCase) => testCase.key)));
        // Entries the tree does not show stay: those of classes and of test cases the repository no longer has.
        const retained = this.ownTests().filter((entry) => !known.has(testClassOf(entry)) || (!caseKeys.has(entry) && entry !== testClassOf(entry) && this.selection().has(entry)));
        const tests: string[] = [];
        const wholeClasses = new Set<string>();
        for (const node of classes) {
            const selected = node.cases.filter((testCase) => !testCase.switchedOff && this.selection().has(testCase.key));
            if (!selected.length) {
                continue;
            }
            if (selected.length === node.cases.length) {
                tests.push(node.qualifiedName);
                wholeClasses.add(node.qualifiedName);
            } else {
                // An overloaded test case cannot be named on its own; it uses the policy only through its whole class.
                selected.filter((testCase) => !testCase.overloaded).forEach((testCase) => tests.push(testCase.key));
            }
        }
        // A class assigned as a whole covers its cases, including those the tree does not show, so the server would refuse them next to it.
        this.applied.emit([...retained.filter((entry) => !wholeClasses.has(testClassOf(entry))), ...tests]);
        this.visible.set(false);
    }
}
