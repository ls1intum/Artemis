import { DestroyRef, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumAetUiAutoCompleteOptionEvent } from '@tumaet/ui-angular/autocomplete';
import { Observable, Subject } from 'rxjs';

interface TeamSearchSuggestion<T> {
    label: string;
    value: T;
    disabled: boolean;
}

/** Connect a team's entity-specific search to the autocomplete's typed options and lifetime. */
export function createTeamSearch<T>(destroyRef: DestroyRef, search: (queries: Observable<string>) => Observable<T[]>, format: (value: T) => string, select: (value: T) => void) {
    const suggestions = signal<TeamSearchSuggestion<T>[]>([]);
    const queries = new Subject<string>();
    search(queries)
        .pipe(takeUntilDestroyed(destroyRef))
        .subscribe((values) => suggestions.set(values.map((value) => ({ label: format(value), value, disabled: false }))));

    return {
        suggestions,
        requestSearch: (query: string) => queries.next(query),
        selectOption: (event: TumAetUiAutoCompleteOptionEvent) => {
            const option = suggestions().find((suggestion) => suggestion === event.value);
            if (option && !option.disabled) {
                select(option.value);
            }
        },
    };
}
