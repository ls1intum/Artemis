import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPlus, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SecurityTestStructure } from 'app/programming/shared/entities/security-policy.model';
import { SecurityAddTestCasesDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-add-test-cases-dialog.component';
import { SecurityConfirmDialogComponent, SecurityTranslatable } from 'app/programming/manage/update/update-components/security/policies/security-confirm-dialog.component';
import { SecurityDeclareClassesDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-declare-classes-dialog.component';
import { SecurityHelpComponent } from 'app/programming/manage/update/update-components/security/policies/security-help.component';
import {
    EditorPolicy,
    TEST_CASE_SEPARATOR,
    classesOfTests,
    countKey,
    testClassOf,
    withRestoredEntry,
} from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

/** Whether an entry is assigned, newly assigned or released since the commit. */
export type EntryState = 'committed' | 'new' | 'removed';

/** A test class with the entries the Assigned column lists for it. */
interface ClassEntries {
    qualifiedName: string;
    simpleName: string;
    /** The state of the entry of the whole class; absent if only single test cases are listed. */
    whole?: EntryState;
    testCases: { name: string; entry: string; state: EntryState }[];
}

/** A package of the Assigned column. */
interface PackageEntries {
    packageName: string;
    classes: ClassEntries[];
}

/** A class of the Declared column. */
interface DeclaredClass {
    qualifiedName: string;
    simpleName: string;
    packageName: string;
    /** Declared because test cases of it are assigned. */
    fromTests: boolean;
    state: EntryState;
}

const PREFIX = 'artemisApp.programmingExercise.security.advanced.tests';

/**
 * The content of the Assign Test Cases box of a security policy (screen "Assign Test Cases"): on the left the test cases that run under the policy, on the
 * right the classes the policy declares as test code, i.e. the classes of those test cases and helpers without tests. Released entries stay visible until
 * the commit and can be restored.
 */
@Component({
    selector: 'jhi-security-test-cases',
    templateUrl: './security-test-cases.component.html',
    styleUrl: './security-policies.scss',
    imports: [
        NgTemplateOutlet,
        TumAetUiButtonDirective,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        SecurityAddTestCasesDialogComponent,
        SecurityDeclareClassesDialogComponent,
        SecurityConfirmDialogComponent,
        SecurityHelpComponent,
    ],
})
export class SecurityTestCasesComponent {
    protected readonly countKey = countKey;
    protected readonly faPlus = faPlus;
    protected readonly faXmark = faXmark;

    readonly policy = input.required<EditorPolicy>();
    /** The file of the policy, which the dialogs name. */
    readonly policyFile = input('');
    /** The label of the policy that captures each entry of the other policies. */
    readonly otherTests = input<Map<string, string>>(new Map());
    /** The test structure; undefined until loaded. */
    readonly structure = input<SecurityTestStructure | undefined>(undefined);
    readonly disabled = input(false);

    readonly assignmentChange = output<{ tests: string[]; extraClasses: string[] }>();
    readonly structureRequested = output<void>();

    readonly assignOpen = signal(false);
    readonly declareOpen = signal(false);
    readonly confirmOpen = signal(false);
    /** The entry or class the confirmation removes. */
    readonly removing = signal<{ kind: 'test' | 'class'; value: string } | undefined>(undefined);

    readonly assignedPackages = computed<PackageEntries[]>(() => {
        const policy = this.policy();
        const state = (entry: string): EntryState => entryState(policy.tests.includes(entry), policy.baseTests.includes(entry));
        // A removed test case whose whole class is assigned now still uses the policy, so it is not listed as removed.
        const entries = [...policy.tests, ...policy.baseTests.filter((entry) => !policy.tests.includes(entry) && !policy.tests.includes(testClassOf(entry)))];
        const byClass = new Map<string, ClassEntries>();
        for (const entry of entries) {
            const qualifiedName = testClassOf(entry);
            const node = byClass.get(qualifiedName) ?? { qualifiedName, simpleName: qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1), testCases: [] };
            if (entry === qualifiedName) {
                node.whole = state(entry);
            } else {
                node.testCases.push({ name: entry.substring(entry.indexOf(TEST_CASE_SEPARATOR) + 1), entry, state: state(entry) });
            }
            byClass.set(qualifiedName, node);
        }
        const byPackage = new Map<string, ClassEntries[]>();
        for (const node of [...byClass.values()].sort((first, second) => first.simpleName.localeCompare(second.simpleName))) {
            const packageName = node.qualifiedName.substring(0, Math.max(0, node.qualifiedName.lastIndexOf('.')));
            byPackage.set(packageName, [...(byPackage.get(packageName) ?? []), node]);
        }
        return [...byPackage.entries()].sort(([first], [second]) => first.localeCompare(second)).map(([packageName, classes]) => ({ packageName, classes }));
    });

    readonly assignedClassCount = computed(() => this.assignedPackages().reduce((count, group) => count + group.classes.length, 0));

    readonly declaredClasses = computed<DeclaredClass[]>(() => {
        const policy = this.policy();
        const fromTests = classesOfTests(policy.tests);
        const names = [...new Set([...fromTests, ...policy.extraClasses, ...policy.baseExtraClasses])].sort((first, second) => first.localeCompare(second));
        return names.map((qualifiedName) => {
            const dot = qualifiedName.lastIndexOf('.');
            const isFromTests = fromTests.includes(qualifiedName);
            const state = entryState(isFromTests || policy.extraClasses.includes(qualifiedName), isFromTests || policy.baseExtraClasses.includes(qualifiedName));
            return { qualifiedName, simpleName: qualifiedName.substring(dot + 1), packageName: dot >= 0 ? qualifiedName.substring(0, dot) : '', fromTests: isFromTests, state };
        });
    });

    readonly declaredCount = computed(() => this.declaredClasses().filter((declared) => declared.state !== 'removed').length);
    readonly classesOfAssignedTests = computed(() => classesOfTests(this.policy().tests));

    protected openAssign(): void {
        this.structureRequested.emit();
        this.assignOpen.set(true);
    }

    protected openDeclare(): void {
        this.structureRequested.emit();
        this.declareOpen.set(true);
    }

    protected onAssigned(tests: string[]): void {
        this.assignmentChange.emit({ tests, extraClasses: this.policy().extraClasses });
    }

    protected onDeclared(extraClasses: string[]): void {
        this.assignmentChange.emit({ tests: this.policy().tests, extraClasses });
    }

    protected askRemove(kind: 'test' | 'class', value: string): void {
        this.removing.set({ kind, value });
        this.confirmOpen.set(true);
    }

    protected onRemoveConfirmed(): void {
        const removing = this.removing();
        const policy = this.policy();
        if (removing?.kind === 'test') {
            this.assignmentChange.emit({ tests: policy.tests.filter((entry) => entry !== removing.value), extraClasses: policy.extraClasses });
        } else if (removing?.kind === 'class') {
            this.assignmentChange.emit({ tests: policy.tests, extraClasses: policy.extraClasses.filter((name) => name !== removing.value) });
        }
        this.removing.set(undefined);
    }

    protected undoTest(entry: string): void {
        this.assignmentChange.emit({ tests: withRestoredEntry(this.policy().tests, entry), extraClasses: this.policy().extraClasses });
    }

    protected undoClass(qualifiedName: string): void {
        this.assignmentChange.emit({ tests: this.policy().tests, extraClasses: [...this.policy().extraClasses, qualifiedName] });
    }

    /** What the confirmation removes: a declared test class, a test case or a whole test class. */
    private readonly removedTarget = computed<'class' | 'case' | 'wholeClass'>(() => {
        const removing = this.removing();
        if (removing?.kind === 'class') {
            return 'class';
        }
        return removing?.value.includes(TEST_CASE_SEPARATOR) ? 'case' : 'wholeClass';
    });
    readonly removeHeader = computed<SecurityTranslatable>(() => ({ key: `${PREFIX}.remove.${this.removedTarget()}Title` }));
    readonly removeQuestion = computed<SecurityTranslatable>(() => ({ key: `${PREFIX}.remove.${this.removedTarget()}Question`, params: { policy: this.policy().name } }));
    readonly removeWarning = computed<SecurityTranslatable | undefined>(() => {
        const removing = this.removing();
        if (!removing) {
            return undefined;
        }
        const simpleName = testClassOf(removing.value).substring(testClassOf(removing.value).lastIndexOf('.') + 1);
        if (removing.kind === 'class') {
            return { key: `${PREFIX}.remove.classWarning`, params: { testClass: simpleName } };
        }
        const remaining = this.policy().tests.filter((entry) => entry !== removing.value);
        const undeclared = !this.policy().extraClasses.includes(testClassOf(removing.value)) && !classesOfTests(remaining).includes(testClassOf(removing.value));
        return {
            key: `${PREFIX}.remove.${removing.value.includes(TEST_CASE_SEPARATOR) ? 'caseWarning' : 'wholeClassWarning'}${undeclared ? 'Undeclared' : ''}`,
            params: { testClass: simpleName },
        };
    });
}

/** @returns how an entry differs from the committed policy: removed if the working policy no longer has it, new if only the working policy has it */
function entryState(inWorkingPolicy: boolean, inCommittedPolicy: boolean): EntryState {
    if (!inWorkingPolicy) {
        return 'removed';
    }
    return inCommittedPolicy ? 'committed' : 'new';
}
