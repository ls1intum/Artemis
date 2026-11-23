import { DestroyRef } from '@angular/core';
import { Subject, map } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { createTeamSearch } from './team-search.util';

class SearchDestroyRef extends DestroyRef {
    override destroyed = false;
    private callback = () => {};

    override onDestroy(callback: () => void): () => void {
        this.callback = callback;
        return () => {};
    }

    destroy(): void {
        this.destroyed = true;
        this.callback();
    }
}

describe('createTeamSearch', () => {
    it('composes queries, labels and selection while rejecting disabled or foreign options', () => {
        const destroyRef = new SearchDestroyRef();
        const selected = vi.fn();
        const search = createTeamSearch(
            destroyRef,
            (queries) => queries.pipe(map((query) => [query.length])),
            (value) => `Length ${value}`,
            selected,
        );

        search.requestSearch('team');
        const option = search.suggestions()[0];
        expect(option).toEqual({ label: 'Length 4', value: 4, disabled: false });
        search.selectOption({ value: { ...option } });
        expect(selected).not.toHaveBeenCalled();
        option.disabled = true;
        search.selectOption({ value: option });
        expect(selected).not.toHaveBeenCalled();
        option.disabled = false;
        search.selectOption({ value: option });
        expect(selected).toHaveBeenCalledExactlyOnceWith(4);
    });

    it('stops observing results when its component is destroyed', () => {
        const destroyRef = new SearchDestroyRef();
        const results = new Subject<number[]>();
        const search = createTeamSearch(destroyRef, () => results, String, vi.fn());
        results.next([1]);
        destroyRef.destroy();
        results.next([2]);
        expect(search.suggestions().map((option) => option.value)).toEqual([1]);
        expect(results.observed).toBe(false);
    });
});
