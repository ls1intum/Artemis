import { signal } from '@angular/core';
import { afterEach, vi } from 'vitest';
import { ActionsColumnWidth } from 'app/exercise/exercise-action-bar/actions-column-width';

describe('ActionsColumnWidth', () => {
    const rowIds = signal<readonly number[]>([1, 2]);
    let column: ActionsColumnWidth;
    let element: HTMLElement;

    beforeEach(() => {
        rowIds.set([1, 2]);
        column = new ActionsColumnWidth(rowIds);
        element = document.createElement('div');
        vi.spyOn(element, 'clientWidth', 'get').mockReturnValue(1000);
    });

    afterEach(() => vi.restoreAllMocks());

    it('stays unset until a row has reported and the table width is known', () => {
        expect(column.cssWidth()).toBeUndefined();
        column.report(1, 200);
        expect(column.cssWidth()).toBeUndefined();
        column.observe(element)();
        expect(column.cssWidth()).toBe('20.100%');
    });

    it('sizes the column as a share of the table width, so it can shrink below the measured width', () => {
        column.observe(element)();
        column.report(1, 150);
        column.report(2, 250);
        expect(column.cssWidth()).toBe('25.100%');
    });

    it('follows the current rows, so the column narrows when the widest row goes or gets narrower', () => {
        column.observe(element)();
        column.report(1, 150);
        column.report(2, 250);

        rowIds.set([1]);
        expect(column.cssWidth()).toBe('15.100%');

        rowIds.set([1, 2]);
        column.report(2, 100);
        expect(column.cssWidth()).toBe('15.100%');
    });
});
