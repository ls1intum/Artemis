import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityDeclareClassesDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-declare-classes-dialog.component';

type Option = { qualifiedName: string };
type Handlers = { toggle(option: Option): void; apply(): void };

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
