import { Directive, input, output } from '@angular/core';

@Directive({ selector: '[jhiSort]' })
export class SortDirective<T> {
    predicate = input<T | undefined>();
    ascending = input<boolean>(false);

    predicateChange = output<T>();
    ascendingChange = output<boolean>();
    sortChange = output<{ predicate: T; ascending: boolean }>();

    sort(field: T): void {
        const newAscending = field !== this.predicate() ? true : !this.ascending();
        this.predicateChange.emit(field);
        this.ascendingChange.emit(newAscending);
        this.sortChange.emit({ predicate: field, ascending: newAscending });
    }
}
