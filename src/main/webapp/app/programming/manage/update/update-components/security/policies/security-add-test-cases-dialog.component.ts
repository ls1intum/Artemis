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
}

/** A test class of the tree. */
interface ClassNode {
    qualifiedName: string;
    simpleName: string;
    switchedOff: boolean;
    cases: CaseNode[];
    /** The keys of the cases no other policy captures. */
    available: string[];
}

/** A package of the tree. */
interface PackageNode {
    packageName: string;
    classes: ClassNode[];
    available: string[];
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

    readonly packages = computed<PackageNode[]>(() => {
        const byPackage = new Map<string, ClassNode[]>();
        for (const testClass of (this.structure()?.testClasses ?? []).filter((candidate) => candidate.testClass !== false)) {
            const dot = testClass.qualifiedName.lastIndexOf('.');
            const packageName = dot >= 0 ? testClass.qualifiedName.substring(0, dot) : '';
            const testCases = testClass.testCases ?? [];
            const cases: CaseNode[] = testCases.length
                ? testCases.map((name) => ({ key: testClass.qualifiedName + TEST_CASE_SEPARATOR + name, name, coveredBy: this.coveringPolicy(testClass.qualifiedName, name) }))
                : [{ key: testClass.qualifiedName, name: testClass.qualifiedName.substring(dot + 1), coveredBy: this.coveringPolicy(testClass.qualifiedName, undefined) }];
            const switchedOff = !!testClass.switchedOff;
            const node: ClassNode = {
                qualifiedName: testClass.qualifiedName,
                simpleName: testClass.qualifiedName.substring(dot + 1),
                switchedOff,
                cases,
                available: switchedOff ? [] : cases.filter((testCase) => !testCase.coveredBy).map((testCase) => testCase.key),
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
                        return cloneWith(node, { cases: classMatches ? node.cases : node.cases.filter((testCase) => testCase.name.toLowerCase().includes(query)) });
                    })
                    .filter((node) => node.cases.length > 0);
                return { packageName: group.packageName, classes, available: group.available };
            })
            .filter((group) => group.classes.length > 0);
    });

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
            if (this.visible()) {
                const own = this.ownTests();
                untracked(() => {
                    const initial = this.expand(own);
                    this.initial.set(initial);
                    this.selection.set(new Set(initial));
                    this.toggled.set(new Set());
                    this.search.set('');
                });
            }
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
                node.cases.forEach((testCase) => keys.add(testCase.key));
            } else {
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
        this.selection.set(selection);
    }

    protected selectAllUnconnected(): void {
        this.selection.set(new Set([...this.selection(), ...this.unconnected()]));
    }

    /** The tag of a case: whether applying assigns or releases it. */
    changeOf(key: string): 'added' | 'removed' | undefined {
        const was = this.initial().has(key);
        const is = this.selection().has(key);
        return was && !is ? 'removed' : !was && is ? 'added' : undefined;
    }

    /** A fully selected class is assigned as a whole; otherwise its selected cases are. Entries of classes the repository no longer has stay. */
    protected apply(): void {
        const classes = this.packages().flatMap((group) => group.classes);
        const known = new Set(classes.map((node) => node.qualifiedName));
        const tests = this.ownTests().filter((entry) => !known.has(testClassOf(entry)));
        for (const node of classes) {
            const selected = node.cases.filter((testCase) => this.selection().has(testCase.key));
            if (!selected.length) {
                continue;
            }
            if (selected.length === node.cases.length) {
                tests.push(node.qualifiedName);
            } else {
                selected.forEach((testCase) => tests.push(testCase.key));
            }
        }
        this.applied.emit(tests);
        this.visible.set(false);
    }
}
