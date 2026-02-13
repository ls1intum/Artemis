import { Directive, model, output } from '@angular/core';

@Directive({ selector: '[jhiSort]' })
export class SortDirective<T> {
    predicate = model<T | undefined>(undefined);
    ascending = model<boolean | undefined>(undefined);

    sortChange = output<{ predicate: T; ascending: boolean }>();

    sort(field: T): void {
        const newAscending = field !== this.predicate() ? true : !this.ascending();
        this.ascending.set(newAscending);
        this.predicate.set(field);
        this.sortChange.emit({ predicate: field, ascending: newAscending });
    }
}
