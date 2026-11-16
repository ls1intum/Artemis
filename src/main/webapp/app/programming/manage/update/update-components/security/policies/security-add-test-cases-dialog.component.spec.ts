import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityTestStructure } from 'app/programming/shared/entities/security-policy.model';
import { SecurityAddTestCasesDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-add-test-cases-dialog.component';

const STRUCTURE: SecurityTestStructure = {
    testClasses: [
        { qualifiedName: 'de.a.SortingTest', testCases: ['bubble', 'merge'], testClass: true },
        { qualifiedName: 'de.a.MathTest', testCases: ['add', 'divide', 'unsafe'], testClass: true, fixedCases: ['unsafe'] },
        { qualifiedName: 'de.b.StressTest', testCases: ['run'], testClass: true, switchedOff: true },
        { qualifiedName: 'de.b.Helper', testCases: [], testClass: false },
    ],
};

type Handlers = { toggleKeys(keys: readonly string[]): void; selectAllUnconnected(): void; apply(): void };

describe('SecurityAddTestCasesDialogComponent', () => {
    let fixture: ComponentFixture<SecurityAddTestCasesDialogComponent>;
    let comp: SecurityAddTestCasesDialogComponent;
    let handlers: Handlers;
    let applied: string[][];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(SecurityAddTestCasesDialogComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        applied = [];
        comp.applied.subscribe((tests) => applied.push(tests));
        fixture.componentRef.setInput('structure', STRUCTURE);
        fixture.componentRef.setInput('ownTests', ['de.a.SortingTest', 'de.gone.OldTest']);
        fixture.componentRef.setInput('otherTests', new Map([['de.a.MathTest#divide', 'Stress']]));
        comp.visible.set(true);
        fixture.detectChanges();
    });

    it('lists the test classes by package and locks the tests of other policies and of switched off classes', () => {
        const packages = comp.packages();

        expect(packages.map((group) => group.packageName)).toEqual(['de.a', 'de.b']);
        expect(packages[0].classes.map((node) => node.simpleName)).toEqual(['SortingTest', 'MathTest']);
        expect(packages[0].classes[1].cases[1].coveredBy).toBe('Stress');
        expect(packages[0].available).toEqual(['de.a.SortingTest#bubble', 'de.a.SortingTest#merge', 'de.a.MathTest#add']);
        expect(packages[1].classes[0].switchedOff).toBe(true);
        expect(packages[1].available).toEqual([]);
        // A whole class stands for each of its cases.
        expect([...comp.selection()]).toEqual(['de.a.SortingTest#bubble', 'de.a.SortingTest#merge', 'de.gone.OldTest']);
        expect(comp.unconnected()).toEqual(['de.a.MathTest#add']);
    });

    it('assigns a fully selected class as a whole and keeps the entries of unknown classes', () => {
        handlers.toggleKeys(['de.a.SortingTest#merge']);
        handlers.selectAllUnconnected();

        expect(comp.addedCount()).toBe(1);
        expect(comp.removedCount()).toBe(1);
        expect(comp.changeOf('de.a.MathTest#add')).toBe('added');
        expect(comp.changeOf('de.a.SortingTest#merge')).toBe('removed');
        handlers.apply();

        expect(applied).toEqual([['de.gone.OldTest', 'de.a.SortingTest#bubble', 'de.a.MathTest#add']]);
        expect(comp.visible()).toBe(false);
    });

    it('sets up the selection once the structure of the first opening arrives', () => {
        const delayed = TestBed.createComponent(SecurityAddTestCasesDialogComponent);
        const dialog = delayed.componentInstance;
        delayed.componentRef.setInput('ownTests', ['de.a.SortingTest']);
        dialog.visible.set(true);
        delayed.detectChanges();
        expect([...dialog.selection()]).toEqual([]);

        delayed.componentRef.setInput('structure', STRUCTURE);
        delayed.detectChanges();

        expect([...dialog.selection()]).toEqual(['de.a.SortingTest#bubble', 'de.a.SortingTest#merge']);
        expect(dialog.addedCount()).toBe(0);
        expect(dialog.removedCount()).toBe(0);
    });

    it('locks a test case whose own annotation switches the sandbox off and assigns the others one by one', () => {
        const math = comp.packages()[0].classes[1];
        expect(math.cases.map((testCase) => [testCase.name, testCase.switchedOff])).toEqual([
            ['add', false],
            ['divide', false],
            ['unsafe', true],
        ]);
        expect(math.available).toEqual(['de.a.MathTest#add']);

        handlers.toggleKeys(math.available);
        handlers.apply();

        expect(applied[0]).toContain('de.a.MathTest#add');
        expect(applied[0]).not.toContain('de.a.MathTest');
    });

    it('selects only the test cases a search shows and keeps assigned entries the tree does not show', () => {
        fixture.componentRef.setInput('ownTests', ['de.a.SortingTest#bubble', 'de.a.SortingTest#gone']);
        comp.visible.set(false);
        fixture.detectChanges();
        comp.visible.set(true);
        fixture.detectChanges();
        comp.search.set('add');

        const math = comp.view()[0].classes.find((node) => node.simpleName === 'MathTest')!;
        expect(math.available).toEqual(['de.a.MathTest#add']);
        handlers.toggleKeys(math.available);
        handlers.apply();

        expect(applied[0]).toEqual(['de.a.SortingTest#gone', 'de.a.SortingTest#bubble', 'de.a.MathTest#add']);
    });

    it('drops the hidden entries of a class it assigns as a whole and does not select a switched off test case', () => {
        fixture.componentRef.setInput('ownTests', ['de.a.SortingTest#bubble', 'de.a.SortingTest#gone', 'de.a.MathTest#unsafe']);
        comp.visible.set(false);
        fixture.detectChanges();
        comp.visible.set(true);
        fixture.detectChanges();

        expect([...comp.selection()]).toEqual(['de.a.SortingTest#bubble', 'de.a.SortingTest#gone']);
        handlers.toggleKeys(['de.a.SortingTest#merge']);
        handlers.apply();

        expect(applied[0]).toEqual(['de.a.SortingTest']);
    });

    it('checks a whole group and unchecks it when every case is selected', () => {
        const keys = comp.packages()[0].available;
        handlers.toggleKeys(keys);
        expect(comp.isAllSelected(keys)).toBe(true);
        handlers.toggleKeys(keys);
        expect(comp.selectedCount(keys)).toBe(0);
    });

    it('filters by package, class and test case and opens what matches', () => {
        comp.search.set('divi');
        expect(comp.filteredPackages().map((group) => group.classes.map((node) => node.cases.map((testCase) => testCase.name)))).toEqual([[['divide']]]);
        expect(comp.isOpen('de.a.MathTest', false)).toBe(true);

        comp.search.set('de.b');
        expect(comp.filteredPackages()).toHaveLength(1);
        expect(comp.filteredPackages()[0].classes[0].cases).toHaveLength(1);

        comp.search.set('');
        expect(comp.isOpen('de.a', true)).toBe(true);
        expect(comp.isOpen('de.a.MathTest', false)).toBe(false);
    });
});
