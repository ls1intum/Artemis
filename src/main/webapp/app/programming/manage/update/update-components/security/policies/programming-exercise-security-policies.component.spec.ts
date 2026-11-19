import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Subject, forkJoin, of, throwError } from 'rxjs';
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
import { fileNameOf, withAssignment, withRows } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

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

describe('ProgrammingExerciseSecurityPoliciesComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseSecurityPoliciesComponent>;
    let comp: ProgrammingExerciseSecurityPoliciesComponent;
    let service: Record<string, ReturnType<typeof vi.fn>>;

    beforeEach(() => {
        service = {
            getPolicies: vi.fn().mockReturnValue(of(editor())),
            previewPolicies: vi.fn().mockReturnValue(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [] })),
            commitPolicies: vi.fn(),
            importPolicy: vi.fn(),
            getTestStructure: vi.fn().mockReturnValue(of({ testClasses: [] })),
            getTemplateFiles: vi.fn().mockReturnValue(of({ paths: [] })),
            getConfig: vi.fn().mockReturnValue(of(configured())),
        };
        // The server reads both parts of the pushed state from one commit; the tests stub the parts, and the read fails as a whole if one part fails.
        service.getPoliciesWithConfig = vi.fn((exerciseId: number) => forkJoin({ editor: service.getPolicies(exerciseId), config: service.getConfig(exerciseId) }));
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
        const second = { ...DOCUMENT, policyPath: 'Second.yaml' };
        service.getPolicies.mockReturnValue(
            of({ ...editor(), policies: [...editor().policies!, { policyPath: 'Second.yaml', document: second, assignedTests: ['de.example.StressTest'] }] }),
        );
        create(configured(), false);
        comp['askDeletePolicy'](comp.policies()[1]);
        comp['deletePolicy']();
        const policy = comp.policies()[0];
        const assigned = withAssignment(policy, ['de.example.SortingTest#testBubbleSort', 'de.example.StressTest'], ['de.example.Helper']);
        comp['onPolicyChange']({ ...assigned, working: withRows(assigned.working!, 'packages', [{ packageName: 'org.apache.commons' }]) });
        comp['addPolicy']();
        const added = comp.policies()[1];
        comp['onPolicyChange'](withAssignment(added, ['de.example.SortingTest#testMergeSort'], []));
        comp['onVersionChange']('2.3.0');
        // The server answers with the policies and the version as the commit saved them.
        service.commitPolicies.mockImplementation((_exerciseId: number, sent: SecurityPolicyChange) =>
            of({
                editor: {
                    ...editor('b'.repeat(40)),
                    frameworkVersion: sent.frameworkVersion,
                    policies: sent.policies.map((entry) => ({ policyPath: entry.document.policyPath, document: entry.document, assignedTests: entry.assignedTests })),
                },
                config: { ...configured(), frameworkVersion: sent.frameworkVersion },
            }),
        );
        const configs: SecurityFrameworkConfig[] = [];
        comp.configChange.subscribe((config) => configs.push(config));

        expect(comp.problems()).toEqual([]);
        expect(comp.changeParts().build).toBe(1);
        comp['commit']('Update the policies');

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
        expect(change.removedPolicyPaths).toEqual(['Second.yaml']);
        expect(comp.totalChanges()).toBe(0);
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'committed', hash: 'bbbbbbb' }));
        // The editor shows what the commit saved: both policies, without the deleted one, and the new version.
        expect(comp.policies().map((shown) => shown.file?.policyPath)).toEqual(['SecurityPolicy.yaml', change.policies[1].document.policyPath]);
        expect(comp.policies()[0].working?.packages).toEqual([{ packageName: 'org.apache.commons' }]);
        expect(comp.policies()[1].tests).toEqual(['de.example.SortingTest#testMergeSort']);
        expect(comp.deletedPolicies()).toEqual([]);
        expect(comp.editor()?.frameworkVersion).toBe('2.3.0');
        expect(comp.stagedVersion()).toBeUndefined();
        expect(configs).toEqual([{ ...configured(), frameworkVersion: '2.3.0' }]);
    });

    it('moves the focus to the version select when the banner asks to change the version', () => {
        create();
        const trigger = document.getElementById('securityAdvancedVersion')!;
        trigger.scrollIntoView = vi.fn();

        comp['focusVersion']();

        expect(trigger.scrollIntoView).toHaveBeenCalledOnce();
        expect(document.activeElement).toBe(trigger);
    });

    it('does not stage a version before the policies are loaded, because their first load would drop it', () => {
        const policies = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValue(policies);
        create(configured(), false);

        comp['onVersionChange']('2.3.0');
        expect(comp.stagedVersion()).toBeUndefined();

        policies.next(editor());
        comp['onVersionChange']('2.3.0');
        expect(comp.stagedVersion()).toBe('2.3.0');
    });

    it('names one unsaved change in the singular and several in the plural', () => {
        create(configured(), false);
        const prefix = 'artemisApp.programmingExercise.security.advanced.commitBar';
        const policy = comp.policies()[0];
        comp['onPolicyChange']({ ...policy, working: withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]) });

        expect(comp.changeText()).toBe(`${prefix}.restrictions.one`);
        expect(comp.discardQuestion().key).toBe('artemisApp.programmingExercise.security.advanced.discard.question.one');

        comp['onPolicyChange']({ ...policy, working: withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }, { packageName: 'org.junit' }]) });

        expect(comp.changeText()).toBe(`${prefix}.restrictions.other`);
        expect(comp.discardQuestion().key).toBe('artemisApp.programmingExercise.security.advanced.discard.question.other');
    });

    it('shows the pushed policies after a commit whose builds did not start, so Discard returns to them, and resumes the builds with the same request', () => {
        create(configured(), false);
        const configs: SecurityFrameworkConfig[] = [];
        comp.configChange.subscribe((config) => configs.push(config));
        const policy = comp.policies()[0];
        const granted = withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]);
        comp['onPolicyChange']({ ...policy, working: granted });
        const pushed = editor('b'.repeat(40), granted);
        service.commitPolicies.mockReturnValueOnce(
            throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'error.securityFrameworkProcessingFailed', title: 'The builds could not be started.' } })),
        );
        service.getPolicies.mockReturnValue(of(pushed));

        comp['commit']('Grant commons');

        expect(comp.banner()).toEqual({ kind: 'processingFailed', hash: 'bbbbbbb' });
        expect(comp.editor()?.baseCommitHash).toBe('b'.repeat(40));
        expect(comp.totalChanges()).toBe(0);
        expect(configs).toEqual([configured()]);
        comp['onPolicyChange']({ ...comp.policies()[0], working: withRows(granted, 'packages', []) });
        comp['discard']();
        expect(comp.policies()[0].working?.packages).toEqual([{ packageName: 'org.apache.commons' }]);
        expect(comp.banner()?.kind).toBe('processingFailed');

        service.commitPolicies.mockReturnValueOnce(of({ editor: pushed, config: configured() }));
        comp['retryCommit']();
        expect(service.commitPolicies).toHaveBeenCalledTimes(2);
        expect(service.commitPolicies.mock.calls[1][1]).toEqual(service.commitPolicies.mock.calls[0][1]);
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'committed', hash: 'bbbbbbb' }));
    });

    it('keeps editing and switching the sandbox paused until the policies and the config of a pushed but unprocessed commit are read', () => {
        create(configured(), false);
        const pending: boolean[] = [];
        comp.committingChange.subscribe((committing) => pending.push(committing));
        const configs: SecurityFrameworkConfig[] = [];
        comp.configChange.subscribe((config) => configs.push(config));
        const policy = comp.policies()[0];
        const granted = withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]);
        comp['onPolicyChange']({ ...policy, working: granted });
        const policies = new Subject<SecurityPolicyEditor>();
        const config = new Subject<SecurityFrameworkConfig>();
        service.getPolicies.mockReturnValue(policies);
        service.getConfig.mockReturnValue(config);
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'error.securityFrameworkProcessingFailed' } })));

        comp['commit']('Grant commons');

        expect(comp.isCommitting()).toBe(true);
        expect(comp.editingDisabled()).toBe(true);
        comp['onToggleRequested'](false);
        expect(comp.dialogOpen()).toBe(false);

        policies.next(editor('b'.repeat(40), granted));
        policies.complete();
        expect(comp.isCommitting()).toBe(true);
        config.next(configured());
        config.complete();

        expect(comp.isCommitting()).toBe(false);
        expect(pending).toEqual([true, false]);
        expect(configs).toEqual([configured()]);
        expect(comp.editor()?.baseCommitHash).toBe('b'.repeat(40));
    });

    it('reads the pushed policies and config again when only the config read fails, instead of keeping an older config', () => {
        create(configured(), false);
        const configs: SecurityFrameworkConfig[] = [];
        comp.configChange.subscribe((config) => configs.push(config));
        const policy = comp.policies()[0];
        const granted = withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]);
        comp['onPolicyChange']({ ...policy, working: granted });
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'error.securityFrameworkProcessingFailed' } })));
        service.getPolicies.mockReturnValue(of(editor('b'.repeat(40), granted)));
        service.getConfig.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));

        comp['commit']('Grant commons');

        // The state is read as a whole, so the editor offers to read it again rather than showing the policies with an older config.
        expect(comp.loadFailed()).toBe(true);
        expect(configs).toEqual([]);
        comp['retryLoad']();
        expect(service.getPoliciesWithConfig).toHaveBeenCalledTimes(2);
        expect(service.commitPolicies).toHaveBeenCalledOnce();
        expect(configs).toEqual([configured()]);
        expect(comp.loadFailed()).toBe(false);
        expect(comp.editor()?.baseCommitHash).toBe('b'.repeat(40));
    });

    it('blocks the outdated policies until the pushed ones are read when their read fails after an unprocessed commit', () => {
        create(configured(), false);
        const policy = comp.policies()[0];
        const granted = withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]);
        comp['onPolicyChange']({ ...policy, working: granted });
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'error.securityFrameworkProcessingFailed' } })));
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));

        comp['commit']('Grant commons');

        expect(comp.loadFailed()).toBe(true);
        expect(comp.editingDisabled()).toBe(true);
        expect(comp.banner()?.kind).toBe('processingFailed');

        service.getPolicies.mockReturnValueOnce(of(editor('b'.repeat(40), granted)));
        comp['retryLoad']();

        expect(comp.loadFailed()).toBe(false);
        expect(comp.editor()?.baseCommitHash).toBe('b'.repeat(40));
        comp['discard']();
        expect(comp.policies()[0].working?.packages).toEqual([{ packageName: 'org.apache.commons' }]);

        // The banner's Retry still sends the original request, whose processing the server resumes.
        service.commitPolicies.mockReturnValueOnce(of({ editor: editor('b'.repeat(40), granted), config: configured() }));
        comp['retryCommit']();
        expect(service.commitPolicies.mock.calls[1][1]).toEqual(service.commitPolicies.mock.calls[0][1]);
    });

    it('keeps repeated reads of the pushed policies pending, and lets no obsolete one unlock a newer commit', () => {
        create(configured(), false);
        const policy = comp.policies()[0];
        const granted = withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]);
        comp['onPolicyChange']({ ...policy, working: granted });
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'error.securityFrameworkProcessingFailed' } })));
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['commit']('Grant commons');
        expect(comp.isCommitting()).toBe(false);

        const first = new Subject<SecurityPolicyEditor>();
        const second = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValueOnce(first).mockReturnValueOnce(second);
        comp['retryLoad']();
        expect(comp.isCommitting()).toBe(true);
        comp['retryLoad']();
        second.next(editor('b'.repeat(40), granted));
        second.complete();
        expect(comp.isCommitting()).toBe(false);

        // A newer commit runs while the first, obsolete read answers.
        comp['onPolicyChange']({ ...comp.policies()[0], working: withRows(granted, 'packages', []) });
        const commit = new Subject<{ editor: SecurityPolicyEditor; config: SecurityFrameworkConfig }>();
        service.commitPolicies.mockReturnValueOnce(commit);
        comp['commit']('Revoke commons');
        expect(comp.isCommitting()).toBe(true);
        first.next(editor('a'.repeat(40)));
        first.complete();

        expect(comp.isCommitting()).toBe(true);
        expect(comp.editingDisabled()).toBe(true);
        commit.next({ editor: editor('c'.repeat(40)), config: configured() });
        expect(comp.isCommitting()).toBe(false);
    });

    it('removes the file of a deleted committed policy with the commit', () => {
        create(configured(), false);
        comp['askDeletePolicy'](comp.policies()[0]);
        comp['deletePolicy']();
        service.commitPolicies.mockReturnValue(of({ editor: { ...editor(), policies: [] }, config: configured() }));

        expect(comp.policies()).toEqual([]);
        expect(comp.totalChanges()).toBe(1);
        comp['commit']('Delete the policy');

        expect(lastChange().removedPolicyPaths).toEqual(['SecurityPolicy.yaml']);
        expect(lastChange().policies).toEqual([]);
    });

    it('lists what keeps a new policy from being committed and does not preview it', () => {
        create(configured(), false);
        comp['addPolicy']();
        service.previewPolicies.mockClear();

        comp['openReview']();

        expect(comp.problems()).toEqual(['artemisApp.programmingExercise.security.advanced.problem.noTests']);
        expect(comp.reviewOpen()).toBe(true);
        expect(service.previewPolicies).not.toHaveBeenCalled();
    });

    it('keeps the unsaved changes when the policies changed in the repository meanwhile', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.commitPolicies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        service.getPolicies.mockReturnValue(of(editor('c'.repeat(40))));

        comp['commit']('Update');

        expect(comp.banner()).toEqual({ kind: 'conflict', authorName: 'Instructor', hash: 'ccccccc' });
        // A failed reload leaves the editor on the older version, so the banner says so and keeps offering Reload.
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['reload'](true);
        expect(comp.banner()).toEqual({ kind: 'conflict', authorName: 'Instructor', hash: 'ccccccc', reloadFailed: true });
        expect(comp.editor()?.baseCommitHash).toBe('a'.repeat(40));
        comp['reload'](true);
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
        expect(comp.editor()?.baseCommitHash).toBe('c'.repeat(40));
        expect(comp.banner()?.kind).toBe('info');
    });

    it('shows the translated reason of a refused commit', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.commitPolicies.mockReturnValue(
            throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'error.securityPolicyUnused', params: { path: 'Stress.yaml' } } })),
        );

        comp['commit']('Update');

        expect(comp.banner()).toEqual({ kind: 'failed', message: 'error.securityPolicyUnused', commitMessage: 'Update' });
    });

    it('turns dependency scanning on with a commit of only the language configuration', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const scanning = { ...DOCUMENT, languageConfiguration: 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION' };
        service.commitPolicies.mockReturnValue(of({ editor: editor('d'.repeat(40), scanning), config: configured() }));

        comp['onScanRequested'](true);
        expect(comp.dialogMode()).toBe('scanOn');
        comp['onDialogConfirmed'](undefined);

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
        const second = { ...DOCUMENT, policyPath: 'Second.yaml', testClasses: ['de.example.StressTest'] };
        const withSecond = (hash: string, languageConfiguration: string): SecurityPolicyEditor => ({
            ...editor(hash, { ...DOCUMENT, languageConfiguration }),
            policies: [
                { policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, languageConfiguration }, assignedTests: ['de.example.SortingTest'] },
                { policyPath: 'Second.yaml', document: { ...second, languageConfiguration }, assignedTests: ['de.example.StressTest'] },
            ],
        });
        service.getPolicies.mockReturnValue(of(withSecond('a'.repeat(40), DOCUMENT.languageConfiguration!)));
        create(configured(), false);
        comp['askDeletePolicy'](comp.policies()[1]);
        comp['deletePolicy']();
        service.commitPolicies.mockReturnValue(of({ editor: withSecond('d'.repeat(40), 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION'), config: configured() }));

        comp['onScanRequested'](true);
        comp['onDialogConfirmed'](undefined);

        expect(lastChange().policies.map((entry) => entry.previousPolicyPath)).toEqual(['SecurityPolicy.yaml', 'Second.yaml']);
        // Each policy declares the test class it is assigned, as the server requires.
        expect(lastChange().policies[1].document.testClasses).toEqual(['de.example.StressTest']);
        expect(lastChange().removedPolicyPaths).toEqual([]);
        expect(comp.deletedPolicies().map((policy) => policy.file?.policyPath)).toEqual(['Second.yaml']);
        expect(comp.scanDependencies()).toBe(true);
    });

    it('rebases the changes made while the policies reload', () => {
        create(configured(), false);
        const reloaded = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValue(reloaded);

        comp['reload']();
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        reloaded.next(editor('d'.repeat(40)));

        expect(comp.editor()?.baseCommitHash).toBe('d'.repeat(40));
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
    });

    it('applies only the latest of two reloads, also when the earlier one answers after a commit', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const first = new Subject<SecurityPolicyEditor>();
        const second = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValueOnce(first).mockReturnValueOnce(second);
        comp['reload']();
        comp['reload']();

        second.next(editor('c'.repeat(40)));
        expect(comp.loading()).toBe(false);
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));
        comp['commit']('Update');
        expect(comp.editor()?.baseCommitHash).toBe('e'.repeat(40));

        first.next(editor('a'.repeat(40)));
        expect(comp.editor()?.baseCommitHash).toBe('e'.repeat(40));
        expect(comp.loading()).toBe(false);
    });

    it('commits the unsaved changes only once a pending import has answered', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const imported = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(imported);
        comp['openYaml'](comp.policies()[0]);
        comp['importYaml']({ content: 'content', policyPath: 'SecurityPolicy.yaml' });
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));

        comp['commit']('Update');
        expect(service.commitPolicies).not.toHaveBeenCalled();

        imported.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.imported' } });
        expect(comp.pendingImports()).toBe(0);
        comp['commit']('Update');
        expect(lastChange().policies[0].document.supervisedPackage).toBe('de.imported');
    });

    it('does not commit while the YAML dialog reads a file that becomes an import', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        comp['openYaml'](comp.policies()[0]);
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));

        comp.yamlFileReading.set(true);
        comp['commit']('Update');
        expect(service.commitPolicies).not.toHaveBeenCalled();

        comp.yamlFileReading.set(false);
        comp['commit']('Update');
        expect(service.commitPolicies).toHaveBeenCalledOnce();
    });

    it('counts only the imports that can still apply', () => {
        create(configured(), false);
        const first = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        const second = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        const third = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(first).mockReturnValueOnce(second).mockReturnValueOnce(third);
        comp['openYaml'](comp.policies()[0]);

        comp['importYaml']({ content: 'first', policyPath: 'SecurityPolicy.yaml' });
        comp['importYaml']({ content: 'second', policyPath: 'SecurityPolicy.yaml' });
        expect(comp.pendingImports()).toBe(1);
        second.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.second' } });
        expect(comp.pendingImports()).toBe(0);

        comp['importYaml']({ content: 'third', policyPath: 'SecurityPolicy.yaml' });
        comp['discard']();
        expect(comp.pendingImports()).toBe(0);
        // The late answers of dropped imports change neither the count nor the policy.
        first.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.first' } });
        third.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.third' } });
        expect(comp.pendingImports()).toBe(0);
        expect(comp.policies()[0].working?.supervisedPackage).toBe(DOCUMENT.supervisedPackage);
    });

    it('renders the unsaved YAML again when a reload rebases the open policy', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        comp['openYaml'](comp.policies()[0]);
        service.previewPolicies.mockReturnValueOnce(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, files: [{ path: 'SecurityPolicy.yaml', after: 'before' }] }));
        comp['renderYamlUnsaved']();
        expect(comp.yamlUnsaved()).toBe('before');
        service.previewPolicies.mockReturnValueOnce(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, files: [{ path: 'SecurityPolicy.yaml', after: 'rebased' }] }));
        service.getPolicies.mockReturnValue(of(editor('d'.repeat(40))));

        comp['reload']();

        expect(comp.yamlUnsaved()).toBe('rebased');
    });

    it('does not commit while the policies reload', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const reloaded = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValue(reloaded);

        comp['reload']();
        comp['commit']('Update');
        expect(service.commitPolicies).not.toHaveBeenCalled();

        reloaded.next(editor('d'.repeat(40)));
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));
        comp['commit']('Update');
        expect(lastChange().baseCommitHash).toBe('d'.repeat(40));
    });

    it('keeps the requested scanning state and holds its confirmation while the policies reload', () => {
        create();
        const reloaded = new Subject<SecurityPolicyEditor>();
        service.getPolicies.mockReturnValue(reloaded);
        const dialog = fixture.debugElement.query(By.directive(SecurityActivationDialogComponent)).componentInstance as SecurityActivationDialogComponent;

        comp['reload']();
        comp['onScanRequested'](true);
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
        comp['requestStructure']();
        comp['requestStructure']();
        expect(service.getTestStructure).toHaveBeenCalledOnce();

        const refreshed = new Subject<SecurityTestStructure>();
        service.getTestStructure.mockReturnValueOnce(refreshed);
        service.getPolicies.mockReturnValue(of(editor('d'.repeat(40))));
        comp['reload']();

        // The old structure is dropped at once, so the test case dialog waits for the new one, which is read from the new base commit.
        expect(service.getTestStructure).toHaveBeenCalledTimes(2);
        expect(service.getTestStructure).toHaveBeenLastCalledWith(42, 'd'.repeat(40));
        expect(comp.structure()).toBeUndefined();
        refreshed.next({ testClasses: [{ qualifiedName: 'de.example.SortingTest', testCases: ['added'], testClass: true }] });
        expect(comp.structure()?.testClasses?.[0].testCases).toEqual(['added']);
    });

    it('keeps the imported language configuration of a new policy across reloads, whatever the committed configuration', () => {
        const scanning = 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION';
        create(configured(), false);
        comp['addPolicy']();
        const added = comp.policies()[1];
        const path = comp.paths().get(added.id)!;
        service.importPolicy.mockReturnValueOnce(of({ policyPath: path, document: { ...DOCUMENT, policyPath: path, languageConfiguration: scanning } }));
        comp['openYaml'](added);
        comp['importYaml']({ content: 'scanning', policyPath: path });
        const configurationOfAdded = () => comp.policies().find((policy) => policy.id === added.id)?.working?.languageConfiguration;
        expect(configurationOfAdded()).toBe(scanning);

        // Someone switches dependency scanning on and off again; the imported configuration stays both times.
        service.getPolicies.mockReturnValue(of(editor('d'.repeat(40), { ...DOCUMENT, languageConfiguration: scanning })));
        comp['reload']();
        expect(configurationOfAdded()).toBe(scanning);
        service.getPolicies.mockReturnValue(of(editor('e'.repeat(40))));
        comp['reload']();
        expect(configurationOfAdded()).toBe(scanning);
    });

    it('lets a new policy with the configuration it started from follow a switch of dependency scanning', () => {
        const scanning = 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION';
        create(configured(), false);
        comp['addPolicy']();
        const added = comp.policies()[1];
        service.getPolicies.mockReturnValue(of(editor('d'.repeat(40), { ...DOCUMENT, languageConfiguration: scanning })));

        comp['reload']();

        expect(comp.policies().find((policy) => policy.id === added.id)?.working?.languageConfiguration).toBe(scanning);
    });

    it('keeps the language configuration of a new policy when the reloaded policies have none', () => {
        create(configured(), false);
        comp['addPolicy']();
        const added = comp.policies()[1];
        expect(added.working?.languageConfiguration).toBe(DOCUMENT.languageConfiguration);
        service.getPolicies.mockReturnValue(of({ ...editor('d'.repeat(40)), policies: [] }));

        comp['reload']();

        expect(comp.policies().find((policy) => policy.id === added.id)?.working?.languageConfiguration).toBe(DOCUMENT.languageConfiguration);
    });

    it('keeps a newer commit running when the conflict lookup of an earlier commit answers late', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const lookup = new Subject<SecurityPolicyEditor>();
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409 })));
        service.getPolicies.mockReturnValueOnce(lookup);
        comp['commit']('Update');
        expect(comp.banner()).toEqual({ kind: 'conflict' });

        service.commitPolicies.mockReturnValueOnce(new Subject());
        comp['commit']('Update');
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
        // The card hears of the pending commit too, because simple mode can hide this view while the commit runs.
        const committing: boolean[] = [];
        comp.committingChange.subscribe((pending) => committing.push(pending));
        comp['onScanRequested'](true);
        comp['onDialogConfirmed'](undefined);
        // The dialog closes itself once confirmed.
        comp.dialogOpen.set(false);
        expect(comp.isCommitting()).toBe(true);

        comp['onToggleRequested'](false);
        expect(comp.dialogOpen()).toBe(false);

        // A deactivation confirmed in a dialog opened before the commit started waits as well.
        comp.dialogMode.set('deactivate');
        comp['onDialogConfirmed'](undefined);
        expect(deactivated).toEqual([]);
        expect(comp.isCommitting()).toBe(true);

        expect(committing).toEqual([true]);
        committed.next({ editor: editor('d'.repeat(40)), config: configured() });
        expect(committing).toEqual([true, false]);
        comp['onToggleRequested'](false);
        expect(comp.dialogMode()).toBe('deactivate');
        expect(comp.dialogOpen()).toBe(true);
    });

    it('asks before turning the sandbox off and drops the unsaved changes', () => {
        create(configured(), false);
        const deactivated = vi.fn();
        comp.deactivateRequested.subscribe(deactivated);
        comp['addPolicy']();

        comp['onToggleRequested'](false);
        expect(comp.toggleOn()).toBe(false);
        expect(comp.dialogOpen()).toBe(true);
        comp['onDialogConfirmed'](undefined);

        expect(deactivated).toHaveBeenCalledOnce();
        expect(comp.totalChanges()).toBe(0);
    });

    it('emits the version to turn the sandbox on with, and restores the toggle when cancelled', () => {
        create({ status: SecurityActivationStatus.INACTIVE }, false);
        const activated = vi.fn();
        comp.activateRequested.subscribe(activated);

        comp['onToggleRequested'](true);
        expect(comp.toggleOn()).toBe(true);
        comp['onDialogCancelled']();
        expect(comp.toggleOn()).toBe(false);

        comp['onToggleRequested'](true);
        comp['onDialogConfirmed']('2.2.1');
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

        comp['onPolicyChange']({ ...comp.policies()[0], working: { ...comp.policies()[0].working!, testBehavior: { outputMirroring: { mirrored: true } } } });
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

        comp['openYaml'](policy);
        comp['importYaml']({ content: 'other', policyPath: 'Other.yaml' });
        expect(service.importPolicy).not.toHaveBeenCalled();
        comp['importYaml']({ content: 'content', policyPath: 'SecurityPolicy.yaml' });

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
        comp['openYaml'](comp.policies()[0]);

        comp['importYaml']({ content: 'first', policyPath: 'SecurityPolicy.yaml' });
        comp['importYaml']({ content: 'second', policyPath: 'SecurityPolicy.yaml' });
        second.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.second' } });
        first.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.first' } });

        expect(comp.policies()[0].working?.supervisedPackage).toBe('de.second');
    });

    it('applies an import to the policy that started it, but shows its notice only while the dialog shows that policy', () => {
        create(configured(), false);
        comp['addPolicy']();
        const [committed, added] = comp.policies();
        const succeeding = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        const failing = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(succeeding).mockReturnValueOnce(failing);
        comp['openYaml'](committed);
        comp['importYaml']({ content: 'content', policyPath: 'SecurityPolicy.yaml' });
        comp['openYaml'](added);
        comp['importYaml']({ content: 'broken', policyPath: comp.paths().get(added.id)! });
        comp['openYaml'](committed);

        failing.error(new HttpErrorResponse({ status: 500 }));
        expect(comp.importNotice()).toBeUndefined();
        comp['openYaml'](added);
        succeeding.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.imported' } });

        expect(comp.policies()[0].working?.supervisedPackage).toBe('de.imported');
        expect(comp.importNotice()).toBeUndefined();
        expect(comp.importPending()).toBe(false);
    });

    it('stops waiting for an import into a new policy once that policy is deleted', () => {
        create(configured(), false);
        comp['addPolicy']();
        const added = comp.policies()[1];
        const importing = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        const other = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(importing).mockReturnValueOnce(other);
        comp['openYaml'](added);
        comp['importYaml']({ content: 'content', policyPath: comp.paths().get(added.id)! });
        comp['openYaml'](comp.policies()[0]);
        comp['importYaml']({ content: 'other', policyPath: 'SecurityPolicy.yaml' });
        expect(comp.pendingImports()).toBe(2);

        comp['askDeletePolicy'](added);
        comp['deletePolicy']();

        // The import into the remaining policy can still apply, so it keeps holding back a commit.
        expect(comp.pendingImports()).toBe(1);
        other.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.other' } });
        expect(comp.importPending()).toBe(false);
        importing.next({ policyPath: 'Policy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.deleted' } });
        expect(comp.policies()).toHaveLength(1);
        expect(comp.policies()[0].working?.supervisedPackage).toBe('de.other');
    });

    it('describes the files and the assignments the review shows', () => {
        create(configured(), false);
        const committed = comp.policies()[0];
        comp['onPolicyChange'](withAssignment({ ...committed, name: 'Stress' }, ['de.example.StressTest'], []));
        comp['addPolicy']();
        comp['onPolicyChange'](withAssignment(comp.policies()[1], ['de.example.SortingTest#merge'], []));

        expect(comp.reviewDescriptions().get('Stress.yaml')).toBe('Stress · artemisApp.programmingExercise.security.advanced.review.renamedFrom');
        expect([...comp.reviewDescriptions().values()].filter((description) => description.endsWith('review.newFile'))).toHaveLength(1);
        expect(comp.reviewAssignments()).toEqual([
            { sign: '+', entry: 'de.example.StressTest', file: 'Stress.yaml' },
            { sign: '−', entry: 'de.example.SortingTest', file: 'Stress.yaml' },
            { sign: '+', entry: 'de.example.SortingTest#merge', file: expect.stringMatching(/\.yaml$/) },
        ]);
        expect(comp.reviewSummary()).toContain('review.basedOn');
        expect(comp.defaultCommitMessage()).toBe('artemisApp.programmingExercise.security.advanced.review.defaultMessage.other');
        expect(comp.otherTestsByPolicy().get(committed.id)).toEqual(new Map([['de.example.SortingTest#merge', expect.any(String)]]));
    });

    it('lists every test case that splitting a class releases, as the test cases are now', () => {
        // testQuickSort was added after the split was made, e.g. before a reload kept the unsaved split.
        service.getTestStructure.mockReturnValue(
            of({ testClasses: [{ qualifiedName: 'de.example.SortingTest', testClass: true, testCases: ['bubble', 'merge', 'quick', 'off'], fixedCases: ['off'] }] }),
        );
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest#merge'], []));

        comp['openReview']();

        expect(service.getTestStructure).toHaveBeenCalledOnce();
        expect(comp.reviewAssignments()).toEqual([
            { sign: '+', entry: 'de.example.SortingTest#merge', file: 'SecurityPolicy.yaml' },
            { sign: '−', entry: 'de.example.SortingTest#bubble', file: 'SecurityPolicy.yaml' },
            { sign: '−', entry: 'de.example.SortingTest#quick', file: 'SecurityPolicy.yaml' },
        ]);
    });

    it('lists the test cases a split releases also when the class moves from a deleted policy to another one', () => {
        service.getTestStructure.mockReturnValue(of({ testClasses: [{ qualifiedName: 'de.example.SortingTest', testClass: true, testCases: ['bubble', 'merge', 'quick'] }] }));
        create(configured(), false);
        comp['askDeletePolicy'](comp.policies()[0]);
        comp['deletePolicy']();
        comp['addPolicy']();
        const added = comp.policies()[0];
        comp['onPolicyChange'](withAssignment(added, ['de.example.SortingTest#merge'], []));
        const file = fileNameOf(comp.paths().get(added.id)!);

        expect(comp.splitsClass()).toBe(true);
        comp['openReview']();

        expect(service.getTestStructure).toHaveBeenCalledOnce();
        expect(comp.reviewAssignmentsState()).toBe('ready');
        expect(comp.reviewAssignments()).toEqual([
            { sign: '+', entry: 'de.example.SortingTest#merge', file },
            { sign: '−', entry: 'de.example.SortingTest#bubble', file: 'SecurityPolicy.yaml' },
            { sign: '−', entry: 'de.example.SortingTest#quick', file: 'SecurityPolicy.yaml' },
        ]);
    });

    it('lists the test cases a split releases when a kept policy gives a class up and another one takes some of its test cases', () => {
        const structure = new Subject<SecurityTestStructure>();
        service.getTestStructure.mockReturnValue(structure);
        create(configured(), false);
        const kept = comp.policies()[0];
        comp['onPolicyChange'](withAssignment(kept, ['de.example.StressTest'], ['de.example.StressTest']));
        comp['addPolicy']();
        const added = comp.policies()[1];
        comp['onPolicyChange'](withAssignment(added, ['de.example.SortingTest#merge'], []));

        // Neither policy alone both had the whole class and keeps one of its test cases, so only the change as a whole shows the split.
        expect(comp.splitsClass()).toBe(true);
        comp['openReview']();
        expect(comp.reviewAssignmentsState()).toBe('loading');
        // After a reload, the commit the change is based on has a test case quick that the change also releases.
        structure.next({ testClasses: [{ qualifiedName: 'de.example.SortingTest', testClass: true, testCases: ['bubble', 'merge', 'quick'] }] });

        expect(comp.reviewAssignmentsState()).toBe('ready');
        expect(comp.reviewAssignments()).toContainEqual({ sign: '−', entry: 'de.example.SortingTest#bubble', file: 'SecurityPolicy.yaml' });
        expect(comp.reviewAssignments()).toContainEqual({ sign: '−', entry: 'de.example.SortingTest#quick', file: 'SecurityPolicy.yaml' });
        expect(comp.reviewAssignments()).not.toContainEqual(expect.objectContaining({ sign: '−', entry: 'de.example.SortingTest#merge' }));
    });

    it('commits a split only once the review lists the test cases it releases, and offers Retry when they cannot be loaded', () => {
        service.getTestStructure.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        service.commitPolicies.mockReturnValue(of({ editor: editor('b'.repeat(40)), config: configured() }));
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest#merge'], []));
        const javaFile = { path: 'test/de/example/SortingTest.java', before: '@Policy("SecurityPolicy.yaml")', after: '@Policy' };
        service.previewPolicies.mockReturnValue(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [javaFile] }));

        comp['openReview']();
        // The review shows the rewritten annotations of the test class as well.
        expect(comp.reviewDescriptions().get(javaFile.path)).toBe('artemisApp.programmingExercise.security.advanced.review.testAnnotations');
        expect(comp.reviewAssignmentsState()).toBe('failed');
        comp['commit']('Split');
        expect(service.commitPolicies).not.toHaveBeenCalled();

        const structure = new Subject<SecurityTestStructure>();
        service.getTestStructure.mockReturnValueOnce(structure);
        comp['requestStructure']();
        expect(comp.reviewAssignmentsState()).toBe('loading');
        comp['commit']('Split');
        expect(service.commitPolicies).not.toHaveBeenCalled();

        structure.next({ testClasses: [{ qualifiedName: 'de.example.SortingTest', testClass: true, testCases: ['merge', 'quick'] }] });
        expect(comp.reviewAssignmentsState()).toBe('ready');
        expect(comp.reviewAssignments()).toContainEqual({ sign: '−', entry: 'de.example.SortingTest#quick', file: 'SecurityPolicy.yaml' });
        comp['commit']('Split');
        expect(service.commitPolicies).toHaveBeenCalledOnce();
    });

    it('confirms a switch with the committed policies: their strict timeouts and the files deactivating deletes', () => {
        const strict: SecurityPolicyDocument = { ...DOCUMENT, testBehavior: { strictTimeout: { timeout: 5, timeUnit: 'SECONDS' } } };
        service.getPolicies.mockReturnValue(of(editor('a'.repeat(40), strict)));
        create(configured(), false);
        const policy = comp.policies()[0];

        // Neither removing the timeout nor renaming the policy is committed yet, so the switch keeps the timeout and deletes the committed file.
        comp['onPolicyChange']({ ...policy, name: 'Renamed', working: { ...policy.working!, testBehavior: {} } });
        comp['addPolicy']();

        expect(comp.strictTimeouts()).toBe(true);
        expect(comp.policyPaths()).toEqual(['SecurityPolicy.yaml']);
        comp['askDeletePolicy'](comp.policies()[0]);
        comp['deletePolicy']();
        expect(comp.policyPaths()).toEqual(['SecurityPolicy.yaml']);
    });

    it('names the committed policy files in the deactivation dialog also before the policies are loaded', () => {
        service.getPolicies.mockReturnValue(new Subject<SecurityPolicyEditor>());
        create({ ...configured(), policyFiles: [{ policyPath: 'SecurityPolicy.yaml' }, { policyPath: 'Stress.yaml' }] }, false);

        comp['onToggleRequested'](false);

        expect(comp.editor()).toBeUndefined();
        expect(comp.dialogMode()).toBe('deactivate');
        expect(comp.policyPaths()).toEqual(['SecurityPolicy.yaml', 'Stress.yaml']);
    });

    it('replaces the submitted drafts when the builds of a commit whose pushed policies could not be read are resumed', () => {
        create(configured(), false);
        comp['addPolicy']();
        // The draft takes a test class no policy uses yet, so the server accepts the commit and pushes it.
        const draft = comp.policies()[1];
        comp['onPolicyChange'](withAssignment(draft, ['de.example.StressTest'], []));
        const path = comp.paths().get(draft.id)!;
        const committed = editor('b'.repeat(40));
        const document = { ...DOCUMENT, policyPath: path, testClasses: ['de.example.StressTest'] };
        const pushed: SecurityPolicyEditor = {
            ...committed,
            policies: [...committed.policies!, { policyPath: path, content: '', document, assignedTests: ['de.example.StressTest'] }],
        };
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'error.securityFrameworkProcessingFailed' } })));
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['commit']('Add a policy');
        expect(comp.loadFailed()).toBe(true);

        service.commitPolicies.mockReturnValueOnce(of({ editor: pushed, config: configured() }));
        comp['retryCommit']();

        // The draft became the committed policy, so it is not kept as a new policy on top of it.
        expect(comp.policies().map((policy) => policy.file?.policyPath)).toEqual(['SecurityPolicy.yaml', path]);
        expect(comp.totalChanges()).toBe(0);
        expect(comp.banner()?.kind).toBe('committed');
    });

    it('keeps replacing the submitted drafts when a retry of the builds fails again before the pushed policies were read', () => {
        create(configured(), false);
        comp['addPolicy']();
        // The draft takes a test class no policy uses yet, so the server accepts the commit and pushes it.
        const draft = comp.policies()[1];
        comp['onPolicyChange'](withAssignment(draft, ['de.example.StressTest'], []));
        const path = comp.paths().get(draft.id)!;
        const committed = editor('b'.repeat(40));
        const document = { ...DOCUMENT, policyPath: path, testClasses: ['de.example.StressTest'] };
        const pushed: SecurityPolicyEditor = {
            ...committed,
            policies: [...committed.policies!, { policyPath: path, content: '', document, assignedTests: ['de.example.StressTest'] }],
        };
        const processingFailed = () => throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'error.securityFrameworkProcessingFailed' } }));
        service.commitPolicies.mockReturnValueOnce(processingFailed());
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['commit']('Add a policy');

        // The retry of the builds fails again, and so does the read of the pushed policies.
        service.commitPolicies.mockReturnValueOnce(processingFailed());
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['retryCommit']();
        expect(comp.loadFailed()).toBe(true);

        // Reading them now replaces the submitted drafts, as the original commit would have.
        service.getPolicies.mockReturnValueOnce(of(pushed));
        comp['retryLoad']();

        expect(comp.policies().map((policy) => policy.file?.policyPath)).toEqual(['SecurityPolicy.yaml', path]);
        expect(comp.totalChanges()).toBe(0);
    });

    it('names a new policy saved to the file of a policy staged for deletion as a problem before the review', () => {
        create(configured(), false);
        comp['askDeletePolicy'](comp.policies()[0]);
        comp['deletePolicy']();
        comp['addPolicy']();
        const added = comp.policies()[0];
        comp['onPolicyChange']({ ...withAssignment(added, ['de.example.SortingTest'], []), name: 'Security Policy' });

        // The commit would write and delete SecurityPolicy.yaml, which the server refuses.
        expect(comp.paths().get(added.id)).toBe('SecurityPolicy.yaml');
        expect(comp.problems()).toContain('artemisApp.programmingExercise.security.advanced.problem.sameFile');
    });

    it('names the problems of a policy without a name, with an invalid hidden period and of two policies in one file', () => {
        create(configured(), false);
        const policy = comp.policies()[0];
        comp['onPolicyChange']({
            ...policy,
            working: { ...policy.working!, testBehavior: { hiddenTests: { hiddenFrom: '2026-12-24 10:00 Europe/Berlin', hiddenUntil: '2026-12-24 09:00 Europe/Berlin' } } },
        });
        comp['addPolicy']();
        comp['onPolicyChange'](withAssignment({ ...comp.policies()[1], name: 'Security Policy' }, ['de.example.StressTest'], []));
        comp['addPolicy']();
        comp['onPolicyChange']({ ...comp.policies()[2], name: ' ' });

        expect(comp.problems()).toEqual([
            'artemisApp.programmingExercise.security.advanced.problem.hiddenPeriod',
            'artemisApp.programmingExercise.security.advanced.problem.nameRequired',
            'artemisApp.programmingExercise.security.advanced.problem.sameFile',
        ]);
    });

    it('previews the change for the review and reports a refused preview', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const preview = { verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [] };
        service.previewPolicies.mockReturnValue(of(preview));

        comp['openReview']();
        expect(comp.reviewPreview()).toEqual(preview);

        service.previewPolicies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { title: 'Invalid policy' } })));
        comp['openReview']();
        expect(comp.reviewOpen()).toBe(false);
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'failed', message: 'Invalid policy' }));
    });

    it('shows only the latest review of the change and previews a change made while it is open before committing it', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const first = new Subject<SecurityPolicyPreview>();
        const second = new Subject<SecurityPolicyPreview>();
        const third = { verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [{ path: 'SecurityPolicy.yaml', after: 'third' }] };
        service.previewPolicies.mockReturnValueOnce(first).mockReturnValueOnce(second).mockReturnValueOnce(of(third));
        comp['openReview']();
        comp.reviewOpen.set(false);
        comp['onPolicyChange']({ ...comp.policies()[0], working: withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.second' }]) });
        comp['openReview']();

        second.next({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [{ path: 'SecurityPolicy.yaml', after: 'second' }] });
        first.next({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, frameworkVersion: '2.2.1', files: [{ path: 'SecurityPolicy.yaml', after: 'first' }] });
        expect(comp.reviewPreview()?.files?.[0].after).toBe('second');

        // A change while the review is open, e.g. by a late import, is previewed before anything is committed.
        comp['onPolicyChange']({ ...comp.policies()[0], working: withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.third' }]) });
        comp['commit']('Update');
        expect(service.commitPolicies).not.toHaveBeenCalled();
        expect(comp.reviewPreview()?.files?.[0].after).toBe('third');
    });

    it('retries a failed commit with its message and names an unknown author of a conflict', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['commit']('Update');
        expect(comp.banner()).toEqual({ kind: 'failed', message: 'artemisApp.programmingExercise.security.advanced.banner.failedGeneric', commitMessage: 'Update' });

        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409 })));
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['retryCommit']();
        expect(lastChange().commitMessage).toBe('Update');
        expect(comp.banner()).toEqual({ kind: 'conflict' });
        comp['dismissBanner']();
        expect(comp.banner()).toBeUndefined();
    });

    it('turns dependency scanning off', () => {
        service.getPolicies.mockReturnValue(of(editor('a'.repeat(40), { ...DOCUMENT, languageConfiguration: 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION' })));
        create(configured(), false);
        service.commitPolicies.mockReturnValue(of({ editor: editor('e'.repeat(40)), config: configured() }));

        expect(comp.scanDependencies()).toBe(true);
        comp['onScanRequested'](false);
        comp['onDialogConfirmed'](undefined);

        expect(lastChange().policies[0].document.languageConfiguration).toBe('JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ');
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'committed' }));
    });

    it('shows a load error and loads again on retry', () => {
        service.getPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        create();

        expect(comp.loadFailed()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="security-policies-load-error"]')).not.toBeNull();
        comp['retryLoad']();
        expect(comp.loadFailed()).toBe(false);
        expect(comp.policies()).toHaveLength(1);
    });

    it('requests the test structure and the template files once, and again after a failure', () => {
        create(configured(), false);
        service.getTestStructure.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['requestStructure']();
        comp['requestStructure']();
        comp['requestStructure']();
        expect(service.getTestStructure).toHaveBeenCalledTimes(2);

        // A failed listing is no empty one: the files stay unknown, so Browse… reports the error and asks for them again.
        service.getTemplateFiles.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['requestTemplateFiles']();
        expect(comp.templateFiles()).toBeUndefined();
        expect(comp.templateFilesFailed()).toBe(true);

        service.getTemplateFiles.mockReturnValueOnce(of({ paths: ['a.txt'], truncated: true }));
        comp['requestTemplateFiles']();
        comp['requestTemplateFiles']();
        expect(service.getTemplateFiles).toHaveBeenCalledTimes(2);
        expect(comp.templateFilesFailed()).toBe(false);
        expect(comp.templateFiles()).toEqual(['a.txt']);
        expect(comp.templateFilesTruncated()).toBe(true);
    });

    it('renders the YAML of the unsaved changes and reports an import the editor cannot read', () => {
        create(configured(), false);
        comp['openYaml'](comp.policies()[0]);
        service.previewPolicies.mockReturnValue(of({ verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE, files: [{ path: 'SecurityPolicy.yaml', after: 'rendered' }] }));

        comp['renderYamlUnsaved']();
        expect(comp.yamlUnsaved()).toBe('rendered');

        service.importPolicy.mockReturnValue(of({ policyPath: 'SecurityPolicy.yaml', readOnlyReason: SecurityPolicyReadOnlyReason.YAML_ERROR, errorLine: 3 }));
        comp['importYaml']({ content: 'broken: [', policyPath: 'SecurityPolicy.yaml' });
        expect(comp.importNotice()).toEqual({ severity: 'error', text: 'artemisApp.programmingExercise.security.advanced.policy.readOnlyReason.YAML_ERROR' });
    });

    it('shows only the latest rendering of the unsaved YAML', () => {
        create(configured(), false);
        comp['openYaml'](comp.policies()[0]);
        const first = new Subject<SecurityPolicyPreview>();
        const second = new Subject<SecurityPolicyPreview>();
        service.previewPolicies.mockReturnValueOnce(first).mockReturnValueOnce(second);

        comp['renderYamlUnsaved']();
        comp['renderYamlUnsaved']();
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

        fixture.componentRef.setInput('config', configured());
        fixture.detectChanges();
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        const committed = new Subject<{ editor: SecurityPolicyEditor; config: SecurityFrameworkConfig }>();
        service.commitPolicies.mockReturnValue(committed);
        comp['commit']('Update');
        expect(comp.cardState()).toBe('committing');
        committed.next({ editor: editor('b'.repeat(40)), config: configured() });
        expect(comp.cardState()).toBe('configured');
    });

    it('warns what deleting a committed policy changes', () => {
        create(configured(), false);
        comp['askDeletePolicy'](comp.policies()[0]);

        expect(comp.deleteWarning()).toEqual({
            key: 'artemisApp.programmingExercise.security.advanced.deletePolicy.committed',
            params: { file: 'SecurityPolicy.yaml', count: 1 },
        });
    });

    it('retries a failed dependency scanning commit with the same request and keeps the unsaved changes', () => {
        create(configured(), false);
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['onScanRequested'](true);
        comp['onDialogConfirmed'](undefined);
        const failed = lastChange();

        // While the exercise is saved, Retry writes nothing; it is available again once the lock clears.
        fixture.componentRef.setInput('locked', true);
        comp['retryCommit']();
        expect(service.commitPolicies).toHaveBeenCalledOnce();
        fixture.componentRef.setInput('locked', false);

        service.commitPolicies.mockReturnValueOnce(of({ editor: editor('f'.repeat(40)), config: configured() }));
        comp['retryCommit']();

        expect(service.commitPolicies).toHaveBeenCalledTimes(2);
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

        comp['addPolicy']();

        expect(comp.policies()[0].working).toEqual(
            expect.objectContaining({ languageConfiguration: 'JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ', supervisedPackage: 'de.example', testClasses: [] }),
        );
    });

    it('keeps the changes to a policy someone deleted meanwhile as a new policy and says so', () => {
        create(configured(), false);
        const changed = withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.apache.commons' }]);
        comp['onPolicyChange']({ ...comp.policies()[0], working: changed });
        service.getPolicies.mockReturnValue(of({ ...editor('d'.repeat(40)), policies: [] }));

        comp['reload'](true);

        expect(comp.policies()).toHaveLength(1);
        expect(comp.policies()[0].file).toBeUndefined();
        expect(comp.policies()[0].working?.packages).toEqual([{ packageName: 'org.apache.commons' }]);
        expect(comp.policies()[0].tests).toEqual(['de.example.SortingTest']);
        expect(comp.banner()).toEqual({ kind: 'orphaned', files: 'SecurityPolicy.yaml' });
    });

    describe('a policy kept after someone deleted its file', () => {
        const scanning = 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION';
        const second = { ...DOCUMENT, policyPath: 'Second.yaml' };
        const withSecondOnly = (hash: string, languageConfiguration = DOCUMENT.languageConfiguration!): SecurityPolicyEditor => ({
            ...editor(hash, { ...second, languageConfiguration }),
            policies: [{ policyPath: 'Second.yaml', document: { ...second, languageConfiguration }, assignedTests: ['de.example.StressTest'] }],
        });
        const recovered = () => comp.policies().find((policy) => !policy.file);

        beforeEach(() => {
            service.getPolicies.mockReturnValue(
                of({
                    ...editor(),
                    policies: [...editor().policies!, { policyPath: 'Second.yaml', document: second, assignedTests: ['de.example.StressTest'] }],
                }),
            );
            create(configured(), false);
        });

        it('keeps a configuration other than the committed one through a later reload', () => {
            const changed = withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.apache.commons' }]);
            comp['onPolicyChange']({ ...comp.policies()[0], working: { ...changed, languageConfiguration: scanning } });
            service.getPolicies.mockReturnValue(of(withSecondOnly('d'.repeat(40))));
            comp['reload'](true);
            expect(recovered()?.working?.languageConfiguration).toBe(scanning);

            // Another commit conflicts and reloads the policies again; the remaining policy still uses the other configuration.
            service.getPolicies.mockReturnValue(of(withSecondOnly('e'.repeat(40))));
            comp['reload']();

            expect(recovered()?.working?.languageConfiguration).toBe(scanning);
        });

        it('lets the committed configuration follow a later switch of dependency scanning', () => {
            comp['onPolicyChange']({ ...comp.policies()[0], working: withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.apache.commons' }]) });
            service.getPolicies.mockReturnValue(of(withSecondOnly('d'.repeat(40))));
            comp['reload'](true);
            expect(recovered()?.working?.languageConfiguration).toBe(DOCUMENT.languageConfiguration);

            service.getPolicies.mockReturnValue(of(withSecondOnly('e'.repeat(40), scanning)));
            comp['reload']();

            expect(recovered()?.working?.languageConfiguration).toBe(scanning);
        });

        it('keeps a chosen configuration that the commit deleting the policy also gave the remaining one', () => {
            const changed = withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.apache.commons' }]);
            comp['onPolicyChange']({ ...comp.policies()[0], working: { ...changed, languageConfiguration: scanning } });
            // The same commit deletes the edited policy and switches the remaining one to the configuration the instructor chose.
            service.getPolicies.mockReturnValue(of(withSecondOnly('d'.repeat(40), scanning)));
            comp['reload'](true);
            expect(recovered()?.ownLanguageConfiguration).toBe(true);

            service.getPolicies.mockReturnValue(of(withSecondOnly('e'.repeat(40))));
            comp['reload']();

            expect(recovered()?.working?.languageConfiguration).toBe(scanning);
        });
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
        comp['onPolicyChange'](withAssignment(comp.policies()[0], ['de.example.SortingTest', 'de.example.StressTest'], []));
        service.previewPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['openReview']();
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'failed', retry: 'review' }));

        comp['retryCommit']();

        expect(service.commitPolicies).not.toHaveBeenCalled();
        expect(comp.reviewOpen()).toBe(true);
    });

    it('repeats a failed commit on Retry only while its changes are unchanged, and otherwise shows the review again', () => {
        create(configured(), false);
        const policy = comp.policies()[0];
        comp['onPolicyChange']({ ...policy, working: withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]) });
        service.commitPolicies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['commit']('Grant commons');
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'failed', commitMessage: 'Grant commons' }));

        comp['retryCommit']();
        expect(service.commitPolicies).toHaveBeenCalledTimes(2);
        expect(service.commitPolicies.mock.calls[1][1]).toEqual(service.commitPolicies.mock.calls[0][1]);

        // Another version changes the build file, whose diff the failed review never showed.
        comp['onVersionChange']('2.3.0');
        comp['retryCommit']();

        expect(service.commitPolicies).toHaveBeenCalledTimes(2);
        expect(comp.reviewOpen()).toBe(true);
        expect(service.previewPolicies).toHaveBeenLastCalledWith(42, expect.objectContaining({ frameworkVersion: '2.3.0' }));
    });

    it('keeps resuming the builds of a pushed change after a later commit fails before it is pushed', () => {
        create(configured(), false);
        const policy = comp.policies()[0];
        const granted = withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]);
        comp['onPolicyChange']({ ...policy, working: granted });
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'error.securityFrameworkProcessingFailed' } })));
        service.getPolicies.mockReturnValue(of(editor('b'.repeat(40), granted)));
        comp['commit']('Grant commons');
        expect(comp.banner()).toEqual({ kind: 'processingFailed', hash: 'bbbbbbb' });

        comp['onPolicyChange']({ ...comp.policies()[0], working: withRows(granted, 'packages', [{ packageName: 'org.apache.commons' }, { packageName: 'org.junit' }]) });
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['commit']('Grant junit');
        expect(comp.banner()?.kind).toBe('failed');

        comp['discard']();
        expect(comp.banner()).toEqual({ kind: 'processingFailed', hash: 'bbbbbbb' });

        service.commitPolicies.mockReturnValueOnce(of({ editor: editor('b'.repeat(40), granted), config: configured() }));
        comp['retryCommit']();
        expect(service.commitPolicies).toHaveBeenCalledTimes(3);
        expect(service.commitPolicies.mock.calls[2][1]).toEqual(service.commitPolicies.mock.calls[0][1]);
        expect(comp.banner()).toEqual(expect.objectContaining({ kind: 'committed', hash: 'bbbbbbb' }));

        // The builds ran, so a later failure no longer offers to resume them.
        comp['onPolicyChange']({ ...comp.policies()[0], working: withRows(granted, 'packages', []) });
        service.commitPolicies.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['commit']('Revoke commons');
        comp['discard']();
        expect(comp.banner()?.kind).toBe('info');
    });

    it('shows the requested scanning state until the dialog is cancelled or the commit is done', () => {
        create(configured(), false);

        comp['onScanRequested'](true);
        expect(comp.shownScanDependencies()).toBe(true);
        comp['onDialogCancelled']();
        expect(comp.shownScanDependencies()).toBe(false);

        service.commitPolicies.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp['onScanRequested'](true);
        comp['onDialogConfirmed'](undefined);
        expect(comp.shownScanDependencies()).toBe(false);
    });

    it('keeps the changes to a policy that can no longer be read as a new policy with a name of its own', () => {
        create(configured(), false);
        comp['onPolicyChange']({ ...comp.policies()[0], working: withRows(comp.policies()[0].working!, 'packages', [{ packageName: 'org.apache.commons' }]) });
        service.getPolicies.mockReturnValue(
            of({
                ...editor('d'.repeat(40)),
                policies: [{ policyPath: 'SecurityPolicy.yaml', readOnlyReason: SecurityPolicyReadOnlyReason.YAML_ERROR, errorLine: 3, assignedTests: ['de.example.SortingTest'] }],
            }),
        );

        comp['reload'](true);

        expect(comp.policies().map((policy) => [policy.name, !!policy.file, !!policy.working])).toEqual([
            ['Security Policy', true, false],
            ['Security Policy 2', false, true],
        ]);
        expect(comp.policies()[1].working?.packages).toEqual([{ packageName: 'org.apache.commons' }]);
        expect(comp.banner()).toEqual({ kind: 'orphaned', files: 'SecurityPolicy.yaml' });
    });

    it('discards every unsaved change', () => {
        create(configured(), false);
        comp['addPolicy']();
        comp['onVersionChange']('2.3.0');

        comp['discard']();

        expect(comp.policies()).toHaveLength(1);
        expect(comp.stagedVersion()).toBeUndefined();
        expect(comp.banner()?.kind).toBe('info');
    });

    it('drops an import that answers after the unsaved changes were discarded', () => {
        create(configured(), false);
        const imported = new Subject<{ policyPath: string; document: SecurityPolicyDocument }>();
        service.importPolicy.mockReturnValueOnce(imported);
        comp['openYaml'](comp.policies()[0]);
        comp['importYaml']({ content: 'content', policyPath: 'SecurityPolicy.yaml' });

        comp['discard']();
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
        comp['openYaml'](comp.policies()[0]);
        comp['importYaml']({ content: 'before', policyPath: 'SecurityPolicy.yaml' });
        comp['discard']();
        comp['importYaml']({ content: 'after', policyPath: 'SecurityPolicy.yaml' });

        latest.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.after' } });
        discarded.next({ policyPath: 'SecurityPolicy.yaml', document: { ...DOCUMENT, supervisedPackage: 'de.before' } });

        expect(comp.policies()[0].working?.supervisedPackage).toBe('de.after');
    });
});
