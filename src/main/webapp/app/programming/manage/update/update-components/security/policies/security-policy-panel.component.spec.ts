import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityPolicyFile, SecurityPolicyReadOnlyReason } from 'app/programming/shared/entities/security-policy.model';
import { EditorPolicy, editorPolicyOf, summarizeChanges } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';
import { SecurityPolicyPanelComponent } from 'app/programming/manage/update/update-components/security/policies/security-policy-panel.component';

const FILE: SecurityPolicyFile = {
    policyPath: 'test/SecurityPolicy.yaml',
    content: 'thisPolicyFileCompliesToThePolicyVersion: 1\n',
    document: { policyPath: 'test/SecurityPolicy.yaml', supervisedPackage: 'de.example', testClasses: ['de.example.SortingTest'] },
    assignedTests: ['de.example.SortingTest'],
};

type Handlers = {
    onNameChange(name: string): void;
    onPresetsChange(values: unknown[]): void;
    onBehaviorChange(behavior: unknown): void;
    onAssignmentChange(change: { tests: string[]; extraClasses: string[] }): void;
    onWholeClassKept(testClass: string): void;
    setBoxCollapsed(box: string, collapsed: boolean): void;
};

describe('SecurityPolicyPanelComponent', () => {
    let fixture: ComponentFixture<SecurityPolicyPanelComponent>;
    let comp: SecurityPolicyPanelComponent;
    let handlers: Handlers;
    let changes: EditorPolicy[];

    function create(policy: EditorPolicy): void {
        fixture = TestBed.createComponent(SecurityPolicyPanelComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        changes = [];
        comp.policyChange.subscribe((changed) => changes.push(changed));
        fixture.componentRef.setInput('policy', policy);
        fixture.componentRef.setInput('summary', summarizeChanges(policy));
        fixture.componentRef.setInput('policyPath', policy.file?.policyPath ?? '');
        fixture.detectChanges();
    }

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
    });

    it('renders the boxes of an editable policy and emits its changes', () => {
        create(editorPolicyOf(FILE));
        comp.collapsed.set(false);
        for (const box of ['resources', 'behaviour', 'tests']) {
            handlers.setBoxCollapsed(box, false);
        }
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        expect(element.querySelector('[data-testid="security-policy-file"]')?.textContent).toContain('SecurityPolicy.yaml');
        for (const id of ['security-policy-resources', 'security-policy-behaviour', 'security-policy-tests', 'security-policy-supervised']) {
            expect(element.querySelector(`[data-testid="${id}"]`)).not.toBeNull();
        }
        expect(comp.idPrefix()).toMatch(/^securityPolicy\d+$/);
        expect(TestBed.createComponent(SecurityPolicyPanelComponent).componentInstance.idPrefix()).not.toBe(comp.idPrefix());

        handlers.onNameChange('Stress');
        handlers.onPresetsChange(['A', 'B']);
        handlers.onBehaviorChange({ strictTimeout: { timeout: 1 } });
        handlers.onAssignmentChange({ tests: ['de.example.SortingTest', 'de.example.StressTest'], extraClasses: [] });

        expect(changes[0].name).toBe('Stress');
        expect(changes[1].working?.preset).toBe('B');
        expect(changes[2].working?.testBehavior).toEqual({ strictTimeout: { timeout: 1 } });
        expect(changes[3].working?.testClasses).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
    });

    it('ends a conflict of an assignment whose whole class the instructor keeps', () => {
        const conflict = { testClass: 'de.example.SortingTest', removedTestCases: ['de.example.SortingTest#first'] };
        create({ ...editorPolicyOf(FILE), assignmentConflicts: [conflict, { testClass: 'de.example.StressTest', removedTestCases: ['de.example.StressTest#run'] }] });

        handlers.onWholeClassKept('de.example.StressTest');

        expect(changes[0].assignmentConflicts).toEqual([conflict]);
        expect(changes[0].tests).toEqual(comp.policy().tests);
    });

    it('offers the YAML of a policy only once the policy is expanded', () => {
        create(editorPolicyOf(FILE));
        const viewYaml = () => fixture.nativeElement.querySelector('[data-testid="security-policy-view-yaml"]');
        expect(comp.collapsed()).toBe(true);
        expect(viewYaml()).toBeNull();

        comp.collapsed.set(false);
        fixture.detectChanges();
        let opened = 0;
        comp.viewYaml.subscribe(() => opened++);
        viewYaml().click();
        expect(opened).toBe(1);
    });

    it('associates every label with its control', () => {
        create(editorPolicyOf(FILE));
        comp.collapsed.set(false);
        for (const box of ['resources', 'behaviour', 'tests']) {
            handlers.setBoxCollapsed(box, false);
        }
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        const labels = Array.from(element.querySelectorAll<HTMLLabelElement>('label[for]'));
        // Each label points to a control a click and a screen reader can reach, not only to an element with that id.
        for (const label of labels) {
            expect(label.textContent?.trim()).not.toBe('');
            expect(label.control).not.toBeNull();
            expect(label.control?.id).toBe(label.htmlFor);
        }
        const name = element.querySelector('[data-testid="security-policy-name"]');
        expect(labels.find((label) => label.htmlFor === `${comp.idPrefix()}Name`)?.control).toBe(name);
        // The trigger of the presets is a combobox that no label can name, so it carries the name of the field itself.
        const presets = element.querySelector<HTMLElement>(`[id="${comp.idPrefix()}Presets"]`);
        expect(presets?.getAttribute('role')).toBe('combobox');
        expect(presets?.getAttribute('aria-label')).toBe('artemisApp.programmingExercise.security.advanced.policy.presets');
        expect(labels.some((label) => label.htmlFor === `${comp.idPrefix()}Presets`)).toBe(false);
    });

    it('shows a policy it cannot edit as read-only with the reason', () => {
        create(editorPolicyOf({ ...FILE, document: undefined, readOnlyReason: SecurityPolicyReadOnlyReason.YAML_ERROR, errorLine: 3 }));
        comp.collapsed.set(false);
        fixture.detectChanges();

        expect(comp.readOnly()).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="security-policy-read-only"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="security-policy-resources"]')).toBeNull();
    });
});
