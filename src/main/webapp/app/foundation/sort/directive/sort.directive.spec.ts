import { WritableSignal, outputBinding, signal, twoWayBinding } from '@angular/core';
import { DirectiveFixture, TestBed } from '@angular/core/testing';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { Mock, beforeEach, describe, expect, it, vi } from 'vitest';

describe('Directive: SortDirective', () => {
    let fixture: DirectiveFixture<SortDirective<string>>;
    let predicate: WritableSignal<string | undefined>;
    let ascending: WritableSignal<boolean | undefined>;
    let transition: Mock<(event: { predicate: string; ascending: boolean }) => void>;

    beforeEach(() => {
        predicate = signal<string | undefined>(undefined);
        ascending = signal<boolean | undefined>(undefined);
        transition = vi.fn();
        fixture = TestBed.createDirective<SortDirective<string>>(SortDirective, {
            tagName: 'tr',
            bindings: [twoWayBinding('predicate', predicate), twoWayBinding('ascending', ascending), outputBinding('sortChange', transition)],
        });
    });

    it('should update predicate, order and invoke callback function', () => {
        // GIVEN
        const sortDirective = fixture.directiveInstance;

        // WHEN
        fixture.detectChanges();
        sortDirective.sort('ID');

        // THEN
        expect(predicate()).toBe('ID');
        expect(ascending()).toBe(true);
        expect(transition).toHaveBeenCalledOnce();
    });

    it('should change sort order to descending when same field is sorted again', () => {
        // GIVEN
        const sortDirective = fixture.directiveInstance;

        // WHEN
        fixture.detectChanges();
        sortDirective.sort('ID');
        fixture.detectChanges(); // manual changeDetection in zoneless
        // sort again
        sortDirective.sort('ID');

        // THEN
        expect(predicate()).toBe('ID');
        expect(ascending()).toBe(false);
        expect(transition).toHaveBeenCalledTimes(2);
    });

    it('should change sort order to ascending when different field is sorted', () => {
        // GIVEN
        const sortDirective = fixture.directiveInstance;

        // WHEN
        fixture.detectChanges();
        sortDirective.sort('ID');
        // sort again
        sortDirective.sort('NAME');

        // THEN
        expect(predicate()).toBe('NAME');
        expect(ascending()).toBe(true);
        expect(transition).toHaveBeenCalledTimes(2);
    });
});
