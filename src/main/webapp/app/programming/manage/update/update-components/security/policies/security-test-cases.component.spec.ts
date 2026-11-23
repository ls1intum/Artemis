import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { EditorPolicy } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';
import { SecurityTestCasesComponent } from 'app/programming/manage/update/update-components/security/policies/security-test-cases.component';

const PREFIX = 'artemisApp.programmingExercise.security.advanced.tests.remove.';

const policy = (tests: string[], extraClasses: string[] = []): EditorPolicy => ({
    id: 'SecurityPolicy.yaml',
    name: 'Security Policy',
    base: { policyPath: 'SecurityPolicy.yaml' },
    working: { policyPath: 'SecurityPolicy.yaml' },
    tests,
    baseTests: ['de.a.SortingTest', 'de.a.MathTest#add'],
    extraClasses,
    baseExtraClasses: ['de.a.Helper'],
});

type Handlers = { askRemove(kind: 'test' | 'class', value: string): void; onRemoveConfirmed(): void; undoTest(entry: string): void; undoClass(name: string): void };

describe('SecurityTestCasesComponent', () => {
    let fixture: ComponentFixture<SecurityTestCasesComponent>;
    let comp: SecurityTestCasesComponent;
    let handlers: Handlers;
    let changes: { tests: string[]; extraClasses: string[] }[];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(SecurityTestCasesComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        changes = [];
        comp.assignmentChange.subscribe((change) => changes.push(change));
        fixture.componentRef.setInput('policy', policy(['de.a.SortingTest', 'de.b.StressTest#run']));
        fixture.detectChanges();
    });

    it('groups the assigned tests by package and class and tags new and removed entries', () => {
        const packages = comp.assignedPackages();

        expect(packages.map((group) => group.packageName)).toEqual(['de.a', 'de.b']);
        expect(packages[0].classes.map((node) => [node.simpleName, node.whole, node.testCases.map((testCase) => testCase.state)])).toEqual([
            ['MathTest', undefined, ['removed']],
            ['SortingTest', 'committed', []],
        ]);
        expect(packages[1].classes[0].testCases).toEqual([{ name: 'run', entry: 'de.b.StressTest#run', state: 'new' }]);
        expect(comp.assignedClassCount()).toBe(3);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="security-test-cases-class"]')).toHaveLength(1);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="security-test-cases-case"]')).toHaveLength(2);
    });

    it('declares the classes of the tests and the extra classes, and compares them with the committed ones', () => {
        // MathTest lost its only committed test, and StressTest got its first one.
        expect(comp.declaredClasses().map((declared) => [declared.simpleName, declared.fromTests, declared.state])).toEqual([
            ['Helper', false, 'removed'],
            ['MathTest', false, 'removed'],
            ['SortingTest', true, 'committed'],
            ['StressTest', true, 'new'],
        ]);
        expect(comp.declaredCount()).toBe(2);
        // Like the assigned test cases, the declared classes are a tree of packages.
        expect(comp.declaredPackages().map((group) => [group.packageName, group.classes.map((declared) => declared.simpleName)])).toEqual([
            ['de.a', ['Helper', 'MathTest', 'SortingTest']],
            ['de.b', ['StressTest']],
        ]);
        const rows: HTMLElement[] = [...fixture.nativeElement.querySelectorAll('[data-testid="security-test-classes-row"]')];
        expect(rows.map((row) => row.closest('ul')?.parentElement?.querySelector('.sp-strong')?.textContent?.trim())).toEqual(['de.a', 'de.a', 'de.a', 'de.b']);
    });

    it('asks about the whole class when the assignment of a class is removed', () => {
        handlers.askRemove('test', 'de.a.SortingTest');

        expect(comp.removeHeader().key).toBe(PREFIX + 'wholeClassTitle');
        expect(comp.removeQuestion()).toEqual({ key: PREFIX + 'wholeClassQuestion', params: { policy: comp.policy().name } });
    });

    it('removes a test case after confirming and warns that its class is no longer declared', () => {
        handlers.askRemove('test', 'de.b.StressTest#run');

        expect(comp.removeHeader().key).toBe(PREFIX + 'caseTitle');
        expect(comp.removeQuestion().key).toBe(PREFIX + 'caseQuestion');
        expect(comp.removeWarning()).toEqual({ key: PREFIX + 'caseWarningUndeclared', params: { testClass: 'StressTest' } });
        handlers.onRemoveConfirmed();
        expect(changes.at(-1)).toEqual({ tests: ['de.a.SortingTest'], extraClasses: [] });
    });

    it('removes a declared class and restores removed entries', () => {
        fixture.componentRef.setInput('policy', policy(['de.a.SortingTest'], ['de.a.Helper']));
        handlers.askRemove('class', 'de.a.Helper');
        expect(comp.removeHeader().key).toBe(PREFIX + 'classTitle');
        expect(comp.removeQuestion().key).toBe(PREFIX + 'classQuestion');
        expect(comp.removeWarning()).toEqual({ key: PREFIX + 'classWarning', params: { testClass: 'Helper' } });
        handlers.onRemoveConfirmed();
        expect(changes.at(-1)).toEqual({ tests: ['de.a.SortingTest'], extraClasses: [] });

        handlers.undoTest('de.a.MathTest#add');
        expect(changes.at(-1)?.tests).toEqual(['de.a.SortingTest', 'de.a.MathTest#add']);
        handlers.undoClass('de.a.Other');
        expect(changes.at(-1)?.extraClasses).toEqual(['de.a.Helper', 'de.a.Other']);
    });

    it('restores a whole class in place of its test cases and does not list test cases it covers as removed', () => {
        fixture.componentRef.setInput('policy', { ...policy(['de.a.SortingTest#bubble', 'de.a.MathTest']), baseTests: ['de.a.SortingTest', 'de.a.MathTest#add'] });

        expect(comp.assignedPackages()[0].classes.map((node) => [node.simpleName, node.whole, node.testCases.map((testCase) => testCase.state)])).toEqual([
            ['MathTest', 'new', []],
            ['SortingTest', 'removed', ['new']],
        ]);
        handlers.undoTest('de.a.SortingTest');
        expect(changes.at(-1)?.tests).toEqual(['de.a.MathTest', 'de.a.SortingTest']);
    });

    it('keeps the undo of an entry that another policy now uses disabled and names that policy', () => {
        fixture.componentRef.setInput('policy', { ...policy(['de.a.SortingTest']), baseTests: ['de.a.SortingTest', 'de.a.MathTest#add', 'de.c.WholeTest', 'de.d.FreeTest#run'] });
        fixture.componentRef.setInput(
            'otherTests',
            new Map([
                ['de.a.MathTest', 'Other Policy'],
                ['de.c.WholeTest#one', 'Third Policy'],
            ]),
        );
        fixture.detectChanges();

        expect([...comp.undoOwners()]).toEqual([
            ['de.a.MathTest#add', 'Other Policy'],
            ['de.c.WholeTest', 'Third Policy'],
        ]);
        const buttons: HTMLButtonElement[] = [...fixture.nativeElement.querySelectorAll('[data-testid="security-test-cases-undo"]')];
        expect(buttons.map((button) => button.disabled)).toEqual([true, true, false]);
        expect(buttons[0].getAttribute('aria-describedby')).toBe('security-undo-owner-de.a.MathTest#add');
        expect(fixture.nativeElement.querySelectorAll('[data-testid="security-test-cases-undo-owner"]')).toHaveLength(2);

        handlers.undoTest('de.a.MathTest#add');
        handlers.undoTest('de.c.WholeTest');
        expect(changes).toEqual([]);
        handlers.undoTest('de.d.FreeTest#run');
        expect(changes).toEqual([{ tests: ['de.a.SortingTest', 'de.d.FreeTest#run'], extraClasses: [] }]);
    });

    it('shows that no test uses a policy without assigned tests', () => {
        fixture.componentRef.setInput('policy', { ...policy([]), baseTests: [] });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="security-test-cases-empty"]')).not.toBeNull();
    });
});
