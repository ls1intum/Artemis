import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityTestStructure } from 'app/programming/shared/entities/security-policy.model';
import { SecurityAddTestCasesDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-add-test-cases-dialog.component';

const STRUCTURE: SecurityTestStructure = {
    testClasses: [
        { qualifiedName: 'de.a.SortingTest', testCases: ['bubble', 'merge'], testClass: true },
        { qualifiedName: 'de.a.MathTest', testCases: ['add', 'divide'], testClass: true },
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
