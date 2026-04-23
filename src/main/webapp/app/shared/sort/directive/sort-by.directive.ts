import { Directive, contentChild, effect, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition, faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';

import { SortDirective } from './sort.directive';

@Directive({
    selector: '[jhiSortBy]',
    host: { '(click)': 'onClick()' },
})
export class SortByDirective<T> {
    private sort = inject<SortDirective<T>>(SortDirective, { host: true });

    jhiSortBy = input.required<T>();

    iconComponent = contentChild(FaIconComponent);

    sortIcon = faSort;
    sortAscIcon = faSortUp;
    sortDescIcon = faSortDown;

    constructor() {
        effect(() => {
            // Track changes to predicate, ascending, and iconComponent
            this.sort.predicate();
            this.sort.ascending();
            this.updateIconDefinition();
        });
    }

    onClick(): void {
        if (this.iconComponent()) {
            this.sort.sort(this.jhiSortBy());
        }
    }

    private updateIconDefinition(): void {
        const icon = this.iconComponent();
        if (icon) {
            let iconDef: IconDefinition = this.sortIcon;
            if (this.sort.predicate() === this.jhiSortBy()) {
                iconDef = this.sort.ascending() ? this.sortAscIcon : this.sortDescIcon;
            }
            icon.icon.set(iconDef);
        }
    }
}
