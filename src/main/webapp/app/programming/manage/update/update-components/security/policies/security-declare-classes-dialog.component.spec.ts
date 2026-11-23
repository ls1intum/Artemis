import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityDeclareClassesDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-declare-classes-dialog.component';

type Option = { qualifiedName: string };
type Group = { packageName: string; selectable: string[]; allSelected: boolean };
type Handlers = { toggle(option: Option): void; togglePackage(group: Group): void; toggleOpen(packageName: string): void; apply(): void };

describe('SecurityDeclareClassesDialogComponent', () => {
    let fixture: ComponentFixture<SecurityDeclareClassesDialogComponent>;
    let comp: SecurityDeclareClassesDialogComponent;
    let handlers: Handlers;
    let applied: string[][];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(SecurityDeclareClassesDialogComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        applied = [];
        comp.applied.subscribe((classes) => applied.push(classes));
        fixture.componentRef.setInput('structure', {
            testClasses: [
                { qualifiedName: 'de.a.SortingTest', testCases: ['bubble'], testClass: true },
                { qualifiedName: 'de.a.Helper', testClass: false },
            ],
        });
        fixture.componentRef.setInput('classesOfTests', ['de.a.SortingTest']);
        fixture.componentRef.setInput('extraClasses', ['de.gone.Util']);
        comp.visible.set(true);
        fixture.detectChanges();
    });

    it('lists the classes of the repository and the declared ones it no longer has', () => {
        expect(comp.options().map((option) => [option.simpleName, option.fromTests, option.helper])).toEqual([
            ['Helper', false, true],
            ['SortingTest', true, false],
            ['Util', false, false],
        ]);
        comp.search.set('sort');
        expect(comp.shown().map((option) => option.simpleName)).toEqual(['SortingTest']);
    });

    it('shows the classes as a tree of packages and declares all classes of a package at once', () => {
        fixture.detectChanges();
        // The dialog renders its content in an overlay outside the host element.
        const root = (fixture.nativeElement as HTMLElement).ownerDocument;
        expect(comp.groups().map((group) => [group.packageName, group.classes.map((option) => option.simpleName), group.selectable])).toEqual([
            ['de.a', ['Helper', 'SortingTest'], ['de.a.Helper']],
            ['de.gone', ['Util'], ['de.gone.Util']],
        ]);
        const packages = [...root.querySelectorAll<HTMLElement>('[data-testid="security-declare-classes-package"]')];
        expect(packages.map((row) => row.textContent?.trim())).toEqual(['de.a', 'de.gone']);
        expect(root.querySelectorAll('[data-testid="security-declare-classes-row"]')).toHaveLength(3);

        // The class of assigned test cases stays declared, so the package declares only its helper.
        handlers.togglePackage(comp.groups()[0]);
        expect([...comp.selection()]).toEqual(['de.gone.Util', 'de.a.Helper']);
        expect(comp.groups()[0].allSelected).toBe(true);
        handlers.togglePackage(comp.groups()[1]);
        expect([...comp.selection()]).toEqual(['de.a.Helper']);

        handlers.toggleOpen('de.a');
        fixture.detectChanges();
        expect(root.querySelectorAll('[data-testid="security-declare-classes-row"]')).toHaveLength(1);
        // A search shows its matches also in a collapsed package.
        comp.search.set('help');
        fixture.detectChanges();
        expect(root.querySelectorAll('[data-testid="security-declare-classes-row"]')).toHaveLength(1);
        expect(comp.groups().map((group) => [group.packageName, group.open])).toEqual([['de.a', true]]);
    });

    it('declares the checked classes and keeps their order', () => {
        handlers.toggle({ qualifiedName: 'de.a.Helper' });
        handlers.toggle({ qualifiedName: 'de.gone.Util' });
        handlers.toggle({ qualifiedName: 'de.gone.Util' });

        expect(comp.addedCount()).toBe(1);
        expect(comp.removedCount()).toBe(0);
        handlers.apply();
        expect(applied).toEqual([['de.gone.Util', 'de.a.Helper']]);
    });
});
