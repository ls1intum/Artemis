import { Directive, model, output } from '@angular/core';

@Directive({ selector: '[jhiSort]' })
export class SortDirective<T> {
    predicate = model<T | undefined>();
    ascending = model<boolean>(false);

    predicateChange = output<T>();
    ascendingChange = output<boolean>();
    sortChange = output<{ predicate: T; ascending: boolean }>();

    sort(field: T): void {
        const newAscending = field !== this.predicate() ? true : !this.ascending();
        this.predicate.set(field);
        this.ascending.set(newAscending);
        this.sortChange.emit({ predicate: field, ascending: newAscending });
    }
}
