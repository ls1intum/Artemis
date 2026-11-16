import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityActivationStatus, SecurityFrameworkConfig, SecurityVersionCompatibilityVerdict } from 'app/programming/shared/entities/security-framework-config.model';
import {
    SecurityPolicyChange,
    SecurityPolicyDocument,
    SecurityPolicyEditor,
    SecurityPolicyPreview,
    SecurityPolicyReadOnlyReason,
    SecurityTestStructure,
} from 'app/programming/shared/entities/security-policy.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
import { By } from '@angular/platform-browser';
import { SecurityActivationDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-activation-dialog.component';
import { ProgrammingExerciseSecurityPoliciesComponent } from 'app/programming/manage/update/update-components/security/policies/programming-exercise-security-policies.component';
import { EditorPolicy, withAssignment, withRows } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

const DOCUMENT: SecurityPolicyDocument = {
    policyPath: 'SecurityPolicy.yaml',
    languageConfiguration: 'JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ',
    supervisedPackage: 'de.example',
    testClasses: ['de.example.SortingTest'],
    files: [{ path: 'data/input.txt', read: true, overwrite: false, create: false, execute: false, delete: false }],
};

const editor = (hash = 'a'.repeat(40), document = DOCUMENT): SecurityPolicyEditor => ({
    baseCommitHash: hash,
    lastCommit: { hash, authorName: 'Instructor', date: '2026-11-15T10:00:00Z' },
    frameworkVersion: '2.2.1',
    scanDependenciesAvailable: true,
    policies: [{ policyPath: 'SecurityPolicy.yaml', content: 'thisPolicyFileCompliesToThePolicyVersion: 1\n', document, assignedTests: ['de.example.SortingTest'] }],
    presets: [],
});

const configured = (): SecurityFrameworkConfig => ({ status: SecurityActivationStatus.CONFIGURED, frameworkVersion: '2.2.1' });

/** Exposes the protected handlers the template calls. */
type Handlers = {
    onPolicyChange(policy: EditorPolicy): void;
    addPolicy(): void;
    askDeletePolicy(policy: EditorPolicy): void;
    deletePolicy(): void;
    openReview(): void;
    commit(message: string): void;
    onToggleRequested(on: boolean): void;
    onScanRequested(on: boolean): void;
    onDialogConfirmed(version: string | undefined): void;
    onDialogCancelled(): void;
    onVersionChange(version: string): void;
    discard(announce?: boolean): void;
    reload(announce?: boolean): void;
    openYaml(policy: EditorPolicy): void;
    importYaml(selected: { content: string; policyPath: string }): void;
    renderYamlUnsaved(): void;
    retryLoad(): void;
    retryCommit(): void;
    dismissBanner(): void;
    requestStructure(): void;
    requestTemplateFiles(): void;
};

describe('ProgrammingExerciseSecurityPoliciesComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseSecurityPoliciesComponent>;
    let comp: ProgrammingExerciseSecurityPoliciesComponent;
    let handlers: Handlers;
    let service: Record<string, ReturnType<typeof vi.fn>>;

    beforeEach(() => {
        service = {
            getPolicies: vi.fn().mockReturnValue(of(editor())),
            previewPolicies: vi.fn().mockReturnValue(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [] })),
            commitPolicies: vi.fn(),
            importPolicy: vi.fn(),
            getTestStructure: vi.fn().mockReturnValue(of({ testClasses: [] })),
            getTemplateFiles: vi.fn().mockReturnValue(of({ paths: [] })),
        };
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SecurityFrameworkService, useValue: service },
            ],
        });
    });

    afterEach(() => vi.useRealTimers());

    function create(config = configured(), template = true): void {
        if (!template) {
            TestBed.overrideComponent(ProgrammingExerciseSecurityPoliciesComponent, { set: { template: '<div></div>' } });
        }
        fixture = TestBed.createComponent(ProgrammingExerciseSecurityPoliciesComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        fixture.componentRef.setInput('exerciseId', 42);
        fixture.componentRef.setInput('config', config);
        fixture.componentRef.setInput('canCommit', true);
        fixture.detectChanges();
    }

    function lastChange(): SecurityPolicyChange {
        return service.commitPolicies.mock.calls.at(-1)![1] as SecurityPolicyChange;
    }

    it('renders the card, the section and a policy of a configured exercise', () => {
        create();
        comp.sectionCollapsed.set(false);
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        expect(service.getPolicies).toHaveBeenCalledWith(42);
        expect(element.querySelector('[data-testid="security-advanced-card"]')).not.toBeNull();
        expect(element.querySelector('[data-testid="security-policies-section"]')).not.toBeNull();
        expect(element.querySelector('[data-testid="security-policy-file"]')?.textContent).toContain('SecurityPolicy.yaml');
        expect(element.querySelector('[data-testid="security-policies-commit-bar-idle"]')).not.toBeNull();
        expect(comp.cardState()).toBe('configured');
        expect(comp.scanDependencies()).toBe(false);
        expect(comp.scanAvailable()).toBe(true);
    });

    it('shows only the card while the sandbox is off', () => {
        create({ status: SecurityActivationStatus.INACTIVE });

        expect(fixture.nativeElement.querySelector('[data-testid="security-policies-section"]')).toBeNull();
        expect(comp.cardState()).toBe('inactive');
        expect(comp.toggleOn()).toBe(false);
    });

    it('commits the changed policies, the deleted ones and the staged version in one change', () => {
        create(configured(), false);
        const policy = comp.policies()[0];
        const assigned = withAssignment(policy, ['de.example.SortingTest#testBubbleSort', 'de.example.StressTest'], ['de.example.Helper']);
        handlers.onPolicyChange({ ...assigned, working: withRows(assigned.working!, 'packages', [{ packageName: 'org.apache.commons' }]) });
        handlers.addPolicy();
        const added = comp.policies()[1];
        handlers.onPolicyChange(withAssignment(added, ['de.example.SortingTest#testMergeSort'], []));
        handlers.onVersionChange('2.3.0');
        service.commitPolicies.mockReturnValue(of({ editor: editor('b'.repeat(40)), config: configured() }));

        expect(comp.problems()).toEqual([]);
        expect(comp.changeParts().build).toBe(1);
        handlers.commit('Update the policies');

        const change = lastChange();
        expect(change.frameworkVersion).toBe('2.3.0');
        expect(change.commitMessage).toBe('Update the policies');
        expect(change.policies).toHaveLength(2);
        expect(change.policies[0].previousPolicyPath).toBe('SecurityPolicy.yaml');
        expect(change.policies[0].assignedTests).toEqual(['de.example.SortingTest#testBubbleSort', 'de.example.StressTest']);
        expect(change.policies[0].document.testClasses).toEqual(['de.example.SortingTest', 'de.example.Helper', 'de.example.StressTest']);
        expect(change.policies[0].document.packages).toEqual([{ packageName: 'org.apache.commons' }]);
        expect(change.policies[1].previousPolicyPath).toBeUndefined();
        expect(change.policies[1].document.policyPath).toMatch(/\.yaml$/);
        expect(comp.totalChanges()).toBe(0);
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'committed', hash: 'bbbbbbb' }));
    });

    it('removes the file of a deleted committed policy with the commit', () => {
        create(configured(), false);
        handlers.askDeletePolicy(comp.policies()[0]);
        handlers.deletePolicy();
        service.commitPolicies.mockReturnValue(of({ editor: { ...editor(), policies: [] }, config: configured() }));

        expect(comp.policies()).toEqual([]);
        expect(comp.totalChanges()).toBe(1);
        handlers.commit('Delete the policy');

        expect(lastChange().removedPolicyPaths).toEqual(['SecurityPolicy.yaml']);
        expect(lastChange().policies).toEqual([]);
    });

    it('lists what keeps a new policy from being committed and does not preview it', () => {
        create(configured(), false);
        handlers.addPolicy();
        service.previewPolicies.mockClear();

        handlers.openReview();

        expect(comp.problems()).toEqual(['artemisApp.programmingExercise.security.advanced.problem.noTests']);
        expect(comp.reviewOpen()).toBe(true);
        expect(service.previewPolicies).not.toHaveBeenCalled();
    });

    it('keeps the unsaved changes when the policies changed in the repository meanwhile', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.commitPolicies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        service.getPolicies.mockReturnValue(of(editor('c'.repeat(40))));

        handlers.commit('Update');

        expect(comp.banner()).toEqual({ kind: 'conflict', authorName: 'Instructor', hash: 'ccccccc' });
        handlers.reload(true);
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
        expect(comp.editor()?.baseCommitHash).toBe('c'.repeat(40));
        expect(comp.banner()?.kind).toBe('info');
    });

    it('shows the translated reason of a refused commit', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.commitPolicies.mockReturnValue(
            throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'error.securityPolicyUnused', params: { path: 'Stress.yaml' } } })),
        );

        handlers.commit('Update');

        expect(comp.banner()).toEqual({ kind: 'failed', message: 'error.securityPolicyUnused', commitMessage: 'Update' });
    });

    it('turns dependency scanning on with a commit of only the language configuration', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const scanning = { ...DOCUMENT, languageConfiguration: 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION' };
        service.commitPolicies.mockReturnValue(of({ editor: editor('d'.repeat(40), scanning), config: configured() }));

        handlers.onScanRequested(true);
        expect(comp.dialogMode()).toBe('scanOn');
        handlers.onDialogConfirmed(undefined);

        const change = lastChange();
        expect(change.policies).toHaveLength(1);
        expect(change.policies[0].document.languageConfiguration).toBe('JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION');
        expect(change.policies[0].assignedTests).toEqual(['de.example.SortingTest']);
        expect(change.frameworkVersion).toBeUndefined();
        // The unsaved changes stay on top of the new commit, which the switch shows without loading the policies again.
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
        expect(comp.policies()[0].base?.languageConfiguration).toBe('JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION');
        expect(comp.shownScanDependencies()).toBe(true);
        expect(service.getPolicies).toHaveBeenCalledOnce();
    });

    it('switches dependency scanning for a policy staged for deletion as well', () => {
        const second = { ...DOCUMENT, policyPath: 'Second.yaml' };
        const withSecond = (hash: string, languageConfiguration: string): SecurityPolicyEditor => ({
            ...editor(hash, { ...DOCUMENT, languageConfiguration }),
            policies: [
                { policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, languageConfiguration }, assignedTests: ['de.example.SortingTest'] },
                { policyPath: 'Second.yaml', document: { ...second, languageConfiguration }, assignedTests: ['de.example.StressTest'] },
            ],
        });
        service.getPolicies.mockReturnValue(of(withSecond('a'.repeat(40), DOCUMENT.languageConfiguration!)));
        create(configured(), false);
        handlers.askDeletePolicy(comp.policies()[1]);
        handlers.deletePolicy();
        service.commitPolicies.mockReturnValue(of({ editor: withSecond('d'.repeat(40), 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION'), config: configured() }));

        handlers.onScanRequested(true);
        handlers.onDialogConfirmed(undefined);

        expect(lastChange().policies.map((entry) => entry.previousPolicyPath)).toEqual(['SecurityPolicy.yaml', 'Second.yaml']);
        expect(lastChange().removedPolicyPaths).toEqual([]);
        expect(comp.deletedPolicies().map((policy) => policy.file?.policyPath)).toEqual(['Second.yaml']);
        expect(comp.scanDependencies()).toBe(true);
    });

    it('rebases the changes made while the policies reload', () => {
        create(configured(), false);
        const reloaded = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValue(reloaded);

        handlers.reload();
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        reloaded.next(editor('d'.repeat(40)));

        expect(comp.editor()?.baseCommitHash).toBe('d'.repeat(40));
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
    });

    it('applies only the latest of two reloads, also when the earlier one answers after a commit', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const first = new Subject<SecurityPolicyEditor>();
        const second = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValueOnce(first).mockReturnValueOnce(second);
        handlers.reload();
        handlers.reload();

        second.next(editor('c'.repeat(40)));
        expect(comp.loading()).toBe(false);
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));
        handlers.commit('Update');
        expect(comp.editor()?.baseCommitHash).toBe('e'.repeat(40));

        first.next(editor('a'.repeat(40)));
        expect(comp.editor()?.baseCommitHash).toBe('e'.repeat(40));
        expect(comp.loading()).toBe(false);
    });

    it('commits the unsaved changes only once a pending import has answered', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const imported = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(imported);
        handlers.openYaml(comp.policies()[0]);
        handlers.importYaml({ content: 'content', policyPath: 'SecurityPolicy.yaml' });
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));

        handlers.commit('Update');
        expect(service.commitPolicies).not.toHaveBeenCalled();

        imported.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.imported' } });
        expect(comp.pendingImports()).toBe(0);
        handlers.commit('Update');
        expect(lastChange().policies[0].document.supervisedPackage).toBe('de.imported');
    });

    it('does not commit while the YAML dialog reads a file that becomes an import', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        handlers.openYaml(comp.policies()[0]);
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));

        comp.yamlFileReading.set(true);
        handlers.commit('Update');
        expect(service.commitPolicies).not.toHaveBeenCalled();

        comp.yamlFileReading.set(false);
        handlers.commit('Update');
        expect(service.commitPolicies).toHaveBeenCalledOnce();
    });

    it('counts only the imports that can still apply', () => {
        create(configured(), false);
        const first = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        const second = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        const third = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(first).mockReturnValueOnce(second).mockReturnValueOnce(third);
        handlers.openYaml(comp.policies()[0]);

        handlers.importYaml({ content: 'first', policyPath: 'SecurityPolicy.yaml' });
        handlers.importYaml({ content: 'second', policyPath: 'SecurityPolicy.yaml' });
        expect(comp.pendingImports()).toBe(1);
        second.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.second' } });
        expect(comp.pendingImports()).toBe(0);

        handlers.importYaml({ content: 'third', policyPath: 'SecurityPolicy.yaml' });
        handlers.discard();
        expect(comp.pendingImports()).toBe(0);
        // The late answers of dropped imports change neither the count nor the policy.
        first.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.first' } });
        third.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.third' } });
        expect(comp.pendingImports()).toBe(0);
        expect(comp.policies()[0].working?.supervisedPackage).toBe(DOCUMENT.supervisedPackage);
    });

    it('renders the unsaved YAML again when a reload rebases the open policy', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        handlers.openYaml(comp.policies()[0]);
        service.previewPolicies.mockReturnValueOnce(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, files: [{ path: 'SecurityPolicy.yaml', after: 'before' }] }));
        handlers.renderYamlUnsaved();
        expect(comp.yamlUnsaved()).toBe('before');
        service.previewPolicies.mockReturnValueOnce(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, files: [{ path: 'SecurityPolicy.yaml', after: 'rebased' }] }));
        service.getPolicies.mockReturnValue(of(editor('d'.repeat(40))));

        handlers.reload();

        expect(comp.yamlUnsaved()).toBe('rebased');
    });

    it('does not commit while the policies reload', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const reloaded = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValue(reloaded);

        handlers.reload();
        handlers.commit('Update');
        expect(service.commitPolicies).not.toHaveBeenCalled();

        reloaded.next(editor('d'.repeat(40)));
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));
        handlers.commit('Update');
        expect(lastChange().baseCommitHash).toBe('d'.repeat(40));
    });

    it('keeps the requested scanning state and holds its confirmation while the policies reload', () => {
        create();
        const reloaded = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValue(reloaded);
        const dialog = fixture.debugElement.query(By.directive(SecurityActivationDialogComponent)).componentInstance as SecurityActivationDialogComponent;

        handlers.reload();
        handlers.onScanRequested(true);
        fixture.detectChanges();
        expect(dialog.busy()).toBe(true);

        reloaded.next(editor('d'.repeat(40)));
        fixture.detectChanges();
        expect(dialog.busy()).toBe(false);
        expect(comp.shownScanDependencies()).toBe(true);
    });

    it('loads the test structure again when the policies are based on another commit', () => {
        create(configured(), false);
        service.getTestStructure.mockReturnValueOnce(of({ testClasses: [{ qualifiedName: 'de.example.SortingTest', testCases: ['old'], testClass: true }] }));
        handlers.requestStructure();
        handlers.requestStructure();
        expect(service.getTestStructure).toHaveBeenCalledOnce();

        const refreshed = new Subject<SecurityTestStructure>();
        service.getTestStructure.mockReturnValueOnce(refreshed);
        service.getPolicies.mockReturnValue(of(editor('d'.repeat(40))));
        handlers.reload();

        // The old structure is dropped at once, so the test case dialog waits for the new one.
        expect(service.getTestStructure).toHaveBeenCalledTimes(2);
        expect(comp.structure()).toBeUndefined();
        refreshed.next({ testClasses: [{ qualifiedName: 'de.example.SortingTest', testCases: ['added'], testClass: true }] });
        expect(comp.structure()?.testClasses?.[0].testCases).toEqual(['added']);
    });

    it('keeps the language configuration of a new policy when the reloaded policies have none', () => {
        create(configured(), false);
        handlers.addPolicy();
        const added = comp.policies()[1];
        expect(added.working?.languageConfiguration).toBe(DOCUMENT.languageConfiguration);
        service.getPolicies.mockReturnValue(of({ ...editor('d'.repeat(40)), policies: [] }));

        handlers.reload();

        expect(comp.policies().find((policy) => policy.id === added.id)?.working?.languageConfiguration).toBe(DOCUMENT.languageConfiguration);
    });

    it('keeps a newer commit running when the conflict lookup of an earlier commit answers late', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const lookup = new Subject<SecurityPolicyEditor>();
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409 })));
        service.getPolicies.mockReturnValueOnce(lookup);
        handlers.commit('Update');
        expect(comp.banner()).toEqual({ kind: 'conflict' });

        service.commitPolicies.mockReturnValueOnce(new Subject());
        handlers.commit('Update');
        lookup.next(editor('c'.repeat(40)));

        expect(comp.isCommitting()).toBe(true);
        expect(comp.banner()).toEqual({ kind: 'committing' });
    });

    it('does not switch the sandbox while a commit runs, and lets a late commit answer settle first', () => {
        create(configured(), false);
        const committed = new Subject<{ editor: SecurityPolicyEditor; config: SecurityFrameworkConfig }>();
        service.commitPolicies.mockReturnValue(committed);
        const deactivated: void[] = [];
        comp.deactivateRequested.subscribe(() => deactivated.push(undefined));
        handlers.onScanRequested(true);
        handlers.onDialogConfirmed(undefined);
        // The dialog closes itself once confirmed.
        comp.dialogOpen.set(false);
        expect(comp.isCommitting()).toBe(true);

        handlers.onToggleRequested(false);
        expect(comp.dialogOpen()).toBe(false);

        // A deactivation confirmed in a dialog opened before the commit started waits as well.
        comp.dialogMode.set('deactivate');
        handlers.onDialogConfirmed(undefined);
        expect(deactivated).toEqual([]);
        expect(comp.isCommitting()).toBe(true);

        committed.next({ editor: editor('d'.repeat(40)), config: configured() });
        handlers.onToggleRequested(false);
        expect(comp.dialogMode()).toBe('deactivate');
        expect(comp.dialogOpen()).toBe(true);
    });

    it('asks before turning the sandbox off and drops the unsaved changes', () => {
        create(configured(), false);
        const deactivated = vi.fn();
        comp.deactivateRequested.subscribe(deactivated);
        handlers.addPolicy();

        handlers.onToggleRequested(false);
        expect(comp.toggleOn()).toBe(false);
        expect(comp.dialogOpen()).toBe(true);
        handlers.onDialogConfirmed(undefined);

        expect(deactivated).toHaveBeenCalledOnce();
        expect(comp.totalChanges()).toBe(0);
    });

    it('emits the version to turn the sandbox on with, and restores the toggle when cancelled', () => {
        create({ status: SecurityActivationStatus.INACTIVE }, false);
        const activated = vi.fn();
        comp.activateRequested.subscribe(activated);

        handlers.onToggleRequested(true);
        expect(comp.toggleOn()).toBe(true);
        handlers.onDialogCancelled();
        expect(comp.toggleOn()).toBe(false);

        handlers.onToggleRequested(true);
        handlers.onDialogConfirmed('2.2.1');
        expect(activated).toHaveBeenCalledWith('2.2.1');
    });

    it('reloads the policies once the sandbox is on again', () => {
        create({ status: SecurityActivationStatus.GENERATING }, false);
        expect(service.getPolicies).not.toHaveBeenCalled();

        fixture.componentRef.setInput('config', configured());
        fixture.detectChanges();

        expect(service.getPolicies).toHaveBeenCalledOnce();
        expect(comp.cardState()).toBe('configured');
    });

    it('warns when the version the changes are committed with does not accept them', () => {
        vi.useFakeTimers();
        create(configured(), false);
        service.previewPolicies.mockReturnValue(of({ verdict: SecurityVersionCompatibilityVerdict.INCOMPATIBLE, frameworkVersion: '2.2.1', problems: ['x'] }));

        handlers.onPolicyChange({ ...comp.policies()[0], working: { ...comp.policies()[0].working!, testBehavior: { outputMirroring: { mirrored: true } } } });
        fixture.detectChanges();
        vi.advanceTimersByTime(700);

        expect(service.previewPolicies).toHaveBeenCalled();
        expect(comp.versionTooOld()).toBe(true);
    });

    it('replaces a policy with an imported file and keeps its test cases', () => {
        create(configured(), false);
        const policy = comp.policies()[0];
        service.importPolicy.mockReturnValue(
            of({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, files: [], testClasses: ['de.example.SortingTest', 'de.example.Util'] } }),
        );

        handlers.openYaml(policy);
        handlers.importYaml({ content: 'other', policyPath: 'Other.yaml' });
        expect(service.importPolicy).not.toHaveBeenCalled();
        handlers.importYaml({ content: 'content', policyPath: 'SecurityPolicy.yaml' });

        const imported = comp.policies()[0];
        expect(imported.working?.files).toEqual([]);
        expect(imported.tests).toEqual(['de.example.SortingTest']);
        expect(imported.extraClasses).toEqual(['de.example.Util']);
        expect(comp.importNotice()?.severity).toBe('success');
    });

    it('applies only the latest import of a policy when an earlier one answers later', () => {
        create(configured(), false);
        const first = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        const second = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(first).mockReturnValueOnce(second);
        handlers.openYaml(comp.policies()[0]);

        handlers.importYaml({ content: 'first', policyPath: 'SecurityPolicy.yaml' });
        handlers.importYaml({ content: 'second', policyPath: 'SecurityPolicy.yaml' });
        second.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.second' } });
        first.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.first' } });

        expect(comp.policies()[0].working?.supervisedPackage).toBe('de.second');
    });

    it('describes the files and the assignments the review shows', () => {
        create(configured(), false);
        const committed = comp.policies()[0];
        handlers.onPolicyChange(withAssignment({ ...committed, name: 'Stress' }, ['de.example.StressTest'], []));
        handlers.addPolicy();
        handlers.onPolicyChange(withAssignment(comp.policies()[1], ['de.example.SortingTest#merge'], []));

        expect(comp.reviewDescriptions().get('Stress.yaml')).toBe('Stress · artemisApp.programmingExercise.security.advanced.review.renamedFrom');
        expect([...comp.reviewDescriptions().values()].filter((description) => description.endsWith('review.newFile'))).toHaveLength(1);
        expect(comp.reviewAssignments()).toEqual([
            { sign: '+', entry: 'de.example.StressTest', file: 'Stress.yaml' },
            { sign: '−', entry: 'de.example.SortingTest', file: 'Stress.yaml' },
            { sign: '+', entry: 'de.example.SortingTest#merge', file: expect.stringMatching(/\.yaml$/) },
        ]);
        expect(comp.reviewSummary()).toContain('review.basedOn');
        expect(comp.defaultCommitMessage()).toBe('artemisApp.programmingExercise.security.advanced.review.defaultMessage');
        expect(comp.otherTestsByPolicy().get(committed.id)).toEqual(new Map([['de.example.SortingTest#merge', expect.any(String)]]));
    });

    it('names the problems of a policy without a name, with an invalid hidden period and of two policies in one file', () => {
        create(configured(), false);
        const policy = comp.policies()[0];
        handlers.onPolicyChange({
            ...policy,
            working: { ...policy.working!, testBehavior: { hiddenTests: { hiddenFrom: '2026-12-24 10:00 Europe/Berlin', hiddenUntil: '2026-12-24 09:00 Europe/Berlin' } } },
        });
        handlers.addPolicy();
        handlers.onPolicyChange(withAssignment({ ...comp.policies()[1], name: 'Security Policy' }, ['de.example.StressTest'], []));
        handlers.addPolicy();
        handlers.onPolicyChange({ ...comp.policies()[2], name: ' ' });

        expect(comp.problems()).toEqual([
            'artemisApp.programmingExercise.security.advanced.problem.hiddenPeriod',
            'artemisApp.programmingExercise.security.advanced.problem.nameRequired',
            'artemisApp.programmingExercise.security.advanced.problem.sameFile',
        ]);
    });

    it('previews the change for the review and reports a refused preview', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const preview = { verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [] };
        service.previewPolicies.mockReturnValue(of(preview));

        handlers.openReview();
        expect(comp.reviewPreview()).toEqual(preview);

        service.previewPolicies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { title: 'Invalid policy' } })));
        handlers.openReview();
        expect(comp.reviewOpen()).toBe(false);
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'failed', message: 'Invalid policy' }));
    });

    it('shows only the latest review of the change and previews a change made while it is open before committing it', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const first = new Subject<SecurityPolicyPreview>();
        const second = new Subject<SecurityPolicyPreview>();
        const third = { verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [{ path: 'SecurityPolicy.yaml', after: 'third' }] };
        service.previewPolicies.mockReturnValueOnce(first).mockReturnValueOnce(second).mockReturnValueOnce(of(third));
        handlers.openReview();
        comp.reviewOpen.set(false);
        handlers.onPolicyChange({ ...comp.policies()[0], working: withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.second' }]) });
        handlers.openReview();

        second.next({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [{ path: 'SecurityPolicy.yaml', after: 'second' }] });
        first.next({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [{ path: 'SecurityPolicy.yaml', after: 'first' }] });
        expect(comp.reviewPreview()?.files?.[0].after).toBe('second');

        // A change while the review is open, e.g. by a late import, is previewed before anything is committed.
        handlers.onPolicyChange({ ...comp.policies()[0], working: withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.third' }]) });
        handlers.commit('Update');
        expect(service.commitPolicies).not.toHaveBeenCalled();
        expect(comp.reviewPreview()?.files?.[0].after).toBe('third');
    });

    it('retries a failed commit with its message and names an unknown author of a conflict', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        handlers.commit('Update');
        expect(comp.banner()).toEqual({ kind: 'failed', message: 'artemisApp.programmingExercise.security.advanced.banner.failedGeneric', commitMessage: 'Update' });

        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409 })));
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        handlers.retryCommit();
        expect(lastChange().commitMessage).toBe('Update');
        expect(comp.banner()).toEqual({ kind: 'conflict' });
        handlers.dismissBanner();
        expect(comp.banner()).toBeUndefined();
    });

    it('turns dependency scanning off', () => {
        service.getPolicies.mockReturnValue(of(editor('a'.repeat(40), { ...DOCUMENT, languageConfiguration: 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION' })));
        create(configured(), false);
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));

        expect(comp.scanDependencies()).toBe(true);
        handlers.onScanRequested(false);
        handlers.onDialogConfirmed(undefined);

        expect(lastChange().policies[0].document.languageConfiguration).toBe('JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ');
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'committed' }));
    });

    it('shows a load error and loads again on retry', () => {
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        create();

        expect(comp.loadFailed()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="security-policies-load-error"]')).not.toBeNull();
        handlers.retryLoad();
        expect(comp.loadFailed()).toBe(false);
        expect(comp.policies()).toHaveLength(1);
    });

    it('requests the test structure and the template files once, and again after a failure', () => {
        create(configured(), false);
        service.getTestStructure.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        handlers.requestStructure();
        handlers.requestStructure();
        handlers.requestStructure();
        expect(service.getTestStructure).toHaveBeenCalledTimes(2);

        service.getTemplateFiles.mockReturnValueOnce(of({ paths: ['a.txt'], truncated: true }));
        handlers.requestTemplateFiles();
        handlers.requestTemplateFiles();
        expect(service.getTemplateFiles).toHaveBeenCalledOnce();
        expect(comp.templateFiles()).toEqual(['a.txt']);
        expect(comp.templateFilesTruncated()).toBe(true);
    });

    it('renders the YAML of the unsaved changes and reports an import the editor cannot read', () => {
        create(configured(), false);
        handlers.openYaml(comp.policies()[0]);
        service.previewPolicies.mockReturnValue(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, files: [{ path: 'SecurityPolicy.yaml', after: 'rendered' }] }));

        handlers.renderYamlUnsaved();
        expect(comp.yamlUnsaved()).toBe('rendered');

        service.importPolicy.mockReturnValue(of({ policyPath: 'SecurityPolicy.yaml', readOnlyReason: SecurityPolicyReadOnlyReason.YAML_ERROR, errorLine: 3 }));
        handlers.importYaml({ content: 'broken: [', policyPath: 'SecurityPolicy.yaml' });
        expect(comp.importNotice()).toEqual({ severity: 'error', text: 'artemisApp.programmingExercise.security.advanced.policy.readOnlyReason.YAML_ERROR' });
    });

    it('shows only the latest rendering of the unsaved YAML', () => {
        create(configured(), false);
        handlers.openYaml(comp.policies()[0]);
        const first = new Subject<SecurityPolicyPreview>();
        const second = new Subject<SecurityPolicyPreview>();
        service.previewPolicies.mockReturnValueOnce(first).mockReturnValueOnce(second);

        handlers.renderYamlUnsaved();
        handlers.renderYamlUnsaved();
        second.next({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [{ path: 'SecurityPolicy.yaml', after: 'second' }] });
        first.next({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [{ path: 'SecurityPolicy.yaml', after: 'first' }] });
        first.error(new HttpErrorResponse({ status: 500 }));

        expect(comp.yamlUnsaved()).toBe('second');
        expect(comp.importNotice()).toBeUndefined();
    });

    it('shows the state of the card while turning the sandbox on, off and while committing', () => {
        create({ status: SecurityActivationStatus.GENERATING }, false);
        expect(comp.cardState()).toBe('turningOn');
        fixture.componentRef.setInput('config', { status: SecurityActivationStatus.DELETING });
        expect(comp.cardState()).toBe('turningOff');
    });

    it('warns what deleting a committed policy changes', () => {
        create(configured(), false);
        handlers.askDeletePolicy(comp.policies()[0]);

        expect(comp.deleteWarning()).toEqual({
            key: 'artemisApp.programmingExercise.security.advanced.deletePolicy.committed',
            params: { file: 'SecurityPolicy.yaml', count: 1 },
        });
    });

    it('retries a failed dependency scanning commit with the same request and keeps the unsaved changes', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        handlers.onScanRequested(true);
        handlers.onDialogConfirmed(undefined);
        const failed = lastChange();

        service.commitPolicies.mockReturnValueOnce(of({ editor: editor('f'.repeat(40)), config: configured() }));
        handlers.retryCommit();

        expect(lastChange()).toEqual(failed);
        expect(lastChange().policies[0].assignedTests).toEqual(['de.example.SortingTest']);
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'committed', titleKey: 'artemisApp.programmingExercise.security.advanced.scanOn.committedTitle' }));
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
    });

    it('starts a new policy with the template of the server when no policy is left', () => {
        service.getPolicies.mockReturnValue(
            of({
                ...editor(),
                policies: [],
                newPolicyTemplate: { policyPath: '', languageConfiguration: 'JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ', supervisedPackage: 'de.example' },
            }),
        );
        create(configured(), false);

        handlers.addPolicy();

        expect(comp.policies()[0].working).toEqual(
            expect.objectContaining({ languageConfiguration: 'JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ', supervisedPackage: 'de.example', testClasses: [] }),
        );
    });

    it('keeps the changes to a policy someone deleted meanwhile as a new policy and says so', () => {
        create(configured(), false);
        const changed = withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.apache.commons' }]);
        handlers.onPolicyChange({ ...comp.policies()[0], working: changed });
        service.getPolicies.mockReturnValue(of({ ...editor('d'.repeat(40)), policies: [] }));

        handlers.reload(true);

        expect(comp.policies()).toHaveLength(1);
        expect(comp.policies()[0].file).toBeUndefined();
        expect(comp.policies()[0].working?.packages).toEqual([{ packageName: 'org.apache.commons' }]);
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest']);
        expect(comp.banner()).toEqual({ kind: 'orphaned', files: 'SecurityPolicy.yaml' });
    });

    it('does not switch dependency scanning while a policy is read-only', () => {
        service.getPolicies.mockReturnValue(
            of({
                ...editor(),
                policies: [
                    ...editor().policies!,
                    { policyPath: 'Broken.yaml', readOnlyReason: SecurityPolicyReadOnlyReason.YAML_ERROR, errorLine: 2, assignedTests: ['de.example.StressTest'] },
                ],
            }),
        );
        create(configured(), false);

        expect(comp.scanBlockedByReadOnly()).toBe(true);
        expect(comp.scanAvailable()).toBe(false);
    });

    it('opens the review again when Retry follows a failed preview, instead of committing', () => {
        create(configured(), false);
        handlers.onPolicyChange(withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.previewPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        handlers.openReview();
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'failed', retry: 'review' }));

        handlers.retryCommit();

        expect(service.commitPolicies).not.toHaveBeenCalled();
        expect(comp.reviewOpen()).toBe(true);
    });

    it('shows the requested scanning state until the dialog is cancelled or the commit is done', () => {
        create(configured(), false);

        handlers.onScanRequested(true);
        expect(comp.shownScanDependencies()).toBe(true);
        handlers.onDialogCancelled();
        expect(comp.shownScanDependencies()).toBe(false);

        service.commitPolicies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        handlers.onScanRequested(true);
        handlers.onDialogConfirmed(undefined);
        expect(comp.shownScanDependencies()).toBe(false);
    });

    it('keeps the changes to a policy that can no longer be read as a new policy with a name of its own', () => {
        create(configured(), false);
        handlers.onPolicyChange({ ...comp.policies()[0], working: withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.apache.commons' }]) });
        service.getPolicies.mockReturnValue(
            of({
                ...editor('d'.repeat(40)),
                policies: [{ policyPath: 'SecurityPolicy.yaml', readOnlyReason: SecurityPolicyReadOnlyReason.YAML_ERROR, errorLine: 3, assignedTests: ['de.example.SortingTest'] }],
            }),
        );

        handlers.reload(true);

        expect(comp.policies().map((policy) => [policy.name, !!policy.file, !!policy.working])).toEqual([
            ['Security Policy', true, false],
            ['Security Policy 2', false, true],
        ]);
        expect(comp.policies()[1].working?.packages).toEqual([{ packageName: 'org.apache.commons' }]);
        expect(comp.banner()).toEqual({ kind: 'orphaned', files: 'SecurityPolicy.yaml' });
    });

    it('discards every unsaved change', () => {
        create(configured(), false);
        handlers.addPolicy();
        handlers.onVersionChange('2.3.0');

        handlers.discard();

        expect(comp.policies()).toHaveLength(1);
        expect(comp.stagedVersion()).toBeUndefined();
        expect(comp.banner()?.kind).toBe('info');
    });

    it('drops an import that answers after the unsaved changes were discarded', () => {
        create(configured(), false);
        const imported = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(imported);
        handlers.openYaml(comp.policies()[0]);
        handlers.importYaml({ content: 'content', policyPath: 'SecurityPolicy.yaml' });

        handlers.discard();
        imported.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.imported' } });

        expect(comp.policies()[0].working?.supervisedPackage).toBe(DOCUMENT.supervisedPackage);
        expect(comp.totalChanges()).toBe(0);
        expect(comp.importNotice()).toBeUndefined();
    });

    it('drops an import from before a discard also when a newer import of the policy answers first', () => {
        create(configured(), false);
        const discarded = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        const latest = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(discarded).mockReturnValueOnce(latest);
        handlers.openYaml(comp.policies()[0]);
        handlers.importYaml({ content: 'before', policyPath: 'SecurityPolicy.yaml' });
        handlers.discard();
        handlers.importYaml({ content: 'after', policyPath: 'SecurityPolicy.yaml' });

        latest.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.after' } });
        discarded.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.before' } });

        expect(comp.policies()[0].working?.supervisedPackage).toBe('de.after');
    });
});
