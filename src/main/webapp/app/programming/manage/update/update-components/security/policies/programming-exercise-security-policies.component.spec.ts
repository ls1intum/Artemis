import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityActivationStatus, SecurityFrameworkConfig, SecurityVersionCompatibilityVerdict } from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityPolicyChange, SecurityPolicyDocument, SecurityPolicyEditor } from 'app/programming/shared/entities/security-policy.model';
import { SecurityFrameworkService } from 'app/programming/shared/services/security-framework.service';
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
    importYaml(content: string): void;
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
        service.commitPolicies.mockReturnValue(of({ editor: editor('d'.repeat(40)), config: configured() }));

        handlers.onScanRequested(true);
        expect(comp.dialogMode()).toBe('scanOn');
        handlers.onDialogConfirmed(undefined);

        const change = lastChange();
        expect(change.policies).toHaveLength(1);
        expect(change.policies[0].document.languageConfiguration).toBe('JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION');
        expect(change.policies[0].assignedTests).toEqual(['de.example.SortingTest']);
        expect(change.frameworkVersion).toBeUndefined();
        // The unsaved changes stay on top of the new commit.
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
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
        handlers.importYaml('content');

        const imported = comp.policies()[0];
        expect(imported.working?.files).toEqual([]);
        expect(imported.tests).toEqual(['de.example.SortingTest']);
        expect(imported.extraClasses).toEqual(['de.example.Util']);
        expect(comp.importNotice()?.severity).toBe('success');
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
});
